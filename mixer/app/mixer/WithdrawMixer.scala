package mixer

import mixer.Columns._
import mixer.ErgoMixerUtil._
import mixer.Models.MixStatus.Complete
import mixer.Models.MixWithdrawStatus.{WithdrawRequested, Withdrawn}
import mixer.Models.{GroupMixStatus, MixRequest, WithdrawTx}
import cli.ErgoMixCLIUtil
import db.ScalaDB._

class WithdrawMixer(tables: Tables) {
  import tables._

  def processWtithraws(): Unit = {
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
            println(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} An error occurred. Stacktrace below")
            a.printStackTrace()
        }
    }
  }

  private def processWithdraw(tx: WithdrawTx) = ErgoMixCLIUtil.usingClient { implicit ctx =>
    println(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} processing.")
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
        println(s"  broadcasting transaction: $result.")
      } else {
        println(s"  transaction is in pool, waiting to be mined.")
      }

    } else if (numConf >= minConfirmations) { // tx is confirmed enough, mix is done!
      println(s"  transaction is confirmed enough. Mix is done.")
      mixRequestsTable.update(mixStatusCol <-- Complete.value, mixWithdrawStatusCol <-- Withdrawn.value)
        .where(mixIdCol === tx.mixId)
      val mix: MixRequest = mixRequestsTable.selectStar.where(mixIdCol === tx.mixId).as(MixRequest(_)).head
      val numRunning = mixRequestsTable.countWhere(mixGroupIdCol === mix.groupId, mixStatusCol <> Complete.value)
      if (numRunning == 0) { // mix is done
        mixRequestsGroupTable.update(mixStatusCol <-- GroupMixStatus.Complete.value).where(mixGroupIdCol === mix.groupId)
      }

    } else
      println(s"  not enough confirmations yet: $numConf.")
  }
}
