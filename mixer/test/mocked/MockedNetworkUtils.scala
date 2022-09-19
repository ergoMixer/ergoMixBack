package mocked

import network.NetworkUtils
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.scalatestplus.mockito.MockitoSugar
import app._
import mixinterface.TokenErgoMix
import org.ergoplatform.appkit.{BlockchainContext, SignedTransaction}

import javax.inject.Singleton

@Singleton
class MockedNetworkUtils
  extends MockitoSugar
  with HttpClientTesting {

  private val networkUtils = mock[NetworkUtils]

  def getMocked = networkUtils

  /**
   * Set the output type of NetworkUtils.UsingClient
   */
  def setUsingClientReturnType[T](): Unit = {
    when(networkUtils.usingClient(any())).thenAnswer((invocation: InvocationOnMock) => {
      val f = invocation.getArgument(0, classOf[BlockchainContext => T])
      val ergoClient: FileMockedErgoClient = createMockedErgoClient(MockData(Nil, Nil))

      ergoClient.execute { ctx =>
        f(ctx)
      }
    })
  }

  /**
   * Convert jsonString representation of transaction to SignedTransaction
   *
   * @param jsonString transaction Json string
   */
  def jsonToSignedTx(jsonString: String): SignedTransaction = networkUtils.usingClient { implicit ctx => ctx.signedTxFromJson(jsonString)}

  setUsingClientReturnType[String]()

  val tokenErgoMix: Option[TokenErgoMix] = networkUtils.usingClient { implicit ctx => Some(new TokenErgoMix(ctx)) }
  when(networkUtils.tokenErgoMix).thenReturn(tokenErgoMix)

}
