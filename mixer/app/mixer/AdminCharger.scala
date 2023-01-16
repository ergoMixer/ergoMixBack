package mixer

import config._
import helpers.ErgoMixerUtils
import mixinterface.TokenErgoMix
import network.NetworkUtils

import org.ergoplatform.appkit._
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters._

// TODO: need write unit test (#80)
@Singleton
class AdminCharger @Inject()(networkUtils: NetworkUtils, ergoMixerUtils: ErgoMixerUtils) {
  private val logger: Logger = Logger(this.getClass)
  private var prover: ErgoProver = _
  private var toUseIncomes: Seq[InputBox] = Seq()

  /**
   * handle charging fee emission and token emission boxes
   */
  def handleCharging(): Unit = {
    networkUtils.usingClient(implicit ctx => {
      if (prover == null) prover = ctx.newProverBuilder()
        .withDLogSecret(AdminConfigs.ownerSecret.bigInteger)
        .withDLogSecret(AdminConfigs.incomeSecret.bigInteger)
        .build()

      AdminConfigs.tokens = networkUtils.getTokenEmissionBoxes(0)
      AdminConfigs.fees = networkUtils.getFeeEmissionBoxes
      updateToUseIncomes()

      try {
        handleFeeCharging
        handleTokenCharging

      } catch {
        case a: Throwable =>
          logger.error(s" [Admin Charger: An error occurred. Stacktrace below")
          logger.error(ergoMixerUtils.getStackTraceStr(a))
      }
    })
  }

  /**
   * check fee emission boxes and charge them
   *
   * @param ctx
   */
  def handleFeeCharging(implicit ctx: BlockchainContext): Unit = {
    val toChargeFees = AdminConfigs.fees.filter(fee => fee.getValue <= AdminConfigs.feeThreshold)
    if (toChargeFees.nonEmpty) {
      updateToUseIncomes()
      logger.info(s"Going to charge ${toChargeFees.length} fee boxes")
      toChargeFees.foreach(fee => {
        val ergNeed = AdminConfigs.feeCharge + AdminConfigs.fee + MainConfigs.minPossibleErgInBox
        val usingIncome: (Seq[InputBox], Long) = getNeededIncomeBoxes(ergNeed)
        logger.info(s"Number using income box for handle fee charging: ${usingIncome._1.length.toString}")
        if (usingIncome._2 > 0) {
          logger.error("No income box to charge fee boxes!")
          return
        }

        val txB = ctx.newTxBuilder()
        val feeCp = txB.outBoxBuilder()
          .contract(ctx.newContract(fee.getErgoTree))
          .registers(fee.getRegisters.asScala: _*)
          .value(AdminConfigs.feeCharge)
          .build()

        val signed = prover.sign(txB.addInputs(usingIncome._1 :+ fee: _*)
          .addOutputs(feeCp)
          .fee(AdminConfigs.fee)
          .sendChangeTo(TokenErgoMix.mixerIncome)
          .build())
        val signedTx = ctx.sendTransaction(signed)
        logger.info(s"fee box charging transaction sent. response: $signedTx")
      })
    }
  }

  /**
   * check token emission boxes and charge them
   *
   * @param ctx
   */
  def handleTokenCharging(implicit ctx: BlockchainContext): Unit = {
    val toChargeTokens = AdminConfigs.tokens.filter(token => {
      token.getTokens.get(0).getValue <= AdminConfigs.tokenThreshold
    }).sortBy(token => token.getTokens.get(0).getValue)

    if (toChargeTokens.nonEmpty) {
      logger.info(s"${toChargeTokens.length} token boxes need to be charged. Will charge ${Math.min(toChargeTokens.length, AdminConfigs.maxTokenBoxInInput)} of them now.")
      val tokenBox = networkUtils.getOwnerBox
      if (tokenBox.isEmpty) {
        logger.error("The box containing tokens was not found!!")
        return
      }
      val tokenBankIn = tokenBox.get
      val toCharges = toChargeTokens.slice(0, AdminConfigs.maxTokenBoxInInput)
      updateToUseIncomes()
      val ergNeed = AdminConfigs.fee + MainConfigs.minPossibleErgInBox
      val usingIncome: (Seq[InputBox], Long) = getNeededIncomeBoxes(ergNeed)

      logger.info(s"Number using income box for handle token charging: ${usingIncome._1.length.toString}")
      if (usingIncome._2 > 0) {
        logger.error("No income box to charge token boxes!")
        return
      }

      val txB = ctx.newTxBuilder()
      var tokenNeed = 0L
      val tokenOuts = toCharges.map(toCharge => {
        tokenNeed = tokenNeed + (AdminConfigs.tokenCharge - toCharge.getTokens.get(0).getValue)
        txB.outBoxBuilder()
          .contract(ctx.newContract(toCharge.getErgoTree))
          .registers(toCharge.getRegisters.asScala: _*)
          .tokens(new ErgoToken(TokenErgoMix.tokenId, AdminConfigs.tokenCharge))
          .value(toCharge.getValue)
          .build()
      })
      val tokenBankOut = txB.outBoxBuilder()
        .value(tokenBankIn.getValue)
        .contract(ctx.newContract(tokenBankIn.getErgoTree))
        .tokens(new ErgoToken(TokenErgoMix.tokenId, tokenBankIn.getTokens.get(0).getValue - tokenNeed))
        .build()

      val inputs = Seq(tokenBankIn) ++ usingIncome._1 ++ toCharges
      val outputs = Seq(tokenBankOut) ++ tokenOuts
      val unsignedTx = txB.addInputs(inputs: _*)
        .addOutputs(outputs: _*)
        .fee(AdminConfigs.fee)
        .sendChangeTo(TokenErgoMix.mixerIncome)
        .build()
      val signed = prover.sign(unsignedTx)
      val signedTx = ctx.sendTransaction(signed)
      logger.info(s"token box charging transaction sent. response: $signedTx")
    }
  }

  /**
   * Update toUseIncomes if is empty
   */
  def updateToUseIncomes(): Unit = {
    if (toUseIncomes.isEmpty) toUseIncomes = networkUtils.getIncomeBoxes.sortBy(_.getValue)(Ordering[Long].reverse)
  }

  /**
   * return covered Income boxes for needed erg
   * @param neededErg amount of needed erg
   */
  def getNeededIncomeBoxes(neededErg: Long): (Seq[InputBox], Long) = {
    var ergNeed = neededErg
    var toUseInd = 0
    val usingIncome: Seq[InputBox] = toUseIncomes.takeWhile( box => {
      ergNeed -= box.getValue
      toUseInd += 1
      ergNeed > 0
    })
    toUseIncomes = toUseIncomes.drop(toUseInd)
    (usingIncome, ergNeed)
  }

}
