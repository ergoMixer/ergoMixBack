package mixer

import helpers.ErgoMixerUtils
import mocked._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import testHandlers.{MixScannerDataset, TestSuite}

class MixScannerSpec extends TestSuite {

  // Defining MixScanner class parameters
  val networkUtils  = new MockedNetworkUtils
  val blockExplorer = new MockedBlockExplorer

  val ergoMixerUtils: ErgoMixerUtils = mock[ErgoMixerUtils]
  when(ergoMixerUtils.getStackTraceStr(any())).thenReturn("MockedErgoMixerUtils: getStackTrace requested!")

  private val dataset = MixScannerDataset

  def createMixScannerObject: MixScanner = new MixScanner(
    networkUtils.getMocked,
    blockExplorer.getMocked
  )

  /**
   * Name: followFullMix and followHalfMix
   * Purpose: Testing function return type and value
   * Dependencies:
   *    blockExplorer
   */
  property("followFullMix and followHalfMix should be able to recover lost mix rounds") {
    // get test data from dataset
    val testSample   = dataset.followMix_specData
    val spentBoxId   = testSample._1
    val round        = testSample._2
    val masterSecret = testSample._3
    val result       = testSample._4

    val mixScanner = createMixScannerObject
    val res        = mixScanner.followFullMix(spentBoxId, round, masterSecret)

    // verify return value
    res should equal(result)
  }

  /**
   * Name: followWithdrawal
   * Purpose: Testing function return type and value
   * Dependencies:
   *    blockExplorer
   */
  property("followWithdrawal should be able to recover lost hop rounds and withdraw tx") {
    // get test data from dataset
    val testSample       = dataset.scanHopMix_specData
    val boxId            = testSample._1
    val masterSecret     = testSample._2
    val followedHops     = testSample._3
    val followedWithdraw = testSample._4

    val mixScanner = createMixScannerObject
    val res        = mixScanner.followWithdrawal(boxId, masterSecret)

    // verify return value
    res._1 should equal(followedHops)
    res._2 should equal(followedWithdraw)
  }

  /**
   * Name: followWithdrawal
   * Purpose: Testing function return type and value
   * Dependencies:
   *    blockExplorer
   */
  property("followWithdrawal should return withdraw tx and spent boxIds when there is a withdraw") {
    // get test data from dataset
    val testSample   = dataset.getWithdrawal_specData
    val lastBoxId    = testSample._1
    val masterSecret = testSample._2
    val result       = (testSample._3, testSample._4)

    val mixScanner = createMixScannerObject
    val res        = mixScanner.followWithdrawal(lastBoxId, masterSecret)

    // verify return value
    res should equal(result)
  }

}
