package mixinterface

import java.math.BigInteger
import models.Box.{EndBox, FullMixBox, HalfMixBox}
import models.Transaction.{FullMixTx, HalfMixTx}
import network.NetworkUtils
import config.MainConfigs
import org.ergoplatform.ErgoLikeTransaction
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.SignedTransactionImpl
import sigmastate.eval._
import special.sigma.GroupElement
import wallet.WalletHelper

import scala.collection.JavaConverters._

package object ErgoMixBase {

  trait FullMixBoxSpender {
    def spendFullMixBox(f: FullMixBox, endBoxes: Seq[EndBox], feeAmount: Long, otherInputBoxIds: Array[String], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], burnTokens: Seq[ErgoToken], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT])(implicit ctx: BlockchainContext): SignedTransaction =
      spendFullMixBox(f, endBoxes, feeAmount, ctx.getBoxesById(otherInputBoxIds: _*), changeAddress, changeBoxRegs, burnTokens, additionalDlogSecrets, additionalDHTuples)

    def getProver(f: FullMixBox): ErgoProver

    def spendFullMixBox(f: FullMixBox, endBoxes: Seq[EndBox], feeAmount: Long, otherInputBoxes: Array[InputBox], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], burnTokens: Seq[ErgoToken], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT]): SignedTransaction

    /**
     * mix as alice, will potentially sign twice if dynamicFee is set to true
     *
     * @param f                     full mix box
     * @param nextX                 next secret
     * @param feeAmount             fee amount
     * @param otherInputBoxes       other inputs like fee box
     * @param changeAddress         change address
     * @param changeBoxRegs         change address registers
     * @param additionalDlogSecrets secrets for spending inputs
     * @param additionalDHTuples    dh tuples
     * @param dynamicFee            whether to fix transaction fee/byte or not
     * @param ctx                   blockchain context
     * @return transaction which mixes as alice
     */
    def spendFullMixBoxNextAlice(f: FullMixBox, nextX: BigInteger, feeAmount: Long, otherInputBoxes: Array[InputBox], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT], dynamicFee: Boolean = true)(implicit ctx: BlockchainContext, networkUtils: NetworkUtils): HalfMixTx = {
      implicit val ergoMix: TokenErgoMix = networkUtils.tokenErgoMix.get
      val fullBoxToken = f.inputBox.getTokens.get(0).getValue
      val fullBoxBurnToken = new ErgoToken(TokenErgoMix.tokenId,1)
      var tokens = Seq(new ErgoToken(TokenErgoMix.tokenId, fullBoxToken - fullBoxBurnToken.getValue))
      if (f.inputBox.getTokens.size() > 1) tokens = tokens :+ f.inputBox.getTokens.get(1)
      val endBox = EndBox(ergoMix.halfMixContract.getErgoTree, Seq(ErgoValue.of(WalletHelper.g.exp(nextX))), f.inputBox.getValue, tokens)
      val feeCp = EndBox(ergoMix.feeEmissionContract.getErgoTree, otherInputBoxes(0).getRegisters.asScala, otherInputBoxes(0).getValue - feeAmount)
      val signedTransaction = spendFullMixBox(f, Seq(endBox, feeCp), feeAmount, otherInputBoxes, changeAddress, changeBoxRegs, Seq(fullBoxBurnToken), additionalDlogSecrets, additionalDHTuples)
      val expectedFee = ErgoLikeTransaction.serializer.toBytes(signedTransaction.asInstanceOf[SignedTransactionImpl].getTx).length * MainConfigs.dynamicFeeRate
      if (expectedFee != feeAmount && dynamicFee) {
        return spendFullMixBoxNextAlice(f, nextX, expectedFee, otherInputBoxes, changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples, dynamicFee = false)
      }
      HalfMixTx(signedTransaction)
    }

    def spendFullMixBoxNextAlice(f: FullMixBox, nextX: BigInteger, feeAmount: Long, otherInputBoxIds: Array[String], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT])(implicit ctx: BlockchainContext, networkUtils: NetworkUtils): HalfMixTx =
      spendFullMixBoxNextAlice(f, nextX, feeAmount, ctx.getBoxesById(otherInputBoxIds: _*), changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples)

    def spendFullMixBoxNextBob(f: FullMixBox, h: HalfMixBox, nextY: BigInteger, feeAmount: Long, otherInputBoxIds: Array[String], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT])(implicit ctx: BlockchainContext, networkUtils: NetworkUtils): (FullMixTx, Boolean) =
      spendFullMixBoxNextBob(f, h, nextY, feeAmount, ctx.getBoxesById(otherInputBoxIds: _*), changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples)

    /**
     * mix as bob, will potentially sign twice if dynamicFee is set to true
     *
     * @param f                     full-box
     * @param h                     half-box
     * @param nextY                 next secret
     * @param feeAmount             fee amount
     * @param otherInputBoxes       other inputs like fee-box
     * @param changeAddress         change address
     * @param changeBoxRegs         change address registers
     * @param additionalDlogSecrets secrets to spent inputs
     * @param additionalDHTuples    dh tuples
     * @param dynamicFee            whether to fix transaction fee/byte or not
     * @param ctx                   blockchain context
     * @return transaction which mixes as bob
     */
    def spendFullMixBoxNextBob(f: FullMixBox, h: HalfMixBox, nextY: BigInteger, feeAmount: Long, otherInputBoxes: Array[InputBox], changeAddress: String, changeBoxRegs: Seq[ErgoValue[_]], additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT], dynamicFee: Boolean = true)(implicit ctx: BlockchainContext, networkUtils: NetworkUtils): (FullMixTx, Boolean) = {
      implicit val ergoMix: TokenErgoMix = networkUtils.tokenErgoMix.get
      val totalInpDistroToken = f.inputBox.getTokens.get(0).getValue + h.inputBox.getTokens.get(0).getValue
      val distrToken: Long = (totalInpDistroToken - 1) / 2
      val distrBurnToken = new ErgoToken(TokenErgoMix.tokenId, totalInpDistroToken - (distrToken * 2))
      var tokens = Seq(new ErgoToken(TokenErgoMix.tokenId, distrToken))
      if (f.inputBox.getTokens.size() > 1) tokens = tokens :+ f.inputBox.getTokens.get(1)
      val inputBoxes: Array[InputBox] = h.inputBox +: otherInputBoxes
      val gX = h.gX
      val gY = WalletHelper.g.exp(nextY)
      val gXY = gX.exp(nextY)
      val bit = scala.util.Random.nextBoolean()
      val (c1, c2) = if (bit) (gY, gXY) else (gXY, gY)
      val outBox1 = EndBox(ergoMix.fullMixScriptErgoTree, Seq(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(ergoMix.halfMixScriptHash)), f.inputBox.getValue, tokens)
      val outBox2 = EndBox(ergoMix.fullMixScriptErgoTree, Seq(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(ergoMix.halfMixScriptHash)), f.inputBox.getValue, tokens)
      val feeCp = EndBox(ergoMix.feeEmissionContract.getErgoTree, otherInputBoxes(0).getRegisters.asScala, otherInputBoxes(0).getValue - feeAmount)
      val endBoxes = Seq(outBox1, outBox2, feeCp)
      val tx: SignedTransaction = spendFullMixBox(f, endBoxes, feeAmount, inputBoxes, changeAddress, changeBoxRegs, Seq(distrBurnToken), additionalDlogSecrets, DHT(WalletHelper.g, gX, gY, gXY, nextY) +: additionalDHTuples)
      val expectedFee = ErgoLikeTransaction.serializer.toBytes(tx.asInstanceOf[SignedTransactionImpl].getTx).length * MainConfigs.dynamicFeeRate
      if (expectedFee != feeAmount && dynamicFee) {
        return spendFullMixBoxNextBob(f, h, nextY, expectedFee, otherInputBoxes, changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples, dynamicFee = false)
      }
      val fullMixTx: FullMixTx = FullMixTx(tx)
      (fullMixTx, bit)
    }
  }

  case class DHT(gv: GroupElement, hv: GroupElement, uv: GroupElement, vv: GroupElement, x: BigInteger)

  trait HalfMixBoxSpender {
    def spendHalfMixBox(halfMixBox: HalfMixBox, inputBoxes: Array[InputBox], fee: Long, changeAddress: String, additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT], numToken: Int): (FullMixTx, Boolean)

    def spendHalfMixBox(halfMixBox: HalfMixBox, inputBoxIds: Array[String], fee: Long, changeAddress: String, additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT], numToken: Int)(implicit ctx: BlockchainContext): (FullMixTx, Boolean) =
      spendHalfMixBox(halfMixBox, ctx.getBoxesById(inputBoxIds: _*), fee, changeAddress, additionalDlogSecrets, additionalDHTuples, numToken)
  }

  trait HalfMixBoxCreator {
    def createHalfMixBox(inputBoxes: Array[InputBox], feeAmount: Long, changeAddress: String, additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT], poolAmount: Long, numToken: Int, mixingTokenId: String, mixingTokenAmount: Long): HalfMixTx

    def createHalfMixBox(inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT], poolAmount: Long, numToken: Int, mixingTokenId: String, mixingTokenAmount: Long)(implicit ctx: BlockchainContext): HalfMixTx =
      createHalfMixBox(ctx.getBoxesById(inputBoxIds: _*), feeAmount, changeAddress, additionalDlogSecrets, additionalDHTuples, poolAmount, numToken, mixingTokenId, mixingTokenAmount)
  }

  trait Bob extends FullMixBoxSpender with HalfMixBoxSpender

  trait Alice extends FullMixBoxSpender with HalfMixBoxCreator

}
