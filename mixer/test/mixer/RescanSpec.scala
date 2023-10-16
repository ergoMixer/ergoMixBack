package mixer

import dao.DAOUtils
import helpers.ErgoMixerUtils
import mocked._
import models.Status.MixStatus.Complete
import models.Status.MixWithdrawStatus.UnderHop
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import testHandlers.{MixScannerDataset, TestSuite}

class RescanSpec extends TestSuite {

  // Defining Rescan class parameters
  val networkUtils      = new MockedNetworkUtils
  val mixScanner        = new MockedMixScanner
  val blockchainContext = new MockedBlockchainContext

  val mockedDBConfigProvider = mock[DatabaseConfigProvider]
  val daoUtils               = new DAOUtils(mockedDBConfigProvider)

  val ergoMixerUtils = mock[ErgoMixerUtils]
  when(ergoMixerUtils.getStackTraceStr(any())).thenReturn("MockedErgoMixerUtils: getStackTrace requested!")

  private val dataset = MixScannerDataset

  def createRescanObject: Rescan = new Rescan(
    mixScanner.getMocked,
    daoUtils: DAOUtils,
    daoContext.mixingRequestsDAO,
    daoContext.rescanDAO,
    daoContext.mixStateHistoryDAO,
    daoContext.halfMixDAO,
    daoContext.fullMixDAO,
    daoContext.mixStateDAO,
    daoContext.hopMixDAO,
    daoContext.withdrawDAO
  )

  /**
   * Name: processRescan
   * Purpose: Testing insertion of data in database
   * Dependencies:
   *    mixingRequestDAO
   *    mixScanner
   *    (
   *      fullMixDAO,
   *      halfMixDAO,
   *      mixStateHistoryDAO,
   *      mixStateDAO,
   *      hopMixDAO,
   *      withdrawDAO
   *    )
   */
  property(
    "processRescan should insert recovered rounds of mix, hop and withdraw into their corresponding tables (rescan fullBox)"
  ) {
    // get test data from dataset
    val testSample_pendingRescan = dataset.rescan_specData
    val testSample_mixingRequest = dataset.rescan_dbData
    val testSample_result        = dataset.rescan_resultData
    val mixId                    = testSample_pendingRescan._1
    val round                    = testSample_pendingRescan._2
    val goBackward               = testSample_pendingRescan._3
    val boxType                  = testSample_pendingRescan._4
    val mixBoxId                 = testSample_pendingRescan._5

    val fullMixList         = testSample_result._1
    val halfMixList         = testSample_result._2
    val hopMixList          = testSample_result._3
    val mixStateHistoryList = testSample_result._4
    val mixState            = testSample_result._5
    val withdrawTx          = testSample_result._6

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.fullMixDAO.clear)
    daoUtils.awaitResult(daoContext.halfMixDAO.clear)
    daoUtils.awaitResult(daoContext.mixStateHistoryDAO.clear)
    daoUtils.awaitResult(daoContext.mixStateDAO.clear)
    daoUtils.awaitResult(daoContext.hopMixDAO.clear)
    daoUtils.awaitResult(daoContext.withdrawDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(testSample_mixingRequest))

    val rescan = createRescanObject
    rescan.processRescan(mixId, round, goBackward, boxType, mixBoxId)

    // verify return value
    val db_mixingRequest =
      daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (req.id, req.mixStatus, req.withdrawStatus))
    db_mixingRequest should contain((testSample_mixingRequest.id, Complete, UnderHop.value))
    val db_fullMix = daoUtils
      .awaitResult(daoContext.fullMixDAO.all)
      .map(mix => (mix.mixId, mix.round, mix.halfMixBoxId, mix.fullMixBoxId))
    db_fullMix should equal(fullMixList)
    val db_halfMix =
      daoUtils.awaitResult(daoContext.halfMixDAO.all).map(mix => (mix.mixId, mix.round, mix.halfMixBoxId))
    db_halfMix should equal(halfMixList)
    val db_mixStateHistory =
      daoUtils.awaitResult(daoContext.mixStateHistoryDAO.all).map(mix => (mix.id, mix.round, mix.isAlice))
    db_mixStateHistory should equal(mixStateHistoryList)
    val db_mixState =
      daoUtils.awaitResult(daoContext.mixStateDAO.all).map(state => (state.id, state.round, state.isAlice))
    db_mixState should contain((mixState.id, mixState.round, mixState.isAlice))
    val db_hopMix = daoUtils.awaitResult(daoContext.hopMixDAO.all).map(mix => (mix.mixId, mix.round, mix.boxId))
    db_hopMix should equal(hopMixList)
    val db_withdraw = daoUtils.awaitResult(daoContext.withdrawDAO.all).map(tx => (tx.mixId, tx.txId, tx.boxId))
    db_withdraw should contain((withdrawTx.mixId, withdrawTx.txId, withdrawTx.boxId))
  }

  /**
   * Name: processRescan
   * Purpose: Testing insertion of data in database
   * Dependencies:
   *    mixingRequestDAO
   *    mixScanner
   *    (
   *      fullMixDAO,
   *      halfMixDAO,
   *      mixStateHistoryDAO,
   *      mixStateDAO,
   *      hopMixDAO,
   *      withdrawDAO
   *    )
   */
  property(
    "processRescan should insert recovered rounds of hop and withdraw into their corresponding tables (rescan hopBox)"
  ) {
    // get test data from dataset
    val testSample_pendingRescan = dataset.rescanHop_specData
    val testSample_mixingRequest = dataset.rescanHop_dbData
    val testSample_result        = dataset.rescanHop_resultData
    val mixId                    = testSample_pendingRescan._1
    val round                    = testSample_pendingRescan._2
    val goBackward               = testSample_pendingRescan._3
    val boxType                  = testSample_pendingRescan._4
    val mixBoxId                 = testSample_pendingRescan._5

    val hopMixList = testSample_result._1
    val withdrawTx = testSample_result._2

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.fullMixDAO.clear)
    daoUtils.awaitResult(daoContext.halfMixDAO.clear)
    daoUtils.awaitResult(daoContext.mixStateHistoryDAO.clear)
    daoUtils.awaitResult(daoContext.mixStateDAO.clear)
    daoUtils.awaitResult(daoContext.hopMixDAO.clear)
    daoUtils.awaitResult(daoContext.withdrawDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(testSample_mixingRequest))

    val rescan = createRescanObject
    rescan.processRescan(mixId, round, goBackward, boxType, mixBoxId)

    // verify return value
    val db_mixingRequest =
      daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (req.id, req.mixStatus, req.withdrawStatus))
    db_mixingRequest should contain((testSample_mixingRequest.id, Complete, UnderHop.value))
    val db_fullMix = daoUtils
      .awaitResult(daoContext.fullMixDAO.all)
      .map(mix => (mix.mixId, mix.round, mix.halfMixBoxId, mix.fullMixBoxId))
    db_fullMix shouldBe empty
    val db_halfMix =
      daoUtils.awaitResult(daoContext.halfMixDAO.all).map(mix => (mix.mixId, mix.round, mix.halfMixBoxId))
    db_halfMix shouldBe empty
    val db_mixStateHistory =
      daoUtils.awaitResult(daoContext.mixStateHistoryDAO.all).map(mix => (mix.id, mix.round, mix.isAlice))
    db_mixStateHistory shouldBe empty
    val db_mixState =
      daoUtils.awaitResult(daoContext.mixStateDAO.all).map(state => (state.id, state.round, state.isAlice))
    db_mixState shouldBe empty
    val db_hopMix = daoUtils.awaitResult(daoContext.hopMixDAO.all).map(mix => (mix.mixId, mix.round, mix.boxId))
    db_hopMix should equal(hopMixList)
    val db_withdraw = daoUtils.awaitResult(daoContext.withdrawDAO.all).map(tx => (tx.mixId, tx.txId, tx.boxId))
    db_withdraw should contain((withdrawTx.mixId, withdrawTx.txId, withdrawTx.boxId))
  }

  /**
   * Name: processRescan
   * Purpose: Testing data removal in database
   * Dependencies:
   *    mixingRequestDAO
   *    mixScanner
   *    fullMixDAO,
   *    halfMixDAO
   */
  property("processRescan should previous mix box (half box) in backward rescanning") {
    // get test data from dataset
    val testSample_pendingRescan = dataset.backwardRescanFull_specData
    val testSample_dbData        = dataset.backwardRescanMix_dbData
    val testSample_result        = dataset.backwardRescan_mixResultData
    val mixId                    = testSample_pendingRescan._1
    val round                    = testSample_pendingRescan._2
    val goBackward               = testSample_pendingRescan._3
    val boxType                  = testSample_pendingRescan._4
    val mixBoxId                 = testSample_pendingRescan._5

    val fullMixList = testSample_result._1
    val halfMixList = testSample_dbData._3.map(mix => (mix.mixId, mix.round, mix.halfMixBoxId))

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.fullMixDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(testSample_dbData._1))
    testSample_dbData._2.foreach(full => daoUtils.awaitResult(daoContext.fullMixDAO.insert(full)))
    testSample_dbData._3.foreach(half => daoUtils.awaitResult(daoContext.halfMixDAO.insert(half)))

    val rescan = createRescanObject
    rescan.processRescan(mixId, round, goBackward, boxType, mixBoxId)

    // verify return value
    val db_fullMix = daoUtils
      .awaitResult(daoContext.fullMixDAO.all)
      .map(mix => (mix.mixId, mix.round, mix.halfMixBoxId, mix.fullMixBoxId))
    db_fullMix should equal(fullMixList)
    val db_halfMix =
      daoUtils.awaitResult(daoContext.halfMixDAO.all).map(mix => (mix.mixId, mix.round, mix.halfMixBoxId))
    db_halfMix should equal(halfMixList)
  }

  /**
   * Name: processRescan
   * Purpose: Testing data removal in database
   * Dependencies:
   *    mixingRequestDAO
   *    mixScanner
   *    fullMixDAO,
   *    halfMixDAO
   */
  property("processRescan should delete previous mix box (full box) in backward rescanning") {
    // get test data from dataset
    val testSample_pendingRescan = dataset.backwardRescanHalf_specData
    val testSample_dbData        = dataset.backwardRescanMix_dbData
    val testSample_result        = dataset.backwardRescan_mixResultData
    val mixId                    = testSample_pendingRescan._1
    val round                    = testSample_pendingRescan._2
    val goBackward               = testSample_pendingRescan._3
    val boxType                  = testSample_pendingRescan._4
    val mixBoxId                 = testSample_pendingRescan._5

    val fullMixList = testSample_result._1
    val halfMixList = testSample_result._2

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.fullMixDAO.clear)
    daoUtils.awaitResult(daoContext.halfMixDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(testSample_dbData._1))
    testSample_dbData._2.foreach(full => daoUtils.awaitResult(daoContext.fullMixDAO.insert(full)))
    testSample_dbData._3.foreach(half => daoUtils.awaitResult(daoContext.halfMixDAO.insert(half)))

    val rescan = createRescanObject
    rescan.processRescan(mixId, round, goBackward, boxType, mixBoxId)

    // verify return value
    val db_fullMix = daoUtils
      .awaitResult(daoContext.fullMixDAO.all)
      .map(mix => (mix.mixId, mix.round, mix.halfMixBoxId, mix.fullMixBoxId))
    db_fullMix should equal(fullMixList)
    val db_halfMix =
      daoUtils.awaitResult(daoContext.halfMixDAO.all).map(mix => (mix.mixId, mix.round, mix.halfMixBoxId))
    db_halfMix should equal(halfMixList)
  }

  /**
   * Name: processRescan
   * Purpose: Testing data removal in database
   * Dependencies:
   *    mixingRequestDAO
   *    mixScanner
   *    hopMixDAO
   */
  property("processRescan should delete last hop box in backward rescanning") {
    // get test data from dataset
    val testSample_pendingRescan = dataset.backwardRescanHop_specData
    val testSample_dbData        = dataset.backwardRescanHop_dbData
    val mixId                    = testSample_pendingRescan._1
    val round                    = testSample_pendingRescan._2
    val goBackward               = testSample_pendingRescan._3
    val boxType                  = testSample_pendingRescan._4
    val mixBoxId                 = testSample_pendingRescan._5

    val hopMixList = dataset.backwardRescan_hopResultData

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.hopMixDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(testSample_dbData._1))
    testSample_dbData._2.foreach(hop => daoUtils.awaitResult(daoContext.hopMixDAO.insert(hop)))

    val rescan = createRescanObject
    rescan.processRescan(mixId, round, goBackward, boxType, mixBoxId)

    // verify return value
    val db_hopMix = daoUtils.awaitResult(daoContext.hopMixDAO.all).map(mix => (mix.mixId, mix.round, mix.boxId))
    db_hopMix should equal(hopMixList)
  }

}
