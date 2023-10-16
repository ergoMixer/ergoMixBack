package services.mixer

import javax.inject.Inject

import scala.util.Try

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import mixer._
import network.NetworkUtils
import play.api.Logger
import services.mixer.ScheduledJobs.RefreshMixingStats

class ErgoMixerJobs @Inject() (
  val covertMixer: CovertMixer,
  val groupMixer: GroupMixer,
  val rescan: Rescan,
  val halfMixer: HalfMixer,
  val fullMixer: FullMixer,
  val newMixer: NewMixer,
  val withdrawMixer: WithdrawMixer,
  val deposits: Deposits,
  val networkUtils: NetworkUtils,
  val hopMixer: HopMixer
)

class ScheduledJobs(mixerJobs: ErgoMixerJobs) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)
  import mixerJobs.networkUtils

  override def receive: Receive = {
    case RefreshMixingStats =>
      logger.info("Refreshing mixing stats: jobs started")

      networkUtils.pruneClients()
      if (networkUtils.clientsAreOk) {
        logger.info("covert mixes: " + Try(mixerJobs.covertMixer.processCovert()))
        logger.info("group mixes: " + Try(mixerJobs.groupMixer.processGroupMixes()))
        logger.info("deposits: " + Try(mixerJobs.deposits.processDeposits()))
        logger.info("new mixes: " + Try(mixerJobs.newMixer.processNewMixQueue()))
        logger.info("half mixes: " + Try(mixerJobs.halfMixer.processHalfMixQueue()))
        logger.info("full mixes: " + Try(mixerJobs.fullMixer.processFullMixQueue()))
        logger.info("process withdraws: " + Try(mixerJobs.withdrawMixer.processWithdrawals()))
        logger.info("hop mixes: " + Try(mixerJobs.hopMixer.processHopBoxes()))
        logger.info("rescans: " + Try(mixerJobs.rescan.processRescanQueue()))

        logger.info("Refreshing mixing stats: jobs finished")
      }
  }
}

object ScheduledJobs {

  def props(mixerJobs: ErgoMixerJobs)(implicit system: ActorSystem): Props =
    Props.create(classOf[ScheduledJobs], mixerJobs)

  case object RefreshMixingStats

}
