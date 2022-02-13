package mixer

import app.Configs
import helpers.ErgoMixerUtils

import javax.inject.Inject
import models.Models.MixWithdrawStatus.{NoWithdrawYet, Withdrawn}
import models.Models.{GroupMixStatus, MixRequest, WithdrawTx}
import network.{BlockExplorer, NetworkUtils}
import play.api.Logger
import dao.{DAOUtils, MixingRequestsDAO, WithdrawDAO, MixingGroupRequestDAO}

class WithdrawMixer @Inject()(ergoMixerUtils: ErgoMixerUtils,
                              networkUtils: NetworkUtils, explorer: BlockExplorer,
                              daoUtils: DAOUtils,
                              withdrawDAO: WithdrawDAO,
                              mixingRequestsDAO: MixingRequestsDAO,
                              mixingGroupRequestDAO: MixingGroupRequestDAO) {
  private val logger: Logger = Logger(this.getClass)

  import ergoMixerUtils._


  /**
   * processes withdrawals one by one
   */
  def processWithdrawals(): Unit = {
    val withdrawals = daoUtils.awaitResult(withdrawDAO.getWithdrawals)
    val withdraws = withdrawals._1
    val minting = withdrawals._2

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

    minting.foreach {
      case tx =>
        try {
          processWithdraw(tx, isMinting = true)
        } catch {
          case a: Throwable =>
            logger.info(s" [WITHDRAW (minting): ${tx.mixId}] txId: ${tx.txId} An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
    }
  }

  /**
   * processes a specific withdraw, marks as done if tx is confirmed enough
   * @param tx withdraw transaction
   */
  private def processWithdraw(tx: WithdrawTx, isMinting: Boolean = false) = networkUtils.usingClient { implicit ctx =>
    logger.info(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} processing. isAgeUSD: $isMinting")
    val numConf = explorer.getTxNumConfirmations(tx.txId)
    if (numConf == -1) { // not mined yet!
      // will broadcast tx independent of whether it is in pool or not!
      // other cases include the following:
      //   * this is a half box and is spent with someone else --> will be handled in HalfMixer
      //   * inputs are not available due to fork --> will be handled in other jobs (HalfMixer, FullMixer, NewMixer)
      val result = ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
      logger.info(s"  broadcasting transaction: $result.")

      // if fee box is used, check to see if it is double spent
      if (tx.getFeeBox.nonEmpty && !isMinting) {
        val spentTxId = explorer.getSpendingTxId(tx.getFeeBox.get)
        if (spentTxId.nonEmpty && spentTxId.get != tx.txId) {
          logger.info(s"  fee ${tx.getFeeBox.get} is double spent, will try in next round...")
          withdrawDAO.delete(tx.mixId)
        }
      }

      // There are two main cases that minting tx will be invalid
      //   * oracle data change (data input will be spent)
      //   * bank box double spent
      if (isMinting) {
        if (explorer.getSpendingTxId(tx.getDataInputs.head).nonEmpty) { // oracle data has changed
          if (explorer.getTxNumConfirmations(tx.txId) == -1) {
            logger.info(s"  oracle data has changed - our minting is invalid, will remove minting status.")
            withdrawDAO.delete(tx.mixId)
            mixingRequestsDAO.updateWithdrawStatus(tx.mixId, NoWithdrawYet.value)
          }
        } else {
          // we check the first bank box. It should either be unspent or be spent by our transaction
          // otherwise, our whole chain is invalid
          val firstBankId = tx.additionalInfo.split(",").head // first bank box in our chain - is being spent in our first tx
          val firstTxId = tx.additionalInfo.split(",").last // first tx in the chain of txs - is spending the first bank box
          val bankSpendingTxId = explorer.getSpendingTxId(firstBankId)
          if (bankSpendingTxId.nonEmpty && bankSpendingTxId.get != firstTxId) {
            // the chain is invalid
            logger.info(s"  bank box is spent with ${bankSpendingTxId.get} while our first tx was $firstTxId - our minting is invalid, will remove minting status.")
            withdrawDAO.delete(tx.mixId)
            mixingRequestsDAO.updateWithdrawStatus(tx.mixId, NoWithdrawYet.value)
          }
        }
      }

    } else if (numConf >= Configs.numConfirmation) { // tx is confirmed enough, mix is done!
      logger.info(s"  transaction is confirmed enough. Mix is done.")
      daoUtils.awaitResult(mixingRequestsDAO.withdrawTheRequest(tx.mixId))
      val mix: MixRequest = daoUtils.awaitResult(mixingRequestsDAO.selectByMixId(tx.mixId)).getOrElse(throw new Exception(s"mixId ${tx.mixId} not found in MixingRequests"))
      val numRunning = daoUtils.awaitResult(mixingRequestsDAO.countWithdrawn(mix.groupId, Withdrawn.value))
      if (numRunning == 0) { // group mix is done because all mix boxes are withdrawn
        mixingGroupRequestDAO.updateStatusById(mix.groupId, GroupMixStatus.Complete.value)
      }

    } else
      logger.info(s"  not enough confirmations yet: $numConf.")
  }
}
