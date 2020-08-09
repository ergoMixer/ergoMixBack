package mixer

import cli.{Alice, AliceOrBob, Bob, ErgoMixCLIUtil}
import db.ScalaDB._
import mixer.Columns._
import mixer.ErgoMixerUtil._
import mixer.Models.MixStatus.{Queued, Running}
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import mixer.Models.{Deposit, MixRequest}
import mixer.Util.now
import app.{Configs, ErgoMix, TokenErgoMix}

import scala.jdk.CollectionConverters._

class NewMixer(tables: Tables) {

  import tables._

  def getDeposits(address: String): List[Deposit] = unspentDepositsTable.selectStar.where(addressCol === address).as(Deposit(_))

  def processNewMixQueue(): Unit = {
    val reqs = mixRequestsTable.select(
      mixReqCols :+ masterSecretCol: _*
    ).where(
      mixStatusCol === Queued.value,
      depositCompletedCol === true
    ).as(arr =>
      (MixRequest(arr), arr.last.asInstanceOf[BigDecimal].toBigInt) // Mix details along with master secret
    )

    if (reqs.nonEmpty) println(s"[NEW] Processing following ids")

    reqs.foreach {
      case (mr, _) => println(s"  > ${mr.id} depositAddress: ${mr.depositAddress}")
    }

    reqs.foreach {
      case (mixRequest, masterSecret) =>
        try {
          initiateMix(mixRequest, masterSecret)
        } catch {
          case a: Throwable =>
            println(s" [NEW:${mixRequest.id}] An error occurred. Stacktrace below")
            a.printStackTrace()
        }
    }
  }

  private implicit val insertReason = "NewMixer.initiateMix"

  private def initiateMix(mixRequest: MixRequest, masterSecret: BigInt): Unit = ErgoMixCLIUtil.usingClient { implicit ctx =>
    val id = mixRequest.id
    val depositAddress = mixRequest.depositAddress
    val depositsToUse = getDeposits(depositAddress)
    val boxIds = depositsToUse.map(_.boxId)
    if (mixRequest.withdrawStatus.equals(WithdrawRequested.value)) { // withdrawing
      if (shouldWithdraw(mixRequest.id, boxIds.mkString(","))) {
        require(mixRequest.withdrawAddress.nonEmpty)
        val wallet = new Wallet(masterSecret)
        val secret = wallet.getSecret(-1).bigInteger
        val tx = AliceOrBob.spendBoxes(boxIds.toArray, mixRequest.withdrawAddress, Array(secret), Configs.feeAmount, broadCast = true)
        val txBytes = tx.toJson(false).getBytes("utf-16")
        tables.insertWithdraw(id, tx.getId, now, boxIds.mkString(","), txBytes)
        println(s" [Deposit: $id] Withdraw txId: ${tx.getId}, is requested: ${mixRequest.withdrawStatus.equals(WithdrawRequested.value)}")
      }
      return
    }

    val avbl = depositsToUse.map(_.amount).sum
    val poolAmount = mixRequest.amount
    val numToken = mixRequest.numToken
    val needed = mixRequest.neededAmount
    val optTokenBoxId = getRandomValidBoxId(ErgoMixCLIUtil.getTokenEmissionBoxes(numToken, considerPool = true)
      .filterNot(box => spentTokenEmissionBoxTable.exists(boxIdCol === box.getId.toString)).map(_.getId.toString))

    if (avbl < needed) { // should not happen because we are only considering completed deposits.
      throw new Exception(s"Insufficient funds. Needed $needed. Available $avbl")

    } else if (optTokenBoxId.isEmpty) {
      println("  No token emission box to get token from!")

    } else {
      // now we have enough balance, lets proceed to the first round of the queue ...
      // always try to initiate as Bob first, and if it fails, do as Alice
      val wallet = new Wallet(masterSecret) // initialize wallet
      val secret = wallet.getSecret(0) // secret for the entry round

      val dLogSecret = wallet.getSecret(-1).toString()
      val inputBoxIds = depositsToUse.map(_.boxId).toArray

      val currentTime = now
      val optHalfMixBoxId = getRandomValidBoxId(ErgoMixCLIUtil.getHalfMixBoxes(poolAmount, considerPool = true)
        .filterNot(box => fullMixTable.exists(halfMixBoxIdCol === box.getId.toString))
        .filter(box => box.getRegisters.size() > 0
          && box.getTokens.asScala.map(_.getId.toString).toList.contains(TokenErgoMix.tokenId)).map(_.getId.toString))

      val (txId, isAlice) = if (optHalfMixBoxId.nonEmpty) {
        // half-mix box exists... behave as Bob
        val halfMixBoxId = optHalfMixBoxId.get
        val (fullMixTx, bit) = Bob.spendHalfMixBox(secret, halfMixBoxId, inputBoxIds :+ optTokenBoxId.get, ErgoMix.feeAmount, depositAddress, Array(dLogSecret), broadCast = true, numToken)
        val (left, right) = fullMixTx.getFullMixBoxes
        val bobFullMixBox = if (bit) right else left
        tables.insertFullMix(mixRequest.id, round = 0, time = currentTime, halfMixBoxId, bobFullMixBox.id)
        tables.insertTx(bobFullMixBox.id, fullMixTx.tx)
        println(s" [NEW:$id] --> Bob [halfMixBoxId:$halfMixBoxId, txId:${fullMixTx.tx.getId}]")
        (fullMixTx.tx.getId, false) // is not Alice
      } else {
        // half-mix box does not exist... behave as Alice
        // get a random token emission box

        val tx = Alice.createHalfMixBox(secret, inputBoxIds :+ optTokenBoxId.get, ErgoMix.feeAmount,
          depositAddress, Array(dLogSecret), broadCast = true, poolAmount, numToken)
        tables.insertHalfMix(mixRequest.id, round = 0, time = currentTime, tx.getHalfMixBox.id, isSpent = false)
        tables.insertTx(tx.getHalfMixBox.id, tx.tx)
        println(s" [NEW:$id] --> Alice [txId:${tx.tx.getId}]")
        (tx.tx.getId, true) // is Alice
      }

      depositsToUse.map { d =>
        tables.insertSpentDeposit(d.address, d.boxId, d.amount, d.createdTime, txId, currentTime, id)
        unspentDepositsTable.deleteWhere(boxIdCol === d.boxId)
      }

      mixRequestsTable.update(mixStatusCol <-- Running.value).where(mixIdCol === mixRequest.id)
      spentTokenEmissionBoxTable.insert(mixRequest.id, optTokenBoxId.get)
      mixStateTable.insert(mixRequest.id, 0, isAlice)
      tables.insertMixStateHistory(mixRequest.id, round = 0, isAlice = isAlice, time = currentTime)

    }
  }

}
