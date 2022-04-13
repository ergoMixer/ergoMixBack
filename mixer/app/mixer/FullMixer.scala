package mixer

import app.Configs
import mixinterface.{AliceOrBob, TokenErgoMix}
import helpers.ErgoMixerUtils
import wallet.WalletHelper.now

import javax.inject.Inject
import models.Models.MixStatus.{Complete, Running}
import models.Models.MixWithdrawStatus.{HopRequested, WithdrawRequested}
import models.Models.{CreateMixTransaction, FullMix, HalfMix, MixHistory, MixState, MixTransaction, OutBox, PendingRescan, WithdrawTx}
import network.{BlockExplorer, NetworkUtils}
import play.api.Logger
import wallet.{Wallet, WalletHelper}
import dao.{AllMixDAO, DAOUtils, EmissionDAO, FullMixDAO, HalfMixDAO, MixStateDAO, MixStateHistoryDAO, MixTransactionsDAO, MixingRequestsDAO, RescanDAO, TokenEmissionDAO, WithdrawDAO}

class FullMixer @Inject()(aliceOrBob: AliceOrBob, ergoMixerUtils: ErgoMixerUtils,
                          networkUtils: NetworkUtils, explorer: BlockExplorer,
                          daoUtils: DAOUtils,
                          allMixDAO: AllMixDAO,
                          emissionDAO: EmissionDAO,
                          tokenEmissionDAO: TokenEmissionDAO,
                          withdrawDAO: WithdrawDAO,
                          rescanDAO: RescanDAO,
                          halfMixDAO: HalfMixDAO,
                          fullMixDAO: FullMixDAO,
                          mixingRequestsDAO: MixingRequestsDAO,
                          mixStateDAO: MixStateDAO,
                          mixStateHistoryDAO: MixStateHistoryDAO,
                          mixTransactionsDAO: MixTransactionsDAO) {
  private val logger: Logger = Logger(this.getClass)

  import ergoMixerUtils._

  var fees: Seq[OutBox] = _
  var halfs: Seq[OutBox] = _

  /**
   * gets and sets half-boxes to be used by all full-boxes
   *
   * @return half-boxes
   */
  def getHalfBoxes: Seq[OutBox] = {
    if (halfs == null)
      halfs = networkUtils.getHalfMixBoxes(considerPool = true)
    halfs
  }

  /**
   * gets and sets fee-boxes to be used by all full-boxes
   *
   * @return fee-boxes
   */
  def getFeeBoxes: Seq[OutBox] = {
    if (fees == null)
      fees = networkUtils.getFeeEmissionBoxes(considerPool = true)
    fees
  }

  /**
   * processes full-boxes one by one
   */
  def processFullMixQueue(): Unit = {

    var fullMixes = daoUtils.awaitResult(allMixDAO.groupFullMixesProgress)

    fullMixes = scala.util.Random.shuffle(fullMixes)
    fees = null
    halfs = null

    if (fullMixes.nonEmpty) logger.info(s"[FULL] Processing following ids")
    fullMixes foreach (x => logger.info(s"  > ${x._1}"))

    fullMixes.foreach {
      case (mixId, maxRounds, withdrawAddress, masterSecret, isAlice, fullMixBoxId, currentRound, halfMixBoxId, withdrawStatus, mixingTokenId) =>
        try {
          val optEmissionBoxIdFuture = emissionDAO.selectBoxId(mixId, currentRound)
          val tokenBoxIdFuture = tokenEmissionDAO.selectBoxId(mixId)
          val optEmissionBoxId = daoUtils.awaitResult(optEmissionBoxIdFuture)
          val tokenBoxId = daoUtils.awaitResult(tokenBoxIdFuture).getOrElse(throw new Exception(s"mixId $mixId not found in TokenEmissionBox"))
          processFullMix(mixId, maxRounds, withdrawAddress, masterSecret, isAlice, fullMixBoxId, currentRound, halfMixBoxId, optEmissionBoxId, tokenBoxId, withdrawStatus, mixingTokenId)
        } catch {
          case a: Throwable =>
            logger.error(s" [FULL:$mixId ($currentRound)] An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
    }
  }

  private implicit val insertReason = "FullMixer.processFullMix"

  private def str(isAlice: Boolean) = if (isAlice) "Alice" else "Bob"

  /**
   * processes a specific full-box (withdraw, mix as alice, mix as bob)
   *
   * @param mixId            mix id of the full-box
   * @param maxRounds        mix level
   * @param withdrawAddress  withdraw address of this mix
   * @param masterSecret     master secret
   * @param isAlice          is type of full-box alice or bob
   * @param fullMixBoxId     box id
   * @param currentRound     current round of te mix
   * @param halfMixBoxId     half-box id (which was spent in order to create this full-box)
   * @param optEmissionBoxId fee-box spent
   * @param tokenBoxId       token-emission box id
   * @param withdrawStatus   withdraw status (whether is set to be withdrawn)
   * @param mixingTokenId    mixing token id (empty if erg)
   */
  private def processFullMix(mixId: String, maxRounds: Int, withdrawAddress: String, masterSecret: BigInt, isAlice: Boolean, fullMixBoxId: String, currentRound: Int, halfMixBoxId: String, optEmissionBoxId: Option[String], tokenBoxId: String, withdrawStatus: String, mixingTokenId: String): Unit = networkUtils.usingClient { implicit ctx =>

    // If all is ok then the following should be true:
    //  1. halfMixBoxId must have minConfirmations
    //  2. fullMixBoxId must be unspent
    // If either of these are violated then we have to do a rescan and repopulate the database
    // The violations can occur due to a fork

    val fullMixBoxConfirmations = explorer.getConfirmationsForBoxId(fullMixBoxId)

    if (fullMixBoxConfirmations >= Configs.numConfirmation) {
      // proceed only if full mix box is mature enough
      val currentTime = now
      explorer.getSpendingTxId(fullMixBoxId) match {
        case Some(txId) => // spent
          if (!daoUtils.awaitResult(withdrawDAO.existsByTxId(txId))) { // we need to fast-forward, full box is spent but its not withdraw... by rescanning block-chain
            logger.error(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] [ERROR] Rescanning because full:$fullMixBoxId is spent")
            val new_scan = PendingRescan(mixId, currentTime, currentRound, goBackward = false, "full", fullMixBoxId)
            rescanDAO.updateById(new_scan)
          }
        case None => // not spent, good to go
          val fullMixBox: OutBox = networkUtils.getOutBoxById(fullMixBoxId)
          val numTokens = fullMixBox.getToken(TokenErgoMix.tokenId)
          var tokenSize = 1
          if (mixingTokenId.nonEmpty) tokenSize = 2
          val optHalfMixBoxId = getRandomValidBoxId(getHalfBoxes
            .filterNot(box => daoUtils.awaitResult(fullMixDAO.existsByBoxId(box.id)))
            .filter(box => box.amount == fullMixBox.amount && box.getToken(mixingTokenId) == fullMixBox.getToken(mixingTokenId)
              && box.tokens.size == tokenSize && box.registers.nonEmpty
              && box.getToken(TokenErgoMix.tokenId) > 0).map(_.id)
          )

          val wallet = new Wallet(masterSecret)
          val secret = wallet.getSecret(currentRound)
          if (numTokens < 2 || (withdrawStatus.equals(WithdrawRequested.value) || withdrawStatus.equals(HopRequested.value)) || (currentRound >= maxRounds && Configs.stopMixingWhenReachedThreshold)) {
            if (withdrawAddress.nonEmpty) {
              val optFeeEmissionBoxId = getRandomValidBoxId(getFeeBoxes.map(_.id).filterNot(id => daoUtils.awaitResult(emissionDAO.existsByBoxId(id))))
              if (withdrawDAO.shouldWithdraw(mixId, fullMixBoxId) && optFeeEmissionBoxId.nonEmpty) {
                val tx = if (withdrawStatus.equals(HopRequested.value)) {
                  val hopSecret = wallet.getSecret(0, toFirst = true)
                  val hopAddress = WalletHelper.getProveDlogAddress(hopSecret, ctx)
                  val tx = aliceOrBob.spendFullMixBox(isAlice, secret, fullMixBoxId, hopAddress, Array[String](optFeeEmissionBoxId.get), Configs.defaultHalfFee, withdrawAddress, broadCast = false)
                  val txBytes = tx.toJson(false).getBytes("utf-16")
                  val new_withdraw = WithdrawTx(mixId, tx.getId, currentTime, fullMixBoxId + "," + optFeeEmissionBoxId.get, txBytes)
                  withdrawDAO.updateById(new_withdraw, HopRequested.value)
                  logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Hop txId: ${tx.getId}, is requested: ${(withdrawStatus.equals(WithdrawRequested.value) || withdrawStatus.equals(HopRequested.value))}")
                  tx
                }
                else {
                  val tx = aliceOrBob.spendFullMixBox(isAlice, secret, fullMixBoxId, withdrawAddress, Array[String](optFeeEmissionBoxId.get), Configs.defaultHalfFee, withdrawAddress, broadCast = false)
                  val txBytes = tx.toJson(false).getBytes("utf-16")
                  val new_withdraw = WithdrawTx(mixId, tx.getId, currentTime, fullMixBoxId + "," + optFeeEmissionBoxId.get, txBytes)
                  withdrawDAO.updateById(new_withdraw, WithdrawRequested.value)
                  logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Withdraw txId: ${tx.getId}, is requested: ${(withdrawStatus.equals(WithdrawRequested.value) || withdrawStatus.equals(HopRequested.value))}")
                  tx
                }

                try {
                  ctx.sendTransaction(tx)
                }
                catch {
                  case e: Throwable =>
                    logger.error(s"  something unexpected has happened! tx got refused by the node!")
                    logger.debug(s"  Exception: ${e.getMessage}")
                }
              }
            } else {
              mixingRequestsDAO.updateMixStatus(mixId, Complete)
              logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] is done mixing but no withdraw address to withdraw!")
            }
          } else { // need to remix
            // Note that the emission box contract requires that there must always be a emission box as the output. This will only work if there is some change to be given back
            // Hence we only select those emission boxes which have at least twice the fee amount.
            val currentTime = now

            val optFeeEmissionBoxId = getRandomValidBoxId(getFeeBoxes.map(_.id).filterNot(id => daoUtils.awaitResult(emissionDAO.existsByBoxId(id))))
            if (optFeeEmissionBoxId.nonEmpty) { // proceed only if there is at least one fee emission box
              val feeEmissionBoxId = optFeeEmissionBoxId.get
              // store emission boxid in db to ensure we are not double spending same emission box in multiple iterations of the loop
              val nextRound = currentRound + 1
              val nextSecret = wallet.getSecret(nextRound)

              def nextAlice = {
                val feeAmount = getFee(mixingTokenId, tokenAmount = fullMixBox.getToken(mixingTokenId), fullMixBox.amount, isFull = false)
                val halfMixTx = aliceOrBob.spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, feeEmissionBoxId, feeAmount)
                halfMixDAO.insertHalfMix(HalfMix(mixId, nextRound, currentTime, halfMixTx.getHalfMixBox.id, isSpent = false))
                mixStateDAO.updateById(MixState(mixId, nextRound, true))
                mixStateHistoryDAO.insertMixHistory(MixHistory(mixId, nextRound, isAlice = true, currentTime))
                val new_tx = halfMixTx.tx
                mixTransactionsDAO.updateById(MixTransaction(halfMixTx.getHalfMixBox.id, new_tx.getId, new_tx.toJson(false).getBytes("utf-16")))
                logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Alice [full:$fullMixBoxId, fee:$feeEmissionBoxId], txId: ${halfMixTx.tx.getId}")
                try {
                  ctx.sendTransaction(halfMixTx.tx)
                }
                catch {
                  case e: Throwable =>
                    logger.error(s"  something unexpected has happened! tx got refused by the node!")
                    logger.debug(s"  Exception: ${e.getMessage}")
                }
                halfMixTx.tx.getId
              }

              def nextBob(halfMixBoxId: String) = {
                val feeAmount = getFee(mixingTokenId, tokenAmount = fullMixBox.getToken(mixingTokenId), fullMixBox.amount, isFull = true)
                val (fullMixTx, bit) = aliceOrBob.spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, halfMixBoxId, feeEmissionBoxId, feeAmount)
                val (left, right) = fullMixTx.getFullMixBoxes
                val bobFullMixBox = if (bit) right else left
                fullMixDAO.insertFullMix(FullMix(mixId, nextRound, currentTime, halfMixBoxId, bobFullMixBox.id))
                mixStateDAO.updateById(MixState(mixId, nextRound, false))
                mixStateHistoryDAO.insertMixHistory(MixHistory(mixId, nextRound, isAlice = false, currentTime))
                val new_tx = fullMixTx.tx
                mixTransactionsDAO.updateById(MixTransaction(bobFullMixBox.id, new_tx.getId, new_tx.toJson(false).getBytes("utf-16")))
                logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Bob [full:$fullMixBoxId, half:$halfMixBoxId, fee:$feeEmissionBoxId], txId: ${fullMixTx.tx.getId}")
                try {
                  ctx.sendTransaction(fullMixTx.tx)
                }
                catch {
                  case e: Throwable =>
                    logger.error(s"  something unexpected has happened! tx got refused by the node!")
                    logger.debug(s"  Exception: ${e.getMessage}")
                }
                fullMixTx.tx.getId
              }

              if (optHalfMixBoxId.isEmpty) {
                // do nothing
                if (Configs.mixOnlyAsBob) {
                  logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Ignored because mixOnlyAsBob is set to true and no half-box is available currently!")
                  return
                }

                nextAlice
              } else {
                nextBob(optHalfMixBoxId.get)
              }
              emissionDAO.insert(mixId, nextRound, feeEmissionBoxId)
            } else {
              logger.warn(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] No fee emission boxes")
            }
          }
      }
    } else {
      logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Insufficient confirmations ($fullMixBoxConfirmations) [full:$fullMixBoxId]")
      if (fullMixBoxConfirmations == 0) { // 0 confirmations for fullMixBoxId
        // here we check if transaction is missing from mempool and also not mined. if so, we rebroadcast it!
        val prevTx = daoUtils.awaitResult(mixTransactionsDAO.selectByBoxId(fullMixBoxId))
        try {
          if (prevTx.nonEmpty) {
            ctx.sendTransaction(ctx.signedTxFromJson(prevTx.get.toString))
            logger.info(s"  broadcasted tx ${prevTx.get.txId}")
          }
        }
        catch {
          case e: Throwable =>
            logger.info(s"  broadcasted tx ${prevTx.get.txId}, failed (probably due to double spent or fork)")
            logger.debug(s"  Exception: ${e.getMessage}")
            // first check the fork condition. If the halfMixBoxId is not confirmed then there is a fork
            explorer.doesBoxExist(halfMixBoxId) match {
              case Some(false) =>
                // halfMixBoxId is no longer confirmed. This indicates a fork. We need to rescan
                logger.error(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] [ERROR] Rescanning [half:$halfMixBoxId disappeared]")
                Thread.currentThread().getStackTrace foreach println
                val new_scan = PendingRescan(mixId, now, currentRound, goBackward = true, "full", fullMixBoxId)
                rescanDAO.updateById(new_scan)
              case Some(true) =>
                if (networkUtils.isDoubleSpent(halfMixBoxId, fullMixBoxId) || (currentRound == 0 && networkUtils.isDoubleSpent(tokenBoxId, fullMixBoxId))) {
                  // the halfMixBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
                  try {
                    logger.info(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] <-- Bob (undo). [full: $fullMixBoxId not spent while half: $halfMixBoxId or token: $tokenBoxId spent]")
                    allMixDAO.undoMixStep(mixId, currentRound, fullMixBoxId, true)
                  } catch {
                    case a: Throwable =>
                      logger.error(getStackTraceStr(a))
                  }
                } else {
                  optEmissionBoxId.map { emissionBoxId =>
                    if (networkUtils.isDoubleSpent(emissionBoxId, fullMixBoxId)) {
                      // the emissionBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
                      try {
                        logger.info(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] <-- Bob (undo). [full:$fullMixBoxId not spent while fee:$emissionBoxId spent]")
                        allMixDAO.undoMixStep(mixId, currentRound, fullMixBoxId, true)
                      } catch {
                        case a: Throwable =>
                          logger.error(getStackTraceStr(a))
                      }
                    }
                  }
                }
              case _ =>
            }
        }
      }
    }
  }
}
