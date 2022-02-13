package app

import java.math.BigInteger
import java.util

import mixinterface.TokenErgoMix
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.collection.Coll
import special.sigma.GroupElement
import wallet.WalletHelper

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
  val batchPrice: Long = batchSize * Configs.defaultFullFee
  val batchPrices: Array[(Int, Long)] = Seq(Tuple2(batchSize, batchPrice)).toArray
  val mixToken: String = ""
  val mixTokenVal: Long = 1000

  val batchPricesValue: ErgoValue[Coll[(Int, Long)]] = ErgoValue.of(batchPrices, ErgoType.pairType(ErgoType.integerType(), ErgoType.longType()))

  def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  property("mixerOwnerSpendsTokenAndFeeBoxes") {
    // mixer owner should be able to spend token and fee emission boxes
    ergoClient.execute { ctx: BlockchainContext =>
      val tmp = new TokenErgoMix(ctx)
      println(ErgoAddressEncoder(NetworkType.TESTNET.networkPrefix).fromProposition(tmp.feeEmissionContract.getErgoTree))
      println(ErgoAddressEncoder(NetworkType.TESTNET.networkPrefix).fromProposition(tmp.feeEmissionContract.getErgoTree))
      println(ErgoAddressEncoder(NetworkType.TESTNET.networkPrefix).fromProposition(tmp.halfMixContract.getErgoTree))
      println(s"full contract cost: ${tmp.fullMixScriptContract.getErgoTree.complexity}")
      println(s"half contract cost: ${tmp.halfMixContract.getErgoTree.complexity}")
      println(s"token contract cost: ${tmp.tokenEmissionContract.getErgoTree.complexity}")
      println(s"fee contract cost: ${tmp.feeEmissionContract.getErgoTree.complexity}")
      println(s"token-emission: ${convertBytesToHex(tmp.tokenEmissionContract.getErgoTree.bytes)}")

      println(s"half-mix: ${convertBytesToHex(tmp.halfMixContract.getErgoTree.bytes)}")
      println(s"full-mix: ${convertBytesToHex(tmp.fullMixScriptContract.getErgoTree.bytes)}")
      println(s"token-emission: ${convertBytesToHex(tmp.tokenEmissionContract.getErgoTree.bytes)}")
      println(s"fee-emission: ${convertBytesToHex(tmp.feeEmissionContract.getErgoTree.bytes)}")
      val cur = ctx.compileContract(
        ConstantsBuilder.create()
          .item("gZ", g.exp(x))
          .item("gY", g.exp(y))
          .build(),
        """
          |{
          | val a = SELF.R4[Int].get
          | val b = SELF.R5[Int].get
          | val c = SELF.R6[Int].get
          | (sigmaProp(a < b) && proveDlog(gZ)) || proveDlog(gY)
          |}
          |""".stripMargin
      )
      println(s"impppppppp: ${convertBytesToHex(cur.getErgoTree.bytes)}")

      val mnemonic = "kite grape era zone habit robust drop purse story correct also gas fix motion announce"
      val mnemonicPass = "abc"
      val prover = ctx.newProverBuilder()
        .withMnemonic(SecretString.create(mnemonic), SecretString.create(mnemonicPass))
        .build()
      val mix = new TokenErgoMix(ctx)
      val feeEmissionContract: ErgoContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("mixerOwner", prover.getAddress.getPublicKey)
          .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
          .build(),
        mix.feeEmissionScript
      )
      val income = new ErgoTreeContract(TokenErgoMix.mixerIncome.getErgoAddress.script)
      val tokenEmissionContract: ErgoContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("halfMixScriptHash", mix.halfMixScriptHash)
          .item("mixerOwner", prover.getAddress.getPublicKey)
          .item("mixerIncome", WalletHelper.getHash(income.getErgoTree.bytes))
          .build(),
        mix.tokenEmissionScript
      )

      // we spend token emission box and enter mixing as alice
      val tokenBox = ctx.newTxBuilder.outBoxBuilder
        .tokens(new ErgoToken(tokenId, 200))
        .value(1000000).contract(tokenEmissionContract)
        .registers(batchPricesValue, ErgoValue.of(200))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(1000000000).contract(feeEmissionContract)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val out = ctx.newTxBuilder().outBoxBuilder()
        .value(10000000)
        .contract(feeEmissionContract)
        .build()
      // spends token box, with dummy registers!
      prover.sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(tokenBox).asJava)
          .outputs(out, out, out)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )

      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(10000000)
        .contract(ctx.compileContract(
          ConstantsBuilder.empty(),
          "{sigmaProp(1 < 2)}"
        ))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val out2 = ctx.newTxBuilder().outBoxBuilder().value(1000000).contract(feeEmissionContract).build()
      // spends fee box
      prover.sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(spendableBox, feeBox).asJava)
          .outputs(out)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
    }
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
        .value(poolValue + poolValue / 20 + batchPrice + Configs.defaultFullFee)
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
      val tx = ctx.newTxBuilder().boxesToSpend(List(tokenBox, spendableBox).asJava)
        .outputs(halfBox, pay, copy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder().withDLogSecret(dummySecret).build().sign(tx)
      println(signed.toJson(false))

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val outAddr = new ErgoTreeContract(trashAddress.getErgoAddress.script)
      val out = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .contract(outAddr)
        .build()
      // we have to be able to spend half box with our secret x
      val halfSigned = ctx.newProverBuilder().withDLogSecret(x).build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(signed.getOutputsToSpend.get(0), feeBox).asJava)
          .outputs(out, feeCopy)
          .fee(Configs.defaultFullFee)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
      println(halfSigned.toJson(false))
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
        .value(poolValue + batchPrice + Configs.defaultFullFee + poolValue / 20)
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

      val tx = ctx.newTxBuilder().boxesToSpend(List(halfBox, spendableBox, tokenBox).asJava)
        .outputs(fullBox1, fullBox2, pay, copy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()

      ctx.newProverBuilder()
        .withDHTData(g, gX, gY, gXY, y).build().sign(tx)
    }
  }

  property("FullBoxesSpendable") {
    // full boxes must be spendable by alice and bob, i.e. getting out of mixing by destroying tokens!
    ergoClient.execute { ctx: BlockchainContext =>
      val mix = new TokenErgoMix(ctx)
      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox1 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val fullBox2 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      // alice spends his full-box
      ctx.newProverBuilder()
        .withDHTData(g, gY, gX, gXY, x)
        .build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(fullBox1).asJava)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val outAddr = new ErgoTreeContract(trashAddress.getErgoAddress.script)
      val out = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .contract(outAddr)
        .build()

      // bob spends his full-box
      val signed = ctx.newProverBuilder()
        .withDLogSecret(y)
        .build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(fullBox2, feeBox).asJava)
          .outputs(out, feeCopy)
          .fee(Configs.defaultFullFee)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
      println(signed.toJson(false))
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
        .registers(ErgoValue.of(Configs.defaultFullFee))
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
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val tx = ctx.newTxBuilder().boxesToSpend(List(fullBox, feeBox).asJava)
        .outputs(halfBox, feeCopy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDHTData(g, gY, gX, gXY, x)
        .build().sign(tx)
      println(signed.toJson(false))
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
        .registers(ErgoValue.of(Configs.defaultFullFee))
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
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
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

      val tx = ctx.newTxBuilder().boxesToSpend(List(halfBox, fullBox, feeBox).asJava)
        .outputs(fullBox1, fullBox2, feeCopy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDLogSecret(dummySecret) // for spending full box
        .withDHTData(g, gX, gY, gXY, y)
        .build().sign(tx)
      println(signed.toJson(false))
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
        .value(poolValue + poolValue / 20 + batchPrice + Configs.defaultFullFee)
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
      val tx = ctx.newTxBuilder().boxesToSpend(List(tokenBox, spendableBox).asJava)
        .outputs(halfBox, pay, copy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder().withDLogSecret(dummySecret).build().sign(tx)
      println(signed.toJson(false))

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val outAddr = new ErgoTreeContract(trashAddress.getErgoAddress.script)
      val out = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(mixToken, mixTokenVal))
        .contract(outAddr)
        .build()
      // we have to be able to spend half box with our secret x
      val halfSigned = ctx.newProverBuilder().withDLogSecret(x).build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(signed.getOutputsToSpend.get(0), feeBox).asJava)
          .outputs(out, feeCopy)
          .fee(Configs.defaultFullFee)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
      println(halfSigned.toJson(false))
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
        .value(poolValue + batchPrice + Configs.defaultFullFee + poolValue / 20)
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

      val tx = ctx.newTxBuilder().boxesToSpend(List(halfBox, spendableBox, tokenBox).asJava)
        .outputs(fullBox1, fullBox2, pay, copy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()

      ctx.newProverBuilder()
        .withDHTData(g, gX, gY, gXY, y).build().sign(tx)
    }
  }

  property("FullBoxesSpendableToken") {
    // full boxes must be spendable by alice and bob, i.e. getting out of mixing by destroying tokens!
    ergoClient.execute { ctx: BlockchainContext =>
      val mix = new TokenErgoMix(ctx)
      val (c1, c2) = (gY, gXY) // randomness of outputs does not matter here
      val fullBox1 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c1), ErgoValue.of(c2), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60), new ErgoToken(mixToken, mixTokenVal))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val fullBox2 = ctx.newTxBuilder().outBoxBuilder()
        .value(poolValue)
        .registers(ErgoValue.of(c2), ErgoValue.of(c1), ErgoValue.of(gX), ErgoValue.of(mix.halfMixScriptHash))
        .contract(mix.fullMixScriptContract)
        .tokens(new ErgoToken(tokenId, 60), new ErgoToken(mixToken, mixTokenVal))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      // alice spends his full-box
      ctx.newProverBuilder()
        .withDHTData(g, gY, gX, gXY, x)
        .build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(fullBox1).asJava)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(100000000).contract(mix.feeEmissionContract)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val feeCopy = ctx.newTxBuilder().outBoxBuilder
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val outAddr = new ErgoTreeContract(trashAddress.getErgoAddress.script)
      val out = ctx.newTxBuilder().outBoxBuilder
        .value(poolValue)
        .tokens(new ErgoToken(mixToken, mixTokenVal))
        .contract(outAddr)
        .build()

      // bob spends his full-box
      val signed = ctx.newProverBuilder()
        .withDLogSecret(y)
        .build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(fullBox2, feeBox).asJava)
          .outputs(out, feeCopy)
          .fee(Configs.defaultFullFee)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
      println(signed.toJson(false))
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
        .registers(ErgoValue.of(Configs.defaultFullFee))
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
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
        .contract(mix.feeEmissionContract)
        .build()
      val tx = ctx.newTxBuilder().boxesToSpend(List(fullBox, feeBox).asJava)
        .outputs(halfBox, feeCopy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDHTData(g, gY, gX, gXY, x)
        .build().sign(tx)
      println(signed.toJson(false))
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
        .registers(ErgoValue.of(Configs.defaultFullFee))
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
        .value(feeBox.getValue - Configs.defaultFullFee)
        .registers(ErgoValue.of(Configs.defaultFullFee))
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

      val tx = ctx.newTxBuilder().boxesToSpend(List(halfBox, fullBox, feeBox).asJava)
        .outputs(fullBox1, fullBox2, feeCopy)
        .fee(Configs.defaultFullFee)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder()
        .withDLogSecret(dummySecret) // for spending full box
        .withDHTData(g, gX, gY, gXY, y)
        .build().sign(tx)
      println(signed.toJson(false))
    }
  }
}
