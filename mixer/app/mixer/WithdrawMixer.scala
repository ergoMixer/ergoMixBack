package mixer

import javax.inject.Inject

import config.MainConfigs
import dao._
import dao.mixing.{HopMixDAO, MixingGroupRequestDAO, MixingRequestsDAO, WithdrawDAO}
import helpers.ErgoMixerUtils
import models.Models.HopMix
import models.Request.MixRequest
import models.Status.GroupMixStatus
import models.Status.MixStatus.Complete
import models.Status.MixWithdrawStatus.{NoWithdrawYet, UnderHop}
import models.Transaction.WithdrawTx
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.BlockchainContext
import play.api.Logger
import wallet.WalletHelper

class WithdrawMixer @Inject() (
  ergoMixerUtils: ErgoMixerUtils,
  networkUtils: NetworkUtils,
  explorer: BlockExplorer,
  daoUtils: DAOUtils,
  withdrawDAO: WithdrawDAO,
  mixingRequestsDAO: MixingRequestsDAO,
  mixingGroupRequestDAO: MixingGroupRequestDAO,
  hopMixDAO: HopMixDAO
) {
  private val logger: Logger = Logger(this.getClass)

  import ergoMixerUtils._

  /**
   * processes withdrawals one by one
   */
  def processWithdrawals(): Unit = {
    val withdrawals = daoUtils.awaitResult(withdrawDAO.getWithdrawals)
    val withdraws   = withdrawals._1
    val minting     = withdrawals._2
    val hopping     = withdrawals._3

    withdraws.foreach { tx =>
      try
        processWithdraw(tx)
      catch {
        case a: Throwable =>
          logger.info(s" [WITHDRAW: ${tx.mixId}] txId: ${tx.txId} An error occurred. Stacktrace below")
          logger.error(getStackTraceStr(a))
      }
    }

    minting.foreach { tx =>
      try
        processWithdraw(tx, isMinting = true)
      catch {
        case a: Throwable =>
          logger.info(s" [WITHDRAW (minting): ${tx.mixId}] txId: ${tx.txId} An error occurred. Stacktrace below")
          logger.error(getStackTraceStr(a))
      }
    }

    hopping.foreach { tx =>
      try
        networkUtils.usingClient(implicit ctx => processInitiateHops(tx))
      catch {
        case a: Throwable =>
          logger.info(s" [WITHDRAW (hopping): ${tx.mixId}] txId: ${tx.txId} An error occurred. Stacktrace below")
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
      try {
        ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
        logger.info(s"  broadcasted transaction ${tx.txId}.")
      } catch {
        case e: Throwable =>
          logger.info(s"  broadcasted transaction, failed (probably due to double spent or fork)")
          logger.debug(s"  Exception: ${e.getMessage}")
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
              // otherwise, our whole chain is invalid (if the first txId in a chain spent by another person this chain will be invalid)
              val firstBankId =
                tx.additionalInfo.split(",").head // first bank box in our chain - is being spent in our first tx
              val firstTxId =
                tx.additionalInfo.split(",").last // first tx in the chain of txs - is spending the first bank box
              val bankSpendingTxId = explorer.getSpendingTxId(firstBankId)
              if (bankSpendingTxId.nonEmpty && bankSpendingTxId.get != firstTxId) {
                // the chain is invalid
                logger.info(
                  s"  bank box is spent with ${bankSpendingTxId.get} while our first tx was $firstTxId - our minting is invalid, will remove minting status."
                )
                withdrawDAO.delete(tx.mixId)
                mixingRequestsDAO.updateWithdrawStatus(tx.mixId, NoWithdrawYet.value)
              }
            }
          }
      }
    } else if (numConf >= MainConfigs.numConfirmation) { // tx is confirmed enough, mix is done!
      logger.info(s"  transaction is confirmed enough. Mix is done.")
      daoUtils.awaitResult(mixingRequestsDAO.withdrawTheRequest(tx.mixId))
      val mix: MixRequest = daoUtils
        .awaitResult(mixingRequestsDAO.selectByMixId(tx.mixId))
        .getOrElse(throw new Exception(s"mixId ${tx.mixId} not found in MixingRequests"))
      val numRunning = daoUtils.awaitResult(mixingRequestsDAO.countNotWithdrawn(mix.groupId))
      if (numRunning == 0) { // group mix is done because all mix boxes are withdrawn
        mixingGroupRequestDAO.updateStatusById(mix.groupId, GroupMixStatus.Complete.value)
      }

    } else
      logger.info(s"  not enough confirmations yet: $numConf.")
  }

  /**
   * processes initiate hop withdrawals
   */
  private def processInitiateHops(withdrawTx: WithdrawTx)(implicit ctx: BlockchainContext): Unit = {
    logger.info(s" [INITIATE HOP: ${withdrawTx.mixId}] txId: ${withdrawTx.txId} processing.")
    val numConf = explorer.getTxNumConfirmations(withdrawTx.txId)

    if (numConf == -1) { // not mined yet!
      val tx = ctx.signedTxFromJson(withdrawTx.toString)
      try {
        ctx.sendTransaction(tx)
        logger.info(s"  broadcasted tx: ${tx.getId}.")
      } catch {
        case e: Throwable =>
          logger.info(s"  broadcasted tx ${tx.getId}, failed (probably due to double spent)")
          logger.debug(s"  Exception: ${e.getMessage}")

          // if fee box is used, check to see if it is double spent
          if (withdrawTx.getFeeBox.nonEmpty) {
            val spentTxId = explorer.getSpendingTxId(withdrawTx.getFeeBox.get)
            if (spentTxId.nonEmpty && spentTxId.get != withdrawTx.txId) {
              logger.info(s"  fee ${withdrawTx.getFeeBox.get} is double spent, will try in next round...")
              daoUtils.awaitResult(withdrawDAO.delete(withdrawTx.mixId))
            }
          }
      }
    } else if (numConf >= MainConfigs.numConfirmation) {
      logger.info(s"  transaction is confirmed enough. Hop is initiated.")
      val tx       = ctx.signedTxFromJson(withdrawTx.toString)
      val hopBoxId = tx.getOutputsToSpend.get(0).getId.toString
      daoUtils.awaitResult(hopMixDAO.insert(HopMix(withdrawTx.mixId, 0, WalletHelper.now, hopBoxId)))
      daoUtils.awaitResult(withdrawDAO.delete(withdrawTx.mixId))
      daoUtils.awaitResult(mixingRequestsDAO.updateMixStatus(withdrawTx.mixId, Complete))
      daoUtils.awaitResult(mixingRequestsDAO.updateWithdrawStatus(withdrawTx.mixId, UnderHop.value))
    } else {
      logger.info(s"  not enough confirmations yet: $numConf.")
    }
  }

}
