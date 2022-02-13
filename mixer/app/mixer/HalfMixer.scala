package mixer

import app.Configs
import mixinterface.AliceOrBob
import helpers.ErgoMixerUtils
import wallet.WalletHelper.now

import javax.inject.Inject
import models.Models.{FullMix, FullMixBox, PendingRescan, WithdrawTx}
import models.Models.MixWithdrawStatus.WithdrawRequested
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.InputBox
import play.api.Logger
import sigmastate.eval._
import wallet.{Wallet, WalletHelper}
import dao.{AllMixDAO, DAOUtils, EmissionDAO, FullMixDAO, HalfMixDAO, MixTransactionsDAO, RescanDAO, SpentDepositsDAO, TokenEmissionDAO, WithdrawDAO}

class HalfMixer @Inject()(aliceOrBob: AliceOrBob, ergoMixerUtils: ErgoMixerUtils,
                          networkUtils: NetworkUtils, explorer: BlockExplorer,
                          daoUtils: DAOUtils,
                          allMixDAO: AllMixDAO,
                          emissionDAO: EmissionDAO,
                          tokenEmissionDAO: TokenEmissionDAO,
                          withdrawDAO: WithdrawDAO,
                          halfMixDAO: HalfMixDAO,
                          fullMixDAO: FullMixDAO,
                          mixTransactionsDAO: MixTransactionsDAO,
                          spentDepositsDAO: SpentDepositsDAO,
                          rescanDAO: RescanDAO) {
  private val logger: Logger = Logger(this.getClass)

  import ergoMixerUtils._

  /**
   * processes half-boxes one by one
   */
  def processHalfMixQueue(): Unit = {
    // Read (from db) our half mix boxes with unspent status
    // Check (from block explorer) if any of those are spent, obtain our full mix box for each spent half mix box, save to full mix table and mark half mix box as spent

    val halfMixes = daoUtils.awaitResult(allMixDAO.groupRequestsProgress)

    if (halfMixes.nonEmpty) logger.info(s"[HALF] Processing following ids")
    halfMixes foreach (x => logger.info(s"  > ${x._1}"))

    halfMixes.map {
      case (mixId, currentRound, halfMixBoxId, masterSecret, withdrawStatus, withdrawAddress) =>
        try {
          val optEmissionBoxIdFuture = emissionDAO.selectBoxId(mixId, currentRound)
          val tokenBoxIdFuture = tokenEmissionDAO.selectBoxId(mixId)
          val optEmissionBoxId = daoUtils.awaitResult(optEmissionBoxIdFuture)
          val tokenBoxId = daoUtils.awaitResult(tokenBoxIdFuture).getOrElse(throw new Exception(s"mixId $mixId not found in TokenEmissionBox"))

          processHalfMix(mixId, currentRound, halfMixBoxId, masterSecret, optEmissionBoxId, tokenBoxId, withdrawStatus, withdrawAddress)
        } catch {
          case a: Throwable =>
            logger.error(s" [HALF:$mixId ($currentRound)] An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
    }
  }

  private implicit val insertReason = "HalfMixer.processHalfMix"

  /**
   * processes a sepecific half-box
   *
   * @param mixId            mix id
   * @param currentRound     current round of mixing
   * @param halfMixBoxId     half-box id
   * @param masterSecret     master secret
   * @param optEmissionBoxId fee-emission box id (spent in order to create this half-box)
   * @param tokenBoxId       token-emission box id
   * @param withdrawStatus   withdraw status of this mix
   * @param withdrawAddress  withdraw address
   */
  private def processHalfMix(mixId: String, currentRound: Int, halfMixBoxId: String, masterSecret: BigInt, optEmissionBoxId: Option[String], tokenBoxId: String, withdrawStatus: String, withdrawAddress: String) = networkUtils.usingClient { implicit ctx =>
    logger.info(s"[HALF:$mixId ($currentRound)] [half:$halfMixBoxId]")
    val currentTime = now

    val halfMixBoxConfirmations = explorer.getConfirmationsForBoxId(halfMixBoxId)
    if (halfMixBoxConfirmations >= Configs.numConfirmation) {
      logger.info(s" [HALF: $mixId ($currentRound)] Sufficient confirmations ($halfMixBoxConfirmations) [half:$halfMixBoxId]")
      val spendingTx = networkUtils.getSpendingTxId(halfMixBoxId)
      if (spendingTx.isEmpty && withdrawStatus.equals(WithdrawRequested.value)) {
        if (withdrawDAO.shouldWithdraw(mixId, halfMixBoxId)) {
          val optFeeEmissionBoxId = getRandomValidBoxId(
            networkUtils.getFeeEmissionBoxes(considerPool = true)
              .map(_.id).filterNot(id => daoUtils.awaitResult(emissionDAO.existsByBoxId(id)))
          )
          if (optFeeEmissionBoxId.nonEmpty) {
            require(withdrawAddress.nonEmpty)
            val wallet = new Wallet(masterSecret)
            val secret = wallet.getSecret(currentRound).bigInteger
            val tx = aliceOrBob.spendBox(halfMixBoxId, optFeeEmissionBoxId, withdrawAddress, Array(secret), Configs.defaultHalfFee, broadCast = false)
            val txBytes = tx.toJson(false).getBytes("utf-16")

            val new_withdraw = WithdrawTx(mixId, tx.getId, currentTime, halfMixBoxId + "," + optFeeEmissionBoxId.get, txBytes)
            withdrawDAO.updateById(new_withdraw, WithdrawRequested.value)
            logger.info(s" [Half: $mixId ($currentRound)] Withdraw txId: ${tx.getId}, is requested: ${withdrawStatus.equals(WithdrawRequested.value)}")
            val sendRes = ctx.sendTransaction(tx)
            if (sendRes == null) logger.error(s"  something unexpected has happened! tx got refused by the node!")

          } else
            logger.info(s"  HALF: $mixId  No fee emission box available to withdraw, waiting...")

        }
      }
      spendingTx.map { fullMixTxId =>
        explorer.getTransaction(fullMixTxId).map { tx =>

          val boxIds: Seq[String] = tx.outboxes.flatMap(_.getFBox(networkUtils.tokenErgoMix.get).map(_.id))

          val boxes: Seq[InputBox] = boxIds.flatMap { boxId =>
            try ctx.getBoxesById(boxId).toList catch {
              case a: Throwable => Nil
            }
          }
          val x = new Wallet(masterSecret).getSecret(currentRound).bigInteger
          val gX = WalletHelper.g.exp(x)
          boxes.map { box =>
            val fullMixBox = FullMixBox(box)
            require(fullMixBox.r6 == gX)
            if (fullMixBox.r5 == fullMixBox.r4.exp(x)) { // this is our box
              halfMixDAO.setAsSpent(mixId, currentRound)
              val new_fullMix = FullMix(mixId, currentRound, currentTime, halfMixBoxId, fullMixBox.id)
              fullMixDAO.insertFullMix(new_fullMix)
              logger.info(s" [HALF:$mixId ($currentRound)] -> FULL [half:$halfMixBoxId, full:${fullMixBox.id}]")
            }
          }
        }
      }
    } else {
      logger.info(s" [HALF:$mixId ($currentRound)] Insufficient confirmations ($halfMixBoxConfirmations) [half:$halfMixBoxId]")
      if (halfMixBoxConfirmations == 0) { // 0 confirmations for halfMixBoxId
        // There are three possibilities
        //  1. Transaction is good, waiting for confirmations.. do nothing
        //  2. Emission box input of the transaction has been spent elsewhere, and this transaction is invalid
        //  3. This is a remix and the inputs of the transaction don't exist anymore due to a fork (low chance, considered below)
        //  4. This is an entry and the inputs of the transaction don't exist anymore due to a fork (low chance, considered below)

        // logger.info(s"   [HalfMix $mixId] Zero conf. isAlice: halfMixBoxId: $halfMixBoxId, currentRound: $currentRound")

        // here we check if transaction is missing from mempool and also not mined. if so, we rebroadcast it!
        val prevTx = daoUtils.awaitResult(mixTransactionsDAO.selectByBoxId(halfMixBoxId)).getOrElse(throw new Exception(s"halfMixBoxId $halfMixBoxId not found in MixTransactions"))
        try {
          val res = ctx.sendTransaction(ctx.signedTxFromJson(prevTx.toString))
          logger.info(s"  broadcasted tx ${prevTx.txId}, response: $res")
        }
        catch {
          case e: Throwable =>
            logger.info(s"  broadcasted tx ${prevTx.txId}, failed (probably due to double spent or fork)")
            logger.debug(s"  Exception: ${e.getMessage}")
            optEmissionBoxId match {
              case Some(emissionBoxId) =>
                // we are here, thus; there was a fee emission box used and so this is a remix transaction
                if (networkUtils.isDoubleSpent(emissionBoxId, halfMixBoxId)) {
                  // logger.info(s"    [HalfMix $mixId] Zero conf. halfMixBoxId: $halfMixBoxId, currentRound: $currentRound while emissionBoxId $emissionBoxId spent")
                  // the emissionBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
                  try {
                    allMixDAO.undoMixStep(mixId, currentRound, halfMixBoxId, false)
                    logger.info(s" [HALF:$mixId ($currentRound)] <-- (undo) [half:$halfMixBoxId not spent while fee:$emissionBoxId spent]")
                  } catch {
                    case a: Throwable =>
                      logger.error(getStackTraceStr(a))
                  }
                } else {
                  // if emission box is ok, lets now check if the input does not exist, if so then this is a fork
                  // the input is a full mix box (since this is a remix)
                  daoUtils.awaitResult(fullMixDAO.selectBoxId(mixId, currentRound - 1)).map { fullMixBoxId =>
                    explorer.doesBoxExist(fullMixBoxId) match {
                      case Some(true) =>
                      case Some(false) =>
                        logger.error(s" [HALF:$mixId ($currentRound)] [ERROR] Rescanning [full:$fullMixBoxId disappeared]")
                        Thread.currentThread().getStackTrace foreach println
                        val new_scan = PendingRescan(mixId, now, currentRound, goBackward = true, isHalfMixTx = true, halfMixBoxId)
                        rescanDAO.updateById(new_scan)
                      case _ =>
                    }
                  }
                }
              case _ => {
                // no emission box, so this is an entry.
                // token emission box double spent check
                if (networkUtils.isDoubleSpent(tokenBoxId, halfMixBoxId)) {
                  try {
                    allMixDAO.undoMixStep(mixId, currentRound, halfMixBoxId, false)
                    logger.info(s" [HALF:$mixId ($currentRound)] <-- (undo) [half:$halfMixBoxId not spent while token:$tokenBoxId spent]")
                  } catch {
                    case a: Throwable =>
                      logger.error(getStackTraceStr(a))
                  }
                } else {
                  // token emission box is ok
                  // Need to check for fork by checking if any of the deposits have suddenly disappeared
                  daoUtils.awaitResult(spentDepositsDAO.selectBoxIdByPurpose(mixId)).find { depositBoxId =>
                    explorer.doesBoxExist(depositBoxId).contains(false) // deposit has disappeared, so need to rescan
                  }.map { depositBoxId =>
                    logger.error(s" [HALF:$mixId ($currentRound)] [ERROR] Rescanning [deposit:$depositBoxId disappeared]")
                    Thread.currentThread().getStackTrace foreach println
                    val new_scan = PendingRescan(mixId, now, currentRound, goBackward = true, isHalfMixTx = true, halfMixBoxId)
                    rescanDAO.updateById(new_scan)
                  }
                }
              }
            }
        }
      }
    }
  }

}
