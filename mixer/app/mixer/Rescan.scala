package mixer

import wallet.WalletHelper.now

import javax.inject.Inject
import models.Models.{FollowedMix, FullMix, HalfMix, MixHistory, MixState, PendingRescan}
import dao.{DAOUtils, FullMixDAO, HalfMixDAO, MixStateDAO, MixStateHistoryDAO, MixingRequestsDAO, RescanDAO, SpentDepositsDAO}

class Rescan @Inject()(mixScanner: MixScanner,
                       daoUtils: DAOUtils,
                       mixingRequestsDAO: MixingRequestsDAO,
                       rescanDAO: RescanDAO,
                       spentDepositsDAO: SpentDepositsDAO,
                       mixStateHistoryDAO: MixStateHistoryDAO,
                       halfMixDAO: HalfMixDAO,
                       fullMixDAO: FullMixDAO,
                       mixStateDAO: MixStateDAO) {

  private implicit val insertReason = "rescan"

  def processRescanQueue() = {
    daoUtils.awaitResult(rescanDAO.all).foreach {
      case PendingRescan(mixId, _, round, goBackward, isHalfMixTx, mixBoxId) =>
        processRescan(mixId, round, goBackward, isHalfMixTx, mixBoxId)
        rescanDAO.delete(mixId)
    }
  }

  def processRescan(mixId: String, round: Int, goBackward: Boolean, isHalfMixTx: Boolean, mixBoxId: String) = {
    val masterSecretFuture = mixingRequestsDAO.selectMasterKey(mixId)
    val poolAmountFuture = mixingRequestsDAO.selectByMixId(mixId)
    val masterSecret = daoUtils.awaitResult(masterSecretFuture).getOrElse(throw new Exception("Unable to read master secret"))
    val poolAmount = daoUtils.awaitResult(poolAmountFuture).getOrElse(throw new Exception("Unable to read pool amount")).amount

    val followedMixes: Seq[FollowedMix] = if (!goBackward) { // go forward
      if (isHalfMixTx) mixScanner.followHalfMix(mixBoxId, round, masterSecret) else mixScanner.followFullMix(mixBoxId, round, masterSecret)
    } else { // go backward
      // TODO: Rescan from last good box instead of beginning. For now doing from beginning
      mixScanner.followDeposit(mixBoxId, masterSecret, poolAmount)
    }
    applyMixes(mixId, followedMixes)
  }

  private def updateMixHistory(mixId: String, round: Int, isAlice: Boolean) = {
    val mixHistory = MixHistory(mixId, round, isAlice, now)
    mixStateHistoryDAO.updateById(mixHistory)
  }

  private def updateFullMix(mixId: String, round: Int, halfMixBoxId: String, fullMixBoxId: String) = {
    val fullMix = FullMix(mixId, round, now, halfMixBoxId, fullMixBoxId)
    fullMixDAO.updateById(fullMix)
  }

  private def updateHalfMix(mixId: String, round: Int, halfMixBoxId: String, isSpent: Boolean) = {
    val halfMix = HalfMix(mixId, round, now, halfMixBoxId, isSpent)
    halfMixDAO.updateById(halfMix)
  }

  private def updateMixState(mixId: String, round: Int, isAlice: Boolean) = {
    val mixState = MixState(mixId, round, isAlice)
    mixStateDAO.updateInRescan(mixState)
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
    mixStateHistoryDAO.deleteFutureRounds(mixId, round)
    halfMixDAO.deleteFutureRounds(mixId, round)
    fullMixDAO.deleteFutureRounds(mixId, round)
  }

  private def applyMixes(mixId: String, followedMixes: Seq[FollowedMix]) = {
    followedMixes.foreach(applyMix(mixId, _))
    followedMixes.lastOption.map(lastMix => clearFutureRounds(mixId, lastMix.round))
  }

}
