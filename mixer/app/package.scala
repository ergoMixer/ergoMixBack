package app

import java.math.BigInteger

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit._
import sigmastate.Values.ErgoTree
import special.sigma.GroupElement

import scala.jdk.CollectionConverters._
import sigmastate.eval._

package object ergomix {

  import ErgoMix._

  trait MixTx

  case class HalfMixTx(tx:SignedTransaction)(implicit ergoMix: TokenErgoMix) extends MixTx {
    val getHalfMixBox: HalfMixBox = HalfMixBox(tx.getOutputsToSpend.get(0))
    require(getHalfMixBox.inputBox.getErgoTree == ergoMix.halfMixContract.getErgoTree)
  }

  case class FullMixTx(tx:SignedTransaction)(implicit ergoMix: TokenErgoMix) extends MixTx {
    val getFullMixBoxes: (FullMixBox, FullMixBox) = (FullMixBox(tx.getOutputsToSpend.get(0)), FullMixBox(tx.getOutputsToSpend.get(1)))
    require(getFullMixBoxes._1.inputBox.getErgoTree == ergoMix.fullMixScriptErgoTree)
    require(getFullMixBoxes._2.inputBox.getErgoTree == ergoMix.fullMixScriptErgoTree)
  }

  abstract class MixBox(inputBox: InputBox) {
    def getRegs = inputBox.getRegisters.asScala
    def getR4 = getRegs(0)
    def getR5 = getRegs(1)
    def getR6 = getRegs(2)
  }

  case class HalfMixBox(inputBox: InputBox) extends MixBox(inputBox) {
    def id = inputBox.getId.toString
    val gX: GroupElement = getR4.getValue match {
      case g:GroupElement => g
      case any => throw new Exception(s"Invalid value in R4: $any of type ${any.getClass}")
    }
  }

  case class FullMixBox(inputBox: InputBox) extends MixBox(inputBox) {
    def id = inputBox.getId.toString
    val (r4, r5, r6) = (getR4.getValue, getR5.getValue, getR6.getValue) match {
      case (c1:GroupElement, c2:GroupElement, gX:GroupElement) => (c1, c2, gX) //Values.GroupElementConstant(c1), Values.GroupElementConstant(c2)) => (c1, c2)
      case (r4, r5, r6) => throw new Exception(s"Invalid registers R4:$r4, R5:$r5, R6:$r6")
    }
  }

  case class EndBox(receiverBoxScript:ErgoTree, receiverBoxRegs:Seq[ErgoValue[_]] = Nil, value:Long, tokens: Seq[ErgoToken] = Nil) // box spending full mix box

  trait FullMixBoxSpender {
    def spendFullMixBox(f: FullMixBox, endBoxes: Seq[EndBox], feeAmount:Long, otherInputBoxIds:Array[String], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT])(implicit ctx:BlockchainContext): SignedTransaction =
      spendFullMixBox(f, endBoxes, feeAmount, ctx.getBoxesById(otherInputBoxIds:_*), changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples)

    def spendFullMixBox(f: FullMixBox, endBoxes: Seq[EndBox], feeAmount:Long, otherInputBoxes:Array[InputBox], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT]): SignedTransaction

    def spendFullMixBoxNextAlice(f: FullMixBox, nextX:BigInteger, feeAmount:Long, otherInputBoxes:Array[InputBox], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT])(implicit ctx: BlockchainContext): HalfMixTx = {
      implicit val ergoMix: TokenErgoMix = new TokenErgoMix(ctx)
      val fullBoxToken = f.inputBox.getTokens.get(0).getValue
      val endBox = EndBox(ergoMix.halfMixContract.getErgoTree, Seq(ErgoValue.of(g.exp(nextX))), f.inputBox.getValue, Seq(new ErgoToken(TokenErgoMix.tokenId, fullBoxToken - 1)))
      val signedTransaction = spendFullMixBox(f, Seq(endBox), feeAmount, otherInputBoxes, changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples)
      HalfMixTx(signedTransaction)
    }

    def spendFullMixBoxNextAlice(f: FullMixBox, nextX:BigInteger, feeAmount:Long, otherInputBoxIds:Array[String], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT])(implicit ctx: BlockchainContext): HalfMixTx =
      spendFullMixBoxNextAlice(f, nextX, feeAmount, ctx.getBoxesById(otherInputBoxIds:_*), changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples)

    def spendFullMixBoxNextBob(f: FullMixBox, h:HalfMixBox, nextY:BigInteger, feeAmount:Long, otherInputBoxIds:Array[String], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT])(implicit ctx: BlockchainContext): (FullMixTx, Boolean) =
      spendFullMixBoxNextBob(f, h, nextY, feeAmount, ctx.getBoxesById(otherInputBoxIds:_*), changeAddress, changeBoxRegs, additionalDlogSecrets, additionalDHTuples)

    def spendFullMixBoxNextBob(f: FullMixBox, h:HalfMixBox, nextY:BigInteger, feeAmount:Long, otherInputBoxes:Array[InputBox], changeAddress:String, changeBoxRegs:Seq[ErgoValue[_]], additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT])(implicit ctx: BlockchainContext): (FullMixTx, Boolean) = {
      implicit val ergoMix: TokenErgoMix = new TokenErgoMix(ctx)
      val distrToken: Long = (f.inputBox.getTokens.get(0).getValue + h.inputBox.getTokens.get(0).getValue - 1) / 2
      val inputBoxes: Array[InputBox] = h.inputBox +: otherInputBoxes
      val gX = h.gX
      val gY = g.exp(nextY)
      val gXY = gX.exp(nextY)
      val bit = scala.util.Random.nextBoolean()
      val (c1, c2) = if (bit) (gY, gXY) else (gXY, gY)
      val outBox1 = EndBox(ergoMix.fullMixScriptErgoTree, Seq(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(ergoMix.halfMixScriptHash)), f.inputBox.getValue, Seq(new ErgoToken(TokenErgoMix.tokenId, distrToken)))
      val outBox2 = EndBox(ergoMix.fullMixScriptErgoTree, Seq(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(ergoMix.halfMixScriptHash)), f.inputBox.getValue, Seq(new ErgoToken(TokenErgoMix.tokenId, distrToken)))
      val endBoxes = Seq(outBox1, outBox2)
      val tx: SignedTransaction = spendFullMixBox(f, endBoxes, feeAmount, inputBoxes, changeAddress, changeBoxRegs, additionalDlogSecrets, DHT(g, gX, gY, gXY, nextY) +: additionalDHTuples)
      val fullMixTx:FullMixTx = FullMixTx(tx)
      (fullMixTx, bit)
    }
  }

  case class DHT(gv:GroupElement, hv:GroupElement, uv:GroupElement, vv:GroupElement, x:BigInteger)

  trait HalfMixBoxSpender {
    def spendHalfMixBox(halfMixBox: HalfMixBox, inputBoxes:Array[InputBox], fee:Long, changeAddress:String, additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT], numToken: Int): (FullMixTx, Boolean)
    def spendHalfMixBox(halfMixBox: HalfMixBox, inputBoxIds:Array[String], fee:Long, changeAddress:String, additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT], numToken: Int)(implicit ctx:BlockchainContext): (FullMixTx, Boolean) =
      spendHalfMixBox(halfMixBox, ctx.getBoxesById(inputBoxIds: _*), fee, changeAddress, additionalDlogSecrets, additionalDHTuples, numToken)
  }

  trait HalfMixBoxCreator {
    def createHalfMixBox(inputBoxes:Array[InputBox], feeAmount:Long, changeAddress:String, additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT], poolAmount: Long, numToken: Int): HalfMixTx
    def createHalfMixBox(inputBoxIds:Array[String], feeAmount:Long, changeAddress:String, additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT], poolAmount: Long, numToken: Int)(implicit ctx:BlockchainContext): HalfMixTx =
      createHalfMixBox(ctx.getBoxesById(inputBoxIds:_*), feeAmount, changeAddress, additionalDlogSecrets, additionalDHTuples, poolAmount, numToken)
  }

  trait Bob extends FullMixBoxSpender with HalfMixBoxSpender

  trait Alice extends FullMixBoxSpender with HalfMixBoxCreator

  case class FeeEmissionBox(inputBox: InputBox, gZ:GroupElement, address:ErgoAddress)

  case class FeeEmissionTx(tx:SignedTransaction, gZ:GroupElement, address:ErgoAddress)(implicit ergoMix: ErgoMix) {
    val getFeeEmissionBox: FeeEmissionBox = FeeEmissionBox(tx.getOutputsToSpend.get(0), gZ, address)
//    require(getFeeEmissionBox.inputBox.getErgoTree == ergoMix.feeEmissionContract.getErgoTree)
//    require(getFeeEmissionBox.inputBox.getRegisters.get(0).getValue == gZ)
  }

  trait Carol {  // just a better name
    def createFeeEmissionBox(amount:Long, inputBoxes:Array[InputBox], feeAmount:Long, changeAddress:String,
                             additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT]):FeeEmissionTx
    def createFeeEmissionBox(amount:Long, inputBoxIds:Array[String], feeAmount:Long, changeAddress:String,
                             additionalDlogSecrets:Array[BigInteger], additionalDHTuples:Array[DHT])(implicit  ctx:BlockchainContext):FeeEmissionTx = {
      createFeeEmissionBox(amount, ctx.getBoxesById(inputBoxIds:_*), feeAmount, changeAddress,additionalDlogSecrets,additionalDHTuples)
    }
  }

}
