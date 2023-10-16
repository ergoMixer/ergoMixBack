package mocked

import javax.inject.Singleton

import org.ergoplatform.appkit.{BlockchainContext, Constants, SignedTransaction}
import org.mockito.invocation.InvocationOnMock
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import testHandlers.WithdrawMixerDataset

@Singleton
class MockedBlockchainContext extends MockitoSugar {

  private val context               = mock[BlockchainContext]
  private val dataset_withdrawMixer = WithdrawMixerDataset
  private val testNetworkUtils      = new MockedNetworkUtils

  def getMocked = context

  def setTestCases(): Unit = {
    val notMinedTx_initiateHop = dataset_withdrawMixer.notMinedWithdrawTx

    setReturnValue_signedTxFromJson(notMinedTx_initiateHop._1.toString, notMinedTx_initiateHop._2)

    setExceptionOnCall_sendTransaction(notMinedTx_initiateHop._2)

    when(context.compileContract(any(), any())).thenAnswer { (invocation: InvocationOnMock) =>
      val constants  = invocation.getArgument(0, classOf[Constants])
      val ergoScript = invocation.getArgument(1, classOf[String])
      testNetworkUtils.getMocked.usingClient(implicit ctx => ctx.compileContract(constants, ergoScript))
    }
  }

  /**
   * specify what to return when getSpendingTxId of mock class called
   *
   * @param txJson jsonString of tx
   * @param tx signed transaction
   */
  def setReturnValue_signedTxFromJson(txJson: String, tx: SignedTransaction): Unit =
    when(context.signedTxFromJson(txJson)).thenReturn(tx)

  /**
   * specify the mocked object to throw exception on sendTransaction method call
   *
   * @param tx signed transaction
   */
  def setExceptionOnCall_sendTransaction(tx: SignedTransaction): Unit = when(context.sendTransaction(tx)).thenAnswer {
    _ => throw new Throwable("Malformed transaction (unit-test case)")
  }

  setTestCases()

}
