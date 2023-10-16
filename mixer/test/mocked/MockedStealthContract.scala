package mocked

import javax.inject.Singleton

import scala.collection.JavaConverters._

import models.StealthModels.SpendBox
import org.ergoplatform.appkit.{BlockchainContext, ErgoToken, SignedTransaction}
import org.ergoplatform.ErgoBox
import org.ergoplatform.ErgoBox.TokenId
import org.mockito.invocation.InvocationOnMock
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.scalatestplus.mockito.MockitoSugar
import scorex.util.bytesToId
import scorex.util.encode.Base16
import sigmastate.eval._
import special.collection.Coll
import stealth.StealthContract
import wallet.WalletHelper

/**
 * mocking environment for test
 */
@Singleton
class MockedStealthContract extends MockitoSugar {

  def randomBytes(): Array[Byte] =
    Array.fill(32)((scala.util.Random.nextInt(256) - 128).toByte)

  def randomId(): String = {
    val bytes = randomBytes()
    Base16.encode(bytes)
  }

  def mockObjectsForSpendStealthBoxes(
    stealthSpy: StealthContract,
    stealthOriginal: StealthContract,
    networkUtils: MockedNetworkUtils
  )(implicit spyCtx: BlockchainContext): Unit = {
    // mock method of createProver
    doAnswer { (invocation: InvocationOnMock) =>
      networkUtils.getMocked.usingClient { implicit ctx =>
        val stealthId  = invocation.getArgument(0, classOf[String])
        val spendBoxes = invocation.getArgument(1, classOf[Seq[SpendBox]])
        stealthOriginal.createProver(stealthId, spendBoxes)(ctx)
      }
    }.when(stealthSpy).createProver(any(), any())(any())

    // mock method of newTxBuilder
    doAnswer(_ => networkUtils.getMocked.usingClient(implicit ctx => ctx.newTxBuilder()))
      .when(spyCtx)
      .newTxBuilder()

    // mock method of sendTransaction
    doAnswer { (invocation: InvocationOnMock) =>
      val SignedTx = invocation.getArgument(0, classOf[SignedTransaction])
      networkUtils.getMocked.usingClient(implicit ctx => SignedTx.getId)
    }.when(spyCtx).sendTransaction(any())
  }

  def createFakeStealthBox(address: String, mockedErgValueInBoxes: Long = 10000L, fakeTokenId: String = ""): ErgoBox = {
    val networkUtils = new MockedNetworkUtils
    networkUtils.getMocked.usingClient { ctx =>
      val txB = ctx.newTxBuilder()
      var boxBuilder = txB
        .outBoxBuilder()
        .value(mockedErgValueInBoxes)
        .contract(WalletHelper.getAddress(address).toErgoContract)
      if (fakeTokenId != "") boxBuilder = boxBuilder.tokens(new ErgoToken(fakeTokenId, 3000))
      val box = boxBuilder.build().convertToInputWith(randomId(), 1)

      var tokens: Coll[(TokenId, Long)] = Colls.emptyColl[(TokenId, Long)]
      val boxTokens                     = box.getTokens.asScala
      if (boxTokens.nonEmpty) {
        tokens =
          Colls.fromArray(boxTokens.toArray.map(token => (token.getId.getBytes.asInstanceOf[TokenId], token.getValue)))
      }
      val ergoBox = new ErgoBox(
        value            = box.getValue,
        ergoTree         = box.getErgoTree,
        additionalTokens = tokens,
        transactionId    = bytesToId(randomBytes()),
        index            = 0,
        creationHeight   = box.getCreationHeight
      )
      ergoBox
    }
  }
}
