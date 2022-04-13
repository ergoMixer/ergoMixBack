package mixer

import wallet.WalletHelper.now

import javax.inject.Inject
import models.Models.{FollowedHop, FollowedMix, FollowedWithdraw, FullMix, HalfMix, HopMix, MixHistory, MixState, PendingRescan, WithdrawTx}
import dao.{DAOUtils, FullMixDAO, HalfMixDAO, HopMixDAO, MixStateDAO, MixStateHistoryDAO, MixingRequestsDAO, RescanDAO, WithdrawDAO}
import models.Models.MixStatus.Complete
import models.Models.MixWithdrawStatus.{UnderHop, WithdrawRequested}
import network.NetworkUtils
import org.ergoplatform.appkit.BlockchainContext

class Rescan @Inject()(networkUtils: NetworkUtils,
                       mixScanner: MixScanner,
                       daoUtils: DAOUtils,
                       mixingRequestsDAO: MixingRequestsDAO,
                       rescanDAO: RescanDAO,
                       mixStateHistoryDAO: MixStateHistoryDAO,
                       halfMixDAO: HalfMixDAO,
                       fullMixDAO: FullMixDAO,
                       mixStateDAO: MixStateDAO,
                       hopMixDAO: HopMixDAO,
                       withdrawDAO: WithdrawDAO) {

  private implicit val insertReason = "rescan"

  def processRescanQueue(): Unit = {
    daoUtils.awaitResult(rescanDAO.all).foreach {
      case PendingRescan(mixId, _, round, goBackward, boxType, mixBoxId) =>
        processRescan(mixId, round, goBackward, boxType, mixBoxId)
        rescanDAO.delete(mixId)
    }
  }

  def processRescan(mixId: String, round: Int, goBackward: Boolean, boxType: String, mixBoxId: String): Unit = {
    val masterSecretFuture = mixingRequestsDAO.selectMasterKey(mixId)
    val poolAmountFuture = mixingRequestsDAO.selectByMixId(mixId)
    val masterSecret = daoUtils.awaitResult(masterSecretFuture).getOrElse(throw new Exception("Unable to read master secret"))
    val poolAmount = daoUtils.awaitResult(poolAmountFuture).getOrElse(throw new Exception("Unable to read pool amount")).amount

    if (!goBackward) { // go forward
      boxType match {
        case "half" =>
          val followedMixes: Seq[FollowedMix] = mixScanner.followHalfMix(mixBoxId, round, masterSecret)
          applyMixes(mixId, followedMixes)

          val lastMixBox = if(followedMixes.nonEmpty) followedMixes.last.fullMixBoxId.getOrElse(followedMixes.last.halfMixBoxId) else mixBoxId
          processWithdrawal(mixId, lastMixBox, masterSecret)

        case "full" =>
          val followedMixes: Seq[FollowedMix] = mixScanner.followFullMix(mixBoxId, round, masterSecret)
          applyMixes(mixId, followedMixes)

          val lastMixBox = if(followedMixes.nonEmpty) followedMixes.last.fullMixBoxId.getOrElse(followedMixes.last.halfMixBoxId) else mixBoxId
          processWithdrawal(mixId, lastMixBox, masterSecret)

        case "hop" =>
          val (followedHop, followedWithdraw) = mixScanner.followHopMix(mixBoxId, round, masterSecret)
          applyHopMixes(mixId, followedHop)
          if (followedWithdraw.isDefined) applyWithdrawTx(mixId, followedWithdraw.get, UnderHop.value)
      }
    }
    else { // go backward
      // TODO: Rescan from last good box instead of beginning. For now doing from beginning
      val followedMixes: Seq[FollowedMix] = mixScanner.followDeposit(mixBoxId, masterSecret, poolAmount)
      applyMixes(mixId, followedMixes)
    }

  }

  /**
   * recovers possible withdraw or hops and updates the database
   *
   * @param mixId mix request ID
   * @param lastMixBoxId last known box in mix request
   * @param masterSecret master secret key of mix request
   */
  def processWithdrawal(mixId: String, lastMixBoxId: String, masterSecret: BigInt): Unit = {
    val (followedHopMixes, followedWithdraw) = mixScanner.followWithdrawal(lastMixBoxId, masterSecret)

    if (followedHopMixes.nonEmpty) {
      daoUtils.awaitResult(mixingRequestsDAO.updateMixStatus(mixId, Complete))
      applyHopMixes(mixId, followedHopMixes)
    }
    if (followedWithdraw.isDefined) applyWithdrawTx(mixId, followedWithdraw.get, UnderHop.value)
  }

  private def updateMixHistory(mixId: String, round: Int, isAlice: Boolean) = {
    val mixHistory = MixHistory(mixId, round, isAlice, now)
    daoUtils.awaitResult(mixStateHistoryDAO.updateById(mixHistory))
  }

  private def updateFullMix(mixId: String, round: Int, halfMixBoxId: String, fullMixBoxId: String) = {
    val fullMix = FullMix(mixId, round, now, halfMixBoxId, fullMixBoxId)
    daoUtils.awaitResult(fullMixDAO.updateById(fullMix))
  }

  private def updateHalfMix(mixId: String, round: Int, halfMixBoxId: String, isSpent: Boolean) = {
    val halfMix = HalfMix(mixId, round, now, halfMixBoxId, isSpent)
    daoUtils.awaitResult(halfMixDAO.updateById(halfMix))
  }

  private def updateMixState(mixId: String, round: Int, isAlice: Boolean) = {
    val mixState = MixState(mixId, round, isAlice)
    daoUtils.awaitResult(mixStateDAO.updateInRescan(mixState))
  }

  private def updateHopMix(mixId: String, round: Int, hopBoxId: String) = {
    val hopMix = HopMix(mixId, round, now, hopBoxId)
    daoUtils.awaitResult(hopMixDAO.updateById(hopMix))
  }

  private def applyMix(mixId: String, followedMix: FollowedMix) = {
    followedMix match {
      case FollowedMix(round, true, halfMixBoxId, Some(fullMixBoxId)) =>

        updateFullMix(mixId, round, halfMixBoxId, fullMixBoxId)
        updateHalfMix(mixId, round, halfMixBoxId, isSpent = true)
        updateMixHistory(mixId, round, isAlice = true)
        updateMixState(mixId, round, isAlice = true)

      case FollowedMix(round, true, halfMixBoxId, None) =>

        updateHalfMix(mixId, round, halfMixBoxId, isSpent = false)
        updateMixHistory(mixId, round, isAlice = true)
        updateMixState(mixId, round, isAlice = true)

      case FollowedMix(round, false, halfMixBoxId, Some(fullMixBoxId)) =>

        updateFullMix(mixId, round, halfMixBoxId, fullMixBoxId)
        updateMixHistory(mixId, round, isAlice = false)
        updateMixState(mixId, round, isAlice = false)

      case _ => ??? // should never happen
    }
  }

  private def clearFutureRounds(mixId: String, round: Int): Unit = {
    daoUtils.awaitResult(mixStateHistoryDAO.deleteFutureRounds(mixId, round))
    daoUtils.awaitResult(halfMixDAO.deleteFutureRounds(mixId, round))
    daoUtils.awaitResult(fullMixDAO.deleteFutureRounds(mixId, round))
  }

  private def applyMixes(mixId: String, followedMixes: Seq[FollowedMix]) = {
    followedMixes.foreach(applyMix(mixId, _))
    followedMixes.lastOption.map(lastMix => clearFutureRounds(mixId, lastMix.round))
  }

  private def applyHopMixes(mixId: String, followedHopMixes: Seq[FollowedHop]) = {
    followedHopMixes.foreach(followedHop => updateHopMix(mixId, followedHop.round, followedHop.hopMixBoxId))
    followedHopMixes.lastOption.map(lastHop => hopMixDAO.deleteFutureRounds(mixId, lastHop.round))
  }

  private def applyWithdrawTx(mixId: String, tx: FollowedWithdraw, withdrawStatus: String) = {
    val withdrawTx = WithdrawTx(mixId, tx.txId, now, tx.boxId, Array.empty[Byte], "generated by rescan")
    daoUtils.awaitResult(withdrawDAO.updateById(withdrawTx, withdrawStatus))
  }

}
