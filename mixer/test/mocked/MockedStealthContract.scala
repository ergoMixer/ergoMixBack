package mocked

import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.{ConstantsBuilder, ErgoContract}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import scorex.util.bytesToId
import scorex.util.encode.Base64
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants.dlogGroup
import special.sigma.GroupElement
import stealth.StealthContract
import wallet.WalletHelper
import wallet.WalletHelper.randomBoxId

import scala.collection.mutable
import scala.language.postfixOps

/**
 * mocking environment for test
 * */

class MockedStealthContract {

  private val mockedStealthContract = mock(classOf[StealthContract])

  def getMockedStealthContract: StealthContract = mockedStealthContract

  def generateStealthContract(g: GroupElement, x: BigInt, script: String): ErgoContract = {
    val networkUtils = new MockedNetworkUtils

    val r = WalletHelper.randBigInt
    val y = WalletHelper.randBigInt
    val u: GroupElement = g.exp(x.bigInteger)

    val gr = Base64.encode(g.exp(r.bigInteger).getEncoded.toArray)
    val gy = Base64.encode(g.exp(y.bigInteger).getEncoded.toArray)
    val ur = Base64.encode(u.exp(r.bigInteger).getEncoded.toArray)
    val uy = Base64.encode(u.exp(y.bigInteger).getEncoded.toArray)

    networkUtils.getMocked.usingClient(ctx=> {
      val newScript = script
        .replace("GR", gr)
        .replace("GY", gy)
        .replace("UR", ur)
        .replace("UY", uy)
      val contract = ctx.compileContract(
        ConstantsBuilder.create()
          .build()
        , newScript)
      contract
    })
  }

  def getStealthBoxes(script: String): Seq[ErgoBox] = {
    val networkUtils = new MockedNetworkUtils

    val x = WalletHelper.randBigInt
    val g: GroupElement = dlogGroup.generator

    val outPuts = mutable.Buffer[ErgoBox]()
    for (_ <- 0 until 5) {
       networkUtils.getMocked.usingClient(ctx=> {

        val txB = ctx.newTxBuilder()
        val box = txB.outBoxBuilder()
          .value(10000L)
          .contract(generateStealthContract(g, x, script))
          .build().convertToInputWith(randomBoxId(),1)

        val ergoBox = new ErgoBox(box.getValue, box.getErgoTree, null  , null,  bytesToId("".getBytes()), 0, box.getCreationHeight)
         outPuts += ergoBox
      })
    }
    outPuts.toSeq
  }

}
