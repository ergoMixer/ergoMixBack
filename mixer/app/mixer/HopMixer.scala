package mixer

import app.Configs
import dao._
import helpers.ErgoMixerUtils
import mixinterface.AliceOrBob
import models.Models.MixWithdrawStatus.{UnderHop, WithdrawRequested}
import models.Models.{FullMix, FullMixBox, HopMix, PendingRescan, WithdrawTx}
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.{BlockchainContext, InputBox}
import play.api.Logger
import sigmastate.eval._
import wallet.WalletHelper.now
import wallet.{Wallet, WalletHelper}

import javax.inject.Inject

class HopMixer @Inject()(ergoMixerUtils: ErgoMixerUtils,
                         ergoMixer: ErgoMixer, aliceOrBob: AliceOrBob,
                         networkUtils: NetworkUtils, explorer: BlockExplorer,
                         daoUtils: DAOUtils,
                         allMixDAO: AllMixDAO,
                         withdrawDAO: WithdrawDAO,
                         hopMixDAO: HopMixDAO,
                         rescanDAO: RescanDAO) {
  private val logger: Logger = Logger(this.getClass)

  import ergoMixerUtils._

  private implicit val insertReason: String = "HopMixer.processHopBox"

  /**
   * processes hop-boxes one by one
   *
   */
  def processHopBoxes(): Unit = {
    daoUtils.awaitResult(allMixDAO.groupHopMixesProgress).foreach(hopBox => {
      try {
        networkUtils.usingClient(implicit ctx => processHopBox(hopBox))
      } catch {
        case a: Throwable =>
          logger.info(s" [HOP: ${hopBox.mixId}] boxId: ${hopBox.boxId} An error occurred. Stacktrace below")
          logger.error(getStackTraceStr(a))
      }
    })
  }

  /**
   * processes a hop-box
   *
   * @param hopBox HopMix
   */
  private def processHopBox(hopBox: HopMix)(implicit ctx: BlockchainContext): Unit = {
    logger.info(s" [HOP: ${hopBox.mixId}] boxId: ${hopBox.boxId} processing.")
    val hopMixBoxConfirmations = explorer.getConfirmationsForBoxId(hopBox.boxId)
    if (hopMixBoxConfirmations >= Configs.numConfirmation) {
      logger.info(s" [HOP:${hopBox.mixId} (${hopBox.round})] Sufficient confirmations ($hopMixBoxConfirmations) [boxId:${hopBox.boxId}].")
      val masterSecret = ergoMixer.getMasterSecret(hopBox.mixId)
      val wallet = new Wallet(masterSecret)
      val secret = wallet.getSecret(hopBox.round, toFirst = true).bigInteger

      if (hopBox.round >= Configs.hopRounds - 1) {
        if (withdrawDAO.shouldWithdraw(hopBox.mixId, hopBox.boxId)) {
          val withdrawAddress = ergoMixer.getWithdrawAddress(hopBox.mixId)

          try {
            val tx = aliceOrBob.spendHopBox(secret, hopBox.boxId, withdrawAddress)
            val txBytes = tx.toJson(false).getBytes("utf-16")
            logger.info(s" [HOP:${hopBox.mixId}] withdraw txId: ${tx.getId}")

            ctx.sendTransaction(tx)
            val new_withdraw = WithdrawTx(hopBox.mixId, tx.getId, now, hopBox.boxId, txBytes)
            daoUtils.awaitResult(withdrawDAO.insertAndArchive(new_withdraw))
          }
          catch {
            case e: Throwable =>
              logger.error(s"  something unexpected has happened! tx got refused by the node!")
              logger.debug(s"  Exception: ${e.getMessage}")

              investigateHopBoxStatus(hopBox)
          }
        }
      }
      else {
        val nextHopSecret = wallet.getSecret(hopBox.round + 1, toFirst = true)
        val nextHopAddress = WalletHelper.getProveDlogAddress(nextHopSecret, ctx)

        try {
          val tx = aliceOrBob.spendHopBox(secret, hopBox.boxId, nextHopAddress)
          logger.info(s" [HOP:${hopBox.mixId}] hop txId: ${tx.getId}")

          ctx.sendTransaction(tx)
          val newHopBox = HopMix(hopBox.mixId, hopBox.round + 1, now, tx.getOutputsToSpend.get(0).getId.toString)
          daoUtils.awaitResult(hopMixDAO.insert(newHopBox))
        }
        catch {
          case e: Throwable =>
            logger.error(s"  something unexpected has happened! tx got refused by the node!")
            logger.debug(s"  Exception: ${e.getMessage}")

            investigateHopBoxStatus(hopBox)
        }
      }
    }
    else {
      logger.info(s" [HOP:${hopBox.mixId} (${hopBox.round})] Insufficient confirmations ($hopMixBoxConfirmations) [boxId:${hopBox.boxId}]")
    }
  }


  /**
   * checks and request a rescan if the box is spent
   *
   * @param hopBox HopMix
   */
  private def investigateHopBoxStatus(hopBox: HopMix): Unit = {
    explorer.getSpendingTxId(hopBox.boxId) match {
      case Some(txId) =>
        logger.error(s" [HOP:${hopBox.mixId} (${hopBox.round})] [ERROR] Rescanning because hop:$hopBox is spent in txId: $txId")
        Thread.currentThread().getStackTrace foreach println
        val new_scan = PendingRescan(hopBox.mixId, now, hopBox.round, goBackward = false, "hop", hopBox.boxId)
        rescanDAO.updateById(new_scan)
    }
  }

}
