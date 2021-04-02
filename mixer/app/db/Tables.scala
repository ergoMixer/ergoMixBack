package db

import db.ScalaDB._
import models.Models.MixStatus.Queued
import models.Models.MixWithdrawStatus.{AgeUSDRequested, WithdrawRequested}
import models.Models.{Deposit, MixHistory, MixRequest, WithdrawTx}
import org.ergoplatform.appkit.SignedTransaction
import play.api.Logger
import db.core.DataStructures.anyToAny
import javax.inject.Inject
import network.{BlockExplorer, NetworkUtils}
import play.api.db.Database

class Tables @Inject() (playDB: Database, networkUtils: NetworkUtils, explorer: BlockExplorer) {
  private val logger: Logger = Logger(this.getClass)

  import db.Columns._

  implicit val playDb: Database = playDB
  /*
   The need to undo any of the steps (described below) can arise due to any of the following reasons:
     (F) Fork
     (H) A half-mix spent by us (when behaving like Bob) is also spent by someone else and our transaction gets rejected
     (E) A fee emission box spent by us is also spent by someone else and our transaction gets rejected.

   The steps to be undone can be classified into any of the 5. The possible undo reasons are also given
   1. Entry as Alice, i.e., round 0 as Alice (F)
   2. Entry as Bob, i.e., round 0 as Bob (F, H)
   3. Remix as Alice (F, E)
   4. Remix as Bob (F, H, E)
   5. Withdraw (F)

   Since 1 and 5 need to be undone only in the case of a fork, which is rare (assuming minConfirmation is large enough), we will keep the task manual for now.
   Note that 5 is already implemented as undoWithdraw below.

   TODO: Check if undo for 1 is already handled in Alice undo case, if not implement it

   The case for 2, 3, 4 is implemented below. Since these transactions can be undone due to a double spend, they must be handed automatically via jobs

   TODO: Track spent boxes so that above undo operations (1 and 5) are done via a job in case of a fork

   We must have archive tables for any tables that are affected by undo:
    1. unspentDeposits (done)
    2. spentDeposits (done)
    3. mixStateHistory (different from mixState!) (done)
    4. fullMix (done)
    5. halfMix (done)
    6. withdraw (done)
  */

  // covert mix
  val mixCovertCols = Seq(nameCovertCol, mixGroupIdCol, createdTimeCol, depositAddressCol, numTokenCol, isManualCovertCol)
  val mixCovertTable = Tab.withName("mixing_covert_request").withCols(mixCovertCols :+ masterSecretGroupCol: _*).withPriKey(mixGroupIdCol)
  val covertDefaultsTable = Tab.withName("covert_defaults").withCols(mixGroupIdCol, tokenIdCol, mixingTokenAmount, depositCol, lastActivityCol).withPriKey(mixGroupIdCol, tokenIdCol)
  val covertAddressesTable = Tab.withName("covert_addresses").withCols(mixGroupIdCol, addressCol).withPriKey(mixGroupIdCol, addressCol)

  // mix requests
  val mixGroupReqCols = Seq(mixGroupIdCol, amountCol, mixStatusCol, createdTimeCol, depositAddressCol, depositCol, tokenDepositCol, mixingAmount, mixingTokenAmount, mixingTokenNeeded, tokenIdCol)
  val mixRequestsGroupTable = Tab.withName("mixing_group_request").withCols(mixGroupReqCols :+ masterSecretGroupCol: _*).withPriKey(mixGroupIdCol)

  val distributeTxsTable = Tab.withName("distribute_transactions").withCols(mixGroupIdCol, txIdCol, chainOrderCol, createdTimeCol, txCol, inputsCol).withPriKey(txIdCol)

  val mixReqCols = Seq(mixIdCol, mixGroupIdCol, amountCol, numRoundsCol, mixStatusCol, createdTimeCol, withdrawAddressCol, depositAddressCol, depositCompletedCol, neededCol, numTokenCol, mixWithdrawStatusCol, mixingTokenAmount, mixingTokenNeeded, tokenIdCol)
  val mixRequestsTable = Tab.withName("mixing_requests").withCols(mixReqCols :+ masterSecretCol: _*).withPriKey(mixIdCol)

  // stores unspent deposits
  val unspentDepositsTable = Tab.withName("unspent_deposits").withCols(addressCol, boxIdCol, amountCol, createdTimeCol, mixingTokenAmount).withPriKey(boxIdCol)
  private val unspentDepositsArchiveTable = Tab.withName("unspent_deposits_archived").withCols(addressCol, boxIdCol, amountCol, createdTimeCol, mixingTokenAmount, insertReasonCol).withPriKey()


  def insertUnspentDeposit(address: String, boxId: String, amount: Long, tokenAmount: Long, time: Long)(implicit insertReason: String) = {
    unspentDepositsTable.insert(address, boxId, amount, time, tokenAmount)
    unspentDepositsArchiveTable.insert(address, boxId, amount, time, tokenAmount, insertReason)
  }

  // stores spent deposits
  val spentDepositsTable = Tab.withName("spent_deposits").withCols(addressCol, boxIdCol, amountCol, createdTimeCol, mixingTokenAmount, txIdCol, spentTimeCol, purposeCol).withPriKey(boxIdCol)
  private val spentDepositsArchiveTable = Tab.withName("spent_deposits_archived").withCols(addressCol, boxIdCol, amountCol, createdTimeCol, mixingTokenAmount, txIdCol, spentTimeCol, purposeCol, insertReasonCol).withPriKey()

  def insertSpentDeposit(address: String, boxId: String, amount: Long, tokenAmount: Long, createdTime: Long, txId: String, spentTime: Long, purpose: String)(implicit insertReason: String) = {
    spentDepositsTable.insert(address, boxId, amount, createdTime, tokenAmount, txId, spentTime, purpose)
    spentDepositsArchiveTable.insert(address, boxId, amount, createdTime, tokenAmount, txId, spentTime, purpose, insertReason)
  }

  private def undoSpend(deposit: Deposit) = networkUtils.usingClient { implicit ctx =>
    if (unspentDepositsTable.exists(boxIdCol === deposit.boxId)) throw new Exception(s"Unspent deposit already exists ${deposit.boxId}")
    if (explorer.getBoxById(deposit.boxId).spendingTxId.isDefined) throw new Exception(s"Cannot undo spend for already spend box ${deposit.boxId}")
    unspentDepositsTable.insert(deposit.address, deposit.boxId, deposit.amount, deposit.tokenAmount, deposit.createdTime)
    spentDepositsTable.deleteWhere(boxIdCol === deposit.boxId)
  }

  def undoSpends(depositAddress: String) = {
    spentDepositsTable.selectStar.where(addressCol === depositAddress).as(Deposit(_)).map { deposit =>
      logger.info(s"[UndoDeposit ${deposit.address}]")
      try {
        undoSpend(deposit)
      } catch {
        case a: Throwable =>
          logger.error(s" [UndoDeposit ${deposit.address}] Error " + a.getMessage)
      }
    }
  }

  // mixStateTable stores the current status of the mix (reqId, (current) roundCol and isAliceCol).
  // It only stores the CURRENT state, that is whenever a round is incremented, the roundCol is incremented and isAliceCol is updated
  // The complete history of mix is stored in a separate table below (mixStateHistoryTable), which is needed for undoing a mix step in case of a double spend by someone else
  val mixStateTable = Tab.withName("mix_state").withCols(
    mixIdCol,
    roundCol, // current round
    isAliceCol // is Alice in current round?
  ).withPriKey(mixIdCol)

  // halfMixTable contains the entire history of half mixes (for this user).
  // Whether a row corresponds to the CURRENT state is determined by mixStateTable.. that is, if a row in mixStateTable exists with a matching roundCol and mixIdCol
  // We will never delete from this table, hence composite primary key.
  // When some other user spends a half mix box owned by us, then we update the isSpentCol to true and also add a corresponding entry in the fullMixTable for our full-mix box
  // For undoing, we will simply delete the row that was added to this table and update the mixStateTable accordingly
  val halfMixTable = Tab.withName("half_mix").withCols(
    mixIdCol, roundCol, createdTimeCol, halfMixBoxIdCol, isSpentCol
  ).withPriKey(mixIdCol, roundCol)

  private val halfMixArchiveTable = Tab.withName("half_mix_archived").withCols(
    mixIdCol, roundCol, createdTimeCol, halfMixBoxIdCol, isSpentCol, insertReasonCol
  ).withPriKey()

  def insertHalfMix(mixId: String, round: Int, time: Long, halfMixBoxId: String, isSpent: Boolean)(implicit insertReason: String) = {
    halfMixTable.insert(mixId, round, time, halfMixBoxId, isSpent)
    halfMixArchiveTable.insert(mixId, round, time, halfMixBoxId, isSpent, insertReason)
  }

  // fullMixTable contains the entire history of half mixes (for this user).
  // Whether a row corresponds to the CURRENT state is determined by mixStateTable.. that is, if a row in mixStateTable exists with a matching roundCol and mixIdCol
  // We will never delete from this table, hence composite primary key.
  // A row is added whenever we do a remix as Bob (i.e., consume someone else's half-mix box) to generate two full-mix boxes. The row will store the boxId of our full-mix box
  // Recall that when we do a (re)mix as Alice, a row is created in the halfMixTable as explained above.
  // A row is also added to the fullMixTable whenever someone else spends that half mix box. The row will store the boxId of our full-mix box
  // Thus, this table contains a row for both Alice and Bob roles, while the halfMixTable contains rows only for Alice roles
  // For undoing, we will simply delete the row that was added to this table and update the mixStateTable accordingly
  val fullMixTable = Tab.withName("full_mix").withCols(
    mixIdCol, roundCol, createdTimeCol, halfMixBoxIdCol, fullMixBoxIdCol // one belonging to us
  ).withPriKey(mixIdCol, roundCol)

  private val fullMixArchiveTable = Tab.withName("full_mix_archived").withCols(
    mixIdCol, roundCol, createdTimeCol, halfMixBoxIdCol, fullMixBoxIdCol, insertReasonCol // one belonging to us
  ).withPriKey()

  def insertFullMix(mixId: String, round: Int, time: Long, halfMixBoxId: String, fullMixBoxId: String)(implicit insertReason: String) = {
    fullMixTable.insert(mixId, round, time, halfMixBoxId, fullMixBoxId)
    fullMixArchiveTable.insert(mixId, round, time, halfMixBoxId, fullMixBoxId, insertReason)
  }

  // In case, there is a fork or a box is spent elsewhere, we would have to undo. Specifically, we would need to undo any of the following
  //  1. Bob entry (in case the half mix box is spent elsewhere)
  //  2. Reentry as alice (in case the fee emission box is spent elsewhere)
  //  3. Reentry as bob (in case either the fee emission box or the half mix box is spent elsewhere)
  //
  // Recall that the mixStateTable holds the CURRENT state of the mix, and a remix updates (roundCol, isAliceCol) for each new round.
  // The mixStateHistoryTable will store the entire history of the mix, not just the current state.
  // A new row must be inserted in this table whenever mixStateTable is updated
  //
  // To undo a round, we will follow the below strategy:
  //  1. Find the previous row in the history table
  //  2. Delete the corresponding row in fullMixTable and/or halfMixTable
  //  3. Update the corresponding row in the mixStateTable
  val mixStateHistoryTable = Tab.withName("mix_state_history").withCols(
    mixIdCol,
    roundCol, // current round
    isAliceCol, // is Alice in current round?
    createdTimeCol // time this row was entered
  ).withPriKey(mixIdCol, roundCol)

  private val mixStateHistoryArchiveTable = Tab.withName("mix_state_history_archived").withCols(
    mixIdCol,
    roundCol, // current round
    isAliceCol, // is Alice in current round?
    createdTimeCol, // time this row was entered
    insertReasonCol
  ).withPriKey()

  def insertMixStateHistory(mixId: String, round: Int, isAlice: Boolean, time: Long)(implicit insertReason: String) = {
    mixStateHistoryTable.insert(mixId, round, isAlice, time)
    mixStateHistoryArchiveTable.insert(mixId, round, isAlice, time, insertReason)
  }

  def undoMixStep[T](mixId: String, round: Int, mixTable: DBManager, boxId: String) = {
    val currRound = mixStateTable.select(roundCol).where(mixIdCol === mixId).firstAsT[Int].headOption.getOrElse(throw new Exception(s"No entry exists for $mixId in mixStateTable"))
    if (currRound != round) throw new Exception(s"Current round ($currRound) != undo round ($round)")

    val maxRound = mixStateHistoryTable.select(roundCol).where(mixIdCol === mixId).firstAsT[Int].max
    if (currRound != maxRound) throw new Exception(s"Current round ($currRound) != max round ($maxRound)")

    mixStateHistoryTable.deleteWhere(mixIdCol === mixId, roundCol === round)
    mixTransactionsTable.deleteWhere(boxIdCol === boxId)

    if (round == 0) {
      // delete from mixStateTable
      // delete from mixStateHistoryTable
      // set mixRequest as Queued

      mixStateTable.deleteWhere(mixIdCol === mixId)
      spentTokenEmissionBoxTable.deleteWhere(mixIdCol === mixId)
      spentFeeEmissionBoxTable.deleteWhere(mixIdCol === mixId, roundCol === round)
      mixRequestsTable.update(mixStatusCol <-- Queued.value).where(mixIdCol === mixId)
      mixRequestsTable.select(depositAddressCol).where(mixIdCol === mixId).firstAsT[String].headOption.map(undoSpends)
    } else {
      val prevRound = round - 1
      mixStateHistoryTable.selectStar.where(mixIdCol === mixId, roundCol === prevRound).as(MixHistory(_)).headOption match {
        case Some(prevMixState) =>

          mixStateTable.update(
            roundCol <-- prevRound,
            isAliceCol <-- prevMixState.isAlice,
          ).where(mixIdCol === mixId)
          spentFeeEmissionBoxTable.deleteWhere(mixIdCol === mixId, roundCol === round)

        case _ => throw new Exception(s"No history found for previous round $prevRound and mixId $mixId")
      }
    }
    mixTable.deleteWhere(mixIdCol === mixId, roundCol === round)
  }

  val withdrawTable = Tab.withName("withdraw").withCols(mixIdCol, txIdCol, createdTimeCol, boxIdCol, txCol, additional_info).withPriKey(mixIdCol)
  private val withdrawArchiveTable = Tab.withName("withdraw_archived").withCols(mixIdCol, txIdCol, createdTimeCol, fullMixBoxIdCol, txCol, additional_info, insertReasonCol).withPriKey()

  def insertWithdraw(mixId: String, txId: String, time: Long, boxId: String, txBytes: Array[Byte], withdrawStat: String = WithdrawRequested.value, additionalInfo: String = null)(implicit insertReason: String) = {
    withdrawTable.deleteWhere(mixIdCol === mixId)
    withdrawTable.insert(mixId, txId, time, boxId, txBytes, additionalInfo)
    withdrawArchiveTable.insert(mixId, txId, time, boxId, txBytes, additionalInfo, insertReason)
    mixRequestsTable.update(mixWithdrawStatusCol <-- withdrawStat).where(mixIdCol === mixId)
  }

  def deleteWithdraw(mixId: String): Unit = {
    withdrawTable.deleteWhere(mixIdCol === mixId)
  }

  def getMintingTxs: Seq[WithdrawTx] = {
    withdrawTable.selectStar
      .where(
        (mixIdCol of withdrawTable) === (mixIdCol of mixRequestsTable),
        (mixWithdrawStatusCol of mixRequestsTable) === AgeUSDRequested.value
      ).as(WithdrawTx(_))
  }

  val mixTransactionsTable = Tab.withName("mix_transactions").withCols(boxIdCol, txIdCol, txCol).withPriKey(boxIdCol) // saves the transaction in which boxIdCol is created
  def insertTx(boxId: String, tx: SignedTransaction): Unit = {
    mixTransactionsTable.deleteWhere(boxIdCol === boxId)
    mixTransactionsTable.insert(boxId, tx.getId, tx.toJson(false).getBytes("utf-16"))
  }

  def shouldWithdraw(mixId: String, boxId: String): Boolean = {
    withdrawTable.selectStar.where(mixIdCol === mixId).as(WithdrawTx(_)).headOption match {
      case Some(tx) => !tx.boxId.contains(boxId)
      case _ => true
    }
  }

  // TODO: Add isAliceCol
  val spentFeeEmissionBoxTable = Tab.withName("emission_box").withCols(mixIdCol, roundCol, boxIdCol).withPriKey(boxIdCol)

  val spentTokenEmissionBoxTable = Tab.withName("token_emission_box").withCols(mixIdCol, boxIdCol).withPriKey(mixIdCol)

  //mixId, time, round, goBackward, isHalfMixTx, mixBoxId
  val rescanTable = Tab.withName("rescan").withCols(
    mixIdCol, createdTimeCol, roundCol, goBackwardCol, isHalfMixTxCol, mixBoxIdCol
  ).withPriKey(mixIdCol)

  private val rescanArchiveTable = Tab.withName("rescan_archive").withCols(
    mixIdCol, createdTimeCol, roundCol, goBackwardCol, isHalfMixTxCol, mixBoxIdCol, insertReasonCol
  ).withPriKey()

  def insertForwardScan(mixId: String, time: Long, round: Int, isHalfMixTx: Boolean, mixBoxId: String)(implicit insertReason: String) = {
    insertPendingRescan(mixId, time, round, false, isHalfMixTx, mixBoxId, insertReason)
  }

  def insertBackwardScan(mixId: String, time: Long, round: Int, isHalfMixTx: Boolean, mixBoxId: String)(implicit insertReason: String) = {
    insertPendingRescan(mixId, time, round, true, isHalfMixTx, mixBoxId, insertReason)
  }

  private def insertPendingRescan(mixId: String, time: Long, round: Int, goBackward: Boolean, isHalfMixTx: Boolean, mixBoxId: String, insertReason: String) = {
    rescanTable.deleteWhere(mixIdCol === mixId)
    rescanTable.insert(mixId, time, round, goBackward, isHalfMixTx, mixBoxId)
    rescanArchiveTable.insert(mixId, time, round, goBackward, isHalfMixTx, mixBoxId, insertReason)
  }

  /**
   * deletes a mix box and everything associated with that box including secrets
   * only call this if mix for the box is done and it is withdrawn
   * @param box mix request
   */
  def deleteMixBox(box: MixRequest): Unit = {
    mixRequestsTable.deleteWhere(mixIdCol === box.id)

    unspentDepositsTable.deleteWhere(addressCol === box.depositAddress)
    unspentDepositsArchiveTable.deleteWhere(addressCol === box.depositAddress)

    spentDepositsTable.deleteWhere(addressCol === box.depositAddress)
    spentDepositsArchiveTable.deleteWhere(addressCol === box.depositAddress)

    mixStateTable.deleteWhere(mixIdCol === box.id)
    mixStateHistoryTable.deleteWhere(mixIdCol === box.id)
    mixStateHistoryArchiveTable.deleteWhere(mixIdCol === box.id)

    val boxIds: Seq[String] = halfMixTable.select(halfMixBoxIdCol).where(mixIdCol === box.id)
      .as(_.toIterator.next().as[String]) ++ fullMixTable.select(fullMixBoxIdCol)
      .where(mixIdCol === box.id).as(_.toIterator.next().as[String])

    halfMixTable.deleteWhere(mixIdCol === box.id)
    halfMixArchiveTable.deleteWhere(mixIdCol === box.id)

    fullMixTable.deleteWhere(mixIdCol === box.id)
    fullMixArchiveTable.deleteWhere(mixIdCol === box.id)

    withdrawTable.deleteWhere(mixIdCol === box.id)
    withdrawArchiveTable.deleteWhere(mixIdCol === box.id)

    boxIds.foreach(boxId => {
      mixTransactionsTable.deleteWhere(boxIdCol === boxId)
    })

    spentFeeEmissionBoxTable.deleteWhere(mixIdCol === box.id)
    spentTokenEmissionBoxTable.deleteWhere(mixIdCol === box.id)

    rescanTable.deleteWhere(mixIdCol === box.id)
    rescanArchiveTable.deleteWhere(mixIdCol === box.id)
  }

  /**
   * deletes a group mix request and everything associated with that request including mix boxes and secrets
   * only call this if group mix is done and every box is withdrawn
   * @param groupId group mix request
   */
  def deleteGroupMix(groupId: String): Unit = {
    val boxes = mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === groupId).as(MixRequest(_))
    mixRequestsGroupTable.deleteWhere(mixGroupIdCol === groupId)
    distributeTxsTable.deleteWhere(mixGroupIdCol === groupId)
    mixRequestsTable.deleteWhere(mixGroupIdCol === groupId)
    boxes.foreach(box => {
      deleteMixBox(box)
    })
  }
}
