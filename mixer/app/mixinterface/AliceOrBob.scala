package mixinterface


import java.math.BigInteger
import io.circe.Json
import mixinterface.ErgoMixBase._
import config.MainConfigs

import javax.inject.{Inject, Singleton}
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit._
import models.Models.TokenMap
import models.Box.{EndBox, FullMixBox, HalfMixBox}
import models.Transaction.{FullMixTx, HalfMixTx}
import scorex.util.encode.Base64
import wallet.WalletHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

@Singleton
class AliceOrBob @Inject()(implicit val networkUtils: NetworkUtils, explorer: BlockExplorer) {

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
      var distrBurnTokenAmount: Long = 0
      input.getTokens.forEach(token => {
        if (token.getId.toString == TokenErgoMix.tokenId) distrBurnTokenAmount += token.getValue
        else tokens.add(token)
      })
      var outputBuilder = txC.outBoxBuilder()
        .contract(ctx.newContract(Address.create(withdrawAddress).getErgoAddress.script))
      if (!tokens.isEmpty) outputBuilder = outputBuilder.tokens(tokens.asScala: _*)
      if (feeBox.nonEmpty && distrBurnTokenAmount > 0) {
        outputs.add(outputBuilder.value(input.getValue).build())
        val fee = ctx.getBoxesById(feeBox.get)(0)
        inputs.add(fee)
        outputs.add(txC.outBoxBuilder()
          .contract(ctx.newContract(fee.getErgoTree))
          .value(fee.getValue - feeAmount)
          .registers(fee.getRegisters.asScala: _*)
          .build())
      } else outputs.add(outputBuilder.value(input.getValue - feeAmount).build())

      val distrBurnToken = new ErgoToken(TokenErgoMix.tokenId, distrBurnTokenAmount)
      val preTxB = txC
        .addInputs(inputs.asScala: _*)
        .addOutputs(outputs.asScala: _*)
        .sendChangeTo(Address.create(withdrawAddress))
        .fee(feeAmount)
      val uTx = if (distrBurnTokenAmount > 0) preTxB.tokensToBurn(distrBurnToken).build() else preTxB.build()
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
            .contract(ctx.newContract(out.receiverBoxScript))
            .value(out.value)
          if (out.tokens.nonEmpty) outB.tokens(out.tokens: _*).build()
          else outB.build()
        })
        val remErg = boxesToSpend.map(_.getValue).sum - outBoxes.map(_.getValue).sum - feeAmount
        if (remErg > 0) {
          val tokens = inTokens.filter(tok => tok._2 - outTokens.getOrElse(tok._1, 0L) > 0)
            .map(tok => new ErgoToken(tok._1, tok._2 - outTokens.getOrElse(tok._1, 0L)))
          var changeBox = txB.outBoxBuilder()
            .value(remErg)
            .contract(ctx.newContract(Address.create(changeAddress).getErgoAddress.script))
          if (tokens.nonEmpty) changeBox = changeBox.tokens(tokens.toSeq: _*)
          outBoxes = outBoxes :+ changeBox.build()
        }
        val tx = txB.addInputs(boxesToSpend: _*)
          .addOutputs(outBoxes: _*)
          .fee(feeAmount)
          .sendChangeTo(Address.create(changeAddress))
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
   * sign a mint transaction and broadcast tx
   * @param reducedTransaction mint transaction
   * @param inputBoxId
   * @param secret
   * @param isAlice
   * @param sendTx if false doesn't send transaction
   * @return signed transaction
   */
  def mint(reducedTransaction: String, inputBoxId: String, secret: BigInteger, isAlice: Boolean, sendTx: Boolean): SignedTransaction = {
    usingClient { implicit ctx =>
      val ourInBox = ctx.getBoxesById(inputBoxId)
      val prover = getProver(secret, isAlice).getProver(FullMixBox(ourInBox.last))
      val signed = prover.signReduced(ctx.parseReducedTransaction(Base64.decode(reducedTransaction).get), 0)
      if (sendTx) ctx.sendTransaction(signed)
      return signed
    }
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
          .contract(ctx.newContract(outBox.receiverBoxScript))
          .value(outBox.value)
        if (outBox.tokens.nonEmpty) outB.tokens(outBox.tokens: _*).build()
        else outB.build()
      }
      val tx = txB.addInputs(inputs: _*)
        .fee(feeAmount)
        .addOutputs(output)
        .sendChangeTo(Address.create(changeAddress))
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
  def spendFullMixBox(isAlice: Boolean, secret: BigInt, fullMixBoxId: String, withdrawAddress: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, broadCast: Boolean): SignedTransaction = {
    val ergoMix = tokenErgoMix.get
    usingClient { implicit ctx =>
      val alice_or_bob = getProver(secret, isAlice)
      val fullMixBox: InputBox = ctx.getBoxesById(fullMixBoxId)(0)
      var tokens = Seq[ErgoToken]()
      var distrBurnTokenAmount: Long = 0
      fullMixBox.getTokens.forEach(token => {
        if (token.getId.toString == TokenErgoMix.tokenId) distrBurnTokenAmount = distrBurnTokenAmount + token.getValue
        else tokens = tokens :+ token
      })
      val distrBurnToken = new ErgoToken(TokenErgoMix.tokenId, distrBurnTokenAmount)
      val endBox = EndBox(WalletHelper.getAddress(withdrawAddress).script, Nil, if (inputBoxIds.nonEmpty) fullMixBox.getValue else fullMixBox.getValue - feeAmount, tokens)
      var outs = Seq(endBox)
      if (inputBoxIds.length > 0) { // there is fee box
        val feeBox = ctx.getBoxesById(inputBoxIds.head)(0)
        val feeCp = EndBox(ergoMix.feeEmissionContract.getErgoTree, feeBox.getRegisters.asScala, feeBox.getValue - feeAmount)
        outs = outs :+ feeCp
      }
      val tx: SignedTransaction = alice_or_bob.spendFullMixBox(FullMixBox(fullMixBox), outs, feeAmount, inputBoxIds, changeAddress, Nil, Seq(distrBurnToken), Array[BigInteger](), Array[DHT]())
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
      val feeEmissionBoxAddress = WalletHelper.getAddress(feeEmissionBox.getErgoTree).toString
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
      val feeEmissionBoxAddress = WalletHelper.getAddress(feeEmissionBox.getErgoTree).toString
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
  def spendHalfMixBox(y: BigInt, halfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean, numToken: Int = 0): (FullMixTx, Boolean) = {
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
                       mixingTokenId: String, mixingTokenAmount: Long): HalfMixTx = {
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

  /**
   * spends hop box (withdrawing the box or send it to next hop)
   */
  def spendHopBox(proverDlogSecret: BigInteger, inputId: String, destinationAddress: String): SignedTransaction = {
    usingClient { implicit ctx =>
      val prover: ErgoProver = ctx.newProverBuilder().withDLogSecret(proverDlogSecret).build()

      val inputs = new java.util.ArrayList[InputBox]()
      val input = ctx.getBoxesById(inputId)(0)

      if (!input.getTokens.isEmpty) throw new Exception("Weird case happened: hopBox containing token. Aborting hopping transaction generation...")
      inputs.add(input)

      val txC = ctx.newTxBuilder()

      val nextHopBox = txC.outBoxBuilder()
        .value(input.getValue - MainConfigs.distributeFee)
        .contract(ctx.newContract(Address.create(destinationAddress).getErgoAddress.script))
        .build()

      val uTx = txC
        .addInputs(inputs.asScala: _*)
        .addOutputs(nextHopBox)
        .fee(MainConfigs.distributeFee)
        .sendChangeTo(Address.create(destinationAddress))
        .build()
      val tx = prover.sign(uTx)
      tx
    }
  }

  /**
   * withdraw tokens from covert
   */
  def withdrawToken(proverDlogSecrets: BigInteger, inputIds: Seq[String], tokenIds: Seq[String], withdrawAddress: String, depositAddress: String): SignedTransaction = {
    usingClient { implicit ctx =>
      val prover: ErgoProver = Array(proverDlogSecrets).foldLeft(ctx.newProverBuilder())(
        (proverBuilder, bigInteger) => proverBuilder.withDLogSecret(bigInteger)
      ).build()

      val inputs = new java.util.ArrayList[InputBox]()
      var inputErgValues: Long = 0
      val withdrawBoxTokensMap = new TokenMap()
      var outBoxCount = 1

      // extract inputBoxes tokens
      // split them to tokens to remain in covert and token to withdraw
      inputIds.foreach(inputId => {
        val input = ctx.getBoxesById(inputId)(0)
        inputErgValues = inputErgValues + input.getValue
        input.getTokens.forEach(token => {
          if (tokenIds.contains(token.getId.toString)) withdrawBoxTokensMap.add(token)
          else outBoxCount = 2
        })
        inputs.add(input)
      })

      // if there is not enough Erg in inputBoxes, get more box
      if (inputErgValues < MainConfigs.distributeFee + outBoxCount * MainConfigs.minPossibleErgInBox) {
        val allBoxes = explorer.getUnspentBoxes(depositAddress)
        allBoxes.foreach(box => {
          // while erg is not enough, add box to input
          if (inputErgValues < MainConfigs.distributeFee + outBoxCount * MainConfigs.minPossibleErgInBox && !inputIds.contains(box.id)) {
            val input = ctx.getBoxesById(box.id)(0)
            inputErgValues = inputErgValues + input.getValue
            // split box tokens
            if (!input.getTokens.isEmpty) outBoxCount = 2
            inputs.add(input)
          }
        })
        // throw exception if there is not enough erg to generate transaction
        if (inputErgValues < MainConfigs.distributeFee + outBoxCount * MainConfigs.minPossibleErgInBox) throw new Exception(s"Not enough erg. current value: $inputErgValues, required: ${MainConfigs.distributeFee + outBoxCount * MainConfigs.minPossibleErgInBox}")
      }
      val txC = ctx.newTxBuilder()

      val withdrawBox = txC.outBoxBuilder()
        .value(MainConfigs.minPossibleErgInBox)
        .contract(ctx.newContract(Address.create(withdrawAddress).getErgoAddress.script))
        .tokens(withdrawBoxTokensMap.toJavaArray.asScala: _*)
        .build()

      val uTx = txC
        .addInputs(inputs.asScala: _*)
        .addOutputs(withdrawBox)
        .fee(MainConfigs.distributeFee)
        .sendChangeTo(Address.create(depositAddress))
        .build()
      val tx = prover.sign(uTx)
      tx
    }
  }


}
