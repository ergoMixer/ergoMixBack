package mixer

import mixer.ErgoMixerUtils._
import mixer.Models.MixStatus.Complete
import mixer.Models.MixWithdrawStatus.{WithdrawRequested, Withdrawn}
import mixer.Models.{GroupMixStatus, MixRequest, WithdrawTx}
import cli.ErgoMixCLIUtil
import db.ScalaDB._
import mixer.Columns._
import play.api.Logger

class WithdrawMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)
  import tables._

  def processWitthraws(): Unit = {
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

  private def processWithdraw(tx: WithdrawTx) = ErgoMixCLIUtil.usingClient { implicit ctx =>
    logger.info(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} processing.")
    val explorer = new BlockExplorer()
    val numConf = explorer.getTxNumConfirmations(tx.txId)
    if (numConf == -1) { // not mined yet!
      // if tx is in pool just wait
      // if tx is not in pool and inputs are ok then broadcast
      // other cases include the following:
      //   * this is a half box and is spent with someone else --> will be handled in HalfMixer
      //   * inputs are not available due to fork --> will be handled in other jobs (HalfMixer, FullMixer, NewMixer)
      if (!ErgoMixCLIUtil.isTxInPool(tx.txId)) { // however, we broadcast anyway if tx is not in the pool
        val result = ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
        logger.info(s"  broadcasting transaction: $result.")
      } else {
        logger.info(s"  transaction is in pool, waiting to be mined.")
      }

      // if fee box is used, check to see if it is double spent
      if (tx.getFeeBox.nonEmpty && isDoubleSpent(tx.getFeeBox.get, tx.getFirstInput)) {
        logger.info(s"  fee ${tx.getFeeBox.get} is double spent, will try in next round...")
        deleteWithdraw(tx.mixId)
      }

    } else if (numConf >= minConfirmations) { // tx is confirmed enough, mix is done!
      logger.info(s"  transaction is confirmed enough. Mix is done.")
      mixRequestsTable.update(mixStatusCol <-- Complete.value, mixWithdrawStatusCol <-- Withdrawn.value)
        .where(mixIdCol === tx.mixId)
      val mix: MixRequest = mixRequestsTable.selectStar.where(mixIdCol === tx.mixId).as(MixRequest(_)).head
      val numRunning = mixRequestsTable.countWhere(mixGroupIdCol === mix.groupId, mixStatusCol <> Complete.value)
      if (numRunning == 0) { // mix is done
        mixRequestsGroupTable.update(mixStatusCol <-- GroupMixStatus.Complete.value).where(mixGroupIdCol === mix.groupId)
      }

    } else
      logger.info(s"  not enough confirmations yet: $numConf.")
  }
}
