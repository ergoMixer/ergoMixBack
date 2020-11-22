package mixer

import app.Configs
import mixinterface.{AliceOrBob, TokenErgoMix}
import db.Columns._
import db.ScalaDB._
import db.Tables
import helpers.ErgoMixerUtils
import wallet.WalletHelper.now
import javax.inject.Inject
import models.Models.MixStatus.{Queued, Running}
import models.Models.MixWithdrawStatus.WithdrawRequested
import models.Models.{Deposit, MixRequest, OutBox}
import network.NetworkUtils
import play.api.Logger
import wallet.Wallet

class NewMixer @Inject()(tables: Tables, aliceOrBob: AliceOrBob, ergoMixerUtils: ErgoMixerUtils, networkUtils: NetworkUtils) {
  private val logger: Logger = Logger(this.getClass)

  import tables._
  import ergoMixerUtils._

  var halfs: Seq[OutBox] = _

  /**
   * gets half-boxes to be used by all new mixes
   * @return half-boxes
   */
  def getHalfBoxes: Seq[OutBox] = {
    if (halfs == null)
      halfs = networkUtils.getHalfMixBoxes(considerPool = true)
    halfs
  }

  /**
   * @param address deposit address of a mix
   * @return returns deposits of this address
   */
  def getDeposits(address: String): List[Deposit] = unspentDepositsTable.selectStar.where(addressCol === address).as(Deposit(_))

  /**
   * processes new mixes one by one
   */
  def processNewMixQueue(): Unit = {
    val reqs = mixRequestsTable.select(
      mixReqCols :+ masterSecretCol: _*
    ).where(
      mixStatusCol === Queued.value,
      depositCompletedCol === true
    ).as(arr =>
      (MixRequest(arr), arr.last.asInstanceOf[BigDecimal].toBigInt) // Mix details along with master secret
    )

    if (reqs.nonEmpty) logger.info(s"[NEW] Processing following ids")

    reqs.foreach {
      case (mr, _) => logger.info(s"  > ${mr.id} depositAddress: ${mr.depositAddress}")
    }

    halfs = null
    reqs.foreach {
      case (mixRequest, masterSecret) =>
        try {
          initiateMix(mixRequest, masterSecret)
        } catch {
          case a: Throwable =>
            logger.error(s" [NEW: ${mixRequest.id}] An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
    }
  }

  private implicit val insertReason = "NewMixer.initiateMix"

  /**
   * starts mixing (as bob if possible or as alice)
   * @param mixRequest mix request
   * @param masterSecret master secret
   */
  private def initiateMix(mixRequest: MixRequest, masterSecret: BigInt): Unit = networkUtils.usingClient { implicit ctx =>
    val id = mixRequest.id
    val depositAddress = mixRequest.depositAddress
    val depositsToUse = getDeposits(depositAddress)
    val boxIds = depositsToUse.map(_.boxId)
    if (mixRequest.withdrawStatus.equals(WithdrawRequested.value)) { // withdrawing
      if (shouldWithdraw(mixRequest.id, boxIds.mkString(","))) {
        require(mixRequest.withdrawAddress.nonEmpty)
        val wallet = new Wallet(masterSecret)
        val secret = wallet.getSecret(-1).bigInteger
        assert(boxIds.size == 1)
        val tx = aliceOrBob.spendBox(boxIds.head, Option.empty, mixRequest.withdrawAddress, Array(secret), Configs.defaultHalfFee, broadCast = true)
        val txBytes = tx.toJson(false).getBytes("utf-16")
        tables.insertWithdraw(id, tx.getId, now, boxIds.head, txBytes)
        val sendRes = ctx.sendTransaction(tx)
        if (sendRes == null) logger.error(s" [Deposit: $id] something unexpected has happened! tx got refused by the node: ${tx.getId}!")
        logger.info(s" [Deposit: $id] Withdraw txId: ${tx.getId}, is requested: ${mixRequest.withdrawStatus.equals(WithdrawRequested.value)}")
      }
      return
    }

    val avbl = depositsToUse.map(_.amount).sum
    val avblToken = depositsToUse.map(_.tokenAmount).sum
    val poolAmount = mixRequest.amount
    val numFeeToken = mixRequest.numToken
    val neededErg = mixRequest.neededAmount
    val neededToken = mixRequest.neededTokenAmount
    val optTokenBoxId = getRandomValidBoxId(networkUtils.getTokenEmissionBoxes(numFeeToken, considerPool = true)
      .filterNot(box => spentTokenEmissionBoxTable.exists(boxIdCol === box.id)).map(_.id.toString))

    if (avbl < neededErg || avblToken < neededToken) { // should not happen because we are only considering completed deposits.
      throw new Exception(s"Insufficient funds. Needed $neededErg. Available $avbl")

    } else if (optTokenBoxId.isEmpty) {
      logger.warn("  No token emission box to get token from!")

    } else {
      // now we have enough balance, lets proceed to the first round of the queue ...
      // always try to initiate as Bob first, and if it fails, do as Alice
      val wallet = new Wallet(masterSecret) // initialize wallet
      val secret = wallet.getSecret(0) // secret for the entry round

      val dLogSecret = wallet.getSecret(-1).toString()
      val inputBoxIds = depositsToUse.map(_.boxId).toArray

      val currentTime = now
      var tokenSize = 1
      if (mixRequest.tokenId.nonEmpty) tokenSize = 2
      val optHalfMixBoxId = getRandomValidBoxId(getHalfBoxes
        .filterNot(box => fullMixTable.exists(halfMixBoxIdCol === box.id))
        .filter(box => box.amount == poolAmount && box.getToken(mixRequest.tokenId) == mixRequest.mixingTokenAmount
          && box.tokens.size == tokenSize && box.registers.nonEmpty
          && box.getToken(TokenErgoMix.tokenId) > 0).map(_.id)
      )

      val (txId, isAlice) = if (optHalfMixBoxId.nonEmpty) {
        // half-mix box exists... behave as Bob
        val halfMixBoxId = optHalfMixBoxId.get
        val (fullMixTx, bit) = aliceOrBob.spendHalfMixBox(secret, halfMixBoxId, inputBoxIds :+ optTokenBoxId.get, Configs.startFee, depositAddress, Array(dLogSecret), broadCast = false, numFeeToken)
        val (left, right) = fullMixTx.getFullMixBoxes
        val bobFullMixBox = if (bit) right else left
        tables.insertFullMix(mixRequest.id, round = 0, time = currentTime, halfMixBoxId, bobFullMixBox.id)
        tables.insertTx(bobFullMixBox.id, fullMixTx.tx)
        logger.info(s" [NEW: $id] --> Bob [halfMixBoxId:$halfMixBoxId, txId:${fullMixTx.tx.getId}]")
        val sendRes = ctx.sendTransaction(fullMixTx.tx)
        if (sendRes == null) logger.error(s"  something unexpected has happened! tx got refused by the node!")
        (fullMixTx.tx.getId, false) // is not Alice
      } else {
        // half-mix box does not exist... behave as Alice
        // get a random token emission box
        val tx = aliceOrBob.createHalfMixBox(secret, inputBoxIds :+ optTokenBoxId.get, Configs.startFee,
          depositAddress, Array(dLogSecret), broadCast = false, poolAmount, numFeeToken, mixRequest.tokenId, mixRequest.mixingTokenAmount)
        tables.insertHalfMix(mixRequest.id, round = 0, time = currentTime, tx.getHalfMixBox.id, isSpent = false)
        tables.insertTx(tx.getHalfMixBox.id, tx.tx)
        val sendRes = ctx.sendTransaction(tx.tx)
        if (sendRes == null) logger.info(s"  something unexpected has happened! tx got refused by the node!")
        logger.info(s" [NEW:$id] --> Alice [txId: ${tx.tx.getId}]")
        (tx.tx.getId, true) // is Alice
      }

      depositsToUse.map { d =>
        tables.insertSpentDeposit(d.address, d.boxId, d.amount, d.createdTime, d.tokenAmount, txId, currentTime, id)
        unspentDepositsTable.deleteWhere(boxIdCol === d.boxId)
      }

      mixRequestsTable.update(mixStatusCol <-- Running.value).where(mixIdCol === mixRequest.id)
      spentTokenEmissionBoxTable.insert(mixRequest.id, optTokenBoxId.get)
      mixStateTable.insert(mixRequest.id, 0, isAlice)
      tables.insertMixStateHistory(mixRequest.id, round = 0, isAlice = isAlice, time = currentTime)

    }
  }

}
