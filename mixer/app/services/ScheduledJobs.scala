package services

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import mixer.{ErgoMixerJobs, Stats}
import services.ScheduledJobs.{RefreshMixingStats, RefreshPoolStats}

import scala.util.Try


class ScheduledJobs(mixerJobs: ErgoMixerJobs) extends Actor with ActorLogging {

  private def currentTimeString = {
    val date = new Date()
    val formatter = new SimpleDateFormat("HH:mm:ss")
    formatter.format(date)
  }

  override def receive: Receive = {
    case RefreshMixingStats =>
      println(s"$currentTimeString: Refreshing mixing stats: jobs started")
      println("group mixes: " + Try(mixerJobs.groupMixer.processGroupMixes()))
      println("deposits: " + Try(mixerJobs.deposits.processDeposits()))
      println("new mixes: " + Try(mixerJobs.newMixer.processNewMixQueue()))
      println("half mixes: " + Try(mixerJobs.halfMixer.processHalfMixQueue()))
      println("full mixes: " + Try(mixerJobs.fullMixer.processFullMixQueue()))
      println("process withdraws: " + Try(mixerJobs.withdrawMixer.processWtithraws()))
      println("rescans: " + Try(mixerJobs.rescan.processRescanQueue()))
      println(s"$currentTimeString: Refreshing mixing stats: jobs finished")
      // read mixing statuses table here and process each (unfinished) record based on its status

    case RefreshPoolStats =>
      println(s"$currentTimeString: Refreshing stats: jobs started")
      val res = mixerJobs.statScanner.scanTokens
      if (res != (Map.empty, 1000000)) {
        Stats.tokenPrices = Some(res._1)
        Stats.entranceFee = Some(res._2)
      }
      Stats.ringStats = mixerJobs.statScanner.ringStats()
      println(s"$currentTimeString: Refreshing stats: jobs finished")
  }
}

object ScheduledJobs {

  def props(mixerJobs: ErgoMixerJobs)(implicit system: ActorSystem): Props =
    Props.create(classOf[ScheduledJobs], mixerJobs)

  case object RefreshMixingStats
  case object RefreshPoolStats
}
