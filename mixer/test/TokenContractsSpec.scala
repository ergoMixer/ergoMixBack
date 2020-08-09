package app

import java.math.BigInteger
import java.util

import special.collection.{Coll, PairColl}
import app.ErgoMix
import org.ergoplatform.appkit._
import org.ergoplatform.{ErgoAddressEncoder, Pay2SAddress}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.util.encode.Base16
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement

import scala.collection.JavaConverters._
import scala.collection.mutable

class TokenContractsSpec extends PropSpec with Matchers
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
  val batchPrices = new util.HashMap[Integer, java.lang.Long]()
  val batchSize = 60
  val batchPrice: Long = batchSize * TokenErgoMix.feeAmount
  batchPrices.put(batchSize, batchPrice)

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
      println(ctx.compileContract(
        ConstantsBuilder.create().item(
          "gZ", g.exp(x)
        ).build(),"{proveDlog(gZ)}"
      ).getErgoTree.complexity)

      val mnemonic = "kite grape era zone habit robust drop purse story correct also gas fix motion announce"
      val mnemonicPass = "abc"
      val prover = ctx.newProverBuilder()
        .withMnemonic(SecretString.create(mnemonic), SecretString.create(mnemonicPass))
        .build()
      val mix = new TokenErgoMix(ctx)
      val feeEmissionContract: ErgoContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("fullMixScriptHash", mix.fullMixScriptHash)
          .item("halfMixScriptHash", mix.halfMixScriptHash)
          .item("fee", TokenErgoMix.feeAmount)
          .item("mixerOwner", prover.getAddress.getPublicKey)
          .build(),
        mix.feeEmissionScript
      )
      val tokenEmissionContract: ErgoContract = ctx.compileContract(
        ConstantsBuilder.create()
          .item("tokenId", ErgoId.create(TokenErgoMix.tokenId).getBytes)
          .item("fullMixScriptHash", mix.fullMixScriptHash)
          .item("halfMixScriptHash", mix.halfMixScriptHash)
          .item("mixerOwner", prover.getAddress.getPublicKey)
          .build(),
        mix.tokenEmissionScript
      )

      // we spend token emission box and enter mixing as alice
      val tokenBox = ctx.newTxBuilder.outBoxBuilder
        .tokens(new ErgoToken(tokenId, 200))
        .value(1000000).contract(tokenEmissionContract)
        .registers(ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(Array[Byte]()), ErgoValue.of(batchPrices))
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val feeBox = ctx.newTxBuilder.outBoxBuilder
        .value(1000000000).contract(feeEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)

      val out = ctx.newTxBuilder().outBoxBuilder().value(10000).contract(feeEmissionContract).build()
      // spends token box, with dummy registers!
      prover.sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(tokenBox).asJava)
          .outputs(out, out)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
      // spends fee box
      prover.sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(feeBox).asJava)
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
        .registers(ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(Array[Byte]()), ErgoValue.of(batchPrices), ErgoValue.of(20))
        .value(1000000)
        .contract(mix.tokenEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(5000000)
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
        .value(tokenBox.getValue + batchPrice + poolValue / 20)
        .tokens(new ErgoToken(tokenId, tokenBox.getTokens.get(0).getValue - batchSize))
        .registers(tokenBox.getRegisters.asScala:_*)
        .contract(mix.tokenEmissionContract)
        .build()
      val tx = ctx.newTxBuilder().boxesToSpend(List(tokenBox, spendableBox).asJava)
        .outputs(halfBox, copy)
        .fee(TokenErgoMix.feeAmount)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = ctx.newProverBuilder().withDLogSecret(dummySecret).build().sign(tx)
      println(signed.toJson(false))

      // we have to be able to spend half box with our secret x
      ctx.newProverBuilder().withDLogSecret(x).build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(signed.getOutputsToSpend.get(0)).asJava)
          .sendChangeTo(trashAddress.getErgoAddress)
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
        .registers(ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(Array[Byte]()), ErgoValue.of(batchPrices))
        .value(1000000).contract(mix.tokenEmissionContract)
        .build()
        .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)
      val spendableBox = ctx.newTxBuilder.outBoxBuilder
        .value(50000000)
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
        .value(tokenBox.getValue + batchPrice)
        .registers(ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(g), ErgoValue.of(Array[Byte]()),ErgoValue.of(batchPrices))
        .tokens(new ErgoToken(tokenId, tokenBox.getTokens.get(0).getValue - batchSize))
        .contract(mix.tokenEmissionContract)
        .build()

      val tx = ctx.newTxBuilder().boxesToSpend(List(halfBox, spendableBox, tokenBox).asJava)
        .outputs(fullBox1, fullBox2, copy)
        .fee(TokenErgoMix.feeAmount)
        .sendChangeTo(trashAddress.getErgoAddress)
        .build()

      // we have to be able to spend half box
      val signed: SignedTransaction = ctx.newProverBuilder()
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

      // bob spends his full-box
      ctx.newProverBuilder()
        .withDLogSecret(y)
        .build().sign(
        ctx.newTxBuilder()
          .boxesToSpend(List(fullBox2).asJava)
          .sendChangeTo(trashAddress.getErgoAddress)
          .build()
      )
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
        .value(feeBox.getValue - TokenErgoMix.feeAmount)
        .contract(mix.feeEmissionContract)
        .build()
      val tx = ctx.newTxBuilder().boxesToSpend(List(fullBox, feeBox).asJava)
        .outputs(halfBox, feeCopy)
        .fee(TokenErgoMix.feeAmount)
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
        .value(feeBox.getValue - TokenErgoMix.feeAmount)
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
        .fee(TokenErgoMix.feeAmount)
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

