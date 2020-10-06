package org.ergoplatform.appkit.examples

import org.ergoplatform.appkit.{AppkitTesting, HttpClientTesting, SecretString, BlockchainContext}

object RunMockedScala extends App with AppkitTesting with HttpClientTesting {
  val data = MockData(
    nodeResponses = Seq(loadNodeResponse("response_Box1.json")),
    explorerResponses = Seq())

  val ergoClient = createMockedErgoClient(data)
  val res = ergoClient.execute { ctx: BlockchainContext =>
    val r = new ExampleScenarios(ctx)
    val res = r.aggregateUtxoBoxes("storage/E2.json", SecretString.create("abc"), addrStr, 10, "d47f958b201dc7162f641f7eb055e9fa7a9cb65cc24d4447a10f86675fc58328")
    res
  }
  println(res)
}
