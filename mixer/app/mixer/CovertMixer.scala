package mixer

import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}

import scala.collection.mutable
import scala.collection.JavaConverters._

import config.MainConfigs
import dao.mixing._
import dao.DAOUtils
import helpers.ErgoMixerUtils
import mixinterface.AliceOrBob
import models.Box.{EndBox, OutBox}
import models.Models.CovertAsset
import models.Request.MixCovertRequest
import models.Status.CovertAssetWithdrawStatus
import models.Transaction.{CovertAssetWithdrawTx, DistributeTx}
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.{Address, ErgoToken, ErgoTreeTemplate}
import play.api.Logger
import scorex.crypto.hash.Sha256
import scorex.util.encode.Base16
import sigmastate.Values.ErgoTree
import wallet.{Wallet, WalletHelper}

@Singleton
class CovertMixer @Inject() (
  ergoMixer: ErgoMixer,
  aliceOrBob: AliceOrBob,
  ergoMixerUtils: ErgoMixerUtils,
  networkUtils: NetworkUtils,
  explorer: BlockExplorer,
  daoUtils: DAOUtils,
  mixingCovertRequestDAO: MixingCovertRequestDAO,
  covertDefaultsDAO: CovertDefaultsDAO,
  distributeTransactionsDAO: DistributeTransactionsDAO,
  covertAddressesDAO: CovertAddressesDAO,
  mixingRequestsDAO: MixingRequestsDAO,
  withdrawCovertTokenDAO: WithdrawCovertTokenDAO
) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * processes covert addresses, i.e. handle deposits, merges input boxes, initiate mixing.
   */
  def processCovert(): Unit =
    networkUtils.usingClient { implicit ctx =>
      daoUtils.awaitResult(mixingCovertRequestDAO.all).foreach { req =>
        try {
          logger.info(s"[covert: ${req.id}] processing deposits...")
          val assets      = daoUtils.awaitResult(covertDefaultsDAO.selectAllAssetsByMixGroupId(req.id))
          val supported   = assets.filter(_.ring > 0)
          val unsupported = assets.filter(_.ring == 0)
          // zero chainOrder means tx is confirmed! no need to consider it here.
          val spent =
            daoUtils.awaitResult(distributeTransactionsDAO.selectSpentTransactionsInputs(req.id)).mkString(",")
          val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
          val deposits =
            mutable.Map
              .empty[String, Long] // considers txs in mempool so avoids counting those boxes we already trying to spend
          val realDeposits = mutable.Map.empty[String, Long] // doesn't consider current txs in mempool
          supported.foreach(sup => deposits(sup.tokenId) = 0L)
          (supported ++ unsupported).foreach(sup => realDeposits(sup.tokenId) = 0L)
          val confirmedBoxes = allBoxes
            .map { box => // all confirmed boxes
              val conf = explorer.getConfirmationsForBoxId(box.id)
              // we only consider enough confirmed boxes as deposit
              if (conf >= MainConfigs.numConfirmation) {
                if (!spent.contains(box.id)) {
                  // we can spend this box
                  deposits("") = deposits.getOrElse("", 0L) + box.amount
                  box.tokens.foreach(token =>
                    deposits(token.getId.toString) = deposits.getOrElse(token.getId.toString, 0L) + token.getValue
                  )
                }
                realDeposits("") = realDeposits.getOrElse("", 0L) + box.amount
                box.tokens.foreach(token =>
                  realDeposits(token.getId.toString) = realDeposits.getOrElse(token.getId.toString, 0L) + token.getValue
                )
                box
              } else null
            }
            .filter(_ != null)
            .filter(box => !spent.contains(box.id))
            .toList

          realDeposits.foreach { dep =>
            if ((supported ++ unsupported).exists(_.tokenId == dep._1)) {
              if ((supported ++ unsupported).filter(_.tokenId == dep._1).head.confirmedDeposit != dep._2) {
                covertDefaultsDAO.updateConfirmedDeposit(req.id, dep._1, dep._2, WalletHelper.now)
                var name = dep._1
                if (name.isEmpty) name = "erg"
                logger.info(s"  processed confirmed deposits, $name: ${dep._2}")
              }
            } else {
              logger.info(s"  processed confirmed unsupported deposits, ${dep._1}: ${dep._2}")
              ergoMixer.handleCovertSupport(req.id, dep._1, 0L)
              covertDefaultsDAO.updateConfirmedDeposit(req.id, dep._1, dep._2, WalletHelper.now)
            }
          }

          processTx(req)
          if (confirmedBoxes.size >= MainConfigs.maxIns) {
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
      }

      // process current withdrawals of coverts tokens
      daoUtils
        .awaitResult(withdrawCovertTokenDAO.selectRequestedWithdraws)
        .foreach(req => processRequestedWithdrawAsset(req.covertId, req.tokenId, req.txId, req.tx))
      // process new requests to withdraw coverts tokens
      processNotWithdrawnAssets(daoUtils.awaitResult(withdrawCovertTokenDAO.selectNotProcessedWithdraws))
    }

  /**
   * enters mixing for a specific covert address (possibly for multiple assets)
   *
   * @param req       covert request
   * @param inputs    inputs to be used for entering mix
   * @param supported supported assets of this covert address
   * @param deposit   current deposits of this address
   */
  def enterMixing(
    req: MixCovertRequest,
    inputs: Seq[OutBox],
    supported: Seq[CovertAsset],
    deposit: mutable.Map[String, Long]
  ): Unit = networkUtils.usingClient { implicit ctx =>
    val addresses = daoUtils.awaitResult(covertAddressesDAO.selectAllAddressesByCovertId(req.id))
    var endBoxes  = Seq[EndBox]()
    supported.filter(_.tokenId.nonEmpty).foreach { toMix => // try to mix anything but erg!
      var possible = true
      while (possible) {
        val mixingNeed = req.getMixingNeed(MainConfigs.ergRing, toMix.ring)
        val ergNeeded =
          ((endBoxes.length + MainConfigs.maxOuts) / MainConfigs.maxOuts) * MainConfigs.distributeFee + mixingNeed._1
        if (deposit("") >= ergNeeded && deposit(toMix.tokenId) >= mixingNeed._2) {
          val withdrawAddress = ergoMixerUtils.getRandom(addresses).getOrElse("")
          logger.info(
            s"  creating a box to mix ${toMix.tokenId} in ring: ${toMix.ring}, withdraw address: $withdrawAddress"
          )
          val depAddr = ergoMixer.newMixRequest(
            withdrawAddress,
            req.numRounds,
            MainConfigs.ergRing,
            mixingNeed._1,
            toMix.ring,
            mixingNeed._2,
            toMix.tokenId,
            req.id
          )
          endBoxes = endBoxes :+ EndBox(
            Address.create(depAddr).getErgoAddress.script,
            Seq(),
            mixingNeed._1,
            Seq(new ErgoToken(toMix.tokenId, mixingNeed._2))
          )
          // subtract what was needed from deposits!
          deposit("") -= mixingNeed._1
          deposit(toMix.tokenId) -= mixingNeed._2

        } else possible = false
      }
    }
    if (supported.exists(_.tokenId.isEmpty)) {
      val toMix = supported.filter(_.tokenId.isEmpty).head
      // we try to mix ergs here
      var possible = true
      while (possible) {
        val mixingNeed = req.getMixingNeed(toMix.ring, 0L)
        val ergNeeded =
          ((endBoxes.length + MainConfigs.maxOuts) / MainConfigs.maxOuts) * MainConfigs.distributeFee + mixingNeed._1
        if (deposit("") >= ergNeeded) {
          val withdrawAddress = ergoMixerUtils.getRandom(addresses).getOrElse("")
          logger.info(s"  creating a box to mix erg in ring: ${toMix.ring}, withdraw address: $withdrawAddress")
          val depAddr = ergoMixer.newMixRequest(
            withdrawAddress,
            req.numRounds,
            toMix.ring,
            mixingNeed._1,
            0L,
            mixingNeed._2,
            toMix.tokenId,
            req.id
          )
          endBoxes = endBoxes :+ EndBox(Address.create(depAddr).getErgoAddress.script, Seq(), mixingNeed._1, Nil)
          // subtract what was needed from deposits!
          deposit("") -= mixingNeed._1

        } else possible = false
      }
    }
    if (endBoxes.isEmpty) return
    // here we subtract distributing fee
    deposit("") -= ((endBoxes.length + MainConfigs.maxOuts) / MainConfigs.maxOuts) * MainConfigs.distributeFee

    // here we check a rare but an important case! it is possible that the leftover ergs is 0
    // or less than the minimum amount a box can have (here we consider it 1e6 nano-ergs),
    // in this case if there are some leftover tokens, we will lose them!
    if (
      (deposit.count(dep => dep._1.nonEmpty && dep._2 > 0) > 0 && deposit("") < MainConfigs.minPossibleErgInBox) ||
      (deposit("") > 0 && deposit("") < MainConfigs.minPossibleErgInBox)
    ) {
      // If the above scenario is the case, we simply ignore mixing the last box in the list!
      logger.info(s"  we ignore mixing 1 box, reason is lack of leftover ergs; we may lose our leftover tokens!")
      deposit("") += endBoxes.last.value
      if (endBoxes.last.tokens.nonEmpty) {
        deposit(endBoxes.last.tokens.last.getId.toString) += endBoxes.last.tokens.last.getValue
      }
      val addr = WalletHelper.getErgoAddress(endBoxes.last.receiverBoxScript).toString
      mixingRequestsDAO.deleteByWithdrawAddress(addr)
      endBoxes = endBoxes.dropRight(1)
      if (endBoxes.isEmpty) {
        logger.info("  no other box to mix.")
        return
      }
    }

    val wallet = new Wallet(req.masterKey)
    val secret = wallet.getSecret(-1, req.isManualCovert).bigInteger

    logger.info(s"  we will create ${endBoxes.length} boxes to enter mixing...")

    val transactions = aliceOrBob.distribute(
      inputs.map(_.id).toArray,
      endBoxes.toArray,
      Array(secret),
      MainConfigs.distributeFee,
      req.depositAddress,
      MainConfigs.maxOuts
    )
    for (i <- transactions.indices) {
      val tx     = transactions(i)
      val inputs = tx.getSignedInputs.asScala.map(_.getId).mkString(",")
      val new_tx = DistributeTx(req.id, tx.getId, i + 1, WalletHelper.now, tx.toJson(false).getBytes("utf-16"), inputs)
      distributeTransactionsDAO.insert(new_tx)
      try
        ctx.sendTransaction(tx)
      catch {
        case e: Throwable =>
          logger.error(
            s"transaction ${tx.getId} got refused by the node! maybe it doesn't support chained transactions, waiting... consider updating your node for a faster mixing experience."
          )
          logger.debug(s"  Exception: ${e.getMessage}")
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
    daoUtils
      .awaitResult(distributeTransactionsDAO.selectSpentTransactions(req.id))
      .sortBy(_.order)
      .foreach { tx =>
        val confNum = explorer.getTxNumConfirmations(tx.txId)
        if (confNum == -1) { // not mined yet, broadcast tx again!
          val signedTx = ctx.signedTxFromJson(tx.toString)
          try {
            ctx.sendTransaction(signedTx)
            logger.info(s"  broadcasted tx ${signedTx.getId}")
          } catch {
            case e: Throwable =>
              logger.error(s"  transaction ${signedTx.getId} got refused by the node!")
              logger.debug(s"  Exception: ${e.getMessage}")
              var spendingTxId: String = ""
              val boxStatus = tx.inputs.split(",").forall { boxId =>
                explorer.getSpendingTxId(boxId) match {
                  case Some(txId) =>
                    spendingTxId = txId
                  case None =>
                    spendingTxId = ""
                }
                if ((spendingTxId.nonEmpty && spendingTxId != req.id) || outputs.contains(boxId)) {
                  false
                } else true
              }
              if (boxStatus) {
                logger.error(s"  potential reason: node does not support chain transactions. waiting...")
              } else {
                outputs = outputs ++ signedTx.getOutputsToSpend.asScala.map(box => box.getId.toString)
                distributeTransactionsDAO.setOrderToZeroByTxId(tx.txId)
                logger.info(s"  tx has a box spent or has a wrong box")
              }
          }
        } else if (confNum >= MainConfigs.numConfirmation) { // confirmed enough
          logger.info(s"  tx ${tx.txId} is confirmed.")
          distributeTransactionsDAO.setOrderToZeroByTxId(tx.txId)
        } else {
          logger.info(s"  tx ${tx.txId} is mined, waiting for enough confirmations...")
        }
      }
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

    (0 until inputs.size / MainConfigs.maxIns).foreach { i =>
      val start     = i * MainConfigs.maxIns
      val curInputs = inputs.slice(start, start + MainConfigs.maxIns)
      val ergSum    = curInputs.map(_.amount).sum
      val curTokens = mutable.Map.empty[String, Long]
      curInputs.flatMap(_.tokens).foreach { token =>
        curTokens(token.getId.toString) = curTokens.getOrElse(token.getId.toString, 0L) + token.getValue
      }
      val out = EndBox(
        Address.create(req.depositAddress).getErgoAddress.script,
        Seq(),
        ergSum - MainConfigs.distributeFee,
        curTokens.map(tok => new ErgoToken(tok._1, tok._2)).toSeq
      )
      val tx =
        aliceOrBob.mergeBoxes(curInputs.map(_.id).toArray, out, secret, MainConfigs.distributeFee, req.depositAddress)
      val inputIds = tx.getSignedInputs.asScala.map(_.getId).mkString(",")
      val new_tx   = DistributeTx(req.id, tx.getId, 1, WalletHelper.now, tx.toJson(false).getBytes("utf-16"), inputIds)
      distributeTransactionsDAO.insert(new_tx)
      logger.info(s"  merging inputs with tx ${tx.getId}...")
    }
  }

  /**
   * process withdraw request of a covert's asset
   */
  def processRequestedWithdrawAsset(covertId: String, tokenId: String, txId: String, tx: Array[Byte]): Unit = {
    logger.info(s"[covert: $covertId] processing withdraw request of token $tokenId...")
    if (txId != "") {
      // check confirmation
      val confNum = explorer.getTxNumConfirmations(txId)
      if (confNum == -1) {
        networkUtils.usingClient { ctx =>
          // not mined yet, broadcast tx again!
          val signedTx = ctx.signedTxFromJson(new String(tx, StandardCharsets.UTF_16))
          try {
            ctx.sendTransaction(signedTx)
            logger.info(s"  broadcasted tx ${signedTx.getId}")
          } catch {
            case e: Throwable =>
              logger.error(s"  transaction ${signedTx.getId} got refused by the node! removing transaction from db...")
              logger.debug(s"  Exception: ${e.getMessage}")
              daoUtils.awaitResult(withdrawCovertTokenDAO.resetRequest(covertId, tokenId))
          }
        }
      } else if (confNum >= MainConfigs.numConfirmation) { // confirmed enough
        logger.info(s"  tx $txId is confirmed.")
        withdrawCovertTokenDAO.setRequestComplete(covertId, tokenId)
        covertDefaultsDAO.deleteIfRingIsEmpty(covertId, tokenId)
      } else {
        logger.info(s"  tx $txId is mined, waiting for enough confirmations...")
      }
    } else {
      logger.error(s"  withdraw status is requested but it has not transaction")
    }
  }

  /**
   * withdraw a covert's assets
   */
  def processNotWithdrawnAssets(covertAssetWithdraws: Seq[CovertAssetWithdrawTx]): Unit = {
    val requests = mutable.Map.empty[(String, String), Seq[String]]
    covertAssetWithdraws.foreach { req =>
      val key = (req.covertId, req.withdrawAddress)
      if (requests.contains(key)) requests(key) = requests(key) :+ req.tokenId
      else requests(key)                        = Seq(req.tokenId)
    }

    requests.keys.foreach(key => withdrawAsset(key._1, key._2, requests(key)))
  }

  /**
   * withdraw a covert's assets
   */
  def withdrawAsset(covertId: String, withdrawAddress: String, tokenIds: Seq[String]): Unit = {
    logger.info(s"[covert: $covertId] processing withdraw request of token list [${tokenIds.mkString(",")}]...")
    val covertInfo        = ergoMixer.covertInfoById(covertId)
    val depositAddress    = covertInfo._1
    val proverDlogSecrets = covertInfo._2.bigInteger

    val ergoTree: ErgoTree   = Address.create(depositAddress).getErgoAddress.script
    val ergoTreeTemplateHash = Base16.encode(Sha256(ErgoTreeTemplate.fromErgoTree(ergoTree).getBytes))

    val inputs: Seq[String] =
      tokenIds.flatMap(tokenId => explorer.getUnspentBoxIdsWithAsset(ergoTreeTemplateHash, tokenId)).distinct
    // if any of the boxes are in distribute transactions (i.e. a deposit is in progress), don't try to withdraw token in this job
    var depositInProgress: Boolean = false
    val txPoolInputs               = daoUtils.awaitResult(distributeTransactionsDAO.selectInputsByMixGroupId(covertId)).mkString(",")
    inputs.foreach(boxId => if (txPoolInputs.contains(boxId)) depositInProgress = true)
    if (depositInProgress) {
      logger.warn("  an inputBox is in mempool. Ignoring withdraw request for now...")
      return
    }

    val tx = aliceOrBob.withdrawToken(proverDlogSecrets, inputs, tokenIds, withdrawAddress, depositAddress)
    try {
      networkUtils.usingClient(ctx => ctx.sendTransaction(tx))
      logger.info(
        s"  withdraw token list [${tokenIds.mkString(",")}] from covert $covertId requested with txId ${tx.getId}"
      )
      withdrawCovertTokenDAO.updateTx(covertId, tokenIds, tx.getId, tx.toJson(false).getBytes("utf-16"))
    } catch {
      case e: Throwable =>
        logger.error(s"  something unexpected has happened! tx ${tx.getId} got refused by the node!")
        logger.debug(s"  Exception: ${e.getMessage}")
    }
  }

  /**
   * add withdraw request of a covert's assets
   */
  def queueWithdrawAsset(covertId: String, tokenIds: Seq[String], withdrawAddress: String): Unit = {
    WalletHelper.okAddresses(Seq(withdrawAddress))
    tokenIds.foreach { tokenId =>
      if (daoUtils.awaitResult(withdrawCovertTokenDAO.isActiveRequest(covertId, tokenId)))
        throw new Exception(s"request for withdraw of $tokenId in $covertId is already in process")
      val asset = daoUtils
        .awaitResult(covertDefaultsDAO.selectByGroupAndTokenId(covertId, tokenId))
        .getOrElse(throw new Exception(s"asset $tokenId not exists in covert $covertId"))
      if (asset.confirmedDeposit == 0) throw new Exception(s"asset $tokenId has 0 value in covert $covertId")

      withdrawCovertTokenDAO.insert(
        CovertAssetWithdrawTx(
          covertId,
          tokenId,
          withdrawAddress,
          WalletHelper.now,
          CovertAssetWithdrawStatus.NoWithdrawYet.value,
          "",
          Array.empty[Byte]
        )
      )
    }
  }

}
