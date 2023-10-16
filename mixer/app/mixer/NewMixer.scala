package mixer

import javax.inject.Inject

import config.MainConfigs
import dao.mixing._
import dao.DAOUtils
import helpers.ErgoMixerUtils
import mixinterface.{AliceOrBob, TokenErgoMix}
import models.Box.OutBox
import models.Models.{Deposit, FullMix, HalfMix, MixHistory, MixState, SpentDeposit}
import models.Request.MixRequest
import models.Status.MixStatus.Running
import models.Status.MixWithdrawStatus.WithdrawRequested
import models.Transaction.{MixTransaction, WithdrawTx}
import network.NetworkUtils
import org.ergoplatform.appkit.SignedTransaction
import play.api.Logger
import wallet.Wallet
import wallet.WalletHelper.now

class NewMixer @Inject() (
  aliceOrBob: AliceOrBob,
  ergoMixerUtils: ErgoMixerUtils,
  networkUtils: NetworkUtils,
  daoUtils: DAOUtils,
  unspentDepositsDAO: UnspentDepositsDAO,
  spentDepositsDAO: SpentDepositsDAO,
  mixingRequestsDAO: MixingRequestsDAO,
  mixStateDAO: MixStateDAO,
  withdrawDAO: WithdrawDAO,
  tokenEmissionDAO: TokenEmissionDAO,
  halfMixDAO: HalfMixDAO,
  fullMixDAO: FullMixDAO,
  mixStateHistoryDAO: MixStateHistoryDAO,
  mixTransactionsDAO: MixTransactionsDAO
) {
  private val logger: Logger = Logger(this.getClass)

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
  def getDeposits(address: String): Seq[Deposit] = daoUtils.awaitResult(unspentDepositsDAO.selectByAddress(address))

  /**
   * processes new mixes one by one
   */
  def processNewMixQueue(): Unit = {
    val reqs = daoUtils.awaitResult(mixingRequestsDAO.selectAllQueued)

    if (reqs.nonEmpty) logger.info(s"[NEW] Processing following ids")

    reqs.foreach(req => logger.info(s"  > ${req.id} depositAddress: ${req.depositAddress}"))

    halfs = null
    reqs.foreach { req =>
      try
        initiateMix(req.toMixRequest, req.masterKey)
      catch {
        case a: Throwable =>
          logger.error(s" [NEW: ${req.id}] An error occurred. Stacktrace below")
          logger.error(getStackTraceStr(a))
      }
    }
  }

  implicit private val insertReason: String = "NewMixer.initiateMix"

  /**
   * starts mixing (as bob if possible or as alice)
   * @param mixRequest mix request
   * @param masterSecret master secret
   */
  private def initiateMix(mixRequest: MixRequest, masterSecret: BigInt): Unit = networkUtils.usingClient {
    implicit ctx =>
      val id             = mixRequest.id
      val depositAddress = mixRequest.depositAddress
      val depositsToUse  = getDeposits(depositAddress)
      val boxIds         = depositsToUse.map(_.boxId)
      if (mixRequest.withdrawStatus.equals(WithdrawRequested.value)) { // withdrawing
        if (withdrawDAO.shouldWithdraw(id, boxIds.mkString(","))) {
          require(mixRequest.withdrawAddress.nonEmpty)
          val wallet = new Wallet(masterSecret)
          val secret = wallet.getSecret(-1).bigInteger
          assert(boxIds.size == 1)
          val tx = aliceOrBob.spendBox(
            boxIds.head,
            Option.empty,
            mixRequest.withdrawAddress,
            Array(secret),
            MainConfigs.defaultHalfFee,
            broadCast = true
          )
          val txBytes      = tx.toJson(false).getBytes("utf-16")
          val new_withdraw = WithdrawTx(id, tx.getId, now, boxIds.head, txBytes)
          withdrawDAO.updateById(new_withdraw, WithdrawRequested.value)
          try {
            ctx.sendTransaction(tx)
            logger.info(
              s" [Deposit: $id] Withdraw txId: ${tx.getId}, is requested: ${mixRequest.withdrawStatus.equals(WithdrawRequested.value)}"
            )
          } catch {
            case e: Throwable =>
              logger.error(
                s" [Deposit: $id] something unexpected has happened! tx got refused by the node: ${tx.getId}!"
              )
              logger.debug(s"  Exception: ${e.getMessage}")
          }
        }
        return
      }

      def updateTablesMixes(
        isAlice: Boolean,
        mixRequestId: String,
        time: Long,
        tx: SignedTransaction,
        depositsToUse: Seq[Deposit],
        optTokenBoxId: Option[String]
      ): Unit = {
        depositsToUse.map { d =>
          val spent_deposit =
            SpentDeposit(d.address, d.boxId, d.amount, d.createdTime, d.tokenAmount, tx.getId, time, mixRequestId)
          spentDepositsDAO.insertDeposit(spent_deposit)
          unspentDepositsDAO.delete(d.boxId)
        }

        mixingRequestsDAO.updateMixStatus(mixRequest.id, Running)
        tokenEmissionDAO.insert(mixRequest.id, optTokenBoxId.get)
        val state = MixState(mixRequestId, 0, isAlice)
        mixStateDAO.insert(state)
        val new_history = MixHistory(mixRequestId, round = 0, isAlice = isAlice, time = time)
        mixStateHistoryDAO.insertMixHistory(new_history)
      }

      val avbl        = depositsToUse.map(_.amount).sum
      val avblToken   = depositsToUse.map(_.tokenAmount).sum
      val poolAmount  = mixRequest.amount
      val numFeeToken = mixRequest.numToken
      val neededErg   = mixRequest.neededAmount
      val neededToken = mixRequest.neededTokenAmount
      // get a random token emission box
      val optTokenBoxId = getRandomValidBoxId(
        networkUtils
          .getTokenEmissionBoxes(numFeeToken, considerPool = true)
          .filterNot(box => daoUtils.awaitResult(tokenEmissionDAO.existsByBoxId(box.id)))
          .map(_.id)
      )

      if (avbl < neededErg || avblToken < neededToken) { // should not happen because we are only considering completed deposits.
        throw new Exception(s"Insufficient funds. Needed $neededErg. Available $avbl")

      } else if (optTokenBoxId.isEmpty) {
        logger.warn("  No token emission box to get token from!")
      } else {
        // now we have enough balance, lets proceed to the first round of the queue ...
        // always try to initiate as Bob first, and if it fails, do as Alice
        val wallet = new Wallet(masterSecret) // initialize wallet
        val secret = wallet.getSecret(0) // secret for the entry round

        val dLogSecret  = wallet.getSecret(-1).toString()
        val inputBoxIds = depositsToUse.map(_.boxId).toArray

        val currentTime = now
        var tokenSize   = 1
        if (mixRequest.tokenId.nonEmpty) tokenSize = 2
        val optHalfMixBoxId = getRandomValidBoxId(
          getHalfBoxes
            .filterNot(box => daoUtils.awaitResult(fullMixDAO.existsByBoxId(box.id)))
            .filter(box =>
              box.amount == poolAmount && box.getToken(mixRequest.tokenId) == mixRequest.mixingTokenAmount
                && box.tokens.size == tokenSize && box.registers.nonEmpty
                && box.getToken(TokenErgoMix.tokenId) > 0
            )
            .map(_.id)
        )

        if (optHalfMixBoxId.nonEmpty) {
          // half-mix box exists... behave as Bob
          val halfMixBoxId = optHalfMixBoxId.get
          val (fullMixTx, bit) = aliceOrBob.spendHalfMixBox(
            secret,
            halfMixBoxId,
            inputBoxIds :+ optTokenBoxId.get,
            MainConfigs.startFee,
            depositAddress,
            Array(dLogSecret),
            broadCast = false,
            numFeeToken
          )
          val (left, right) = fullMixTx.getFullMixBoxes
          val bobFullMixBox = if (bit) right else left
          val new_fullMix   = FullMix(id, round = 0, currentTime, halfMixBoxId, bobFullMixBox.id)
          fullMixDAO.insertFullMix(new_fullMix)
          val new_mixTransaction =
            MixTransaction(bobFullMixBox.id, fullMixTx.tx.getId, fullMixTx.tx.toJson(false).getBytes("utf-16"))
          mixTransactionsDAO.updateById(new_mixTransaction)
          updateTablesMixes(
            isAlice = false,
            id,
            currentTime,
            fullMixTx.tx,
            depositsToUse,
            optTokenBoxId
          ) // is not Alice
          logger.info(
            s" [NEW: $id] --> Bob [halfMixBoxId:$halfMixBoxId, txId:${fullMixTx.tx.getId}] - before sendTransaction"
          )
          try {
            ctx.sendTransaction(fullMixTx.tx)
            logger.info(s"[txId: ${fullMixTx.tx.getId}] was broadcast.")
          } catch {
            case e: Throwable =>
              logger.error(s"  something unexpected has happened! tx got refused by the node!")
              logger.debug(s"  Exception: ${e.getMessage}")
          }
        } else {
          // half-mix box does not exist... behave as Alice

          // do nothing
          if (MainConfigs.mixOnlyAsBob) {
            logger.info(
              s" [NEW:$id] --> Ignored because mixOnlyAsBob is set to true and no half-box is available currently!"
            )
            return
          }

          val tx = aliceOrBob.createHalfMixBox(
            secret,
            inputBoxIds :+ optTokenBoxId.get,
            MainConfigs.startFee,
            depositAddress,
            Array(dLogSecret),
            broadCast = false,
            poolAmount,
            numFeeToken,
            mixRequest.tokenId,
            mixRequest.mixingTokenAmount
          )
          val new_halfMix = HalfMix(id, round = 0, currentTime, tx.getHalfMixBox.id, isSpent = false)
          halfMixDAO.insertHalfMix(new_halfMix)
          val new_mixTransaction =
            MixTransaction(tx.getHalfMixBox.id, tx.tx.getId, tx.tx.toJson(false).getBytes("utf-16"))
          mixTransactionsDAO.updateById(new_mixTransaction)
          updateTablesMixes(isAlice = true, id, currentTime, tx.tx, depositsToUse, optTokenBoxId) // is Alice
          logger.info(s" [NEW:$id] --> Alice [txId: ${tx.tx.getId}] - before sendTransaction")
          try {
            ctx.sendTransaction(tx.tx)
            logger.info(s"[txId: ${tx.tx.getId}] was broadcast")
          } catch {
            case e: Throwable =>
              logger.error(s"  something unexpected has happened! tx got refused by the node!")
              logger.debug(s"  Exception: ${e.getMessage}")
          }
        }
      }
  }

}
