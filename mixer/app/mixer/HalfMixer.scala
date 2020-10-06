package mixer

import app.ergomix.FullMixBox
import app.{Configs, TokenErgoMix}
import cli.{AliceOrBob, MixUtils}
import db.Columns.{withdrawAddressCol, _}
import db.ScalaDB._
import db.Tables
import db.core.DataStructures.anyToAny
import helpers.ErgoMixerUtils._
import helpers.Util.now
import mixer.Models.MixStatus.Running
import mixer.Models.MixTransaction
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import org.ergoplatform.appkit.InputBox
import play.api.Logger
import sigmastate.eval._

class HalfMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)

  import tables._

  /**
   * processes half-boxes one by one
   */
  def processHalfMixQueue(): Unit = {
    // Read (from db) our half mix boxes with unspent status
    // Check (from block explorer) if any of those are spent, obtain our full mix box for each spent half mix box, save to full mix table and mark half mix box as spent

    val halfMixes = halfMixTable.select(
      mixIdCol of halfMixTable,
      roundCol of mixStateTable,
      halfMixBoxIdCol of halfMixTable,
      masterSecretCol of mixRequestsTable,
      mixWithdrawStatusCol of mixRequestsTable,
      withdrawAddressCol of mixRequestsTable,
    ).where(
      (isSpentCol of halfMixTable) === false,
      (mixIdCol of halfMixTable) === (mixIdCol of mixRequestsTable),
      (mixIdCol of halfMixTable) === (mixIdCol of mixStateTable),
      (roundCol of halfMixTable) === (roundCol of mixStateTable),
      (mixStatusCol of mixRequestsTable) === Running.value or (mixWithdrawStatusCol of mixRequestsTable) === WithdrawRequested.value
    ).as { ar =>
      val i = ar.toIterator
      (
        i.next.as[String], // mix ID
        i.next.as[Int], // current round
        i.next.as[String], // half mix box id
        i.next.as[BigDecimal].toBigInt(), // master secret
        i.next.as[String], // withdrawStatus
        i.next.as[String], // withdrawAddress
      )
    }

    if (halfMixes.nonEmpty) logger.info(s"[HALF] Processing following ids")
    halfMixes foreach (x => logger.info(s"  > ${x._1}"))

    halfMixes.map {
      case (mixId, currentRound, halfMixBoxId, masterSecret, withdrawStatus, withdrawAddress) =>
        try {
          val optEmissionBoxId = spentFeeEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId, roundCol === currentRound).firstAsT[String].headOption
          val tokenBoxId = spentTokenEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId).firstAsT[String].head
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
  private def processHalfMix(mixId: String, currentRound: Int, halfMixBoxId: String, masterSecret: BigInt, optEmissionBoxId: Option[String], tokenBoxId: String, withdrawStatus: String, withdrawAddress: String) = MixUtils.usingClient { implicit ctx =>
    logger.info(s"[HALF:$mixId ($currentRound)] [half:$halfMixBoxId]")
    val currentTime = now
    val explorer = new BlockExplorer()

    val halfMixBoxConfirmations = explorer.getConfirmationsForBoxId(halfMixBoxId)
    if (halfMixBoxConfirmations >= minConfirmations) {
      logger.info(s" [HALF: $mixId ($currentRound)] Sufficient confirmations ($halfMixBoxConfirmations) [half:$halfMixBoxId]")
      val spendingTx = MixUtils.getSpendingTxId(halfMixBoxId)
      if (spendingTx.isEmpty && withdrawStatus.equals(WithdrawRequested.value)) {
        if (shouldWithdraw(mixId, halfMixBoxId)) {
          val optFeeEmissionBoxId = getRandomValidBoxId(
            MixUtils.getFeeEmissionBoxes(considerPool = true)
              .map(_.id).filterNot(id => spentFeeEmissionBoxTable.exists(boxIdCol === id))
          )
          if (optFeeEmissionBoxId.nonEmpty) {
            require(withdrawAddress.nonEmpty)
            val wallet = new Wallet(masterSecret)
            val secret = wallet.getSecret(currentRound).bigInteger
            val tx = AliceOrBob.spendBox(halfMixBoxId, optFeeEmissionBoxId, withdrawAddress, Array(secret), Configs.defaultHalfFee, broadCast = false)
            val txBytes = tx.toJson(false).getBytes("utf-16")
            tables.insertWithdraw(mixId, tx.getId, currentTime, halfMixBoxId + "," + optFeeEmissionBoxId.get, txBytes)
            logger.info(s" [Half: $mixId ($currentRound)] Withdraw txId: ${tx.getId}, is requested: ${withdrawStatus.equals(WithdrawRequested.value)}")
            val sendRes = ctx.sendTransaction(tx)
            if (sendRes == null) logger.error(s"  something unexpected has happened! tx got refused by the node!")

          } else
            logger.info(s"  HALF: $mixId  No fee emission box available to withdraw, waiting...")

        }
      }
      spendingTx.map { fullMixTxId =>
        explorer.getTransaction(fullMixTxId).map { tx =>

          val boxIds: Seq[String] = tx.outboxes.flatMap(_.getFBox.map(_.id))

          val boxes: Seq[InputBox] = boxIds.flatMap { boxId =>
            try ctx.getBoxesById(boxId).toList catch {
              case a: Throwable => Nil
            }
          }
          val x = new Wallet(masterSecret).getSecret(currentRound).bigInteger
          val gX = TokenErgoMix.g.exp(x)
          boxes.map { box =>
            val fullMixBox = FullMixBox(box)
            require(fullMixBox.r6 == gX)
            if (fullMixBox.r5 == fullMixBox.r4.exp(x)) { // this is our box
              halfMixTable.update(isSpentCol <-- true).where(mixIdCol === mixId and roundCol === currentRound)
              tables.insertFullMix(mixId, currentRound, currentTime, halfMixBoxId, fullMixBox.id)
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
        val prevTx = mixTransactionsTable.selectStar.where(boxIdCol === halfMixBoxId).as(MixTransaction(_)).head
        val res = ctx.sendTransaction(ctx.signedTxFromJson(prevTx.toString))
        logger.info(s"  broadcasted tx ${prevTx.txId}, response: $res")

        optEmissionBoxId match {
          case Some(emissionBoxId) =>
            // we are here, thus; there was a fee emission box used and so this is a remix transaction
            if (isDoubleSpent(emissionBoxId, halfMixBoxId)) {
              // logger.info(s"    [HalfMix $mixId] Zero conf. halfMixBoxId: $halfMixBoxId, currentRound: $currentRound while emissionBoxId $emissionBoxId spent")
              // the emissionBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
              try {
                undoMixStep(mixId, currentRound, halfMixTable, halfMixBoxId)
                logger.info(s" [HALF:$mixId ($currentRound)] <-- (undo) [half:$halfMixBoxId not spent while fee:$emissionBoxId spent]")
              } catch {
                case a: Throwable =>
                  logger.error(getStackTraceStr(a))
              }
            } else {
              // if emission box is ok, lets now check if the input does not exist, if so then this is a fork
              // the input is a full mix box (since this is a remix)
              fullMixTable.select(fullMixBoxIdCol).where(mixIdCol === mixId, roundCol === (currentRound - 1)).firstAsT[String].headOption.map { fullMixBoxId =>
                explorer.doesBoxExist(fullMixBoxId) match {
                  case Some(true) =>
                  case Some(false) =>
                    logger.error(s" [HALF:$mixId ($currentRound)] [ERROR] Rescanning [full:$fullMixBoxId disappeared]")
                    Thread.currentThread().getStackTrace foreach println
                    insertBackwardScan(mixId, now, currentRound, isHalfMixTx = true, halfMixBoxId)
                  case _ =>
                }
              }
            }
          case _ => {
            // no emission box, so this is an entry.
            // token emission box double spent check
            if (isDoubleSpent(tokenBoxId, halfMixBoxId)) {
              try {
                undoMixStep(mixId, currentRound, halfMixTable, halfMixBoxId)
                logger.info(s" [HALF:$mixId ($currentRound)] <-- (undo) [half:$halfMixBoxId not spent while token:$tokenBoxId spent]")
              } catch {
                case a: Throwable =>
                  logger.error(getStackTraceStr(a))
              }
            } else {
              // token emission box is ok
              // Need to check for fork by checking if any of the deposits have suddenly disappeared
              spentDepositsTable.select(boxIdCol).where(purposeCol === mixId).firstAsT[String].find { depositBoxId =>
                explorer.doesBoxExist(depositBoxId).contains(false) // deposit has disappeared, so need to rescan
              }.map { depositBoxId =>
                logger.error(s" [HALF:$mixId ($currentRound)] [ERROR] Rescanning [deposit:$depositBoxId disappeared]")
                Thread.currentThread().getStackTrace foreach println
                insertBackwardScan(mixId, now, currentRound, isHalfMixTx = true, halfMixBoxId)
              }
            }
          }
        }
      }
    }
  }

}