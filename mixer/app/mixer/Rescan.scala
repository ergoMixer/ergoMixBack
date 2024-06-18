package mixer

import javax.inject.Inject

import dao.mixing._
import dao.DAOUtils
import models.Models.{FullMix, HalfMix, HopMix, MixHistory, MixState}
import models.Rescan.{FollowedHop, FollowedMix, FollowedWithdraw, PendingRescan}
import models.Status.MixStatus.Complete
import models.Status.MixWithdrawStatus.UnderHop
import models.Transaction.WithdrawTx
import play.api.Logger
import wallet.WalletHelper.now

class Rescan @Inject() (
  mixScanner: MixScanner,
  daoUtils: DAOUtils,
  mixingRequestsDAO: MixingRequestsDAO,
  rescanDAO: RescanDAO,
  mixStateHistoryDAO: MixStateHistoryDAO,
  halfMixDAO: HalfMixDAO,
  fullMixDAO: FullMixDAO,
  mixStateDAO: MixStateDAO,
  hopMixDAO: HopMixDAO,
  withdrawDAO: WithdrawDAO
) {

  private val logger: Logger = Logger(this.getClass)

  implicit private val insertReason: String = "rescan"

  def processRescanQueue(): Unit =
    daoUtils.awaitResult(rescanDAO.all).foreach {
      case PendingRescan(mixId, _, round, goBackward, boxType, mixBoxId) =>
        try {
          processRescan(mixId, round, goBackward, boxType, mixBoxId)
          rescanDAO.delete(mixId)
        } catch {
          case e: Throwable =>
            logger.error(e.getMessage)
        }
    }

  def processRescan(mixId: String, round: Int, goBackward: Boolean, boxType: String, mixBoxId: String): Unit = {
    logger.info(s" [RESCAN: $mixId] boxId: $mixBoxId processing. boxType: $boxType, goBackward: $goBackward")
    val masterSecret = daoUtils
      .awaitResult(mixingRequestsDAO.selectMasterKey(mixId))
      .getOrElse(throw new Exception("Unable to read master secret"))

    if (!goBackward) { // go forward
      boxType match {
        case "half" =>
          val followedMixes: Seq[FollowedMix] = mixScanner.followHalfMix(mixBoxId, round, masterSecret)
          applyMixes(mixId, followedMixes)

          val lastMixBox =
            if (followedMixes.nonEmpty) followedMixes.last.fullMixBoxId.getOrElse(followedMixes.last.halfMixBoxId)
            else mixBoxId
          processWithdrawal(mixId, lastMixBox, masterSecret)

        case "full" =>
          val followedMixes: Seq[FollowedMix] = mixScanner.followFullMix(mixBoxId, round, masterSecret)
          applyMixes(mixId, followedMixes)

          val lastMixBox =
            if (followedMixes.nonEmpty) followedMixes.last.fullMixBoxId.getOrElse(followedMixes.last.halfMixBoxId)
            else mixBoxId
          processWithdrawal(mixId, lastMixBox, masterSecret)

        case "hop" =>
          val (followedHop, followedWithdraw) = mixScanner.followHopMix(mixBoxId, round, masterSecret)
          applyHopMixes(mixId, followedHop)
          if (followedWithdraw.isDefined) applyWithdrawTx(mixId, followedWithdraw.get, UnderHop.value)
      }
    } else { // go backward
      if (round == 0 && boxType != "hop") {
        // TODO: in this case, should get deposit box and follow forward from it.
        logger.warn(s"  backward rescan for $boxType in round 0 not implemented...")
        return
      }
      boxType match {
        case "half" =>
          val mixStateHistory = daoUtils
            .awaitResult(mixStateHistoryDAO.getStateHistoryByIdAndRounds(mixId, round - 1))
            .getOrElse(
              throw new Exception(s"Unable to retrieve mix state history for mixId: $mixId and round: ${round - 1}")
            )
          clearFutureRounds(mixId, round - 1, Some(mixStateHistory.isAlice))
          val previousFullBox = daoUtils
            .awaitResult(fullMixDAO.getMixBoxIdByRound(mixId, round - 1))
            .getOrElse(throw new Exception("Unable to retrieve previous full box"))

          val followedMixes: Seq[FollowedMix] = mixScanner.followFullMix(previousFullBox, round - 1, masterSecret)
          applyMixes(mixId, followedMixes)

        case "full" =>
          val previousHalfBox = daoUtils.awaitResult(halfMixDAO.getMixBoxIdByRound(mixId, round))
          if (previousHalfBox.isDefined) {
            daoUtils.awaitResult(mixStateHistoryDAO.deleteFutureRounds(mixId, round - 1))
            daoUtils.awaitResult(fullMixDAO.deleteFutureRounds(mixId, round - 1))

            val followedMixes: Seq[FollowedMix] = mixScanner.followHalfMix(previousHalfBox.get, round, masterSecret)
            applyMixes(mixId, followedMixes)
          } else {
            val mixStateHistory = daoUtils
              .awaitResult(mixStateHistoryDAO.getStateHistoryByIdAndRounds(mixId, round - 1))
              .getOrElse(
                throw new Exception(s"Unable to retrieve mix state history for mixId: $mixId and round: ${round - 1}")
              )
            clearFutureRounds(mixId, round - 1, Some(mixStateHistory.isAlice))
            val previousFullBox = daoUtils
              .awaitResult(fullMixDAO.getMixBoxIdByRound(mixId, round - 1))
              .getOrElse(throw new Exception("Unable to retrieve previous full box"))

            val followedMixes: Seq[FollowedMix] = mixScanner.followFullMix(previousFullBox, round - 1, masterSecret)
            applyMixes(mixId, followedMixes)
          }

        case "hop" =>
          hopMixDAO.deleteFutureRounds(mixId, round - 1)
          val previousHopBox = daoUtils
            .awaitResult(hopMixDAO.getMixBoxIdByRound(mixId, round - 1))
            .getOrElse(throw new Exception("Unable to retrieve previous hop box"))
          val (followedHop, followedWithdraw) = mixScanner.followHopMix(previousHopBox, round - 1, masterSecret)
          applyHopMixes(mixId, followedHop)
          if (followedWithdraw.isDefined) applyWithdrawTx(mixId, followedWithdraw.get, UnderHop.value)
      }
    }

  }

  /**
   * recovers possible withdraw or hops and updates the database
   *
   * @param mixId        mix request ID
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

  private def updateMixHistory(mixId: String, round: Int, isAlice: Boolean): Unit = {
    val mixHistory = MixHistory(mixId, round, isAlice, now)
    daoUtils.awaitResult(mixStateHistoryDAO.updateById(mixHistory))
  }

  private def updateFullMix(mixId: String, round: Int, halfMixBoxId: String, fullMixBoxId: String): Unit = {
    val fullMix = FullMix(mixId, round, now, halfMixBoxId, fullMixBoxId)
    daoUtils.awaitResult(fullMixDAO.updateById(fullMix))
  }

  private def updateHalfMix(mixId: String, round: Int, halfMixBoxId: String, isSpent: Boolean): Unit = {
    val halfMix = HalfMix(mixId, round, now, halfMixBoxId, isSpent)
    daoUtils.awaitResult(halfMixDAO.updateById(halfMix))
  }

  private def updateMixState(mixId: String, round: Int, isAlice: Boolean): Unit = {
    val mixState = MixState(mixId, round, isAlice)
    daoUtils.awaitResult(mixStateDAO.updateInRescan(mixState))
  }

  private def updateHopMix(mixId: String, round: Int, hopBoxId: String): Unit = {
    val hopMix = HopMix(mixId, round, now, hopBoxId)
    daoUtils.awaitResult(hopMixDAO.updateById(hopMix))
  }

  private def applyMix(mixId: String, followedMix: FollowedMix): Unit =
    followedMix match {
      case FollowedMix(round, true, halfMixBoxId, Some(fullMixBoxId)) =>
        updateFullMix(mixId, round, halfMixBoxId, fullMixBoxId)
        updateHalfMix(mixId, round, halfMixBoxId, isSpent = true)
        updateMixHistory(mixId, round, isAlice            = true)
        updateMixState(mixId, round, isAlice              = true)

      case FollowedMix(round, true, halfMixBoxId, None) =>
        updateHalfMix(mixId, round, halfMixBoxId, isSpent = false)
        updateMixHistory(mixId, round, isAlice            = true)
        updateMixState(mixId, round, isAlice              = true)

      case FollowedMix(round, false, halfMixBoxId, Some(fullMixBoxId)) =>
        updateFullMix(mixId, round, halfMixBoxId, fullMixBoxId)
        updateMixHistory(mixId, round, isAlice = false)
        updateMixState(mixId, round, isAlice   = false)

      case _ => throw new Exception("this case should never happen")
    }

  private def clearFutureRounds(mixId: String, round: Int, isAliceOption: Option[Boolean]): Unit = {
    daoUtils.awaitResult(mixStateHistoryDAO.deleteFutureRounds(mixId, round))
    daoUtils.awaitResult(halfMixDAO.deleteFutureRounds(mixId, round))
    daoUtils.awaitResult(fullMixDAO.deleteFutureRounds(mixId, round))
    if (isAliceOption.isDefined)
      daoUtils.awaitResult(mixStateDAO.updateInRescan(MixState(mixId, round, isAliceOption.get)))
  }

  private def applyMixes(mixId: String, followedMixes: Seq[FollowedMix]) = {
    followedMixes.foreach(applyMix(mixId, _))
    followedMixes.lastOption.map(lastMix => clearFutureRounds(mixId, lastMix.round, Option.empty))
  }

  private def applyHopMixes(mixId: String, followedHopMixes: Seq[FollowedHop]) = {
    followedHopMixes.foreach(followedHop => updateHopMix(mixId, followedHop.round, followedHop.hopMixBoxId))
    followedHopMixes.lastOption.map(lastHop => hopMixDAO.deleteFutureRounds(mixId, lastHop.round))
  }

  private def applyWithdrawTx(mixId: String, tx: FollowedWithdraw, withdrawStatus: String): Unit = {
    val withdrawTx = WithdrawTx(mixId, tx.txId, now, tx.boxId, Array.empty[Byte], "generated by rescan")
    daoUtils.awaitResult(withdrawDAO.updateById(withdrawTx, withdrawStatus))
  }

}
