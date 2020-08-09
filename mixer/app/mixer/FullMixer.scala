package mixer

import mixer.Columns._
import mixer.ErgoMixerUtil._
import mixer.Models.MixStatus.{Complete, Running}
import mixer.Models.MixTransaction
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import mixer.Util.now
import org.ergoplatform.appkit.InputBox
import cli.{AliceOrBob, ErgoMixCLIUtil}
import db.ScalaDB._
import db.core.DataStructures.anyToAny
import app.{TokenErgoMix, Configs}

import scala.jdk.CollectionConverters._

class FullMixer(tables: Tables) {
  import tables._

  def processFullMixQueue():Unit = {

    // Read our full mix boxes from the full mix table and perform the next step. If the number of rounds are completed, then the next step will be withdraw, otherwise the next step is remix
    // If the next stp is remix, then perform as follows: if there is already a full mix box existing, then behave as Bob and try to spend that. Otherwise behave as Alice and create a half mix box.
    val fullMixes = mixRequestsTable.select(
      mixIdCol, // no need to use "of" for the table where the select query is made from. (i.e., mixRequestsTable)
      numRoundsCol,
      withdrawAddressCol,
      masterSecretCol,
      isAliceCol of mixStateTable,
      fullMixBoxIdCol of fullMixTable,
      roundCol of mixStateTable,
      halfMixBoxIdCol of fullMixTable,
      mixWithdrawStatusCol
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
        )
    }

    if (fullMixes.nonEmpty) println(s"[FULL] Processing following ids")

    fullMixes foreach (x => println(s"  > ${x._1}"))

    fullMixes.map{
      case (mixId, maxRounds, withdrawAddress, masterSecret, isAlice, fullMixBoxId, currentRound, halfMixBoxId, withdrawStatus) =>
        try {
          val optEmissionBoxId = spentFeeEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId, roundCol === currentRound).firstAsT[String].headOption
          val tokenBoxId = spentTokenEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId).firstAsT[String].head
          processFullMix(mixId, maxRounds, withdrawAddress, masterSecret, isAlice, fullMixBoxId, currentRound, halfMixBoxId, optEmissionBoxId, tokenBoxId, withdrawStatus)
        } catch {
          case a:Throwable =>
            println(s" [FULL:$mixId ($currentRound)] An error occurred. Stacktrace below")
            a.printStackTrace()
        }
    }
  }

  private implicit val insertReason = "FullMixer.processFullMix"

  private def str(isAlice:Boolean) = if (isAlice) "Alice" else "Bob"
  
  private def processFullMix(mixId:String, maxRounds:Int, withdrawAddress:String, masterSecret:BigInt, isAlice:Boolean, fullMixBoxId:String, currentRound:Int, halfMixBoxId:String, optEmissionBoxId:Option[String], tokenBoxId: String, withdrawStatus: String) = ErgoMixCLIUtil.usingClient{ implicit ctx =>
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
            println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] [ERROR] Rescanning because full:$fullMixBoxId is spent")
            Thread.currentThread().getStackTrace foreach println
            insertForwardScan(mixId, currentTime, currentRound, isHalfMixTx = false, fullMixBoxId)
          }
        case None => // not spent, good to go
          val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
          val numTokens = fullMixBox.getTokens.get(0).getValue
          val optHalfMixBoxId = getRandomValidBoxId(ErgoMixCLIUtil.getHalfMixBoxes(fullMixBox.getValue, considerPool = true)
            .filterNot(box => fullMixTable.exists(halfMixBoxIdCol === box.getId.toString))
            .filter(box => box.getRegisters.size() > 0
              && box.getTokens.asScala.map(_.getId.toString).toList.contains(TokenErgoMix.tokenId)).map(_.getId.toString))

          val wallet = new Wallet(masterSecret)
          val secret = wallet.getSecret(currentRound)
          if (numTokens < 2 || withdrawStatus.equals(WithdrawRequested.value)) {
            if (withdrawAddress.nonEmpty) {
              if (shouldWithdraw(mixId, fullMixBoxId)) {
                val tx = AliceOrBob.spendFullMixBox(isAlice, secret, fullMixBoxId, withdrawAddress, Array[String](), Configs.feeAmount, withdrawAddress, broadCast = true)
                val txBytes = tx.toJson(false).getBytes("utf-16")
                tables.insertWithdraw(mixId, tx.getId, currentTime, fullMixBoxId, txBytes)
                println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Withdraw txId: ${tx.getId}, is requested: ${withdrawStatus.equals(WithdrawRequested.value)}")
              }
            } else {
              mixRequestsTable.update(mixStatusCol <-- Complete.value).where(mixIdCol === mixId)
              println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] is done mixing but no withdraw address to withdraw!")
            }
          } else { // need to remix
            // Note that the emission box contract requires that there must always be a emission box as the output. This will only work if there is some change to be given back
            // Hence we only select those emission boxes which have at least twice the fee amount.
            val optFeeEmissionBoxId = getRandomValidBoxId(
              ErgoMixCLIUtil.getFeeEmissionBoxes(considerPool = true).map(_.id).filterNot(id => spentFeeEmissionBoxTable.exists(boxIdCol === id))
            )

            val currentTime = now

            if (optFeeEmissionBoxId.nonEmpty) { // proceed only if there is at least one fee emission box

              val feeEmissionBoxId = optFeeEmissionBoxId.get
              // store emission boxid in db to ensure we are not double spending same emission box in multiple iterations of the loop
              val nextRound = currentRound + 1
              val nextSecret = wallet.getSecret(nextRound)

              def nextAlice = {
                val halfMixTx = AliceOrBob.spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, feeEmissionBoxId)
                tables.insertHalfMix(mixId, nextRound, currentTime, halfMixTx.getHalfMixBox.id, isSpent = false)
                mixStateTable.update(roundCol <-- nextRound, isAliceCol <-- true).where(mixIdCol === mixId)
                tables.insertMixStateHistory(mixId, nextRound, isAlice = true, currentTime)
                tables.insertTx(halfMixTx.getHalfMixBox.id, halfMixTx.tx)
                println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Alice [full:$fullMixBoxId, fee:$feeEmissionBoxId], txId: ${halfMixTx.tx.getId}")
                halfMixTx.tx.getId
              }

              def nextBob(halfMixBoxId: String) = {
                val (fullMixTx, bit) = AliceOrBob.spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, halfMixBoxId, feeEmissionBoxId)
                val (left, right) = fullMixTx.getFullMixBoxes
                val bobFullMixBox = if (bit) right else left
                tables.insertFullMix(mixId, nextRound, currentTime, halfMixBoxId, bobFullMixBox.id)
                mixStateTable.update(roundCol <-- nextRound, isAliceCol <-- false).where(mixIdCol === mixId)
                tables.insertMixStateHistory(mixId, nextRound, isAlice = false, currentTime)
                tables.insertTx(bobFullMixBox.id, fullMixTx.tx)
                println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] --> Bob [full:$fullMixBoxId, half:$halfMixBoxId, fee:$feeEmissionBoxId], txId: ${fullMixTx.tx.getId}")
                fullMixTx.tx.getId
              }

              if (optHalfMixBoxId.isEmpty) {
                nextAlice
              } else {
                nextBob(optHalfMixBoxId.get)
              }
              spentFeeEmissionBoxTable.insert(mixId, nextRound, feeEmissionBoxId)
            } else {
              println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] No fee emission boxes")
            }
          }
      }
    } else {
      println(s" [FULL:$mixId ($currentRound) ${str(isAlice)}] Insufficient confirmations ($fullMixBoxConfirmations) [full:$fullMixBoxId]")
      if (fullMixBoxConfirmations == 0) { // 0 confirmations for fullMixBoxId
        // here we check if transaction is missing from mempool and also not mined. if so, we rebroadcast it!
        val prevTx = mixTransactionsTable.selectStar.where(boxIdCol === fullMixBoxId).as(MixTransaction(_)).headOption
//        if (prevTx.nonEmpty && !ErgoMixCLIUtil.isTxInPool(prevTx.get.txId)) {
        println(s"  rebroadcasting tx ${prevTx.get.txId}")
        ctx.sendTransaction(ctx.signedTxFromJson(prevTx.get.toString))
//        }

        // first check the fork condition. If the halfMixBoxId is not confirmed then there is a fork
        explorer.doesBoxExist(halfMixBoxId) match {
          case Some(false) =>
            // halfMixBoxId is no longer confirmed. This indicates a fork. We need to rescan
            println(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] [ERROR] Rescanning [half:$halfMixBoxId disappeared]")
            Thread.currentThread().getStackTrace foreach println
            insertBackwardScan(mixId, now, currentRound, isHalfMixTx = false, fullMixBoxId)
          case Some(true) =>
            if (isDoubleSpent(halfMixBoxId, fullMixBoxId) || (currentRound == 0 && isDoubleSpent(tokenBoxId, fullMixBoxId))) {
              // the halfMixBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
              try {
                println(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] <-- Bob (undo). [full: $fullMixBoxId not spent while half: $halfMixBoxId or token: $tokenBoxId spent]")
                undoMixStep(mixId, currentRound, fullMixTable)
              } catch {
                case a:Throwable =>
                  a.printStackTrace()
              }
            } else {
              optEmissionBoxId.map{ emissionBoxId =>
                if (isDoubleSpent(emissionBoxId, fullMixBoxId)) {
                  // the emissionBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
                  try {
                    println(s"  [FULL:$mixId ($currentRound) ${str(isAlice)}] <-- Bob (undo). [full:$fullMixBoxId not spent while fee:$emissionBoxId spent]")
                    undoMixStep(mixId, currentRound, fullMixTable)
                  } catch {
                    case a:Throwable =>
                      a.printStackTrace()
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
