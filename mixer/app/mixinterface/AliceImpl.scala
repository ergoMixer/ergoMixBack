package mixinterface

import java.math.BigInteger

import mixinterface.ErgoMixBase._
import models.Models.{EndBox, FullMixBox, HalfMixTx}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.eval._
import special.collection.Coll
import special.sigma.GroupElement
import wallet.WalletHelper._

import scala.collection.JavaConverters._

class AliceImpl(x: BigInteger, implicit val tokenErgoMix: TokenErgoMix)(implicit ctx: BlockchainContext) extends Alice {
  val gX: GroupElement = g.exp(x)

  /**
   * spends full-box, is used for mixing as alice
   *
   * @param f                     full-box
   * @param endBoxes              end-boxes
   * @param feeAmount             fee amount
   * @param otherInputBoxes       other inputs like fee
   * @param changeAddress         change address
   * @param changeBoxRegs         change box registers
   * @param burnTokens            tokens that need to be burned
   * @param additionalDlogSecrets secrets to spent inputs
   * @param additionalDHTuples    dh tuples
   * @return tx spending full-box as alice
   */
  def spendFullMixBox(f: FullMixBox, endBoxes: Seq[EndBox], feeAmount: Long, otherInputBoxes: Array[InputBox], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], burnTokens: Seq[ErgoToken], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT]): SignedTransaction = {
    val (gY, gXY) = (f.r4, f.r5)
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder

    val outBoxes: Seq[OutBox] = endBoxes.map { endBox =>
      var outBoxBuilder = txB.outBoxBuilder().value(endBox.value).contract(new ErgoTreeContract(endBox.receiverBoxScript))
      if (endBox.tokens.nonEmpty)
        outBoxBuilder = outBoxBuilder.tokens(endBox.tokens: _*)
      (if (endBox.receiverBoxRegs.isEmpty) outBoxBuilder else outBoxBuilder.registers(endBox.receiverBoxRegs: _*)).build()
    }

    val inputs = new java.util.ArrayList[InputBox]()

    otherInputBoxes.foreach(inputs.add)
    if (inputs.size > 0 && inputs.asScala.exists(_.getErgoTree == tokenErgoMix.halfMixContract.getErgoTree)) {
      inputs.add(1, f.inputBox)
    } else {
      inputs.add(0, f.inputBox)
    }

    val preTxB = txB.boxesToSpend(inputs)
      .outputs(outBoxes: _*)
      .fee(feeAmount)
      .sendChangeTo(getAddress(changeAddress))
    val txToSign = if (burnTokens.nonEmpty) preTxB.tokensToBurn(burnTokens: _*).build() else preTxB.build()

    val alice: ErgoProver = additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder().withDHTData(g, gY, gX, gXY, x)
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()

    alice.sign(txToSign)
  }

  /**
   * creates half-box, is used for entering mixing
   *
   * @param inputBoxes            input boxes
   * @param feeAmount             fee amount
   * @param changeAddress         change address
   * @param additionalDlogSecrets secrets for spending inputs
   * @param additionalDHTuples    dh tuples
   * @param poolAmount            ring
   * @param numToken              mix level
   * @param mixingTokenId         mixing token id in case of token mixing, empty if it is erg mixing
   * @param mixingTokenAmount     token ring
   * @return
   */
  def createHalfMixBox(inputBoxes: Array[InputBox], feeAmount: Long, changeAddress: String,
                       additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT],
                       poolAmount: Long, numToken: Int = 0, mixingTokenId: String, mixingTokenAmount: Long): HalfMixTx = {
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder

    var tokens = Seq(new ErgoToken(TokenErgoMix.tokenId, numToken))
    if (mixingTokenId.nonEmpty) tokens = tokens :+ new ErgoToken(mixingTokenId, mixingTokenAmount)
    val newBox = txB.outBoxBuilder()
      .value(poolAmount)
      .registers(ErgoValue.of(gX))
      .contract(tokenErgoMix.halfMixContract)
      .tokens(tokens: _*)
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
      ergCommission = poolAmount / rate

      if (mixingTokenId.nonEmpty) {
        val inputTokens = inputBoxes.map(_.getTokens.asScala.filter(_.getId.toString == mixingTokenId).map(_.getValue).sum).sum
        tokenCommission = new ErgoToken(mixingTokenId, inputTokens - mixingTokenAmount)
        assert(tokenCommission.getValue >= mixingTokenAmount / rate)
      }
    }
    val excessErg = inputBoxes.map(_.getValue.toLong).sum - feeAmount - poolAmount - tokenBox.getValue
    assert(excessErg >= ergCommission + batchPrice)
    val copy = ctx.newTxBuilder().outBoxBuilder
      .value(tokenBox.getValue)
      .tokens(new ErgoToken(TokenErgoMix.tokenId, tokenBox.getTokens.get(0).getValue - numToken))
      .registers(tokenBox.getRegisters.asScala: _*)
      .contract(tokenErgoMix.tokenEmissionContract)
      .build()

    val payBoxB = ctx.newTxBuilder().outBoxBuilder
      .value(excessErg)
      .contract(tokenErgoMix.income)
    val payBox = if (tokenCommission != null && tokenCommission.getValue > 0) payBoxB.tokens(tokenCommission).build()
    else payBoxB.build()

    val inputs = new java.util.ArrayList[InputBox]()
    inputs.addAll(inputBoxes.toList.asJava)

    val txToSign = txB.boxesToSpend(inputs)
      .outputs(newBox, payBox, copy)
      .fee(feeAmount)
      .sendChangeTo(getAddress(changeAddress))
      .build()

    val alice: ErgoProver = additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder()
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()

    HalfMixTx(alice.sign(txToSign))
  }

  override def getProver(f: FullMixBox): ErgoProver = {
    val additionalDHTuples: Array[DHT] = Seq().toArray
    val additionalDlogSecrets: Array[BigInteger] = Seq().toArray
    val (gY, gXY) = (f.r4, f.r5)

    additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder().withDHTData(g, gY, gX, gXY, x)
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()
  }
}
