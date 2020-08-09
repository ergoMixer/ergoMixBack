package mixer

import app.Configs
import app.ergomix.EndBox
import cli.{AliceOrBob, ErgoMixCLIUtil}
import db.ScalaDB._
import db.core.DataStructures.anyToAny
import mixer.Columns._
import mixer.ErgoMixerUtils._
import mixer.Models.{DistributeTx, MixCovertRequest, OutBox}
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.appkit.{Address, ErgoToken}
import play.api.Logger
import services.ErgoMixingSystem

class CovertMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)

  import tables._

  def processCovert(): Unit = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      val explorer = new BlockExplorer
      mixCovertTable.selectStar.as(arr => MixCovertRequest(arr)).foreach(req => {
        try {
          logger.info(s"[covert: ${req.id}] processing deposits...")
          val minNeeded = req.getMinNeeded
          // zero chainOrder means tx is confirmed! no need to consider it here.
          val spent = distributeTxsTable.select(inputsCol).where(mixGroupIdCol === req.id, chainOrderCol > 0).as(_.toIterator.next().as[String]).mkString(",")
          val allBoxes = explorer.getUnspentBoxes(req.depositAddress).filterNot(box => spent.contains(box.id))
          var confirmedErgDeposits = 0L
          var confirmedTokenDeposits = 0L
          val confirmedBoxes = allBoxes.map(box => {
            val conf = ErgoMixCLIUtil.getConfirmationsForBoxId(box.id)
            if (conf >= minConfirmations) {
              confirmedErgDeposits += box.amount
              confirmedTokenDeposits += box.getToken(req.tokenId)
              box
            } else null
          }).filter(_ != null)
          if (confirmedErgDeposits > 0 || confirmedTokenDeposits > 0) {
            mixCovertTable.update(depositCol <-- confirmedErgDeposits, tokenDepositCol <-- confirmedTokenDeposits).where(mixGroupIdCol === req.id)
            if (req.tokenId.isEmpty) logger.info(s"  processed confirmed deposits $confirmedErgDeposits")
            else logger.info(s"  processed confirmed deposits, erg: $confirmedErgDeposits, ${req.tokenId}: $confirmedTokenDeposits")
          }

          processTx(req)
          if (confirmedBoxes.size > Configs.maxIns) {
            logger.info(s"  many unspent boxes (${confirmedBoxes.size}), merging them....")
            mergeInputs(req, confirmedBoxes)

          } else if (confirmedErgDeposits >= minNeeded._1 && confirmedTokenDeposits >= minNeeded._2) {
            logger.info("  enough deposits to enter mixing....")
            enterMixing(req, confirmedBoxes)
          }
        } catch {
          case a: Throwable =>
            logger.error(s" [covert: ${req.id}] An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
      })
    }
  }

  def enterMixing(req: MixCovertRequest, inputs: Seq[OutBox]): Unit = ErgoMixCLIUtil.usingClient { implicit ctx =>
    val wallet = new Wallet(req.masterKey)
    val secret = wallet.getSecret(-1).bigInteger

    val ergAmount = inputs.map(_.amount).sum
    val tokenAmount = inputs.map(_.getToken(req.tokenId)).sum
    val needed = req.getMixingNeed
    var numBox = ergAmount / needed._1
    // if there is not enough balance for distribution fee!
    var neededFee = ((numBox + Configs.maxOuts - 1) / Configs.maxOuts) * Configs.distributeFee
    if (ergAmount - numBox * needed._1 < neededFee)
      numBox -= 1
    if (req.tokenId.nonEmpty) numBox = Math.min(numBox, tokenAmount / needed._2)
    neededFee = ((numBox + Configs.maxOuts - 1) / Configs.maxOuts) * Configs.distributeFee
    // if we are spending all ergs, we have to make sure this doesn't result in missing tokens!
    if (ergAmount - numBox * needed._1 - neededFee == 0 && tokenAmount - numBox * needed._2 > 0)
      numBox -= 1
    if (numBox == 0) {
      logger.warn("  we wont enter mixing, potentially because it would result in missing tokens, needs some more erg!")
      return
    }

    val requests = (1L to numBox).map(_ => {
      // TODO fix withdraw address
      ErgoMixingSystem.ergoMixer.newMixRequest(req.numRounds, "", req.numRounds, req.ergRing, needed._1, req.tokenRing, needed._2, req.tokenId, req.id)
    })

    var reqEndBoxes = requests.map(cur => {
      var token: Seq[ErgoToken] = Seq()
      if (!req.tokenId.isEmpty) token = Seq(new ErgoToken(req.tokenId, needed._2))
      EndBox(Address.create(cur).getErgoAddress.script, Seq(), needed._1, token)
    })
    neededFee = ((numBox + Configs.maxOuts - 1) / Configs.maxOuts) * Configs.distributeFee
    val excessErgs = ergAmount - neededFee - numBox * needed._1
    val excessTokens = tokenAmount - numBox * needed._2
    assert((excessErgs == 0 && excessTokens == 0) || excessErgs > 0) // no tokens must get missed!
    logger.info(s"  we will create $numBox boxes to enter mixing...")

    val transactions = AliceOrBob.distribute(inputs.map(_.id).toArray, reqEndBoxes.toArray, Array(secret), Configs.distributeFee, req.depositAddress, Configs.maxOuts, req.tokenId)
    for (i <- transactions.indices) {
      val tx = transactions(i)
      val inputs = tx.getInputBoxes.map(ScalaBridge.isoErgoTransactionInput.from(_).getBoxId).mkString(",")
      distributeTxsTable.insert(req.id, tx.getId, i + 1, Util.now, tx.toJson(false).getBytes("utf-16"), inputs)
      val sendRes = ctx.sendTransaction(tx)
      if (sendRes == null) logger.error(s"  transaction got refused by the node! maybe it doesn't support chained transactions, waiting... consider updating your node for a faster mixing experience.")
    }
  }

  def processTx(req: MixCovertRequest): Unit = ErgoMixCLIUtil.usingClient { implicit ctx =>
    val explorer = new BlockExplorer()
    distributeTxsTable.selectStar.where(mixGroupIdCol === req.id, chainOrderCol > 0).as(DistributeTx(_))
      .sortBy(_.order)
      .foreach(tx => {
        val confNum = explorer.getTxNumConfirmations(tx.txId)
        if (confNum == -1) { // not mined yet, broadcast tx again!
          val res = ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
          logger.info(s"  broadcasting tx ${tx.txId}, response: $res...")

        } else if (confNum >= Configs.numConfirmation) { // confirmed enough
          logger.info(s"  tx ${tx.txId} is confirmed.")
          distributeTxsTable.update(chainOrderCol <-- 0).where(txIdCol === tx.txId)
        } else {
          logger.info(s"  tx ${tx.txId} is mined, waiting for enough confirmations...")
        }
      })
  }

  /**
   * will merge input boxes. it just considers fixed size inputs, e.g. will merge every 10 inputs to one
   *
   * @param req    covert request
   * @param inputs inputs to be merged
   */
  def mergeInputs(req: MixCovertRequest, inputs: Seq[OutBox]): Unit = {
    val wallet = new Wallet(req.masterKey)
    val secret = wallet.getSecret(-1).bigInteger
    (0 until inputs.size / Configs.maxIns).foreach(i => {
      val start = i * Configs.maxIns
      val curInputs = inputs.slice(start, start + Configs.maxIns)
      val ergSum = curInputs.map(_.amount).sum
      val tokenSum = curInputs.map(_.getToken(req.tokenId)).sum
      val out = {
        if (tokenSum == 0) EndBox(Address.create(req.depositAddress).getErgoAddress.script, Seq(), ergSum - Configs.distributeFee, Seq())
        else EndBox(Address.create(req.depositAddress).getErgoAddress.script, Seq(), ergSum - Configs.distributeFee, Seq(new ErgoToken(req.tokenId, tokenSum)))
      }
      val tx = AliceOrBob.mergeBoxes(curInputs.map(_.id).toArray, out, secret, Configs.distributeFee, req.depositAddress)
      val inputIds = tx.getInputBoxes.map(ScalaBridge.isoErgoTransactionInput.from(_).getBoxId).mkString(",")
      distributeTxsTable.insert(req.id, tx.getId, 1, Util.now, tx.toJson(false).getBytes("utf-16"), inputIds)
      logger.info(s"  merging inputs with tx ${tx.getId}...")
    })
  }
}
