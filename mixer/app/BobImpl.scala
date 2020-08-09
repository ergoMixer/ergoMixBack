package app

import java.math.BigInteger
import ergomix.{Bob, DHT, EndBox, FullMixBox, FullMixTx, HalfMixBox}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.eval._
import special.sigma.GroupElement
import scala.jdk.CollectionConverters._
import TokenErgoMix._
import cli.ErgoMixCLIUtil
import special.collection.Coll

class BobImpl(y:BigInteger)(implicit ctx: BlockchainContext) extends Bob {
  val gY: GroupElement = g.exp(y)
  implicit val tokenErgoMix: TokenErgoMix = ErgoMixCLIUtil.tokenErgoMix.get
  val util = new Util()

  def spendFullMixBox(f: FullMixBox, endBoxes: Seq[EndBox], feeAmount:Long, otherInputBoxes:Array[InputBox], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT]): SignedTransaction = {
    val txB = ctx.newTxBuilder

    val outBoxes: Seq[OutBox] = endBoxes.map{ endBox =>
      var outBoxBuilder = txB.outBoxBuilder().value(endBox.value).contract(new ErgoTreeContract(endBox.receiverBoxScript))
      if (endBox.tokens.nonEmpty)
        outBoxBuilder = outBoxBuilder.tokens(endBox.tokens:_*)
      (if (endBox.receiverBoxRegs.isEmpty) outBoxBuilder else outBoxBuilder.registers(endBox.receiverBoxRegs:_*)).build()
    }

    val inputs = new java.util.ArrayList[InputBox]()

    otherInputBoxes.foreach(inputs.add)
    if (inputs.size > 0 && inputs.asScala.exists(_.getErgoTree == tokenErgoMix.halfMixContract.getErgoTree)) {
      inputs.add(1, f.inputBox)
    } else {
      inputs.add(0, f.inputBox)
    }

    val txToSign = txB.boxesToSpend(inputs)
      .outputs(outBoxes: _*)
      .fee(feeAmount)
      .sendChangeTo(util.getAddress(changeAddress), changeBoxRegs:_*)
      .build()

    val bob: ErgoProver = additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder().withDLogSecret(y)
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()


    bob.sign(txToSign)
  }

  def spendHalfMixBox(halfMixBox: HalfMixBox, inputBoxes:Array[InputBox], fee:Long, changeAddress:String, additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT], numToken: Int = 0): (FullMixTx, Boolean) = {
    val gXY: GroupElement = halfMixBox.gX.exp(y)
    val bit: Boolean = scala.util.Random.nextBoolean()

    val (c1, c2) = if (bit) (gY, gXY) else (gXY, gY)
    val txB = ctx.newTxBuilder()

    val halfBoxNumTokens = halfMixBox.inputBox.getTokens.get(0).getValue
    val distributeAmount = (halfBoxNumTokens + numToken) / 2
    require(distributeAmount * 2 > halfBoxNumTokens)

    var tokens = Seq(new ErgoToken(TokenErgoMix.tokenId, distributeAmount))
    var mixingToken: ErgoToken = null
    if (halfMixBox.inputBox.getTokens.size() > 1) {
      mixingToken = halfMixBox.inputBox.getTokens.get(1)
      tokens = tokens :+ mixingToken
    }

    val firstOutBox = txB.outBoxBuilder()
      .value(halfMixBox.inputBox.getValue)
      .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(halfMixBox.gX), ErgoValue.of(tokenErgoMix.halfMixScriptHash))
      .contract(tokenErgoMix.fullMixScriptContract)
      .tokens(tokens:_*)
      .build()

    val secondOutBox = txB.outBoxBuilder()
      .value(halfMixBox.inputBox.getValue)
      .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(halfMixBox.gX), ErgoValue.of(tokenErgoMix.halfMixScriptHash))
      .contract(tokenErgoMix.fullMixScriptContract)
      .tokens(tokens:_*)
      .build()

    val tokenBox = inputBoxes.last
    var batchPrice = 0L
    val batches = tokenBox.getRegisters.get(0).getValue.asInstanceOf[Coll[(Int, Long)]]
    batches.toArray.foreach(batch => {
      if (batch._1 == numToken) batchPrice = batch._2
    })
    if (batchPrice == 0) throw new Exception(s"Could not find appropriate batch for $numToken, batches: $batches")
    var ergCommission = 0L
    var tokenCommission: ErgoToken = null
    if (tokenBox.getRegisters.size() == 2) {
      val rate: Int = tokenBox.getRegisters.get(1).getValue.asInstanceOf[Int]
      ergCommission = halfMixBox.inputBox.getValue / rate

      if (mixingToken != null) {
        val inputTokens = inputBoxes.map(_.getTokens.asScala.filter(_.getId == mixingToken.getId).map(_.getValue).sum).sum
        tokenCommission = new ErgoToken(mixingToken.getId, inputTokens - mixingToken.getValue)
        assert(tokenCommission.getValue >= mixingToken.getValue / rate)
      }
    }

    val excessErg = inputBoxes.map(_.getValue.toLong).sum - fee - halfMixBox.inputBox.getValue - tokenBox.getValue
    assert(excessErg >= ergCommission + batchPrice)
    val tokenCp = ctx.newTxBuilder().outBoxBuilder
      .value(tokenBox.getValue)
      .tokens(new ErgoToken(TokenErgoMix.tokenId, tokenBox.getTokens.get(0).getValue - numToken))
      .registers(tokenBox.getRegisters.asScala:_*)
      .contract(tokenErgoMix.tokenEmissionContract)
      .build()

    val payBoxB = ctx.newTxBuilder().outBoxBuilder
      .value(excessErg)
      .contract(tokenErgoMix.income)
    val payBox = if (tokenCommission != null && tokenCommission.getValue > 0) payBoxB.tokens(tokenCommission).build()
    else payBoxB.build()

    val inputs = new java.util.ArrayList[InputBox]()

    inputs.add(halfMixBox.inputBox)
    inputs.addAll(inputBoxes.toList.asJava)

    val txToSign = txB.boxesToSpend(inputs)
      .outputs(firstOutBox, secondOutBox, payBox, tokenCp)
      .fee(fee)
      .sendChangeTo(util.getAddress(changeAddress))
      .build()

    val bob: ErgoProver = additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder().withDHTData(g, halfMixBox.gX, gY, gXY, y)
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()

    (FullMixTx(bob.sign(txToSign)), bit)
  }
}
