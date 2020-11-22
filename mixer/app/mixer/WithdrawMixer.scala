package mixer

import app.Configs
import db.Columns._
import db.ScalaDB._
import db.Tables
import helpers.ErgoMixerUtils
import javax.inject.Inject
import models.Models.MixStatus.Complete
import models.Models.MixWithdrawStatus.{WithdrawRequested, Withdrawn}
import models.Models.{GroupMixStatus, MixRequest, WithdrawTx}
import network.{BlockExplorer, NetworkUtils}
import play.api.Logger

class WithdrawMixer @Inject()(tables: Tables, ergoMixerUtils: ErgoMixerUtils,
                              networkUtils: NetworkUtils, explorer: BlockExplorer) {
  private val logger: Logger = Logger(this.getClass)

  import tables._
  import ergoMixerUtils._


  /**
   * processes withdrawals one by one
   */
  def processWithdrawals(): Unit = {
    val withdraws = withdrawTable.selectStar
      .where(
        (mixIdCol of withdrawTable) === (mixIdCol of mixRequestsTable),
        (mixWithdrawStatusCol of mixRequestsTable) === WithdrawRequested.value
      ).as(WithdrawTx(_))

    withdraws.foreach {
      case tx =>
        try {
          processWithdraw(tx)
        } catch {
          case a: Throwable =>
            logger.info(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
    }
  }

  /**
   * processes a specific withdraw, marks as done if tx is confirmed enough
   * @param tx withdraw transaction
   */
  private def processWithdraw(tx: WithdrawTx) = networkUtils.usingClient { implicit ctx =>
    logger.info(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} processing.")
    val numConf = explorer.getTxNumConfirmations(tx.txId)
    if (numConf == -1) { // not mined yet!
      // if tx is in pool just wait
      // if tx is not in pool and inputs are ok then broadcast
      // other cases include the following:
      //   * this is a half box and is spent with someone else --> will be handled in HalfMixer
      //   * inputs are not available due to fork --> will be handled in other jobs (HalfMixer, FullMixer, NewMixer)
      if (!networkUtils.isTxInPool(tx.txId)) { // however, we broadcast anyway if tx is not in the pool
        val result = ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
        logger.info(s"  broadcasting transaction: $result.")
      } else {
        logger.info(s"  transaction is in pool, waiting to be mined.")
      }

      // if fee box is used, check to see if it is double spent
      if (tx.getFeeBox.nonEmpty && networkUtils.isDoubleSpent(tx.getFeeBox.get, tx.getFirstInput)) {
        logger.info(s"  fee ${tx.getFeeBox.get} is double spent, will try in next round...")
        deleteWithdraw(tx.mixId)
      }

    } else if (numConf >= Configs.numConfirmation) { // tx is confirmed enough, mix is done!
      logger.info(s"  transaction is confirmed enough. Mix is done.")
      mixRequestsTable.update(mixStatusCol <-- Complete.value, mixWithdrawStatusCol <-- Withdrawn.value)
        .where(mixIdCol === tx.mixId)
      val mix: MixRequest = mixRequestsTable.selectStar.where(mixIdCol === tx.mixId).as(MixRequest(_)).head
      val numRunning = mixRequestsTable.countWhere(mixGroupIdCol === mix.groupId, mixWithdrawStatusCol <> Withdrawn.value)
      if (numRunning == 0) { // group mix is done because all mix boxes are withdrawn
        mixRequestsGroupTable.update(mixStatusCol <-- GroupMixStatus.Complete.value).where(mixGroupIdCol === mix.groupId)
      }

    } else
      logger.info(s"  not enough confirmations yet: $numConf.")
  }
}
