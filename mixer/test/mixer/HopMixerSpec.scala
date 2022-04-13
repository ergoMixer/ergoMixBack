package mixer

import dao._
import helpers.ErgoMixerUtils
import mocked.{MockedAliceOrBob, MockedBlockExplorer, MockedBlockchainContext, MockedErgoMixer, MockedNetworkUtils}
import models.Models.{HopMix, MixingRequest, WithdrawTx}
import org.ergoplatform.appkit.SignedTransaction
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import testHandlers.{HopMixerDataset, TestSuite}

class HopMixerSpec
  extends TestSuite {

  // Defining HopMixer class parameters
  val networkUtils = new MockedNetworkUtils
  val blockExplorer = new MockedBlockExplorer
  val blockchainContext = new MockedBlockchainContext
  val ergoMixer = new MockedErgoMixer
  val aliceOrBob = new MockedAliceOrBob

  val ergoMixerUtils = mock[ErgoMixerUtils]
  when(ergoMixerUtils.getStackTraceStr(any())).thenReturn("MockedErgoMixerUtils: getStackTrace requested!")

  val mockedDBConfigProvider = mock[DatabaseConfigProvider]
  val daoUtils = new DAOUtils(mockedDBConfigProvider)

  private val dataset = HopMixerDataset

  def createHopMixerObject: HopMixer = new HopMixer(
    ergoMixerUtils,
    ergoMixer.getMocked,
    aliceOrBob.getMocked,
    networkUtils.getMocked,
    blockExplorer.getMocked,
    daoUtils,
    daoContext.allMixDAO,
    daoContext.withdrawDAO,
    daoContext.hopMixDAO,
    daoContext.rescanDAO
  )

  /**
   * Name: processHopBox
   * Purpose: Testing updating data in database
   * Dependencies:
   *    blockExplorer
   *    blockchainContext
   *    aliceOrBob
   *    ergoMixer
   *    withdrawDAO
   *    mixingRequestsDAO
   */
  property("processHopBox should withdraw to mixRequest withdraw address if round is greater than or equal to hop rounds config") {
    // get test data from dataset
    val testSample_db = dataset.withdrawFromHop_dbData
    val testSample_spec = dataset.withdrawFromHop_specData
    val withdrawTx: WithdrawTx = testSample_db._1
    val mixRequest: MixingRequest = testSample_db._2
    val hopMix: HopMix = testSample_spec._1
    val signedTx: SignedTransaction = testSample_spec._2

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.withdrawDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(mixRequest))

    val hopMixer = createHopMixerObject
    val processHopBoxMethod = PrivateMethod[Unit]('processHopBox)
    implicit val ctx = blockchainContext.getMocked
    hopMixer invokePrivate processHopBoxMethod(hopMix, ctx)

    // verify change of data in database
    val db_txs: Seq[WithdrawTx] = daoUtils.awaitResult(daoContext.withdrawDAO.all)
    networkUtils.jsonToSignedTx(db_txs.head.toString) should equal (signedTx)
    db_txs.map(tx => (tx.mixId, tx.txId, tx.boxId)) should contain ((withdrawTx.mixId, withdrawTx.txId, withdrawTx.boxId))
  }

  /**
   * Name: processHopBox
   * Purpose: Testing updating data in database
   * Dependencies:
   *    blockExplorer
   *    blockchainContext
   *    aliceOrBob
   *    ergoMixer
   *    hopMixDAO
   *    mixingRequestsDAO
   */
  property("processHopBox should withdraw to next hop if round is less than hop rounds config") {
    // get test data from dataset
    val testSample_db = dataset.nextHop_dbData
    val hopMix: HopMix = testSample_db._1
    val mixRequest: MixingRequest = testSample_db._2
    val nextHopMix: HopMix = dataset.nextHop_specData

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.hopMixDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(mixRequest))
    daoUtils.awaitResult(daoContext.hopMixDAO.insert(hopMix))

    val hopMixer = createHopMixerObject
    val processHopBoxMethod = PrivateMethod[Unit]('processHopBox)
    implicit val ctx = blockchainContext.getMocked
    hopMixer invokePrivate processHopBoxMethod(hopMix, ctx)

    // verify change of data in database
    val db_hops: Seq[(String, Int, String)] = daoUtils.awaitResult(daoContext.hopMixDAO.all).map(hop => (hop.mixId, hop.round, hop.boxId))
    db_hops should contain ((nextHopMix.mixId, nextHopMix.round, nextHopMix.boxId))
  }

}
