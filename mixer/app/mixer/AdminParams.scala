package mixer

import config._
import helpers.ErgoMixerUtils
import mixinterface.TokenErgoMix
import models.Models.EntityInfo
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import play.api.Logger
import special.collection.Coll
import sigmastate.eval.Colls

import scala.collection.JavaConverters._
import javax.inject.{Inject, Singleton}

// TODO: need write unit test (#82)
@Singleton
class AdminParams @Inject()(networkUtils: NetworkUtils, ergoMixerUtils: ErgoMixerUtils) {
  private val explorer = new BlockExplorer
  private val logger: Logger = Logger(this.getClass)
  private var prover: ErgoProver = _
  private var toUseFees: Seq[InputBox] = _
  private var toUseInd = 0

  /**
   * handle parameters (Update Income Params in token emission boxes, Update Fee Params in fee emission boxes, Add Support token, Remove Support token)
   */
  def handleParams(): Unit = {
    networkUtils.usingClient(implicit ctx => {
      AdminConfigs.tokens = networkUtils.getTokenEmissionBoxes(0)
      AdminConfigs.fees = networkUtils.getFeeEmissionBoxes
      AdminConfigs.supports = networkUtils.getParamBoxes

      if (prover == null) prover = ctx.newProverBuilder()
        .withDLogSecret(AdminConfigs.ownerSecret.bigInteger)
        .build()

      // loading defaults. Happens only on startup
      if (AdminConfigs.tokenLevels.isEmpty)
        AdminConfigs.tokenLevels = AdminConfigs.tokens.last.getRegisters.get(0).getValue.asInstanceOf[Coll[(Int, Long)]].toArray.sorted
      if (AdminConfigs.commissionFee == 0)
        AdminConfigs.commissionFee = AdminConfigs.tokens.last.getRegisters.get(1).getValue.asInstanceOf[Int]
      if (AdminConfigs.desiredFee._1 == 0 || AdminConfigs.desiredFee._2 == 0) {
        val dynamicFeeRate = if (AdminConfigs.supports.last.getRegisters.size() >= 5)
          AdminConfigs.supports.last.getRegisters.get(4).getValue.asInstanceOf[Long] else 1000L
        AdminConfigs.desiredFee = (AdminConfigs.fees.last.getRegisters.get(0).getValue.asInstanceOf[Long], dynamicFeeRate)
      }

      try {
        toUseFees = AdminConfigs.fees.filter(box => box.getValue >= 2 * AdminConfigs.fee)
        toUseInd = 0

        handleIncomeParams
        handleFeeParams
        handleAddSupport
        handleRemoveSupport
      } catch {
        case a: Throwable =>
          logger.error(s" [Admin Params: An error occurred. Stacktrace below")
          logger.error(ergoMixerUtils.getStackTraceStr(a))
      }
    })
  }

  /**
   * Update Income Params in token emission boxes
   * @param ctx
   */
  def handleIncomeParams(implicit ctx: BlockchainContext): Unit = {
    AdminConfigs.tokens.foreach(tokenBox => {
      if (tokenBox.getRegisters.get(0).getValue.asInstanceOf[Coll[(Int, Long)]].toArray.toSeq.sorted != AdminConfigs.tokenLevels.sorted ||
        tokenBox.getRegisters.get(1).getValue.asInstanceOf[Int] != AdminConfigs.commissionFee) {
        logger.info(s"Going to update income parameters of ${tokenBox.getId.toString.slice(0, 10)}... box")
        if (toUseInd >= toUseFees.length) {
          logger.error("No other fee box to update params")
          return
        }
        val feeIn = toUseFees(toUseInd)
        val txB = ctx.newTxBuilder()
        val feeCp = txB.outBoxBuilder()
          .contract(ctx.newContract(feeIn.getErgoTree))
          .registers(feeIn.getRegisters.asScala: _*)
          .value(feeIn.getValue - AdminConfigs.fee)
          .build()

        val tokenCp = txB.outBoxBuilder()
          .contract(ctx.newContract(tokenBox.getErgoTree))
          .tokens(tokenBox.getTokens.asScala: _*)
          .registers(
            ErgoValueBuilder.buildFor(Colls.fromArray(AdminConfigs.tokenLevels.sorted.toArray)),
            ErgoValue.of(AdminConfigs.commissionFee)
          )
          .value(tokenBox.getValue)
          .build()

        val signed = prover.sign(txB.addInputs(tokenBox, feeIn)
          .addOutputs(tokenCp, feeCp)
          .fee(AdminConfigs.fee)
          .sendChangeTo(TokenErgoMix.mixerIncome)
          .build())
        val signedTx = ctx.sendTransaction(signed)
        logger.info(s"income params (in token emissions) update transaction sent. response: $signedTx")
        toUseInd += 1
      }
    })
  }

  /**
   * Update Fee Params in fee emission boxes
   * @param ctx
   */
  def handleFeeParams(implicit ctx: BlockchainContext): Unit = {
    // update dynamic fee in Token boxes
    if (AdminConfigs.supports.count(box => {
      val dynamicFeeRate = if (box.getRegisters.size() >= 5) box.getRegisters.get(4).getValue.asInstanceOf[Long] else 1000L
      dynamicFeeRate != AdminConfigs.desiredFee._2
    }) > 0) {
      logger.info("Going to update dynamic fee")
      val prover = ctx.newProverBuilder()
        .withDLogSecret(AdminConfigs.ownerSecret.bigInteger)
        .withDLogSecret(AdminConfigs.paramSecret.bigInteger)
        .build()

      val feeIn = toUseFees(toUseInd)
      val txB = ctx.newTxBuilder()
      val outs = AdminConfigs.supports.map(supp => {
        txB.outBoxBuilder()
          .value(supp.getValue)
          .contract(ctx.newContract(supp.getErgoTree))
          .tokens(supp.getTokens.asScala: _*)
          .registers(supp.getRegisters.subList(0, 4).asScala :+ ErgoValue.of(AdminConfigs.desiredFee._2): _*)
          .build()
      }) :+ txB.outBoxBuilder()
        .contract(ctx.newContract(feeIn.getErgoTree))
        .registers(feeIn.getRegisters.asScala: _*)
        .value(feeIn.getValue - AdminConfigs.fee)
        .build()

      val signed = prover.sign(txB.addInputs(AdminConfigs.supports :+ feeIn: _*)
        .addOutputs(outs: _*)
        .fee(AdminConfigs.fee)
        .sendChangeTo(TokenErgoMix.mixerIncome)
        .build())

      val signedTx = ctx.sendTransaction(signed)
      logger.info(s"Dynamic fee update transaction sent. response: $signedTx")
      toUseInd += 1
    }

    // Update max fee param in Fee boxes
    var toUpdateFees = AdminConfigs.fees.filter(fee => {
      fee.getRegisters.get(0).getValue.asInstanceOf[Long] != AdminConfigs.desiredFee._1 &&
      fee.getValue >= 2 * AdminConfigs.fee
    })

    if (toUpdateFees.nonEmpty) {
      logger.info(s"Going to update ${toUpdateFees.length} boxes to update max fee parameter")

      while (toUpdateFees.nonEmpty) {
        val selectedBoxes = toUpdateFees.slice(0, AdminConfigs.maxFeeBoxInInput)
        toUpdateFees = toUpdateFees.drop(selectedBoxes.length)

        val txB = ctx.newTxBuilder()
        val outs = selectedBoxes.slice(0, selectedBoxes.length - 1).map(fee => {
          txB.outBoxBuilder()
            .value(fee.getValue)
            .contract(ctx.newContract(fee.getErgoTree))
            .tokens(fee.getTokens.asScala: _*)
            .registers(ErgoValue.of(AdminConfigs.desiredFee._1))
            .build()
        }) :+ txB.outBoxBuilder()
          .value(selectedBoxes.last.getValue - AdminConfigs.fee)
          .contract(ctx.newContract(selectedBoxes.last.getErgoTree))
          .tokens(selectedBoxes.last.getTokens.asScala: _*)
          .registers(ErgoValue.of(AdminConfigs.desiredFee._1))
          .build()

        val signed = prover.sign(txB.addInputs(selectedBoxes: _*)
          .addOutputs(outs: _*)
          .fee(AdminConfigs.fee)
          .sendChangeTo(TokenErgoMix.mixerIncome)
          .build())
        val signedTx = ctx.sendTransaction(signed)
        logger.info(s"Max fee update transaction sent. response: $signedTx")
      }
    }
  }

  /**
   * Add Support token
   * @param ctx
   */
  def handleAddSupport(implicit ctx: BlockchainContext): Unit = {
    AdminConfigs.toAddSupport = AdminConfigs.toAddSupport.filterNot(req => {
      AdminConfigs.supports.exists(EntityInfo(_).equals(req._1))
    })
    AdminConfigs.toAddSupport.filter(sup => sup._2 != null).foreach(sup => {
      val txId = sup._2.getId
      val inputs = sup._2.getSignedInputs.asScala.map(_.getId.toString)
      val spent = inputs.map(in => explorer.getSpendingTxId(in)).filter(id => id.isDefined)
      val ok = spent.forall(id => id.get == txId) && spent.length == inputs.length
      if (ok) AdminConfigs.toAddSupport = AdminConfigs.toAddSupport.drop(1)
      if (!ok && spent.nonEmpty) AdminConfigs.toAddSupport = AdminConfigs.toAddSupport.map(req => {
        if (req._1.equals(sup._1)) (req._1, null)
        else req
      })
      if (!ok && spent.isEmpty) ctx.sendTransaction(sup._2)
    })

    val toAddOpt = AdminConfigs.toAddSupport.find(sup => sup._2 == null)
    if (toAddOpt.nonEmpty) {
      val usableFees = toUseFees.slice(toUseInd, toUseFees.length).filter(fee => fee.getValue >= AdminConfigs.fee * 2 + AdminConfigs.supportBoxValue)
      val toAdd = toAddOpt.get
      logger.info(s"Going to add support for ${toAdd._1.toJson()}")
      if (usableFees.isEmpty) {
        logger.error("No other fee box to add support")
        return
      }
      val feeIn = usableFees.head
      val tokenBox = networkUtils.getOwnerBox
      if (tokenBox.isEmpty) {
        logger.error("The box containing tokens was not found!!")
        return
      }
      val tokenIn = tokenBox.get

      val txB = ctx.newTxBuilder()
      val tokenCp = txB.outBoxBuilder()
        .value(tokenIn.getValue)
        .contract(ctx.newContract(tokenIn.getErgoTree))
        .tokens(new ErgoToken(TokenErgoMix.tokenId, tokenIn.getTokens.get(0).getValue - 1))
        .build()
      val feeCp = txB.outBoxBuilder()
        .contract(ctx.newContract(feeIn.getErgoTree))
        .registers(feeIn.getRegisters.asScala: _*)
        .value(feeIn.getValue - AdminConfigs.fee - AdminConfigs.supportBoxValue)
        .build()

      val suppBox = txB.outBoxBuilder()
        .value(AdminConfigs.supportBoxValue)
        .contract(ctx.newContract(TokenErgoMix.paramAddress.getErgoAddress.script))
        .registers(
          ErgoValue.of(toAdd._1.name.getBytes("utf-8")),
          ErgoValue.of(toAdd._1.id.getBytes("utf-8")),
          ErgoValueBuilder.buildFor(Colls.fromArray(toAdd._1.rings.toArray)),
          ErgoValue.of(toAdd._1.decimals),
          ErgoValue.of(AdminConfigs.desiredFee._2)
        )
        .tokens(new ErgoToken(TokenErgoMix.tokenId, 1))
        .build()

      val signed = prover.sign(txB.addInputs(tokenIn, feeIn)
        .addOutputs(suppBox, feeCp, tokenCp)
        .fee(AdminConfigs.fee)
        .sendChangeTo(TokenErgoMix.mixerIncome)
        .build())

      AdminConfigs.toAddSupport = AdminConfigs.toAddSupport.map(req => {
        if (req._1.equals(toAdd._1)) (req._1, signed)
        else req
      })
      val signedTx = ctx.sendTransaction(signed)
      logger.info(s"Add support box, transaction sent. response: $signedTx")
    }
  }

  /**
   * Remove Support token
   * @param ctx
   */
  def handleRemoveSupport(implicit ctx: BlockchainContext): Unit = {
    AdminConfigs.supports.filter(sup => AdminConfigs.toRemoveSupport.exists(e => e.equals(EntityInfo(sup))))
      .foreach(sup => {
        logger.info(s"Going to remove support for ${EntityInfo(sup).toJson()}")
        val txB = ctx.newTxBuilder()
        val dest = txB.outBoxBuilder()
          .value(sup.getValue - AdminConfigs.fee)
          .contract(ctx.newContract(TokenErgoMix.mixerIncome.getErgoAddress.script))
          .build()

        val prover = ctx.newProverBuilder()
          .withDLogSecret(AdminConfigs.paramSecret.bigInteger)
          .build()
        val signed = prover.sign(txB.addInputs(sup)
          .addOutputs(dest)
          .fee(AdminConfigs.fee)
          .tokensToBurn(new ErgoToken(TokenErgoMix.tokenId, 1))
          .sendChangeTo(TokenErgoMix.mixerIncome)
          .build())
        val signedTx = ctx.sendTransaction(signed)
        logger.info(s"Removing support Box, transaction sent. response: $signedTx")
      })
    AdminConfigs.toRemoveSupport = AdminConfigs.toRemoveSupport.filter(e => AdminConfigs.supports.exists(sup => e.equals(EntityInfo(sup))))
  }
}
