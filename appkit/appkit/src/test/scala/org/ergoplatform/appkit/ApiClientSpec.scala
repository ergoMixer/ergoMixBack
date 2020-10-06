package org.ergoplatform.appkit

import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.ergoplatform.Height
import org.ergoplatform.appkit.examples.ExampleScenarios
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.settings.ErgoAlgos
import org.ergoplatform.validation.ValidationRules
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scalan.util.FileUtil._
import sigmastate.{BoolToSigmaProp, LT, SInt}
import sigmastate.Values.{ConstantPlaceholder, IntConstant, SigmaPropConstant}
import sigmastate.serialization.ErgoTreeSerializer

class ApiClientSpec
    extends PropSpec
        with Matchers
        with ScalaCheckDrivenPropertyChecks
        with AppkitTesting
        with HttpClientTesting {

  val seed = SecretString.create("abc")
  val masterKey = JavaHelpers.seedToMasterKey(seed)
  implicit val vs = ValidationRules.currentSettings

  property("parse ErgoTree") {
    val script = "100204a00b08cd02d3a9410ac758ad45dfc85af8626efdacf398439c73977b13064aa8e6c8f2ac88ea02d192a39a8cc7a70173007301"
    val treeBytes = ErgoAlgos.decode(script).get
    val tree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(treeBytes)
    println(tree)
    val newBytes = ErgoTreeSerializer.DefaultSerializer.substituteConstants(
      treeBytes, positions = Array(1), newVals = Array(SigmaPropConstant(masterKey.key.publicImage)))
    val newTree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(newBytes)
    println(newTree)
    println(ErgoAlgos.encode(newBytes))
  }

  property("BlockchainContext") {
    val data = MockData(
      nodeResponses = Seq(loadNodeResponse("response_Box1.json")),
      explorerResponses = Seq())

    val ergoClient = createMockedErgoClient(data)

    // Exercise your application code, which should make those HTTP requests.
    // Responses are returned in the same order that they are enqueued.
    val res = ergoClient.execute { ctx: BlockchainContext =>
      val r = new ExampleScenarios(ctx)
      val res = r.aggregateUtxoBoxes(
        "storage/E2.json", SecretString.create("abc"),
        addrStr, 10, "d47f958b201dc7162f641f7eb055e9fa7a9cb65cc24d4447a10f86675fc58328"
      )
      res
    }

    println(res)
  }
}
