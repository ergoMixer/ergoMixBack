package cli


import java.math.BigInteger

import app.ergomix._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit._
import app.{AliceImpl, BobImpl, TokenErgoMix, Util}
import cli.ErgoMixCLIUtil.{getProver, usingClient}

import scala.collection.JavaConverters._

object AliceOrBob {

  // spends boxes with providing secret. used for withdrawing half-box or deposits
  def spendBox(inputBox: String, feeBox: Option[String], withdrawAddress: String, proverDlogSecrets: Array[BigInteger], feeAmount: Long, broadCast: Boolean): SignedTransaction = {
    usingClient { implicit ctx =>
      val prover: ErgoProver = proverDlogSecrets.foldLeft(ctx.newProverBuilder())(
        (proverBuilder, bigInteger) => proverBuilder.withDLogSecret(bigInteger)
      ).build()
      val txC = ctx.newTxBuilder()
      val input = ctx.getBoxesById(inputBox)(0)
      val inputs = new java.util.ArrayList[InputBox]()
      inputs.add(input)
      val outputs = new java.util.ArrayList[OutBox]()
      val tokens = new java.util.ArrayList[ErgoToken]()
      var hasFeeToken = false
      input.getTokens.forEach(token => {
        if (token.getId.toString != TokenErgoMix.tokenId) tokens.add(token)
        else hasFeeToken |= token.getValue > 0
      })
      var outputBuilder = txC.outBoxBuilder()
        .contract(new ErgoTreeContract(Address.create(withdrawAddress).getErgoAddress.script))
      if (!tokens.isEmpty) outputBuilder = outputBuilder.tokens(tokens.asScala: _*)
      if (feeBox.nonEmpty && hasFeeToken) {
        outputs.add(outputBuilder.value(input.getValue).build())
        val fee = ctx.getBoxesById(feeBox.get)(0)
        inputs.add(fee)
        outputs.add(txC.outBoxBuilder()
          .contract(new ErgoTreeContract(fee.getErgoTree))
          .value(fee.getValue - feeAmount)
          .registers(fee.getRegisters.asScala: _*)
          .build())
      } else outputs.add(outputBuilder.value(input.getValue - feeAmount).build())

      val uTx = ctx.newTxBuilder()
        .boxesToSpend(inputs)
        .outputs(outputs.asScala: _*)
        .fee(feeAmount)
        .sendChangeTo(Address.create(withdrawAddress).getErgoAddress)
        .build()
      val tx = prover.sign(uTx)
      if (broadCast) ctx.sendTransaction(tx)
      tx
    }
  }

  def distribute(inputBoxes: Array[String], outputs: Array[EndBox], proverDlogSecrets: Array[BigInteger], feeAmount: Long, changeAddress: String, outLimit: Int, tokenId: String): List[SignedTransaction] = {
    val transactions = new java.util.ArrayList[SignedTransaction]()
    usingClient { implicit ctx =>
      val prover: ErgoProver = proverDlogSecrets.foldLeft(ctx.newProverBuilder())(
        (proverBuilder, bigInteger) => proverBuilder.withDLogSecret(bigInteger)
      ).build()
      var boxesToSpend = ctx.getBoxesById(inputBoxes: _*)
      val numTokenInInputs = boxesToSpend.map(_.getTokens.asScala.filter(_.getId.toString.equals(tokenId)).map(_.getValue).sum).sum
      var numTokenInOuts = 0L
      val numTxs = (outputs.length + outLimit - 1) / outLimit
      for (i <- 0 until numTxs) {
        val txB = ctx.newTxBuilder()
        var outBoxes = outputs.slice(i * outLimit, i * outLimit + outLimit).map(out => {
          numTokenInOuts += out.tokens.filter(_.getId.toString.equals(tokenId)).map(_.getValue).sum
          val outB = txB.outBoxBuilder()
            .contract(new ErgoTreeContract(out.receiverBoxScript))
            .value(out.value)
          if (out.tokens.nonEmpty) outB.tokens(out.tokens: _*).build()
          else outB.build()
        })
        val remErg = boxesToSpend.map(_.getValue.toLong).sum - outBoxes.map(_.getValue).sum - feeAmount
        if (remErg > 0) {
          var changeBox = txB.outBoxBuilder()
            .value(remErg)
            .contract(new ErgoTreeContract(Address.create(changeAddress).getErgoAddress.script))
          if (numTokenInInputs - numTokenInOuts > 0)
            changeBox = changeBox.tokens(new ErgoToken(tokenId, numTokenInInputs - numTokenInOuts))
          outBoxes = outBoxes :+ changeBox.build()
        }
        val tx = txB.boxesToSpend(boxesToSpend.toList.asJava)
          .fee(feeAmount)
          .outputs(outBoxes: _*)
          .sendChangeTo(Address.create(changeAddress).getErgoAddress)
          .build()
        val signed = prover.sign(tx)
        transactions.add(signed)
        val txOutputs = signed.getOutputsToSpend
        if (txOutputs.size() > 2) boxesToSpend = Array(txOutputs.get(txOutputs.size() - 2))
      }
    }
    transactions.asScala.toList
  }

  def mergeBoxes(inputBoxes: Array[String], outBox: EndBox, secret: BigInteger, feeAmount: Long, changeAddress: String, sendTx: Boolean = true): SignedTransaction = {
    usingClient { implicit ctx =>
      val prover = ctx.newProverBuilder()
        .withDLogSecret(secret)
        .build()
      val inputs = ctx.getBoxesById(inputBoxes: _*)
      val txB = ctx.newTxBuilder()
      val output = {
        val outB = txB.outBoxBuilder()
          .contract(new ErgoTreeContract(outBox.receiverBoxScript))
          .value(outBox.value)
        if (outBox.tokens.nonEmpty) outB.tokens(outBox.tokens: _*).build()
        else outB.build()
      }
      val tx = txB.boxesToSpend(inputs.toList.asJava)
        .fee(feeAmount)
        .outputs(output)
        .sendChangeTo(Address.create(changeAddress).getErgoAddress)
        .build()
      val signed = prover.sign(tx)
      if (sendTx) ctx.sendTransaction(signed)
      return signed
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret.
fullMixBoxId is the boxId of the full-mix box to spend.
withdrawAddress is the address where the funds are to be sent.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  def spendFullMixBox(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, withdrawAddress: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, broadCast: Boolean) = {
    val ergoMix = ErgoMixCLIUtil.tokenErgoMix.get
    usingClient { implicit ctx =>
      val alice_or_bob = getProver(secret, isAlice)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      var tokens = Seq[ErgoToken]()
      fullMixBox.getTokens.forEach(token => {
        if (token.getId.toString != TokenErgoMix.tokenId) tokens = tokens :+ token
      })

      val endBox = EndBox(new Util().getAddress(withdrawAddress).script, Nil, if (inputBoxIds.nonEmpty) fullMixBox.getValue else fullMixBox.getValue - feeAmount, tokens)
      var outs = Seq(endBox)
      if (inputBoxIds.length > 0) { // there is fee box
        val feeBox = ctx.getBoxesById(inputBoxIds.head)(0)
        val feeCp = EndBox(ergoMix.feeEmissionContract.getErgoTree, feeBox.getRegisters.asScala, feeBox.getValue - feeAmount)
        outs = outs :+ feeCp
      }
      val tx: SignedTransaction = alice_or_bob.spendFullMixBox(FullMixBox(fullMixBox), outs, feeAmount, inputBoxIds, changeAddress, Nil, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(tx)
      tx
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret to generate a new half-mix box for remixing.
That is, perform the first step of the next round by behaving like Alice (by creating a new half-mix box)

fullMixBoxId is the boxId of the full-mix box to spend.
feeEmissionBoxId is the boxId of input boxes funding the transaction
The method attempts to create a transaction outputting a half-mix box at index 0.
   */
  def spendFullMixBox_RemixAsAlice(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, feeEmissionBoxId: String, feeAmount: Long): HalfMixTx = {
    usingClient { implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = new Util().addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString
      spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, Array(feeEmissionBoxId), feeAmount, feeEmissionBoxAddress, Seq(), broadCast = false)
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret to generate a new half-mix box for remixing.
That is, perform the first step of the next round by behaving like Alice (by creating a new half-mix box)

fullMixBoxId is the boxId of the full-mix box to spend.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */

  private def spendFullMixBox_RemixAsAlice(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], broadCast: Boolean): HalfMixTx = {
    usingClient { implicit ctx =>
      val alice_or_bob = getProver(secret, isAlice)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      val halfMixTx = alice_or_bob.spendFullMixBoxNextAlice(FullMixBox(fullMixBox), nextSecret.bigInteger, feeAmount, inputBoxIds, changeAddress, changeBoxRegs, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(halfMixTx.tx)
      halfMixTx
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret in another full-mix transaction that spends some half-mix box with this full-mix box.
That is, perform the first step of the next round by behaving like Bob (by creating a two new full-mix boxes)

fullMixBoxId is the boxId of the full-mix box to spend.
feeEmissionBoxId is the boxId of input boxes funding the transaction
The method attempts to create a transaction outputting a half-mix box at index 0.
   */
  def spendFullMixBox_RemixAsBob(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt,
                                 nextHalfMixBoxId: String, feeEmissionBoxId: String, feeAmount: Long): (FullMixTx, Boolean) = {
    usingClient { implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = new Util().addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString
      spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, nextHalfMixBoxId, Array(feeEmissionBoxId), feeAmount, feeEmissionBoxAddress, Seq(), broadCast = false)
    }
  }

  /*
Play Alice's or Bob's role in spending a full-mix box with secret in another full-mix transaction that spends some half-mix box with this full-mix box.
That is, perform the first step of the next round by behaving like Bob (by creating a two new full-mix boxes)

fullMixBoxId is the boxId of the full-mix box to spend.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  private def spendFullMixBox_RemixAsBob(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, nextHalfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], broadCast: Boolean): (FullMixTx, Boolean) = {
    usingClient { implicit ctx =>
      val alice_or_bob: FullMixBoxSpender = if (isAlice) new AliceImpl(secret.bigInteger) else new BobImpl(secret.bigInteger)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      val halfMixBox = ctx.getBoxesById(nextHalfMixBoxId)(0)
      val (fullMixTx, bit) = alice_or_bob.spendFullMixBoxNextBob(FullMixBox(fullMixBox), HalfMixBox(halfMixBox), nextSecret.bigInteger, feeAmount, inputBoxIds, changeAddress, changeBoxRegs, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(fullMixTx.tx)
      (fullMixTx, bit)
    }
  }
}
