package mocked

import mixer.MixScanner
import models.Models.{FollowedHop, FollowedMix, FollowedWithdraw}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import testHandlers.MixScannerDataset

import javax.inject.Singleton

@Singleton
class MockedMixScanner extends MockitoSugar {

  private val mixScanner = mock[MixScanner]
  private val dataset_mixScanner = MixScannerDataset

  def getMocked = mixScanner

  def setTestCases: Unit = {
    val withdrawFromHop = dataset_mixScanner.rescan_mockedData
    val followFullMixMockedData = withdrawFromHop._1
    val withdrawWithHopMockedData = withdrawFromHop._2
    val followHopMix = dataset_mixScanner.mockFollowHopMix

    setReturnValue_followFullMix(followFullMixMockedData._1, followFullMixMockedData._2, followFullMixMockedData._3, followFullMixMockedData._4)
    setReturnValue_followWithdrawal(withdrawWithHopMockedData._1, withdrawWithHopMockedData._2, withdrawWithHopMockedData._3, withdrawWithHopMockedData._4)
    setReturnValue_followHopMix(followHopMix._1, followHopMix._2, followHopMix._3, followHopMix._4, followHopMix._5)
  }

  /**
   * specify what to return when followFullMix of mock class called
   *
   * @param boxId box ID
   * @param round box mix round
   * @param masterKey master secret key
   * @param followedMixes recovered mix rounds returned by method
   */
  def setReturnValue_followFullMix(boxId: String, round: Int, masterKey: BigInt, followedMixes: Seq[FollowedMix]): Unit =
    when(mixScanner.followFullMix(boxId, round, masterKey))
      .thenReturn(followedMixes)

  /**
   * specify what to return when followHopMix of mock class called
   *
   * @param boxId box ID
   * @param round box mix round
   * @param masterKey master secret key
   * @param followedHops recovered hop rounds returned by method
   * @param followedWithdraw optional recovered withdraw returned by method
   */
  def setReturnValue_followHopMix(boxId: String, round: Int, masterKey: BigInt, followedHops: Seq[FollowedHop], followedWithdraw: Option[FollowedWithdraw]): Unit =
    when(mixScanner.followHopMix(boxId, round, masterKey))
      .thenReturn((followedHops, followedWithdraw))

  /**
   * specify what to return when followWithdrawal of mock class called
   *
   * @param boxId box ID
   * @param masterKey master secret key
   * @param followedHops recovered hop rounds returned by method
   * @param followedWithdraw optional recovered withdraw returned by method
   */
  def setReturnValue_followWithdrawal(boxId: String, masterKey: BigInt, followedHops: Seq[FollowedHop], followedWithdraw: Option[FollowedWithdraw]): Unit =
    when(mixScanner.followWithdrawal(boxId, masterKey))
      .thenReturn((followedHops, followedWithdraw))

  setTestCases

}