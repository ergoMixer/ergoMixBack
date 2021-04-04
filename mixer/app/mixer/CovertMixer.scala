package mixer

import app.Configs
import mixinterface.AliceOrBob
import db.Columns._
import db.ScalaDB._
import db.Tables
import db.core.DataStructures.anyToAny
import helpers.ErgoMixerUtils
import javax.inject.{Inject, Singleton}
import models.Models.{CovertAsset, DistributeTx, EndBox, MixCovertRequest, OutBox}
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.appkit.{Address, ErgoToken, InputBox}
import play.api.Logger
import wallet.{Wallet, WalletHelper}

import scala.collection.mutable
import scala.collection.JavaConverters._

@Singleton
class CovertMixer @Inject()(tables: Tables, ergoMixer: ErgoMixer, aliceOrBob: AliceOrBob,
                            ergoMixerUtils: ErgoMixerUtils, networkUtils: NetworkUtils, explorer: BlockExplorer) {
  private val logger: Logger = Logger(this.getClass)

  import tables._

  /**
   * processes covert addresses, i.e. handle deposits, merges input boxes, initiate mixing.
   */
  def processCovert(): Unit = {
    networkUtils.usingClient { implicit ctx =>
      mixCovertTable.selectStar.as(arr => MixCovertRequest(arr)).foreach(req => {
        try {
          logger.info(s"[covert: ${req.id}] processing deposits...")
          val assets = covertDefaultsTable.selectStar.where(mixGroupIdCol === req.id).as(CovertAsset(_))
          val supported = assets.filter(_.ring > 0)
          val unsupported = assets.filter(_.ring == 0)
          // zero chainOrder means tx is confirmed! no need to consider it here.
          val spent = distributeTxsTable.select(inputsCol).where(mixGroupIdCol === req.id, chainOrderCol > 0).as(_.toIterator.next().as[String]).mkString(",")
          val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
          val deposits = mutable.Map.empty[String, Long] // considers txs in mempool so avoids counting those boxes we already trying to spend
          val realDeposits = mutable.Map.empty[String, Long] // doesn't consider current txs in mempool
          supported.foreach(sup => deposits(sup.tokenId) = 0L)
          (supported ++ unsupported).foreach(sup => realDeposits(sup.tokenId) = 0L)
          val confirmedBoxes = allBoxes.map(box => { // all confirmed boxes
            val conf = networkUtils.getConfirmationsForBoxId(box.id)
            // we only consider enough confirmed boxes as deposit
            if (conf >= Configs.numConfirmation) {
              if (!spent.contains(box.id)) {
                // we can spend this box
                deposits("") = deposits.getOrElse("", 0L) + box.amount
                box.tokens.foreach(token => deposits(token.getId.toString) = deposits.getOrElse(token.getId.toString, 0L) + token.getValue)
              }
              realDeposits("") = realDeposits.getOrElse("", 0L) + box.amount
              box.tokens.foreach(token => realDeposits(token.getId.toString) = realDeposits.getOrElse(token.getId.toString, 0L) + token.getValue)
              box
            } else null
          }).filter(_ != null).filter(box => {
            !spent.contains(box.id)
          }).toList

          realDeposits.foreach(dep => {
            if ((supported ++ unsupported).exists(_.tokenId == dep._1)) {
              if ((supported ++ unsupported).filter(_.tokenId == dep._1).head.confirmedDeposit != dep._2) {
                covertDefaultsTable.update(depositCol <-- dep._2, lastActivityCol <-- WalletHelper.now).where(mixGroupIdCol === req.id, tokenIdCol === dep._1)
                var name = dep._1
                if (name.isEmpty) name = "erg"
                logger.info(s"  processed confirmed deposits, $name: ${dep._2}")
              }
            } else {
              logger.info(s"  processed confirmed unsupported deposits, ${dep._1}: ${dep._2}")
              ergoMixer.handleCovertSupport(req.id, dep._1, 0L)
              covertDefaultsTable.update(depositCol <-- dep._2, lastActivityCol <-- WalletHelper.now).where(mixGroupIdCol === req.id, tokenIdCol === dep._1)
            }
          })

          processTx(req)
          if (confirmedBoxes.size >= Configs.maxIns) {
            logger.info(s"  many unspent boxes (${confirmedBoxes.size}), merging them....")
            mergeInputs(req, confirmedBoxes)

          } else {
            enterMixing(req, confirmedBoxes, supported, deposits)
          }
        } catch {
          case a: Throwable =>
            logger.error(s" [covert: ${req.id}] An error occurred. Stacktrace below")
            logger.error(ergoMixerUtils.getStackTraceStr(a))
        }
      })
    }
  }

  /**
   * enters mixing for a specific covert address (possibly for multiple assets)
   *
   * @param req       covert request
   * @param inputs    inputs to be used for entering mix
   * @param supported supported assets of this covert address
   * @param deposit   current deposits of this address
   */
  def enterMixing(req: MixCovertRequest, inputs: Seq[OutBox], supported: Seq[CovertAsset], deposit: mutable.Map[String, Long]): Unit = networkUtils.usingClient { implicit ctx =>
    val addresses = covertAddressesTable.select(addressCol).where(mixGroupIdCol === req.id).as(res => res.toIterator.next().as[String])
    var endBoxes = Seq[EndBox]()
    supported.filter(_.tokenId.nonEmpty).foreach(toMix => { // try to mix anything but erg!
      var possible = true
      while (possible) {
        val mixingNeed = req.getMixingNeed(Configs.ergRing, toMix.ring)
        val ergNeeded = ((endBoxes.length + Configs.maxOuts) / Configs.maxOuts) * Configs.distributeFee + mixingNeed._1
        if (deposit("") >= ergNeeded && deposit(toMix.tokenId) >= mixingNeed._2) {
          val withdrawAddress = ergoMixerUtils.getRandom(addresses).getOrElse("")
          logger.info(s"  creating a box to mix ${toMix.tokenId} in ring: ${toMix.ring}, withdraw address: $withdrawAddress")
          val depAddr = ergoMixer.newMixRequest(withdrawAddress, req.numRounds, Configs.ergRing, mixingNeed._1, toMix.ring, mixingNeed._2, toMix.tokenId, req.id)
          endBoxes = endBoxes :+ EndBox(Address.create(depAddr).getErgoAddress.script, Seq(), mixingNeed._1, Seq(new ErgoToken(toMix.tokenId, mixingNeed._2)))
          // subtract what was needed from deposits!
          deposit("") -= mixingNeed._1
          deposit(toMix.tokenId) -= mixingNeed._2

        } else possible = false
      }
    })
    if (supported.exists(_.tokenId.isEmpty)) {
      val toMix = supported.filter(_.tokenId.isEmpty).head
      // we try to mix ergs here
      var possible = true
      while (possible) {
        val mixingNeed = req.getMixingNeed(toMix.ring, 0L)
        val ergNeeded = ((endBoxes.length + Configs.maxOuts) / Configs.maxOuts) * Configs.distributeFee + mixingNeed._1
        if (deposit("") >= ergNeeded) {
          val withdrawAddress = ergoMixerUtils.getRandom(addresses).getOrElse("")
          logger.info(s"  creating a box to mix erg in ring: ${toMix.ring}, withdraw address: $withdrawAddress")
          val depAddr = ergoMixer.newMixRequest(withdrawAddress, req.numRounds, toMix.ring, mixingNeed._1, 0L, mixingNeed._2, toMix.tokenId, req.id)
          endBoxes = endBoxes :+ EndBox(Address.create(depAddr).getErgoAddress.script, Seq(), mixingNeed._1, Nil)
          // subtract what was needed from deposits!
          deposit("") -= mixingNeed._1

        } else possible = false
      }
    }
    if (endBoxes.isEmpty) return
    // here we subtract distributing fee
    deposit("") -= ((endBoxes.length + Configs.maxOuts) / Configs.maxOuts) * Configs.distributeFee

    // here we check a rare but an important case! it is possible that the leftover ergs is 0
    // or less than the minimum amount a box can have (here we consider it 1e6 nano-ergs),
    // in this case if there are some leftover tokens, we will lose them!
    if ((deposit.count(dep => dep._1.nonEmpty && dep._2 >= 0) > 0 && deposit("") < Configs.minPossibleErgInBox) ||
      (deposit("") > 0 && deposit("") < Configs.minPossibleErgInBox)) {
      // If the above scenario is the case, we simply ignore mixing the last box in the list!
      logger.info(s"  we ignore mixing 1 box, reason is lack of leftover ergs; we may lose our leftover tokens!")
      deposit("") += endBoxes.last.value
      if (endBoxes.last.tokens.nonEmpty) {
        deposit(endBoxes.last.tokens.last.getId.toString) += endBoxes.last.tokens.last.getValue
      }
      val addr = new ErgoAddressEncoder(ctx.getNetworkType.networkPrefix).fromProposition(endBoxes.last.receiverBoxScript).toString
      mixRequestsTable.deleteWhere(withdrawAddressCol === addr)
      endBoxes = endBoxes.dropRight(1)
      if (endBoxes.isEmpty) {
        logger.info("  no other box to mix.")
        return
      }
    }

    val wallet = new Wallet(req.masterKey)
    val secret = wallet.getSecret(-1, req.isManualCovert).bigInteger

    logger.info(s"  we will create ${endBoxes.length} boxes to enter mixing...")

    val transactions = aliceOrBob.distribute(inputs.map(_.id).toArray, endBoxes.toArray, Array(secret), Configs.distributeFee, req.depositAddress, Configs.maxOuts)
    for (i <- transactions.indices) {
      val tx = transactions(i)
      val inputs = tx.getInputBoxes.map(ScalaBridge.isoErgoTransactionInput.from(_).getBoxId).mkString(",")
      distributeTxsTable.insert(req.id, tx.getId, i + 1, WalletHelper.now, tx.toJson(false).getBytes("utf-16"), inputs)
      val sendRes = ctx.sendTransaction(tx)
      if (sendRes == null) {
        logger.error(s"  transaction got refused by the node! maybe it doesn't support chained transactions, waiting... consider updating your node for a faster mixing experience.")
      }
    }
  }

  /**
   * process transactions of this covert request, i.e. broadcasts them if needed, mark them as done if confirmed...
   *
   * @param req covert request
   */
  def processTx(req: MixCovertRequest): Unit = networkUtils.usingClient { implicit ctx =>
    var outputs: Array[String] = Array()
    distributeTxsTable.selectStar.where(mixGroupIdCol === req.id, chainOrderCol > 0).as(DistributeTx(_))
      .sortBy(_.order)
      .foreach(tx => {
        val confNum = explorer.getTxNumConfirmations(tx.txId)
        if (confNum == -1) { // not mined yet, broadcast tx again!
          val signedTx = ctx.signedTxFromJson(tx.toString)
          val res = ctx.sendTransaction(signedTx)
          logger.info(s"  broadcasting tx ${tx.txId}, response: $res...")
          if (res == null) {
            var spendingTxId: String = ""
            val boxStatus = tx.inputs.split(",").forall(boxId => {
              explorer.getSpendingTxId(boxId) match {
                case Some(txId) =>
                  spendingTxId = txId
                case None =>
                  spendingTxId = ""
              }
              if ((!spendingTxId.isEmpty && spendingTxId != req.id) || outputs.contains(boxId)) {
                false
              }
              else true
            })
            if (boxStatus) {
              logger.error(s"  transaction got refused by the node! potential reason: node does not support chain transactions. waiting...")
            }
            else {
              outputs = outputs ++ signedTx.getOutputsToSpend.asScala.map(box => {
                box.getId.toString
              })
              distributeTxsTable.update(chainOrderCol <-- 0).where(txIdCol === tx.txId)
              logger.info(s"  tx ${tx.txId} has a box spent or has a wrong box")
            }
          }
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
    val secret = wallet.getSecret(-1, req.isManualCovert).bigInteger

    (0 until inputs.size / Configs.maxIns).foreach(i => {
      val start = i * Configs.maxIns
      val curInputs = inputs.slice(start, start + Configs.maxIns)
      val ergSum = curInputs.map(_.amount).sum
      val curTokens = mutable.Map.empty[String, Long]
      curInputs.flatMap(_.tokens).foreach(token => {
        curTokens(token.getId.toString) = curTokens.getOrElse(token.getId.toString, 0L) + token.getValue
      })
      val out = EndBox(Address.create(req.depositAddress).getErgoAddress.script, Seq(), ergSum - Configs.distributeFee, curTokens.map(tok => new ErgoToken(tok._1, tok._2)).toSeq)
      val tx = aliceOrBob.mergeBoxes(curInputs.map(_.id).toArray, out, secret, Configs.distributeFee, req.depositAddress)
      val inputIds = tx.getInputBoxes.map(ScalaBridge.isoErgoTransactionInput.from(_).getBoxId).mkString(",")
      distributeTxsTable.insert(req.id, tx.getId, 1, WalletHelper.now, tx.toJson(false).getBytes("utf-16"), inputIds)
      logger.info(s"  merging inputs with tx ${tx.getId}...")
    })
  }
}
