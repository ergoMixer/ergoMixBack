package mixer

import app.{FileMockedErgoClient, HttpClientTesting}
import config.MainConfigs
import mixinterface.TokenErgoMix
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.collection.Coll
import special.sigma.GroupElement

import java.lang
import java.math.BigInteger
import scala.collection.JavaConverters._

class TokenContractsSpec extends AnyPropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting {

  val ergoClient: FileMockedErgoClient = createMockedErgoClient(MockData(Nil, Nil))
  val g: GroupElement = CryptoConstants.dlogGroup.generator
  val x: BigInteger = BigInt("187235612876647164378132684712638457631278").bigInteger
  val y: BigInteger = BigInt("340956873409567839086738967389673896738906").bigInteger
  val dummySecret: BigInteger = BigInt("34111111111111111111111111").bigInteger
  val gX: GroupElement = g.exp(x)
  val gY: GroupElement = g.exp(y)
  val gXY: GroupElement = gX.exp(y)
  val tokenId: String = TokenErgoMix.tokenId
  val trashAddress: Address = Address.create("3WyYs2kRTWnzgJRwegG9kRks2Stw41VLM8s87JTdhRmwuZr71fTg")
  val poolValue = 3000000
  val batchSize = 60
  val batchPrice: Long = batchSize * MainConfigs.defaultFullFee
  val batchPrices: Coll[(Int, Long)] = Colls.fromArray(Seq((batchSize, batchPrice)).toArray)
  val mixToken: String = ""
  val mixTokenVal: Long = 1000

  val batchPricesValue: ErgoValue[Coll[(Integer, lang.Long)]] = ErgoValueBuilder.buildFor(batchPrices)

  def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  property("AliceEntering") {
    // enter as alice to mixing
    // should be able to enter with token
    // half-box must be spendable by alice (maybe she just wants to get out of mixing)
    ergoClient.execute { ctx: BlockchainContext =>
      // we spend token emission box and enter mixing as alice
      val mix = new TokenErgoMix(ctx)
      val tokenBox = ctx.newTxBuilder.outBoxBuilder
        .tokens(new ErgoToken(tokenId, 200))
        .registers(batchPricesValue, ErgoValue.of(20))
        .value(1000000)
        .contract(mix.tokenEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(poolValue + poolValue / 20 + batchPrice + MainConfigs.defaultFullFee)
        .contract(ctx.compileContract(
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(tokenId, batchSize))
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gX))
        .build()

      val copy = ctx.newTxBuilder().outBoxBuilder
        .value(tokenBox.getValue)
        .tokens(new ErgoToken(tokenId, tokenBox.getTokens.get(0).getValue - batchSize))
        .registers(tokenBox.getRegisters.asScala: _*)
        .contract(mix.tokenEmissionContract)
        .build()
      val pay = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue / 20 + batchPrice)
        .contract(mix.income)
        .build()
      val tx = ctx.newTxBuilder().addInputs(tokenBox, spendableBox)
        .addOutputs(halfBox, pay, copy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder().withDLogSecret(dummySecret).build().sign(tx)

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - MainConfigs.defaultFullFee)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val outAddr = ctx.newContract(trashAddress.getErgoAddress.script)
      val out = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .contract(outAddr)
        .build()
      // we have to be able to spend half box with our secret x
      val halfSigned = ctx.newProverBuilder().withDLogSecret(x).build().sign(
        ctx.newTxBuilder()
          .addInputs(signed.getOutputsToSpend.get(0), feeBox)
          .addOutputs(out, feeCopy)
          .fee(MainConfigs.defaultFullFee)
          .sendChangeTo(trashAddress)
          .tokensToBurn(new ErgoToken("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 60))
          .build()
      )
    }
  }

  property("BobEntering") {
    // enter as bob to mixing.
    // should be able to add tokens
    ergoClient.execute { ctx: BlockchainContext =>
      val mix = new TokenErgoMix(ctx)
      val tokenBox = ctx.newTxBuilder.outBoxBuilder
        .tokens(new ErgoToken(tokenId, 200))
        .registers(batchPricesValue, ErgoValue.of(20))
        .value(1000000).contract(mix.tokenEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(poolValue + batchPrice + MainConfigs.defaultFullFee + poolValue / 20)
        .contract(mix.tokenEmissionContract)
        .contract(ctx.compileContract(
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder.outBoxBuilder
        .value(poolValue)
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gX))
        .tokens(new ErgoToken(tokenId, 60))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)


      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox1 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60))
        .build()

      val fullBox2 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60))
        .build()

      val copy = ctx.newTxBuilder().outBoxBuilder
        .value(tokenBox.getValue)
        .registers(tokenBox.getRegisters.asScala:_*)
        .tokens(new ErgoToken(tokenId, tokenBox.getTokens.get(0).getValue - batchSize))
        .contract(mix.tokenEmissionContract)
        .build()
      val pay = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue / 20 + batchPrice)
        .contract(mix.income)
        .build()

      val tx = ctx.newTxBuilder().addInputs(halfBox, spendableBox, tokenBox)
        .addOutputs(fullBox1, fullBox2, pay, copy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .build()

      ctx.newProverBuilder()
        .withDHTData(g, gX, gY, gXY, y).build().sign(tx)
    }
  }

  property("RemixAsAlice") {
    // spending full box and output half box
    // burning sufficient amount of tokens, using fee emission boxes
    ergoClient.execute { ctx: BlockchainContext =>
      // we spend token emission box and enter mixing as alice
      val mix = new TokenErgoMix(ctx)
      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 10)) // after some mixing potentially
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(tokenId, 9)) // burning some tokens!
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gY)) // assuming y is the next secret of alice!
        .build()
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - MainConfigs.defaultFullFee)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val tx = ctx.newTxBuilder().addInputs(fullBox, feeBox)
        .addOutputs(halfBox, feeCopy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .tokensToBurn(new ErgoToken("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 1))
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDHTData(g, gY, gX, gXY, x)
        .build().sign(tx)
    }
  }

  property("RemixAsBob") {
    // spending full box and a half box
    // burning sufficient amount of tokens, using fee emission boxes
    ergoClient.execute { ctx: BlockchainContext =>
      // we spend token emission box and enter mixing as alice
      val mix = new TokenErgoMix(ctx)
      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      // we suppose dummySecret is secret of bob by which he can spend input full box
      val fullBox = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(gX.exp(dummySecret)), ErgoValue.of(g.exp(dummySecret)), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 12)) // after some mixing potentially
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(tokenId, 9)) // after some mixing potentially
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gX)) // assuming y is the next secret of alice!
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - MainConfigs.defaultFullFee)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox1 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 10))
        .build()

      val fullBox2 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 10))
        .build()

      val tx = ctx.newTxBuilder().addInputs(halfBox, fullBox, feeBox)
        .addOutputs(fullBox1, fullBox2, feeCopy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .tokensToBurn(new ErgoToken("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 1))
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDLogSecret(dummySecret) // for spending full box
        .withDHTData(g, gX, gY, gXY, y)
        .build().sign(tx)
    }
  }


  // token mixing
  property("AliceTokenEntering") {
    // enter as alice to mixing
    // should be able to enter with token
    // half-box must be spendable by alice (maybe she just wants to get out of mixing)
    ergoClient.execute { ctx: BlockchainContext =>
      // we spend token emission box and enter mixing as alice
      val mix = new TokenErgoMix(ctx)
      val tokenBox = ctx.newTxBuilder.outBoxBuilder
        .tokens(new ErgoToken(tokenId, 200))
        .registers(batchPricesValue, ErgoValue.of(20))
        .value(1000000)
        .contract(mix.tokenEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(poolValue + poolValue / 20 + batchPrice + MainConfigs.defaultFullFee)
        .tokens(new ErgoToken(mixToken, mixTokenVal + mixTokenVal / 20))
        .contract(ctx.compileContract(
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(tokenId, batchSize), new ErgoToken(mixToken, mixTokenVal))
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gX))
        .build()

      val copy = ctx.newTxBuilder().outBoxBuilder
        .value(tokenBox.getValue)
        .tokens(new ErgoToken(tokenId, tokenBox.getTokens.get(0).getValue - batchSize))
        .registers(tokenBox.getRegisters.asScala: _*)
        .contract(mix.tokenEmissionContract)
        .build()
      val pay = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue / 20 + batchPrice)
        .tokens(new ErgoToken(mixToken, mixTokenVal / 20))
        .contract(mix.income)
        .build()
      val tx = ctx.newTxBuilder().addInputs(tokenBox, spendableBox)
        .addOutputs(halfBox, pay, copy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder().withDLogSecret(dummySecret).build().sign(tx)

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - MainConfigs.defaultFullFee)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val outAddr = ctx.newContract(trashAddress.getErgoAddress.script)
      val out = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(mixToken, mixTokenVal))
        .contract(outAddr)
        .build()
      // we have to be able to spend half box with our secret x
      val halfSigned = ctx.newProverBuilder().withDLogSecret(x).build().sign(
        ctx.newTxBuilder()
          .addInputs(signed.getOutputsToSpend.get(0), feeBox)
          .addOutputs(out, feeCopy)
          .fee(MainConfigs.defaultFullFee)
          .sendChangeTo(trashAddress)
          .tokensToBurn(new ErgoToken("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 60))
          .build()
      )
    }
  }

  property("BobTokenEntering") {
    // enter as bob to mixing.
    // should be able to add tokens
    ergoClient.execute { ctx: BlockchainContext =>
      val mix = new TokenErgoMix(ctx)
      val tokenBox = ctx.newTxBuilder.outBoxBuilder
        .tokens(new ErgoToken(tokenId, 200))
        .registers(batchPricesValue, ErgoValue.of(20))
        .value(1000000).contract(mix.tokenEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(poolValue + batchPrice + MainConfigs.defaultFullFee + poolValue / 20)
        .tokens(new ErgoToken(mixToken, mixTokenVal + mixTokenVal / 20))
        .contract(ctx.compileContract(
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder.outBoxBuilder
        .value(poolValue)
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gX))
        .tokens(new ErgoToken(tokenId, 60), new ErgoToken(mixToken, mixTokenVal))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)


      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox1 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60), new ErgoToken(mixToken, mixTokenVal))
        .build()

      val fullBox2 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60), new ErgoToken(mixToken, mixTokenVal))
        .build()

      val copy = ctx.newTxBuilder().outBoxBuilder
        .value(tokenBox.getValue)
        .registers(tokenBox.getRegisters.asScala:_*)
        .tokens(new ErgoToken(tokenId, tokenBox.getTokens.get(0).getValue - batchSize))
        .contract(mix.tokenEmissionContract)
        .build()
      val pay = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue / 20 + batchPrice)
        .tokens(new ErgoToken(mixToken, mixTokenVal / 20))
        .contract(mix.income)
        .build()

      val tx = ctx.newTxBuilder().addInputs(halfBox, spendableBox, tokenBox)
        .addOutputs(fullBox1, fullBox2, pay, copy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .build()

      ctx.newProverBuilder()
        .withDHTData(g, gX, gY, gXY, y).build().sign(tx)
    }
  }

  property("RemixTokenAsAlice") {
    // spending full box and output half box
    // burning sufficient amount of tokens, using fee emission boxes
    ergoClient.execute { ctx: BlockchainContext =>
      // we spend token emission box and enter mixing as alice
      val mix = new TokenErgoMix(ctx)
      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 10), new ErgoToken(mixToken, mixTokenVal)) // after some mixing potentially
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(tokenId, 9), new ErgoToken(mixToken, mixTokenVal)) // burning some tokens!
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gY)) // assuming y is the next secret of alice!
        .build()
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - MainConfigs.defaultFullFee)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val tx = ctx.newTxBuilder().addInputs(fullBox, feeBox)
        .addOutputs(halfBox, feeCopy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .tokensToBurn(new ErgoToken("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 1))
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDHTData(g, gY, gX, gXY, x)
        .build().sign(tx)
    }
  }

  property("RemixTokenAsBob") {
    // spending full box and a half box
    // burning sufficient amount of tokens, using fee emission boxes
    ergoClient.execute { ctx: BlockchainContext =>
      // we spend token emission box and enter mixing as alice
      val mix = new TokenErgoMix(ctx)
      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      // we suppose dummySecret is secret of bob by which he can spend input full box
      val fullBox = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(gX.exp(dummySecret)), ErgoValue.of(g.exp(dummySecret)), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 12), new ErgoToken(mixToken, mixTokenVal)) // after some mixing potentially
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val halfBox = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(tokenId, 9), new ErgoToken(mixToken, mixTokenVal)) // after some mixing potentially
        .contract(mix.halfMixContract)
        .registers(ErgoValue.of(gX)) // assuming y is the next secret of alice!
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - MainConfigs.defaultFullFee)
        .registers(ErgoValue.of(MainConfigs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox1 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 10), new ErgoToken(mixToken, mixTokenVal))
        .build()

      val fullBox2 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 10), new ErgoToken(mixToken, mixTokenVal))
        .build()

      val tx = ctx.newTxBuilder().addInputs(halfBox, fullBox, feeBox)
        .addOutputs(fullBox1, fullBox2, feeCopy)
        .fee(MainConfigs.defaultFullFee)
        .sendChangeTo(trashAddress)
        .tokensToBurn(new ErgoToken("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 1))
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDLogSecret(dummySecret) // for spending full box
        .withDHTData(g, gX, gY, gXY, y)
        .build().sign(tx)
    }
  }
}
