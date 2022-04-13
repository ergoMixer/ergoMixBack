package mocked

import mixer.ErgoMixer
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import testHandlers.HopMixerDataset
import javax.inject.Singleton

@Singleton
class MockedErgoMixer extends MockitoSugar {

  private val ergoMixer = mock[ErgoMixer]
  private val dataset_hopMixer = HopMixerDataset

  def getMocked = ergoMixer

  def setTestCases: Unit = {
    val withdrawFromHop = dataset_hopMixer.withdrawFromHop_ergoMixerMockedData

    setReturnValue_getMasterSecret(withdrawFromHop._1, withdrawFromHop._2)
    setReturnValue_getWithdrawAddress(withdrawFromHop._1, withdrawFromHop._3)
  }

  /**
   * specify what to return when getMasterSecret of mock class called
   *
   * @param mixId mix request ID
   * @param masterKey master secret key
   */
  def setReturnValue_getMasterSecret(mixId: String, masterKey: BigInt): Unit = when(ergoMixer.getMasterSecret(mixId)).thenReturn(masterKey)

  /**
   * specify what to return when getWithdrawAddress of mock class called
   *
   * @param mixId mix request ID
   * @param withdrawAddress mix request withdraw address
   */
  def setReturnValue_getWithdrawAddress(mixId: String, withdrawAddress: String): Unit = when(ergoMixer.getWithdrawAddress(mixId)).thenReturn(withdrawAddress)

  setTestCases

}
