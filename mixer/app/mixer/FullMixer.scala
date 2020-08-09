package mixer

import app.{Configs, TokenErgoMix}
import cli.{AliceOrBob, ErgoMixCLIUtil}
import db.ScalaDB._
import db.core.DataStructures.anyToAny
import mixer.Columns._
import mixer.ErgoMixerUtils._
import mixer.Models.MixStatus.{Complete, Running}
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import mixer.Models.{MixTransaction, OutBox}
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import mixer.Util.now
import org.ergoplatform.appkit.InputBox
import cli.{AliceOrBob, ErgoMixCLIUtil}
import db.ScalaDB._
import db.core.DataStructures.anyToAny
import app.{Configs, TokenErgoMix}
import play.api.Logger

import scala.jdk.CollectionConverters._

class FullMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)
  import tables._

  var fees: Seq[OutBox] = _
  var halfs: Seq[OutBox] = _

  def getHalfBoxes: Seq[OutBox] = {
    if (halfs == null)
      halfs = ErgoMixCLIUtil.getHalfMixBoxes(considerPool = true)
    halfs
  }

  def getFeeBoxes: Seq[OutBox] = {
    if (fees == null)
      fees = ErgoMixCLIUtil.getFeeEmissionBoxes(considerPool = true)
    fees
  }

  def processFullMixQueue(): Unit = {

    // Read our full mix boxes from the full mix table and perform the next step. If the number of rounds are completed, then the next step will be withdraw, otherwise the next step is remix
    // If the next stp is remix, then perform as follows: if there is already a full mix box existing, then behave as Bob and try to spend that. Otherwise behave as Alice and create a half mix box.
    var fullMixes = mixRequestsTable.select(
      mixIdCol, // no need to use "of" for the table where the select query is made from. (i.e., mixRequestsTable)
      numRoundsCol,
      withdrawAddressCol,
      masterSecretCol,
      isAliceCol of mixStateTable,
      fullMixBoxIdCol of fullMixTable,
      roundCol of mixStateTable,
      halfMixBoxIdCol of fullMixTable,
      mixWithdrawStatusCol,
      tokenId,
      mixingTokenAmount,
    ).where(
      mixIdCol === (mixIdCol of mixStateTable), // no need to use "of" for the table where the select query is made from
      mixIdCol === (mixIdCol of fullMixTable),
      (roundCol of fullMixTable) === (roundCol of mixStateTable),
      mixStatusCol === Running.value or mixWithdrawStatusCol === WithdrawRequested.value
    ).as{arr =>
        val i = arr.toIterator
        (
          i.next.as[String], // mixId
          i.next.as[Int],  // max rounds
          i.next.as[String], // withdraw address
          i.next.as[BigDecimal].toBigInt(), // master secret
          i.next.as[Boolean], // isAlice
          i.next.as[String], // fullMixBoxId
          i.next.as[Int], // current round
          i.next.as[String], // halfMixBoxId
          i.next.as[String], // withdrawStatus
          i.next.as[String], // mixing token id
        )
    }
    fullMixes = scala.util.Random.shuffle(fullMixes)
    fees = null
    halfs = null

    if (fullMixes.nonEmpty) logger.info(s"[FULL] Processing following ids")
    fullMixes foreach (x => logger.info(s"  > ${x._1}"))

    fullMixes.map{
      case (mixId, maxRounds, withdrawAddress, masterSecret, isAlice, fullMixBoxId, currentRound, halfMixBoxId, withdrawStatus, mixingTokenId) =>
        try {
          val optEmissionBoxId = spentFeeEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId, roundCol === currentRound).firstAsT[String].headOption
          val tokenBoxId = spentTokenEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId).firstAsT[String].head
          processFullMix(mixId, maxRounds, withdrawAddress, masterSecret, isAlice, fullMixBoxId, currentRound, halfMixBoxId, optEmissionBoxId, tokenBoxId, withdrawStatus, mixingTokenId)
        } catch {
          case a:Throwable =>
            logger.error(s" [FULL:$mixId ($currentRound)] An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
    }
  }

  private implicit val insertReason = "FullMixer.processFullMix"

  private def str(isAlice:Boolean) = if (isAlice) "Alice" else "Bob"
  
  private def processFullMix(mixId:String, maxRounds:Int, withdrawAddress:String, masterSecret:BigInt, isAlice:Boolean, fullMixBoxId:String, currentRound:Int, halfMixBoxId:String, optEmissionBoxId:Option[String], tokenBoxId: String, withdrawStatus: String, mixingTokenId: String) = ErgoMixCLIUtil.usingClient{ implicit ctx =>
    val explorer = new BlockExplorer()

    // If all is ok then the following should be true:
    //  1. halfMixBoxId must have minConfirmations
    //  2. fullMixBoxId must be unspent
    // If either of these are violated then we have to do a rescan and repopulate the database
    // The violations can occur due to a fork

    val fullMixBoxConfirmations = explorer.getConfirmationsForBoxId(fullMixBoxId)

    if (fullMixBoxConfirmations >= minConfirmations) {
      // proceed only if full mix box is mature enough
      val currentTime = now
      explorer.getSpendingTxId(fullMixBoxId) match {
        case Some(txId) => // spent
          if (!withdrawTable.exists(txIdCol === txId)) { // we need to fast-forward, full box is spent but its not withdraw... by rescanning block-chain
            logger.error(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] [ERROR] Rescanning because full:$fullMixBoxId is spent")
            Thread.currentThread().getStackTrace foreach println
            insertForwardScan(mixId, currentTime, currentRound, isHalfMixTx = false, fullMixBoxId)
          }
        case None => // not spent, good to go
          val fullMixBox: OutBox = ErgoMixCLIUtil.getOutBoxById(fullMixBoxId)
          val numTokens = fullMixBox.getToken(TokenErgoMix.tokenId)
          var tokenSize = 1
          if (mixingTokenId.nonEmpty) tokenSize = 2
          val optHalfMixBoxId = getRandomValidBoxId(getHalfBoxes
            .filterNot(box => fullMixTable.exists(halfMixBoxIdCol === box.id))
            .filter(box => box.amount == fullMixBox.amount && box.getToken(mixingTokenId) == fullMixBox.getToken(mixingTokenId)
              && box.tokens.size == tokenSize && box.registers.nonEmpty
              && box.getToken(TokenErgoMix.tokenId) > 0).map(_.id)
          )

          val wallet = new Wallet(masterSecret)
          val secret = wallet.getSecret(currentRound)
          if (numTokens < 2 || withdrawStatus.equals(WithdrawRequested.value)) {
            if (withdrawAddress.nonEmpty) {
              val optFeeEmissionBoxId = getRandomValidBoxId(getFeeBoxes.map(_.id).filterNot(id => spentFeeEmissionBoxTable.exists(boxIdCol === id)))
              if (shouldWithdraw(mixId, fullMixBoxId) && optFeeEmissionBoxId.nonEmpty) {
                val tx = AliceOrBob.spendFullMixBox(isAlice, secret, fullMixBoxId, withdrawAddress, Array[String](optFeeEmissionBoxId.get), Configs.defaultHalfFee, withdrawAddress, broadCast = false)
                val txBytes = tx.toJson(false).getBytes("utf-16")
                tables.insertWithdraw(mixId, tx.getId, currentTime, fullMixBoxId + "," + optFeeEmissionBoxId.get, txBytes)
                logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Withdraw txId: ${tx.getId}, is requested: ${withdrawStatus.equals(WithdrawRequested.value)}")
                val sendRes = ctx.sendTransaction(tx)
                if (sendRes == null) logger.error(s"  something unexpected has happened! tx got refused by the node!")
              }
            } else {
              mixRequestsTable.update(mixStatusCol <-- Complete.value).where(mixIdCol === mixId)
              logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] is done mixing but no withdraw address to withdraw!")
            }
          } else { // need to remix
            // Note that the emission box contract requires that there must always be a emission box as the output. This will only work if there is some change to be given back
            // Hence we only select those emission boxes which have at least twice the fee amount.
            val currentTime = now

            val optFeeEmissionBoxId = getRandomValidBoxId(getFeeBoxes.map(_.id).filterNot(id => spentFeeEmissionBoxTable.exists(boxIdCol === id)))
            if (optFeeEmissionBoxId.nonEmpty) { // proceed only if there is at least one fee emission box
              val feeEmissionBoxId = optFeeEmissionBoxId.get
              // store emission boxid in db to ensure we are not double spending same emission box in multiple iterations of the loop
              val nextRound = currentRound + 1
              val nextSecret = wallet.getSecret(nextRound)

              def nextAlice = {
                val feeAmount = getFee(mixingTokenId, tokenAmount = fullMixBox.getToken(mixingTokenId), fullMixBox.amount, isFull = false)
                val halfMixTx = AliceOrBob.spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, feeEmissionBoxId, feeAmount)
                tables.insertHalfMix(mixId, nextRound, currentTime, halfMixTx.getHalfMixBox.id, isSpent = false)
                mixStateTable.update(roundCol <-- nextRound, isAliceCol <-- true).where(mixIdCol === mixId)
                tables.insertMixStateHistory(mixId, nextRound, isAlice = true, currentTime)
                tables.insertTx(halfMixTx.getHalfMixBox.id, halfMixTx.tx)
                logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Alice [full:$fullMixBoxId, fee:$feeEmissionBoxId], txId: ${halfMixTx.tx.getId}")
                val sendRes = ctx.sendTransaction(halfMixTx.tx)
                if (sendRes == null) logger.error(s"  something unexpected has happened! tx got refused by the node!")
                halfMixTx.tx.getId
              }

              def nextBob(halfMixBoxId: String) = {
                val feeAmount = getFee(mixingTokenId, tokenAmount = fullMixBox.getToken(mixingTokenId), fullMixBox.amount, isFull = true)
                val (fullMixTx, bit) = AliceOrBob.spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, halfMixBoxId, feeEmissionBoxId, feeAmount)
                val (left, right) = fullMixTx.getFullMixBoxes
                val bobFullMixBox = if (bit) right else left
                tables.insertFullMix(mixId, nextRound, currentTime, halfMixBoxId, bobFullMixBox.id)
                mixStateTable.update(roundCol <-- nextRound, isAliceCol <-- false).where(mixIdCol === mixId)
                tables.insertMixStateHistory(mixId, nextRound, isAlice = false, currentTime)
                tables.insertTx(bobFullMixBox.id, fullMixTx.tx)
                logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Bob [full:$fullMixBoxId, half:$halfMixBoxId, fee:$feeEmissionBoxId], txId: ${fullMixTx.tx.getId}")
                val sendRes = ctx.sendTransaction(fullMixTx.tx)
                if (sendRes == null) logger.error(s"  something unexpected has happened! tx got refused by the node!")
                fullMixTx.tx.getId
              }

              if (optHalfMixBoxId.isEmpty) {
                nextAlice
              } else {
                nextBob(optHalfMixBoxId.get)
              }
              spentFeeEmissionBoxTable.insert(mixId, nextRound, feeEmissionBoxId)
            } else {
              logger.warn(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] No fee emission boxes")
            }
          }
      }
    } else {
      logger.info(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Insufficient confirmations ($fullMixBoxConfirmations) [full:$fullMixBoxId]")
      if (fullMixBoxConfirmations == 0) { // 0 confirmations for fullMixBoxId
        // here we check if transaction is missing from mempool and also not mined. if so, we rebroadcast it!
        val prevTx = mixTransactionsTable.selectStar.where(boxIdCol === fullMixBoxId).as(MixTransaction(_)).headOption
        if (prevTx.nonEmpty) {
          val res = ctx.sendTransaction(ctx.signedTxFromJson(prevTx.get.toString))
          logger.info(s"  broadcasted tx ${prevTx.get.txId}, response: $res")
        }

        // first check the fork condition. If the halfMixBoxId is not confirmed then there is a fork
        explorer.doesBoxExist(halfMixBoxId) match {
          case Some(false) =>
            // halfMixBoxId is no longer confirmed. This indicates a fork. We need to rescan
            logger.error(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] [ERROR] Rescanning [half:$halfMixBoxId disappeared]")
            Thread.currentThread().getStackTrace foreach println
            insertBackwardScan(mixId, now, currentRound, isHalfMixTx = false, fullMixBoxId)
          case Some(true) =>
            if (isDoubleSpent(halfMixBoxId, fullMixBoxId) || (currentRound == 0 && isDoubleSpent(tokenBoxId, fullMixBoxId))) {
              // the halfMixBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
              try {
                logger.info(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] <-- Bob (undo). [full: $fullMixBoxId not spent while half: $halfMixBoxId or token: $tokenBoxId spent]")
                undoMixStep(mixId, currentRound, fullMixTable)
              } catch {
                case a:Throwable =>
                  logger.error(getStackTraceStr(a))
              }
            } else {
              optEmissionBoxId.map{ emissionBoxId =>
                if (isDoubleSpent(emissionBoxId, fullMixBoxId)) {
                  // the emissionBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
                  try {
                    logger.info(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] <-- Bob (undo). [full:$fullMixBoxId not spent while fee:$emissionBoxId spent]")
                    undoMixStep(mixId, currentRound, fullMixTable)
                  } catch {
                    case a:Throwable =>
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
