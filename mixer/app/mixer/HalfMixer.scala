package mixer

import org.ergoplatform.appkit.InputBox
import cli.{AliceOrBob, ErgoMixCLIUtil}
import db.ScalaDB._
import db.core.DataStructures.anyToAny
import mixer.Columns.{withdrawAddressCol, _}
import mixer.ErgoMixerUtil._
import mixer.Models.MixStatus.Running
import mixer.Models.MixTransaction
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import mixer.Util.now
import app.ergomix.FullMixBox
import app.{Configs, ErgoMix}
import sigmastate.eval._

class HalfMixer(tables: Tables) {

  import tables._

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

    if (halfMixes.nonEmpty) println(s"[HALF] Processing following ids")
    halfMixes foreach (x => println(s"  > ${x._1}"))

    halfMixes.map {
      case (mixId, currentRound, halfMixBoxId, masterSecret, withdrawStatus, withdrawAddress) =>
        try {
          val optEmissionBoxId = spentFeeEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId, roundCol === currentRound).firstAsT[String].headOption
          val tokenBoxId = spentTokenEmissionBoxTable.select(boxIdCol).where(mixIdCol === mixId).firstAsT[String].head
          processHalfMix(mixId, currentRound, halfMixBoxId, masterSecret, optEmissionBoxId, tokenBoxId, withdrawStatus, withdrawAddress)
        } catch {
          case a: Throwable =>
            println(s" [HALF:$mixId ($currentRound)] An error occurred. Stacktrace below")
            a.printStackTrace()
        }
    }
  }

  private implicit val insertReason = "HalfMixer.processHalfMix"

  /*
    Purpose of this method:

      it checks the halfMixId (created by us) has been spent by someone else in a mix transaction. If so, it obtains our full mix box and saves it to the fullMix table for further processing by FullMixer class

   */
  private def processHalfMix(mixId: String, currentRound: Int, halfMixBoxId: String, masterSecret: BigInt, optEmissionBoxId: Option[String], tokenBoxId: String, withdrawStatus: String, withdrawAddress: String) = ErgoMixCLIUtil.usingClient { implicit ctx =>
    println(s"[HALF:$mixId ($currentRound)] [half:$halfMixBoxId]")
    val currentTime = now
    val explorer = new BlockExplorer()

    val halfMixBoxConfirmations = explorer.getConfirmationsForBoxId(halfMixBoxId)
    if (halfMixBoxConfirmations >= minConfirmations) {
      println(s" [HALF: $mixId ($currentRound)] Sufficient confirmations ($halfMixBoxConfirmations) [half:$halfMixBoxId]")
      val spendingTx = ErgoMixCLIUtil.getSpendingTxId(halfMixBoxId)
      if (spendingTx.isEmpty && withdrawStatus.equals(WithdrawRequested.value)) {
        if (shouldWithdraw(mixId, halfMixBoxId)) {
          require(withdrawAddress.nonEmpty)
          val wallet = new Wallet(masterSecret)
          val secret = wallet.getSecret(currentRound).bigInteger
          val tx = AliceOrBob.spendBoxes(Array[String](halfMixBoxId), withdrawAddress, Array(secret), Configs.feeAmount, broadCast = true)
          val txBytes = tx.toJson(false).getBytes("utf-16")
          tables.insertWithdraw(mixId, tx.getId, currentTime, halfMixBoxId, txBytes)
          println(s" [Half: $mixId ($currentRound)] Withdraw txId: ${tx.getId}, is requested: ${withdrawStatus.equals(WithdrawRequested.value)}")
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
          val gX = ErgoMix.g.exp(x)
          boxes.map { box =>
            val fullMixBox = FullMixBox(box)
            require(fullMixBox.r6 == gX)
            if (fullMixBox.r5 == fullMixBox.r4.exp(x)) { // this is our box
              halfMixTable.update(isSpentCol <-- true).where(mixIdCol === mixId and roundCol === currentRound)
              tables.insertFullMix(mixId, currentRound, currentTime, halfMixBoxId, fullMixBox.id)
              println(s" [HALF:$mixId ($currentRound)] -> FULL [half:$halfMixBoxId, full:${fullMixBox.id}]")
            }
          }
        }
      }
    } else {
      println(s" [HALF:$mixId ($currentRound)] Insufficient confirmations ($halfMixBoxConfirmations) [half:$halfMixBoxId]")
      if (halfMixBoxConfirmations == 0) { // 0 confirmations for halfMixBoxId
        // There are three possibilities
        //  1. Transaction is good, waiting for confirmations.. do nothing
        //  2. Emission box input of the transaction has been spent elsewhere, and this transaction is invalid
        //  3. This is a remix and the inputs of the transaction don't exist anymore due to a fork (low chance, considered below)
        //  4. This is an entry and the inputs of the transaction don't exist anymore due to a fork (low chance, considered below)

        // println(s"   [HalfMix $mixId] Zero conf. isAlice: halfMixBoxId: $halfMixBoxId, currentRound: $currentRound")

        // here we check if transaction is missing from mempool and also not mined. if so, we rebroadcast it!
        val prevTx = mixTransactionsTable.selectStar.where(boxIdCol === halfMixBoxId).as(MixTransaction(_)).head
//        if (!ErgoMixCLIUtil.isTxInPool(prevTx.txId)) {
        println(s"  rebroadcasting tx ${prevTx.txId}")
        ctx.sendTransaction(ctx.signedTxFromJson(prevTx.toString))
//        }

        optEmissionBoxId match {
          case Some(emissionBoxId) =>
            // we are here, thus; there was a fee emission box used and so this is a remix transaction
            if (isDoubleSpent(emissionBoxId, halfMixBoxId)) {
              // println(s"    [HalfMix $mixId] Zero conf. halfMixBoxId: $halfMixBoxId, currentRound: $currentRound while emissionBoxId $emissionBoxId spent")
              // the emissionBox used in the fullMix has been spent, while the fullMixBox generated has zero confirmations.
              try {
                undoMixStep(mixId, currentRound, halfMixTable)
                println(s" [HALF:$mixId ($currentRound)] <-- (undo) [half:$halfMixBoxId not spent while fee:$emissionBoxId spent]")
              } catch {
                case a: Throwable =>
                  a.printStackTrace()
              }
            } else {
              // if emission box is ok, lets now check if the input does not exist, if so then this is a fork
              // the input is a full mix box (since this is a remix)
              fullMixTable.select(fullMixBoxIdCol).where(mixIdCol === mixId, roundCol === (currentRound - 1)).firstAsT[String].headOption.map { fullMixBoxId =>
                explorer.doesBoxExist(fullMixBoxId) match {
                  case Some(true) =>
                  case Some(false) =>
                    println(s" [HALF:$mixId ($currentRound)] [ERROR] Rescanning [full:$fullMixBoxId disappeared]")
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
                undoMixStep(mixId, currentRound, halfMixTable)
                println(s" [HALF:$mixId ($currentRound)] <-- (undo) [half:$halfMixBoxId not spent while token:$tokenBoxId spent]")
              } catch {
                case a: Throwable =>
                  a.printStackTrace()
              }
            } else {
              // token emission box is ok
              // Need to check for fork by checking if any of the deposits have suddenly disappeared
              spentDepositsTable.select(boxIdCol).where(purposeCol === mixId).firstAsT[String].find { depositBoxId =>
                explorer.doesBoxExist(depositBoxId) == Some(false) // deposit has disappeared, so need to rescan
              }.map { depositBoxId =>
                println(s" [HALF:$mixId ($currentRound)] [ERROR] Rescanning [deposit:$depositBoxId disappeared]")
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