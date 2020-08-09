package org.ergoplatform.appkit

import org.ergoplatform.{ErgoAddressEncoder, Pay2SAddress}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.util.encode.Base16
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import sigmastate.serialization.ErgoTreeSerializer
import special.sigma.GroupElement

class ChangeOutputSpec extends PropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks
  with AppkitTesting
  with HttpClientTesting {

  property("NoTokenChangeOutput") {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    val g: GroupElement = CryptoConstants.dlogGroup.generator
    val x = BigInt("187235612876647164378132684712638457631278").bigInteger
    val y = BigInt("340956873409567839086738967389673896738906").bigInteger
    val gX:GroupElement = g.exp(x)
    val gY:GroupElement = g.exp(y)
    val gXY:GroupElement = gX.exp(y)

    ergoClient.execute { ctx: BlockchainContext =>

      val input0 = ctx.newTxBuilder.outBoxBuilder.registers(
        ErgoValue.of(gY), ErgoValue.of(gXY)
      ).value(30000000).contract(ctx.compileContract(
        ConstantsBuilder.empty(),
        """{
          |  val gY = SELF.R4[GroupElement].get
          |  val gXY = SELF.R5[GroupElement].get
          |  proveDHTuple(gY, gY, gXY, gXY)
          |}""".stripMargin
      )).build().convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val tokenId = input0.getId.toString

      val txB = ctx.newTxBuilder()
      val tokenBox = txB.outBoxBuilder
        .value(15000000) // value of token box, doesn't really matter
        .tokens(new ErgoToken(tokenId, 5000000000L)) // amount of token issuing
        .contract(ctx.compileContract( // contract of the box containing tokens, just has to be spendable
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()

      val inputs = new java.util.ArrayList[InputBox]()
      inputs.add(input0)

      // below is ergoTree of a random box picked from the block explorer. The boxId is 02abc29b6a28ccf7e9620afa16e1067caeb75fcd2e62c066e190742962cdcbae
      // We just need valid ergoTree to construct the change address
      val tree = "100207036ba5cfbc03ea2471fdf02737f64dbcd58c34461a7ec1e586dcd713dacbf89a120400d805d601db6a01ddd6027300d603b2a5730100d604e4c672030407d605e4c672030507eb02ce7201720272047205ce7201720472027205"
      implicit val encoder: ErgoAddressEncoder = ErgoAddressEncoder.apply(NetworkType.MAINNET.networkPrefix);
      val ergoTree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(Base16.decode(tree).get)
      val changeAddr = Pay2SAddress.apply(ergoTree)
      val unsigned = txB.boxesToSpend(inputs).outputs(tokenBox).fee(15000000).sendChangeTo(changeAddr).build()
      val signed = ctx.newProverBuilder().withDHTData(gY, gY, gXY, gXY, x).build().sign(unsigned) // alice signing bob's box. Does not work here but works in other cases.
      val outputs = signed.getOutputsToSpend
      assert(outputs.size == 2)
      println(signed.toJson(false))


      val txB2 = ctx.newTxBuilder()
      val output = txB2.outBoxBuilder
        .value(10000000) // value of token box, doesn't really matter
        .tokens(new ErgoToken(tokenId, 4999999998L)) // we are trying to burn 2 tokens
        .contract(ctx.compileContract(
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()

      val inputs2 = new java.util.ArrayList[InputBox]()
      inputs2.add(signed.getOutputsToSpend.get(0))

      val unsigned2 = txB2.boxesToSpend(inputs2)
        .outputs(output)
        .fee(5000000)
        .sendChangeTo(changeAddr)
        .build()
      val signed2: SignedTransaction = ctx.newProverBuilder().build().sign(unsigned2)

      println(signed2.toJson(false))
      assert(signed2.getOutputsToSpend.size == 2)
    }
  }
}

