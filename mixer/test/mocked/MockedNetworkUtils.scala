package mocked

import network.NetworkUtils
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.scalatestplus.mockito.MockitoSugar

import app._
import org.ergoplatform.appkit.BlockchainContext


class MockedNetworkUtils
  extends MockitoSugar
  with HttpClientTesting {

  private val networkUtils = mock[NetworkUtils]

  def getMocked = networkUtils

  /**
   * Set the output type of NetworkUtils.UsingClient
   *  0: String
   *
   * @param t Int
   */
  def setUsingClientReturnType[T]: Unit = {
    when(networkUtils.usingClient(any())).thenAnswer((invocation: InvocationOnMock) => {
      val f = invocation.getArgument(0, classOf[BlockchainContext => T])
      val ergoClient: FileMockedErgoClient = createMockedErgoClient(MockData(Nil, Nil))

      ergoClient.execute { ctx =>
        f(ctx)
      }
    })
  }

  setUsingClientReturnType[String]

}
