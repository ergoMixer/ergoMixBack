package stealth

import app.Configs
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.{ConstantsBuilder, ErgoClient, ErgoContract, NetworkType, RestApiErgoClient}
import scorex.util.encode.Base64
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants.dlogGroup
import special.sigma.GroupElement

import scala.collection.immutable._
import scala.language.postfixOps
import wallet.WalletHelper
import testHandlers.StealthTestSuite

class NodeProcessSpec
  extends StealthTestSuite {


  val ergoClient: ErgoClient = RestApiErgoClient.create(Configs.nodes.head, NetworkType.MAINNET, "", Configs.nodes.head)

  val fakeStealthScript: String =
    s"""{
       |  val gr = decodePoint(fromBase64("GR"))
       |  val gy = decodePoint(fromBase64("GY"))
       |  val ur = decodePoint(fromBase64("UR"))
       |  val uy = decodePoint(fromBase64("UY"))
       |  proveDHTuple(gr,gy,ur,uy) && sigmaProp(OUTPUTS(0).R4[Int].get == 10)
       |}
       |""".stripMargin

  val stealthScript: String =
    s"""{
       |  val gr = decodePoint(fromBase64("GR"))
       |  val gy = decodePoint(fromBase64("GY"))
       |  val ur = decodePoint(fromBase64("UR"))
       |  val uy = decodePoint(fromBase64("UY"))
       |  proveDHTuple(gr,gy,ur,uy)
       |}
       |""".stripMargin


  def generateStealthContract(g: GroupElement, x: BigInt, script: String): ErgoContract = {
    val r = WalletHelper.randBigInt
    val y = WalletHelper.randBigInt
    val u: GroupElement = g.exp(x.bigInteger)

    val gr = Base64.encode(g.exp(r.bigInteger).getEncoded.toArray)
    val gy = Base64.encode(g.exp(y.bigInteger).getEncoded.toArray)
    val ur = Base64.encode(u.exp(r.bigInteger).getEncoded.toArray)
    val uy = Base64.encode(u.exp(y.bigInteger).getEncoded.toArray)

    ergoClient.execute(ctx=> {
      val newScript = script
        .replace("GR", gr)
        .replace("GY", gy)
        .replace("UR", ur)
        .replace("UY", uy)
      val contract = ctx.compileContract(
        ConstantsBuilder.create()
          .build()
        , newScript)
      val ergoTree = contract.getErgoTree
      println(WalletHelper.toHexString(ergoTree.bytes))
      contract
    })
  }

  def getStealthBoxes(value: Long, script: String): Seq[ErgoBox] = {
    val x = WalletHelper.randBigInt
    val g: GroupElement = dlogGroup.generator

    var outPuts = List()
    for (_ <- 0 until 5) {
      outPuts :+ ergoClient.execute(ctx=> {

        val txB = ctx.newTxBuilder()
        txB.outBoxBuilder()
          .value(value)
          .contract(generateStealthContract(g, x, script))
          .build()
      })
    }
    outPuts
  }


  property("check real stealth address") {
    val outPuts: Seq[ErgoBox] = getStealthBoxes(1000L, stealthScript)

    for (outPut <- outPuts) {
      assertResult(true) {
        NodeProcess.checkStealth(outPut)
      }
    }
  }

  property("check fake stealth address") {
    val outPuts: Seq[ErgoBox] = getStealthBoxes(1000L, fakeStealthScript)

    for (outPut <- outPuts) {
      NodeProcess.checkStealth(outPut) should be(false)
    }
  }
}
