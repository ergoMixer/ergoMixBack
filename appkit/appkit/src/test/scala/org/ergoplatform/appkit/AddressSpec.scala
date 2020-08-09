package org.ergoplatform.appkit

import org.ergoplatform.{ErgoAddressEncoder, Pay2SAddress}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{PropSpec, Matchers}
import scorex.util.encode.Base16
import sigmastate.serialization.ErgoTreeSerializer



class AddressSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with AppkitTesting {

  property("encoding vector") {
    val addr = Address.create(addrStr)
    addr.isMainnet shouldBe false
    addr.isP2PK shouldBe true
    addr.toString shouldBe addrStr
  }

  property("Address fromMnemonic") {
    val mnemonic = SecretString.create("slow silly start wash bundle suffer bulb ancient height spin express remind today effort helmet")
    val addr = Address.fromMnemonic(NetworkType.TESTNET, mnemonic, SecretString.empty())
    addr.toString shouldBe addrStr
    val addr2 = Address.fromMnemonic(NetworkType.MAINNET, mnemonic, SecretString.empty())
    addr2.toString shouldNot be (addrStr)
  }

  property("create Address from ErgoTree with DHT") {
    val tree = "100207036ba5cfbc03ea2471fdf02737f64dbcd58c34461a7ec1e586dcd713dacbf89a120400d805d601db6a01ddd6027300d603b2a5730100d604e4c672030407d605e4c672030507eb02ce7201720272047205ce7201720472027205"
    implicit val encoder: ErgoAddressEncoder = ErgoAddressEncoder.apply(NetworkType.MAINNET.networkPrefix);
    val ergoTree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(Base16.decode(tree).get)
    val addr = Pay2SAddress.apply(ergoTree)
    val addr2 = encoder.fromProposition(ergoTree).get
    addr shouldBe addr2
  }
}
