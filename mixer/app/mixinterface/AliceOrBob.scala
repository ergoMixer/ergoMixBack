package mixinterface


import java.math.BigInteger

import mixinterface.ErgoMixBase._
import javax.inject.{Inject, Singleton}
import network.NetworkUtils
import org.ergoplatform.appkit._
import models.Models.{EndBox, FullMixBox, FullMixTx, HalfMixBox, HalfMixTx}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import wallet.WalletHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

@Singleton
class AliceOrBob @Inject()(implicit val networkUtils: NetworkUtils) {

  import networkUtils._

  /**
   * Withdraws boxes, used for half-box and deposits.
   *
   * @param inputBox          input to spend
   * @param feeBox            fee box to use as fee
   * @param withdrawAddress   withdraw address
   * @param proverDlogSecrets secrets for spending input
   * @param feeAmount         fee amount
   * @param broadCast         broadcast the tx or just return
   * @return
   */
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

  /**
   * distributes ergs or tokens to initiate mixing
   *
   * @param inputBoxes        input boxes
   * @param outputs           outputs (each will enter mixing)
   * @param proverDlogSecrets secrets to sepend inputs
   * @param feeAmount         fee amount
   * @param changeAddress     change address
   * @param outLimit          max number of outputs, will break into multiple txs if reaches this limit)
   * @return txs that contain outputs
   */
  def distribute(inputBoxes: Array[String], outputs: Array[EndBox], proverDlogSecrets: Array[BigInteger], feeAmount: Long, changeAddress: String, outLimit: Int): List[SignedTransaction] = {
    val transactions = new java.util.ArrayList[SignedTransaction]()
    usingClient { implicit ctx =>
      val prover: ErgoProver = proverDlogSecrets.foldLeft(ctx.newProverBuilder())(
        (proverBuilder, bigInteger) => proverBuilder.withDLogSecret(bigInteger)
      ).build()
      var boxesToSpend = ctx.getBoxesById(inputBoxes: _*)
      val numTxs = (outputs.length + outLimit - 1) / outLimit
      for (i <- 0 until numTxs) {
        val txB = ctx.newTxBuilder()
        val inTokens = mutable.Map.empty[String, Long]
        boxesToSpend.foreach(box => {
          box.getTokens.asScala.foreach(tok => {
            inTokens(tok.getId.toString) = inTokens.getOrElse(tok.getId.toString, 0L) + tok.getValue
          })
        })
        val outTokens = mutable.Map.empty[String, Long]
        var outBoxes = outputs.slice(i * outLimit, i * outLimit + outLimit).map(out => {
          out.tokens.foreach(tok => {
            outTokens(tok.getId.toString) = outTokens.getOrElse(tok.getId.toString, 0L) + tok.getValue
          })
          val outB = txB.outBoxBuilder()
            .contract(new ErgoTreeContract(out.receiverBoxScript))
            .value(out.value)
          if (out.tokens.nonEmpty) outB.tokens(out.tokens: _*).build()
          else outB.build()
        })
        val remErg = boxesToSpend.map(_.getValue.toLong).sum - outBoxes.map(_.getValue).sum - feeAmount
        if (remErg > 0) {
          val tokens = inTokens.filter(tok => tok._2 - outTokens.getOrElse(tok._1, 0L) > 0)
            .map(tok => new ErgoToken(tok._1, tok._2 - outTokens.getOrElse(tok._1, 0L)))
          var changeBox = txB.outBoxBuilder()
            .value(remErg)
            .contract(new ErgoTreeContract(Address.create(changeAddress).getErgoAddress.script))
          if (tokens.nonEmpty) changeBox = changeBox.tokens(tokens.toSeq: _*)
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

  /**
   * merges boxes. is used in covert mixing when there are a lot of inputs
   *
   * @param inputBoxes    boxes to be merged
   * @param outBox        merged box
   * @param secret        secret to spend inputs
   * @param feeAmount     fee amount
   * @param changeAddress change address
   * @param sendTx        whether to broadcast or not
   * @return
   */
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

  /**
   * spends a full-box, used in withdrawing in full-box mode
   *
   * @param isAlice         whether full-box is of type alice or bob
   * @param secret          secret associated with this round of mixing
   * @param fullMixBoxId    full-box id
   * @param withdrawAddress address to withdraw to
   * @param inputBoxIds     other inputs (like fee box)
   * @param feeAmount       fee amount
   * @param changeAddress   change address
   * @param broadCast       whether to broadcast or just return
   * @return tx withdrawing the full-box
   */
  def spendFullMixBox(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, withdrawAddress: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, broadCast: Boolean) = {
    val ergoMix = tokenErgoMix.get
    usingClient { implicit ctx =>
      val alice_or_bob = getProver(secret, isAlice)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      var tokens = Seq[ErgoToken]()
      fullMixBox.getTokens.forEach(token => {
        if (token.getId.toString != TokenErgoMix.tokenId) tokens = tokens :+ token
      })

      val endBox = EndBox(WalletHelper.getAddress(withdrawAddress).script, Nil, if (inputBoxIds.nonEmpty) fullMixBox.getValue else fullMixBox.getValue - feeAmount, tokens)
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

  /**
   * remixes as alice, basically converts a full-box to a half box
   *
   * @param isAlice          whether the input full-box is of type alice or bob
   * @param secret           secret associated with this round of mixing
   * @param fullMixBoxId     full-box id
   * @param nextSecret       next round's secret
   * @param feeEmissionBoxId fee box
   * @param feeAmount        fee amount
   * @return tx converting full-box to a half-box
   */
  def spendFullMixBox_RemixAsAlice(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, feeEmissionBoxId: String, feeAmount: Long): HalfMixTx = {
    usingClient { implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = WalletHelper.addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString
      spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, Array(feeEmissionBoxId), feeAmount, feeEmissionBoxAddress, Seq(), broadCast = false)
    }
  }

  /**
   * is basically used by spendFullMixBox_RemixAsAlice
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

  /**
   * remixes as alice, basically converts a full-box to a half box
   *
   * @param isAlice          whether the input full-box is of type alice or bob
   * @param secret           secret associated with this round of mixing
   * @param fullMixBoxId     full-box id
   * @param nextSecret       next round's secret
   * @param halfBoxId        a random half-box used for mixing
   * @param feeEmissionBoxId fee box
   * @param feeAmount        fee amount
   * @return tx spending the half-box and the full-box and generate two full-boxes
   */
  def spendFullMixBox_RemixAsBob(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt,
                                 halfBoxId: String, feeEmissionBoxId: String, feeAmount: Long): (FullMixTx, Boolean) = {
    usingClient { implicit ctx =>
      val feeEmissionBox = ctx.getBoxesById(feeEmissionBoxId)(0)
      val feeEmissionBoxAddress = WalletHelper.addressEncoder.fromProposition(feeEmissionBox.getErgoTree).get.toString
      spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, halfBoxId, Array(feeEmissionBoxId), feeAmount, feeEmissionBoxAddress, Seq(), broadCast = false)
    }
  }

  /**
   * is basically used by spendFullMixBox_RemixAsBob
   */
  private def spendFullMixBox_RemixAsBob(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, nextSecret: BigInt, nextHalfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], broadCast: Boolean): (FullMixTx, Boolean) = {
    usingClient { implicit ctx =>
      val alice_or_bob: FullMixBoxSpender = if (isAlice) new AliceImpl(secret.bigInteger, tokenErgoMix.get) else new BobImpl(secret.bigInteger, tokenErgoMix.get)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      val halfMixBox = ctx.getBoxesById(nextHalfMixBoxId)(0)
      val (fullMixTx, bit) = alice_or_bob.spendFullMixBoxNextBob(FullMixBox(fullMixBox), HalfMixBox(halfMixBox), nextSecret.bigInteger, feeAmount, inputBoxIds, changeAddress, changeBoxRegs, Array[BigInteger](), Array[DHT]())
      if (broadCast) ctx.sendTransaction(fullMixTx.tx)
      (fullMixTx, bit)
    }
  }

  /**
   * enters mixing as bob, i.e. spends a half-box and generates two full-boxes
   *
   * @param y                 secret for the first round
   * @param halfMixBoxId      half-box the will be spend
   * @param inputBoxIds       other inputs of the tx (token emission box, deposit box)
   * @param feeAmount         fee amount
   * @param changeAddress     change address
   * @param proverDlogSecrets secrets for spending inputs
   * @param broadCast         whether to broadcast or just return
   * @param numToken          number of mixing tokens, i.e. mixing level
   * @return tx spending half-box and inputs and enter mixing as bob
   */
  def spendHalfMixBox(y: BigInt, halfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean, numToken: Int = 0) = {
    usingClient { implicit ctx =>
      val bob = new BobImpl(y.bigInteger, tokenErgoMix.get)
      val halfMixBox: InputBox = ctx.getBoxesById(halfMixBoxId)(0)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val (fullMixTx, bit) = bob.spendHalfMixBox(HalfMixBox(halfMixBox), inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT](), numToken)
      if (broadCast) ctx.sendTransaction(fullMixTx.tx)
      (fullMixTx, bit)
    }
  }

  /**
   * Creates a half-box for entering mix as alice
   *
   * @param x                 the secret
   * @param inputBoxIds       input boxes
   * @param feeAmount         fee
   * @param changeAddress     change address
   * @param proverDlogSecrets DLog secrets
   * @param broadCast         broadcast the tx or just return
   * @param poolAmount        mixing ring
   * @param numToken          number of mixing token (mix level)
   * @param mixingTokenId     if of mixing token (if it is a token mixing mix)
   * @param mixingTokenAmount number mixing token
   * @return the transaction in which half-box is created
   */
  def createHalfMixBox(x: BigInt, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String,
                       proverDlogSecrets: Array[String], broadCast: Boolean, poolAmount: Long, numToken: Int = 0,
                       mixingTokenId: String, mixingTokenAmount: Long) = {
    usingClient { implicit ctx =>
      val alice = new AliceImpl(x.bigInteger, tokenErgoMix.get)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val tx = alice.createHalfMixBox(inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT](), poolAmount, numToken, mixingTokenId, mixingTokenAmount)
      if (broadCast) ctx.sendTransaction(tx.tx)
      tx
    }
  }

  /**
   * @param secret  secret associated with mix round
   * @param isAlice was box created as alice or bob
   * @param ctx     blockchain context
   * @return Alic or Bob implementation based on isAlice
   */
  def getProver(secret: BigInt, isAlice: Boolean)(implicit ctx: BlockchainContext): mixinterface.ErgoMixBase.FullMixBoxSpender = {
    if (isAlice) new AliceImpl(secret.bigInteger, tokenErgoMix.get)
    else new BobImpl(secret.bigInteger, tokenErgoMix.get)
  }

}
