package app

import org.ergoplatform.appkit._
import org.ergoplatform.{ErgoAddress, ErgoAddressEncoder}
import scorex.crypto.hash.Digest32
import sigmastate.Values.ErgoTree
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement

object TokenErgoMix {
  val feeAmount: Long = Configs.feeAmount
  val mixerOwner: Address = Address.create("9hoR2npAUVwRGWEa6z1wpeiUAmpCnegCwPVVGZEnZ6Q8w8eeZJb")
  val tokenId: String = "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98"
  def getHash(bytes:Array[Byte]) = scorex.crypto.hash.Blake2b256(bytes)
  val g: GroupElement = CryptoConstants.dlogGroup.generator
  def hexToGroupElement(hex:String):GroupElement = {
    JavaHelpers.decodeStringToGE(hex)
  }
}

import app.ErgoMix._

class TokenErgoMix(ctx:BlockchainContext) {
  // TODO No need to have scripts here, better to replace them with ergoTree before release
  // at the very least having ergo trees instead of actual scripts will have the safety that one can not easily
  // interfere with logic and cause problems (for himself at least)!
  // also cleaner and more efficient.
  val fullMixErgoTree: String = ""
  val halfMixErgoTree: String = ""
  val feeEmissionErgoTree: String = ""
  val tokenEmissionErgoTree: String = ""
  val addressEncoder = new ErgoAddressEncoder(ctx.getNetworkType.networkPrefix)

  val fullMixScript: String =
    """
      |{
      |  val g = groupGenerator
      |  val c1 = SELF.R4[GroupElement].get
      |  val c2 = SELF.R5[GroupElement].get
      |  val gX = SELF.R6[GroupElement].get
      |  val delta = SELF.R7[Coll[Byte]].get
      |
      |  val isHalf = {(b: Box) => blake2b256(b.propositionBytes) == delta && b.value == SELF.value}
      |  val isFull = {(b: Box) => blake2b256(b.propositionBytes) == blake2b256(SELF.propositionBytes) &&
      |                            b.R7[Coll[Byte]].get == delta && b.value == SELF.value
      |               }
      |  val rightToken = {(b:Box) => b.tokens(0)._1 == tokenId && b.tokens(0)._2 >= 1}
      |  val noTokenInBox = {(b:Box) => b.tokens.size == 0}
      |
      |  val destroyToken = OUTPUTS.forall(noTokenInBox)
      |  val nextBob = isHalf(INPUTS(0)) && INPUTS(1).id == SELF.id &&
      |                INPUTS(1).tokens(0)._1 == tokenId
      |  val nextAlice = isHalf(OUTPUTS(0)) && INPUTS(0).id == SELF.id
      |  val nextAliceLogic = rightToken(OUTPUTS(0)) && OUTPUTS(0).tokens(0)._2 == INPUTS(0).tokens(0)._2 - 1
      |
      |  (proveDlog(c2) || proveDHTuple(g, c1, gX, c2)) && {
      |    // spends full-box either as alice or bob, or getting out of mixing
      |    sigmaProp((nextAlice && nextAliceLogic) || nextBob || destroyToken)
      |  }
      |}
      |""".stripMargin

  val halfMixScript: String =
    """
      |{
      |  val g = groupGenerator
      |  val gX = SELF.R4[GroupElement].get
      |  val noTokenBox = {(b:Box) => b.tokens.size == 0}
      |  {proveDlog(gX) && sigmaProp(OUTPUTS.forall(noTokenBox))} ||
      |  {
      |    val c1 = OUTPUTS(0).R4[GroupElement]
      |    if (c1.isDefined && INPUTS.size > 1) {
      |      val rightToken = {(b: Box) => b.tokens(0)._1 == tokenId}
      |      val isFull = {(b: Box) => blake2b256(b.propositionBytes) == fullMixScriptHash && b.R7[Coll[Byte]].get == blake2b256(SELF.propositionBytes) && b.value == SELF.value}
      |
      |      val c2 = OUTPUTS(0).R5[GroupElement].get
      |      val u1 = OUTPUTS(0).R6[GroupElement].get
      |      val u2 = OUTPUTS(1).R6[GroupElement].get
      |      val in0 = if(rightToken(INPUTS(0))) INPUTS(0).tokens(0)._2
      |                else 0L
      |      val in1 = if(INPUTS(1).tokens.size > 0 && INPUTS(1).tokens(0)._1 == tokenId) INPUTS(1).tokens(0)._2
      |                else 0L
      |      val out0 = if(rightToken(OUTPUTS(0))) OUTPUTS(0).tokens(0)._2
      |                 else 0L
      |      val out1 = if(OUTPUTS(1).tokens.size > 0 && rightToken(OUTPUTS(1))) OUTPUTS(1).tokens(0)._2
      |                 else 0L
      |
      |      val fullInInput = INPUTS.exists(isFull)
      |      val mixLogic = isFull(OUTPUTS(0)) && isFull(OUTPUTS(1))
      |      val bobFullTokenLogic = in0 >= 1 && in1 >= 1 && out0 == out1 && in0 + in1 >= out0 + out1 + 1 && in0 + in1 <= out0 + out1 + 2 // burn either one token or two (depending on being odd or even)
      |      val bobFullLogic = fullInInput && bobFullTokenLogic // could not check isFull(INPUTS(1)) because of consting issue
      |      val bobEntranceTokenLogic = out0 + out1 > in0 && out0 == out1
      |      val bobEntranceLogic = bobEntranceTokenLogic && fullInInput == false // bob trying to enter, should increase sum of tokens at the very least
      |
      |      // two use cases, either bob is entering to mix, or using a full-box for remix
      |      sigmaProp((bobEntranceLogic || bobFullLogic) && mixLogic &&
      |      u1 == gX && u2 == gX &&
      |      OUTPUTS(1).R4[GroupElement].get == c2 &&
      |      OUTPUTS(1).R5[GroupElement].get == c1.get &&
      |      SELF.id == INPUTS(0).id && c1.get != c2) && {
      |        proveDHTuple(g, gX, c1.get, c2) ||
      |        proveDHTuple(g, gX, c2, c1.get)
      |      }
      |    } else {
      |      sigmaProp(false)
      |    }
      |  }
      |}
      |""".stripMargin


  val tokenEmissionScript: String =
    """
      |{
      |  val batchPrices = SELF.R8[Coll[(Int, Long)]]
      |  val commissionRate = SELF.R9[Int]
      |  val commission = if (commissionRate.isDefined && commissionRate.get < 1000000) OUTPUTS(0).value / commissionRate.get
      |                   else 0L
      |  val isFull = {(b: Box) => blake2b256(b.propositionBytes) == fullMixScriptHash && b.R7[Coll[Byte]].get == halfMixScriptHash}
      |  val isHalf = {(b: Box) => blake2b256(b.propositionBytes) == halfMixScriptHash}
      |  val isCopy = {(b: Box) => b.propositionBytes == SELF.propositionBytes &&
      |                            b.R8[Coll[(Int, Long)]] == batchPrices &&
      |                            b.R9[Int] == commissionRate &&
      |                            b.tokens(0)._1 == tokenId &&
      |                            batchPrices.get.exists({(batch: (Int, Long)) => {
      |                              b.tokens(0)._2 >= SELF.tokens(0)._2 - batch._1 &&
      |                              b.value >= SELF.value + batch._2 + commission
      |                            }})}
      |  val noToken = {(b: Box) => b.tokens.size == 0}
      |  val aliceBuying = (isHalf(OUTPUTS(0)) && isCopy(OUTPUTS(1)) && OUTPUTS.slice(2, OUTPUTS.size).forall(noToken))
      |  val bobBuying = (isFull(OUTPUTS(0)) && isFull(OUTPUTS(1)) && isCopy(OUTPUTS(2)) && OUTPUTS.slice(3, OUTPUTS.size).forall(noToken))
      |  val asBuyer = (aliceBuying || bobBuying) && INPUTS.filter({(b: Box) => b.propositionBytes == SELF.propositionBytes}).size == 1
      |
      |  sigmaProp(asBuyer) || mixerOwner
      |}
      |""".stripMargin
  val feeEmissionScript: String =
    """
      |{
      |  // In this contract burning valid token requirement is not checked, it is handled in half and full boxes scripts
      |  // skipped checking R7 register because of costing issue, it seems its not important here
      |  val isFull = {(b: Box) => blake2b256(b.propositionBytes) == fullMixScriptHash}
      |  val isHalf = {(b: Box) => blake2b256(b.propositionBytes) == halfMixScriptHash}
      |  val isCopy = {(b: Box) => b.propositionBytes == SELF.propositionBytes &&
      |                            b.value >= SELF.value - fee
      |               }
      |  val asAlice = INPUTS.size == 2 && OUTPUTS.size == 3 && isHalf(OUTPUTS(0)) &&
      |                blake2b256(INPUTS(0).propositionBytes) == fullMixScriptHash
      |  val asBob = INPUTS.size == 3 && OUTPUTS.size == 4 && isHalf(INPUTS(0)) &&
      |              blake2b256(INPUTS(1).propositionBytes) == fullMixScriptHash
      |
      |  sigmaProp((asAlice || asBob) && OUTPUTS.exists(isCopy)) || mixerOwner
      |}
      |""".stripMargin

  val fullMixScriptContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create().item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes).build(),
    fullMixScript
  )
  val fullMixScriptErgoTree: ErgoTree = fullMixScriptContract.getErgoTree
  val fullMixAddress: ErgoAddress = addressEncoder.fromProposition(fullMixScriptErgoTree).get
  val fullMixScriptHash: Digest32 = getHash(fullMixScriptErgoTree.bytes)

  val halfMixContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
      .item("fullMixScriptHash", fullMixScriptHash)
      .build(),
    halfMixScript
  )
  val halfMixScriptHash: Digest32 = getHash(halfMixContract.getErgoTree.bytes)
  val halfMixAddress: ErgoAddress = addressEncoder.fromProposition(halfMixContract.getErgoTree).get

  val feeEmissionContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("fullMixScriptHash", fullMixScriptHash)
      .item("halfMixScriptHash", halfMixScriptHash)
      .item("fee", feeAmount)
      .item("mixerOwner", TokenErgoMix.mixerOwner.getPublicKey)
      .build(),
    feeEmissionScript
  )
  val feeEmissionAddress: ErgoAddress = addressEncoder.fromProposition(feeEmissionContract.getErgoTree).get

  val tokenEmissionContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
      .item("fullMixScriptHash", fullMixScriptHash)
      .item("halfMixScriptHash", halfMixScriptHash)
      .item("mixerOwner", TokenErgoMix.mixerOwner.getPublicKey)
      .build(),
    tokenEmissionScript
  )
  val tokenEmissionAddress: ErgoAddress = addressEncoder.fromProposition(tokenEmissionContract.getErgoTree).get
}
