package mixinterface

import org.ergoplatform.appkit._
import org.ergoplatform.ErgoAddress
import scorex.crypto.hash.Digest32
import sigmastate.Values.ErgoTree
import wallet.WalletHelper.{getErgoAddress, getHash}

object TokenErgoMix {
  val paramAddress: Address  = Address.create("9hUjrNWLTXBU4qkGSA6ssCG8Fe7WpPKT5HW4E5zUr3YJ1HSo1rB")
  val mixerOwner: Address    = Address.create("9hoR2npAUVwRGWEa6z1wpeiUAmpCnegCwPVVGZEnZ6Q8w8eeZJb")
  val mixerIncome: Address   = Address.create("9f4bRuh6yjhz4wWuz75ihSJwXHrtGXsZiQWUaHSDRf3Da16dMuf")
  val stealthIncome: Address = Address.create("9fDoTdtZ8YaM8wdQCMjtKaVWGJqAE2WPrGYZqe1VV6JHyd3ymBj")
  val tokenId: String        = "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98"
}

class TokenErgoMix(ctx: BlockchainContext) {
  val fullMixScript: String =
    """
      |{
      |  val g = groupGenerator
      |  val c1 = SELF.R4[GroupElement].get
      |  val c2 = SELF.R5[GroupElement].get
      |  val gX = SELF.R6[GroupElement].get
      |  val delta = SELF.R7[Coll[Byte]].get
      |
      |  val isHalf = {(b: Box) => blake2b256(b.propositionBytes) == delta}
      |  val noFeeTokenInBox = {(b:Box) => b.tokens.forall({(a: (Coll[Byte], Long)) => a._1 != tokenId})}
      |  val destroyToken = OUTPUTS.forall(noFeeTokenInBox)
      |  val nextBob = isHalf(INPUTS(0)) && blake2b256(INPUTS(2).propositionBytes) == feeEmissionScriptHash
      |  val nextAlice = isHalf(OUTPUTS(0)) && blake2b256(INPUTS(1).propositionBytes) == feeEmissionScriptHash
      |
      |  (proveDlog(c2) || proveDHTuple(g, c1, gX, c2)) && {
      |    sigmaProp(nextAlice || nextBob || destroyToken)
      |  }
      |}
      |""".stripMargin

  val halfMixScript: String =
    """
      |{
      |  val g = groupGenerator
      |  val gX = SELF.R4[GroupElement].get
      |  val noFeeTokenInBox = {(b:Box) => b.tokens.forall({(a: (Coll[Byte], Long)) => a._1 != tokenId})}
      |  {proveDlog(gX) && sigmaProp(OUTPUTS.forall(noFeeTokenInBox))} ||
      |  {
      |    val c1 = OUTPUTS(0).R4[GroupElement]
      |    if (c1.isDefined) {
      |      val isFull = {(b: Box) => blake2b256(b.propositionBytes) == fullMixScriptHash && b.R7[Coll[Byte]].get == blake2b256(SELF.propositionBytes) && b.value == SELF.value}
      |      val c2 = OUTPUTS(0).R5[GroupElement].get
      |      val u1 = OUTPUTS(0).R6[GroupElement].get
      |      val u2 = OUTPUTS(1).R6[GroupElement].get
      |      val mixLogic = isFull(OUTPUTS(0)) && isFull(OUTPUTS(1)) && OUTPUTS(0).tokens == OUTPUTS(1).tokens &&
      |                        SELF.tokens.getOrElse(1, (tokenId, 0L)) == OUTPUTS(0).tokens.getOrElse(1, (tokenId, 0L))
      |      val bobFullLogic = blake2b256(INPUTS(1).propositionBytes) == fullMixScriptHash
      |      val bobEntranceLogic = OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1 &&
      |                             OUTPUTS(0).tokens(0)._2 * 2 > SELF.tokens(0)._2
      |
      |      sigmaProp((bobEntranceLogic || bobFullLogic) && mixLogic &&
      |      u1 == gX && u2 == gX &&
      |      OUTPUTS(1).R4[GroupElement].get == c2 &&
      |      OUTPUTS(1).R5[GroupElement].get == c1.get &&
      |      SELF.id == INPUTS(0).id && c1.get != c2) && {
      |        proveDHTuple(g, gX, c1.get, c2) ||
      |        proveDHTuple(g, gX, c2, c1.get)
      |      }
      |    } else sigmaProp(false)
      |  }
      |}
      |""".stripMargin

  val tokenEmissionScript: String =
    """
      |{
      |  val batchPrices = SELF.R4[Coll[(Int, Long)]]
      |  val rate = SELF.R5[Int]
      |  val tokenId = SELF.tokens(0)._1
      |  val tokenVal = SELF.tokens(0)._2
      |
      |  val isHalf = {(b: Box) => blake2b256(b.propositionBytes) == halfMixScriptHash}
      |  val isCopy = {(b: (Box, Box)) => b._1.propositionBytes == SELF.propositionBytes &&
      |                            b._1.R4[Coll[(Int, Long)]] == batchPrices &&
      |                            b._1.R5[Int] == rate && b._1.tokens(0)._1 == tokenId &&
      |                            blake2b256(b._2.propositionBytes) == mixerIncome &&
      |                            batchPrices.get.exists({(batch: (Int, Long)) => {
      |                              b._1.tokens(0)._2 >= tokenVal - batch._1 && b._1.value >= SELF.value &&
      |                              b._2.value >= batch._2 + (OUTPUTS(0).value / rate.get) && {
      |                                val mixingToken = OUTPUTS(0).tokens.getOrElse(1, (tokenId, 0L))
      |                                val outToken = b._2.tokens.getOrElse(0, (tokenId, 0L))
      |                                outToken._1 == mixingToken._1 && outToken._2 >= mixingToken._2 / rate.get
      |                              }
      |                            }})}
      |  val aliceBuying = OUTPUTS.size == 4 && isHalf(OUTPUTS(0)) && isCopy((OUTPUTS(2), OUTPUTS(1))) && INPUTS.size == 2
      |  val bobBuying = OUTPUTS.size == 5 && isHalf(INPUTS(0)) && isCopy((OUTPUTS(3), OUTPUTS(2))) && INPUTS.size == 3
      |
      |  sigmaProp(aliceBuying || bobBuying) || mixerOwner
      |}
      |""".stripMargin

  val feeEmissionScript: String =
    """
      |{
      |  val maxFee = SELF.R4[Long]
      |  val isCopy = {(b: Box) => b.propositionBytes == SELF.propositionBytes && b.value >= SELF.value - maxFee.get && b.R4[Long] == maxFee}
      |  val getToken = {(b: Box) => {
      |    val token = b.tokens.getOrElse(0, (tokenId, 0l))
      |    if (token._1 == tokenId) token._2 else 0L
      |  }}
      |
      |  mixerOwner || {
      |    if (INPUTS.size == 2 && OUTPUTS.size == 3) { // alice or withdraw
      |      val in = getToken(INPUTS(0))
      |      val out = getToken(OUTPUTS(0))
      |      val tokenOk = out < in
      |      sigmaProp(isCopy(OUTPUTS(1)) && tokenOk)
      |
      |    } else if (INPUTS.size == 3 && OUTPUTS.size == 4) { // bob
      |      val in0 = getToken(INPUTS(0))
      |      val in1 = getToken(INPUTS(1))
      |      val out = getToken(OUTPUTS(0)) + getToken(OUTPUTS(1))
      |      val diff = in0 + in1 - out
      |      val tokenOk = in1 > 0 && in0 > 0 && (diff == 1 || diff == 2)
      |      sigmaProp(isCopy(OUTPUTS(2)) && tokenOk)
      |
      |    } else sigmaProp(false)
      |  }
      |}
      |""".stripMargin

  val feeEmissionContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder
      .create()
      .item("mixerOwner", TokenErgoMix.mixerOwner.getPublicKey)
      .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
      .build(),
    feeEmissionScript
  )
  val feeEmissionAddress: ErgoAddress = getErgoAddress(feeEmissionContract.getErgoTree)
  val feeEmissionErgoTree: ErgoTree   = feeEmissionContract.getErgoTree
  val feeScriptHash: Digest32         = getHash(feeEmissionErgoTree.bytes)

  val fullMixScriptContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder
      .create()
      .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
      .item("feeEmissionScriptHash", feeScriptHash)
      .build(),
    fullMixScript
  )
  val fullMixScriptErgoTree: ErgoTree = fullMixScriptContract.getErgoTree
  val fullMixAddress: ErgoAddress     = getErgoAddress(fullMixScriptErgoTree)
  val fullMixScriptHash: Digest32     = getHash(fullMixScriptErgoTree.bytes)

  val halfMixContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder
      .create()
      .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
      .item("fullMixScriptHash", fullMixScriptHash)
      .build(),
    halfMixScript
  )
  val halfMixScriptHash: Digest32 = getHash(halfMixContract.getErgoTree.bytes)
  val halfMixAddress: ErgoAddress = getErgoAddress(halfMixContract.getErgoTree)

  val income: ErgoContract = ctx.newContract(TokenErgoMix.mixerIncome.getErgoAddress.script)
  val tokenEmissionContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder
      .create()
      .item("halfMixScriptHash", halfMixScriptHash)
      .item("mixerOwner", TokenErgoMix.mixerOwner.getPublicKey)
      .item("mixerIncome", getHash(income.getErgoTree.bytes))
      .build(),
    tokenEmissionScript
  )
  val tokenEmissionAddress: ErgoAddress = getErgoAddress(tokenEmissionContract.getErgoTree)
}
