package services

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import app.Configs
import app.Configs.readKey
import mixer.Models.EntityInfo
import mixer.{ErgoMixerJobs, Stats}
import play.api.Logger
import services.ScheduledJobs.{RefreshMixingStats, RefreshPoolStats}

import scala.collection.mutable
import scala.util.Try


class ScheduledJobs(mixerJobs: ErgoMixerJobs) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  private def currentTimeString = {
    val date = new Date()
    val formatter = new SimpleDateFormat("HH:mm:ss")
    formatter.format(date)
  }

  override def receive: Receive = {
    case RefreshMixingStats =>
      logger.info(s"$currentTimeString: Refreshing mixing stats: jobs started")
      logger.info("covert mixes: " + Try(mixerJobs.covertMixer.processCovert()))
      logger.info("group mixes: " + Try(mixerJobs.groupMixer.processGroupMixes()))
      logger.info("deposits: " + Try(mixerJobs.deposits.processDeposits()))
      logger.info("new mixes: " + Try(mixerJobs.newMixer.processNewMixQueue()))
      logger.info("half mixes: " + Try(mixerJobs.halfMixer.processHalfMixQueue()))
      logger.info("full mixes: " + Try(mixerJobs.fullMixer.processFullMixQueue()))
      logger.info("process withdraws: " + Try(mixerJobs.withdrawMixer.processWitthraws()))
      logger.info("rescans: " + Try(mixerJobs.rescan.processRescanQueue()))
      logger.info(s"$currentTimeString: Refreshing mixing stats: jobs finished")
      // read mixing statuses table here and process each (unfinished) record based on its status

    case RefreshPoolStats =>
      if (!TrayUtils.shownNotification) {
        TrayUtils.shownNotification = true
        TrayUtils.showNotification("ErgoMixer is ready!", s"Go to http://localhost:${readKey("http.port", "9000")} in you browser.")
      }

      logger.info(s"$currentTimeString: Refreshing stats: jobs started")
      val res = mixerJobs.statScanner.scanTokens
      if (res != (Map.empty, 1000000)) {
        Stats.tokenPrices = Some(res._1)
        Stats.entranceFee = Some(res._2)
      }

      val idToParam = mutable.Map.empty[String, EntityInfo]
      mixerJobs.statScanner.scanParams.foreach(param => idToParam(param.id) = param)
      Configs.params = idToParam

      Stats.ringStats = mixerJobs.statScanner.ringStats()
      logger.info(s"$currentTimeString: Refreshing stats: jobs finished")
  }
}

object ScheduledJobs {

  def props(mixerJobs: ErgoMixerJobs)(implicit system: ActorSystem): Props =
    Props.create(classOf[ScheduledJobs], mixerJobs)

  case object RefreshMixingStats
  case object RefreshPoolStats
}
