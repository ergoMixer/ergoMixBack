package mixer

import db.ScalaDB._
import db.core.DataStructures.anyToAny
import mixer.Columns._
import mixer.Models.MixStatus.{Queued, Running}
import mixer.Models.{FollowedMix, PendingRescan}
import mixer.Util.now

class Rescan(tables: Tables) {
  import tables._
  private implicit val insertReason = "rescan"

  def processRescanQueue() = {
    rescanTable.selectStar.as(PendingRescan(_)).foreach {
      case PendingRescan(mixId, _, round, goBackward, isHalfMixTx, mixBoxId) =>
        processRescan(mixId, round, goBackward, isHalfMixTx, mixBoxId)
        rescanTable.deleteWhere(mixIdCol === mixId)
    }
  }

  def processRescan(mixId: String, round: Int, goBackward: Boolean, isHalfMixTx: Boolean, mixBoxId: String) = {
    val masterSecret = mixRequestsTable.select(masterSecretCol).where(mixIdCol === mixId).firstAsT[BigDecimal].headOption.map(_.toBigInt()).getOrElse(throw new Exception("Unable to read master secret"))
    val poolAmount = mixRequestsTable.select(amountCol).where(mixIdCol === mixId).firstAsT[Long].headOption.getOrElse(throw new Exception("Unable to read pool amount"))
    val followedMixes: Seq[FollowedMix] = if (!goBackward) { // go forward
      if (isHalfMixTx) MixScanner.followHalfMix(mixBoxId, round, masterSecret) else MixScanner.followFullMix(mixBoxId, round, masterSecret)
    } else { // go backward
      // TODO: Rescan from last good box instead of beginning. For now doing from beginning
      MixScanner.followDeposit(mixBoxId, masterSecret, poolAmount)
    }
    applyMixes(mixId, followedMixes)
  }

  @deprecated("This takes a lot of time. Use followFullMix and followHalfMix", "1.0")
  def getFollowedMix(mixId: String): Seq[FollowedMix] = {
    mixRequestsTable.select(depositAddressCol, masterSecretCol, amountCol).where(mixIdCol === mixId).as(a => (a(0).as[String], a(1).as[BigDecimal], a(2).as[Int])).headOption.flatMap {
      case (depositAddress, bigDecimal, poolAmount) =>
        spentDepositsTable.select(boxIdCol).where(addressCol === depositAddress).firstAsT[String].headOption.map(boxId => MixScanner.followDeposit(boxId, bigDecimal.toBigInt(), poolAmount))
    }.getOrElse(throw new Exception("Invalid mixID"))
  }

  private def updateMixHistory(mixId: String, round: Int, isAlice: Boolean) = {
    mixStateHistoryTable.deleteWhere(mixIdCol === mixId, roundCol === round)
    tables.insertMixStateHistory(mixId, round, isAlice, now)
  }

  private def updateFullMix(mixId: String, round: Int, halfMixBoxId: String, fullMixBoxId: String) = {
    fullMixTable.deleteWhere(mixIdCol === mixId, roundCol === round)
    tables.insertFullMix(mixId, round, now, halfMixBoxId, fullMixBoxId)
  }

  private def updateHalfMix(mixId: String, round: Int, halfMixBoxId: String, isSpent: Boolean) = {
    halfMixTable.deleteWhere(mixIdCol === mixId, roundCol === round)
    tables.insertHalfMix(mixId, round, now, halfMixBoxId, isSpent)
  }

  private def updateMixState(mixId: String, round: Int, isAlice: Boolean) = {
    mixStateTable.deleteWhere(mixIdCol === mixId)
    mixStateTable.insert(mixId, round, isAlice)
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
    fullMixTable.deleteWhere(mixIdCol === mixId, roundCol > round)
    halfMixTable.deleteWhere(mixIdCol === mixId, roundCol > round)
    mixStateHistoryTable.deleteWhere(mixIdCol === mixId, roundCol > round)
  }

  private def applyMixes(mixId: String, followedMixes: Seq[FollowedMix]) = {
    followedMixes.foreach(applyMix(mixId, _))
    followedMixes.lastOption.map(lastMix => clearFutureRounds(mixId, lastMix.round))
  }

  @deprecated("Use only manually", "1.0")
  def manualRescanFromStart(mixId: String) = {
    mixRequestsTable.select(mixStatusCol).where(
      mixIdCol === mixId
    ).firstAsT[String].headOption match {
      case Some(Running.value) | Some(Queued.value) => // ok
        applyMixes(mixId, getFollowedMix(mixId))
      case _ => throw new Exception("Cannot only rescan running or queued mixes")
    }
  }

}
