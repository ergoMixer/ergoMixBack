package org.ergoplatform.appkit

import java.util

import org.ergoplatform.appkit.BoxOperations.{createProver, loadTop, putToContractTx}
import org.ergoplatform.appkit.ErgoContracts.sendToPK
import org.ergoplatform.appkit.Parameters.MinFee
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement

class AnonymousAccessSpec extends PropSpec with Matchers
    with ScalaCheckDrivenPropertyChecks
    with AppkitTesting
    with HttpClientTesting {

  val data = MockData(
    Seq(
      loadNodeResponse("response_Box1.json"),
      loadNodeResponse("response_Box2.json"),
      loadNodeResponse("response_Box3.json")),
    Seq(
      loadExplorerResponse("response_boxesByAddressUnspent.json")))

  property("Get unspent boxes containing given address") {
    val ergoClient = createMockedErgoClient(data)

    val boxes: java.util.List[InputBox] = ergoClient.execute { ctx: BlockchainContext =>
      ctx.getUnspentBoxesFor(Address.create(addr1))
    }
    boxes.forEach { b: InputBox =>
      println(b.toJson(true))
    }
  }

  // see original example in sigma https://github.com/ScorexFoundation/sigmastate-interpreter/blob/b3695bdb785c9b3a94545ffea506358ee3f8ed3d/sigmastate/src/test/scala/sigmastate/utxo/examples/DHTupleExampleSpecification.scala#L28
  property("ProveDHTuple") {
    val ergoClientAlice = createMockedErgoClient(
      data.appendExplorerResponses(Array(loadExplorerResponse("E1/response_boxesByAddressUnspent.json")))
          .appendNodeResponses(Array(loadNodeResponse("E1/response_Box1.json")))
    )
    val ergoClientAliceBad = createMockedErgoClient(
      data.appendExplorerResponses(Array(loadExplorerResponse("E1/response_boxesByAddressUnspent.json")))
          .appendNodeResponses(Array(loadNodeResponse("E1/response_Box1.json")))
    )
    val ergoClientBob = createMockedErgoClient(
      data.appendExplorerResponses(Array(loadExplorerResponse("E1/response_boxesByAddressUnspent.json")))
          .appendNodeResponses(Array(loadNodeResponse("E1/response_Box1.json")))
    )
    val firstTxSenderStorage = "storage/E2.json"
    val secondTxSenderStorage = "storage/E1.json"
    val g: GroupElement = CryptoConstants.dlogGroup.generator
    val x = BigInt("187235612876647164378132684712638457631278").bigInteger
    val y = BigInt("340956873409567839086738967389673896738906").bigInteger
    val g_x:GroupElement = g.exp(x)
    val g_y:GroupElement = g.exp(y)
    val g_xy = g_x.exp(y)

    ergoClientAlice.execute { ctx: BlockchainContext =>
      val firstTxSender = createProver(ctx, firstTxSenderStorage, "abc").build
      val secondTxReceiver = firstTxSender.getAddress
      val aliceSpender = createProver(ctx, secondTxSenderStorage, "abc").withDHTData(g, g_y, g_x, g_xy, x).build()
      val startTx: SignedTransaction = DhtUtils.createDhtBox(ctx, firstTxSender, MinFee, g_x)
      val contractBox: InputBox = startTx.getOutputsToSpend().get(0)
      val aliceTx = DhtUtils.spendDhtBox(ctx, g_y, g_xy, aliceSpender, contractBox, secondTxReceiver)
      println(aliceTx.toJson(true))
    }

    ergoClientBob.execute { ctx: BlockchainContext =>
      val firstTxSender = createProver(ctx, firstTxSenderStorage, "abc").build
      val secondTxReceiver = firstTxSender.getAddress
      val bobSpender   = createProver(ctx, secondTxSenderStorage, "abc").withDHTData(g, g_x, g_y, g_xy, y).build()
      val startTx: SignedTransaction = DhtUtils.createDhtBox(ctx, firstTxSender, MinFee, g_x)
      val contractBox: InputBox = startTx.getOutputsToSpend().get(0)
      val bobTx = DhtUtils.spendDhtBox(ctx, g_y, g_xy, bobSpender, contractBox, secondTxReceiver)
      println(bobTx.toJson(true))
    }

    ergoClientAliceBad.execute { ctx: BlockchainContext =>
      val firstTxSender = createProver(ctx, firstTxSenderStorage, "abc").build
      val secondTxReceiver = firstTxSender.getAddress
      val aliceSpenderBad = createProver(ctx, secondTxSenderStorage, "abc").withDHTData(g, g_x, g_y, g_xy, x).build() // using wrong order
      val startTx: SignedTransaction = DhtUtils.createDhtBox(ctx, firstTxSender, MinFee, g_x)
      val contractBox: InputBox = startTx.getOutputsToSpend().get(0)
      val aliceTx = DhtUtils.spendDhtBox(ctx, g_y, g_xy, aliceSpenderBad, contractBox, secondTxReceiver) // this should not work
      println(aliceTx.toJson(true))
    }
  }

}

object DhtUtils {
  def createDhtBox(ctx: BlockchainContext, sender: ErgoProver, amountToSend: Long, g_x:GroupElement): SignedTransaction = {

    val contract = ctx.compileContract(
      ConstantsBuilder.create().item("g_x", g_x).build(),
      """{
       |  val g_y = OUTPUTS(0).R4[GroupElement].get
       |  val g_xy = OUTPUTS(0).R5[GroupElement].get
       |
       |  proveDHTuple(groupGenerator, g_x, g_y, g_xy) || // for bob
       |  proveDHTuple(groupGenerator, g_y, g_x, g_xy)    // for alice
       |}""".stripMargin);

    val dhtBoxCreationTx = putToContractTx(ctx, sender, contract, amountToSend)
    dhtBoxCreationTx
  }

  def spendDhtBox(ctx: BlockchainContext, g_y: GroupElement, g_xy: GroupElement, sender: ErgoProver, dhtBox: InputBox, receiver: Address): SignedTransaction = {
    val txB = ctx.newTxBuilder
    val outBox = txB.outBoxBuilder
        .value(dhtBox.getValue)
        .contract(sendToPK(ctx, receiver))
        .registers(ErgoValue.of(g_y), ErgoValue.of(g_xy))
        .build

    val boxesToPayFee = loadTop(ctx, sender.getAddress, MinFee)

    val inputs = new util.ArrayList[InputBox]()
    inputs.add(dhtBox)
    inputs.addAll(boxesToPayFee)

    val tx = txB.boxesToSpend(inputs)
        .outputs(outBox)
        .fee(MinFee)
        .sendChangeTo(sender.getP2PKAddress).build

    sender.sign(tx)
  }
}