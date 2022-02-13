package services

import java.text.SimpleDateFormat
import java.util.Date
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import app.Configs
import app.Configs.readKey
import helpers.TrayUtils

import javax.inject.Inject
import mixer._
import models.Models.EntityInfo
import network.NetworkUtils
import play.api.Logger
import services.ScheduledJobs.{RefreshMixingStats, RefreshPoolStats}

import scala.collection.mutable
import scala.util.Try
import dao.Prune


class ErgoMixerJobs @Inject()(val ergoMixer: ErgoMixer, val covertMixer: CovertMixer, val groupMixer: GroupMixer,
                              val rescan: Rescan, val halfMixer: HalfMixer, val fullMixer: FullMixer, val newMixer: NewMixer,
                              val withdrawMixer: WithdrawMixer, val deposits: Deposits, val prune: Prune,
                              val statScanner: ChainScanner, val networkUtils: NetworkUtils)

class ScheduledJobs(mixerJobs: ErgoMixerJobs) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)
  import mixerJobs.networkUtils

  private def currentTimeString = {
    val date = new Date()
    val formatter = new SimpleDateFormat("HH:mm:ss")
    formatter.format(date)
  }

  override def receive: Receive = {
    case RefreshMixingStats =>
      logger.info(s"$currentTimeString: Refreshing mixing stats: jobs started")

      val wasOk = networkUtils.prunedClients.nonEmpty
      networkUtils.pruneClients()
      if (networkUtils.prunedClients.isEmpty) {
        logger.error("there is no node to connect to! will stop mixing until a node is available!")
        if (wasOk) {
          TrayUtils.showNotification("Can not connect to any node!", s"ErgoMixer can not connect to any specified nodes in the config. We stop mixing until the issue is resolved!")
        }

      } else {
        if (!wasOk) {
          logger.error("Issue with nodes is resolved, will continue mixing.")
          TrayUtils.showNotification(s"Issue with node connectivity is resolved!", s"ErgoMixer can connect to ${networkUtils.prunedClients.size} nodes, will continue mixing!")
        }

        logger.info("covert mixes: " + Try(mixerJobs.covertMixer.processCovert()))
        logger.info("group mixes: " + Try(mixerJobs.groupMixer.processGroupMixes()))
        logger.info("deposits: " + Try(mixerJobs.deposits.processDeposits()))
        logger.info("new mixes: " + Try(mixerJobs.newMixer.processNewMixQueue()))
        logger.info("half mixes: " + Try(mixerJobs.halfMixer.processHalfMixQueue()))
        logger.info("full mixes: " + Try(mixerJobs.fullMixer.processFullMixQueue()))
        logger.info("process withdraws: " + Try(mixerJobs.withdrawMixer.processWithdrawals()))
        logger.info("rescans: " + Try(mixerJobs.rescan.processRescanQueue()))
        logger.info(s"$currentTimeString: Refreshing mixing stats: jobs finished")
      }

    case RefreshPoolStats =>
      if (!TrayUtils.shownNotification) {
        TrayUtils.shownNotification = true
        TrayUtils.showNotification("ErgoMixer is ready!", s"Go to http://localhost:${readKey("http.port", "9000")} in you browser.")
      }

      logger.info(s"$currentTimeString: Refreshing stats: jobs started")
      val res = mixerJobs.statScanner.scanTokens
      if (res != (Map.empty, 1000000)) {
        Configs.tokenPrices = Some(res._1)
        Configs.entranceFee = Some(res._2)
      }

      val idToParam = mutable.Map.empty[String, EntityInfo]
      mixerJobs.statScanner.scanParams.foreach(param => idToParam(param.id) = param)
      Configs.dynamicFeeRate = idToParam.head._2.dynamicFeeRate
      Configs.params = idToParam

      Configs.ringStats = mixerJobs.statScanner.ringStats()

      logger.info("pruneDB: " + Try(mixerJobs.prune.processPrune()))

      logger.info(s"$currentTimeString: Refreshing stats: jobs finished")

  }
}

object ScheduledJobs {

  def props(mixerJobs: ErgoMixerJobs)(implicit system: ActorSystem): Props =
    Props.create(classOf[ScheduledJobs], mixerJobs)

  case object RefreshMixingStats

  case object RefreshPoolStats

}

object StealthJobsInfo {
  val InitBestBlockInDb = "store best block in db"
  val blockChainScan = "block scanned"
  val spendStealth = "spend stealth boxes"
}

class StealthJobs(scanner: ScannerTask, initBestBlock: InitBestBlockTask) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  /**
   * periodically start scanner, task.
   */
  def receive: PartialFunction[Any, Unit] = {
    case StealthJobsInfo.InitBestBlockInDb =>
      logger.info("Start job Store Best Block task.")
      Try(initBestBlock.store_block())
    case StealthJobsInfo.blockChainScan =>
      logger.info("Start job scanner task.")
      Try(scanner.start())
    case StealthJobsInfo.spendStealth =>
      logger.info("Start spending stealth boxes.")
      Try(scanner.spendStealth())
  }
}