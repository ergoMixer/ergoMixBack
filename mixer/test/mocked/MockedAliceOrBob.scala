package mocked

import mixinterface.AliceOrBob
import org.ergoplatform.appkit.SignedTransaction
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import testHandlers.HopMixerDataset

import java.math.BigInteger
import javax.inject.Singleton

@Singleton
class MockedAliceOrBob extends MockitoSugar {

  private val aliceOrBob = mock[AliceOrBob]
  private val dataset_hopMixer = HopMixerDataset

  def getMocked = aliceOrBob

  def setTestCases: Unit = {
    val withdrawFromHop = dataset_hopMixer.withdrawFromHop_aliceOrBobMockedData
    val nextHop = dataset_hopMixer.nextHop_aliceOrBobMockedData

    setReturnValue_spendHopBox(withdrawFromHop._1, withdrawFromHop._2, withdrawFromHop._3, withdrawFromHop._4)
    setReturnValue_spendHopBox(nextHop._1, nextHop._2, nextHop._3, nextHop._4)
  }

  /**
   * specify what to return when spendHopBox of mock class called
   *
   * @param secret proveDLog secret
   * @param boxId input box ID
   * @param destinationAddress output box address
   * @param tx generated signed transaction
   */
  def setReturnValue_spendHopBox(secret: BigInteger, boxId: String, destinationAddress: String, tx: SignedTransaction): Unit = when(aliceOrBob.spendHopBox(secret, boxId, destinationAddress)).thenReturn(tx)

  setTestCases

}
