package services.main

import javax.inject.Inject

import scala.collection.mutable
import scala.util.Try

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import config.MainConfigs
import config.MainConfigs.readKey
import dao.Prune
import helpers.TrayUtils
import mixer.ChainScanner
import models.Models.EntityInfo
import network.NetworkUtils
import play.api.Logger
import services.main.MainScheduledJobs._
import stealth.{FetchTokenInformation, StealthContract, StealthScanner}

class MainJobs @Inject() (
  val prune: Prune,
  val statScanner: ChainScanner,
  val networkUtils: NetworkUtils,
  val trayUtils: TrayUtils,
  val stealthContract: StealthContract,
  val stealthScanner: StealthScanner,
  val fetchTokenInformation: FetchTokenInformation
)

class MainScheduledJobs(mainJobs: MainJobs) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  import mainJobs.{networkUtils, trayUtils}

  override def receive: Receive = {
    case CheckClients =>
      logger.info("Check Clients: jobs started")

      val wasOk = networkUtils.prunedClients.nonEmpty
      networkUtils.pruneClients()
      if (networkUtils.prunedClients.isEmpty) {
        logger.error("there is no node to connect to! will stop mixing until a node is available!")
        if (wasOk) {
          trayUtils.showNotification(
            "Can not connect to any node!",
            s"ErgoMixer can not connect to any specified nodes in the config. We stop mixing until the issue is resolved!"
          )
        }

      } else {
        if (!wasOk) {
          logger.error("Issue with nodes is resolved, will continue mixing.")
          trayUtils.showNotification(
            s"Issue with node connectivity is resolved!",
            s"ErgoMixer can connect to ${networkUtils.prunedClients.size} nodes, will continue mixing!"
          )
        }
      }
      logger.info("Check Clients: jobs finished")

    case RefreshPoolStats =>
      if (!trayUtils.notificationShownState()) {
        trayUtils.activeNotificationState()
        var title = ""
        if (MainConfigs.isAdmin) title = "ADMIN MIXER is ready!" else title = "ErgoMixer is ready!"
        trayUtils.showNotification(title, s"Go to http://localhost:${readKey("http.port", "9000")} in you browser.")

      }
      logger.info("Refreshing stats: jobs started")
      val res = mainJobs.statScanner.scanTokens
      if (res != (Map.empty, 1000000)) {
        MainConfigs.tokenPrices = Some(res._1)
        MainConfigs.entranceFee = Some(res._2)
      }

      val idToParam = mutable.Map.empty[String, EntityInfo]
      mainJobs.statScanner.scanParams.foreach(param => idToParam(param.id) = param)
      MainConfigs.dynamicFeeRate = idToParam.head._2.dynamicFeeRate
      MainConfigs.params         = idToParam

      logger.info("pruneDB: " + Try(mainJobs.prune.processPrune()))

      logger.info("Refreshing stats: jobs finished")

    case UpdateRingStats =>
      logger.info("Updating ring stats: jobs started")

      MainConfigs.ringStats = mainJobs.statScanner.ringStats()

      logger.info("Updating ring stats: jobs finished")

    case InitBestBlock =>
      logger.info("Initial best block for scanner of stealth: jobs started")

      Try(mainJobs.stealthScanner.storeBlock())

      logger.info("Initial best block for scanner of stealth: jobs finished")

    case StealthScanner =>
      logger.info("Scanner of stealth: jobs started")

      Try(mainJobs.stealthScanner.start())

      logger.info("Scanner of stealth: jobs finished")

    case FetchTokenInformation =>
      logger.info("Fetch Token Information: jobs started")

      Try(mainJobs.fetchTokenInformation.fetchTokenInformation())

      logger.info("Fetch Token Information: jobs finished")

    case SpendStealth =>
      logger.info("Spending stealth boxes: jobs started")

      networkUtils.usingClient(implicit ctx => Try(mainJobs.stealthContract.spendStealthBoxes()))

      logger.info("Spending stealth boxes: jobs finished")
  }
}

object MainScheduledJobs {

  def props(mainJobs: MainJobs)(implicit system: ActorSystem): Props =
    Props.create(classOf[MainScheduledJobs], mainJobs)

  case object CheckClients

  case object RefreshPoolStats

  case object UpdateRingStats

  case object InitBestBlock

  case object StealthScanner

  case object FetchTokenInformation

  case object SpendStealth

}
