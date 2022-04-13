package mixer

import dao._
import helpers.ErgoMixerUtils
import mocked.{MockedBlockExplorer, MockedBlockchainContext, MockedNetworkUtils}
import models.Models.{HopMix, MixGroupRequest, MixStatus, MixWithdrawStatus, MixingRequest, WithdrawTx}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import testHandlers.{TestSuite, WithdrawMixerDataset}

class WithdrawMixerSpec
  extends TestSuite {

  // Defining WithdrawMixer class parameters
  val networkUtils = new MockedNetworkUtils
  val blockExplorer = new MockedBlockExplorer
  val blockchainContext = new MockedBlockchainContext

  val ergoMixerUtils = mock[ErgoMixerUtils]
  when(ergoMixerUtils.getStackTraceStr(any())).thenReturn("MockedErgoMixerUtils: getStackTrace requested!")

  val mockedDBConfigProvider = mock[DatabaseConfigProvider]
  val daoUtils = new DAOUtils(mockedDBConfigProvider)

  private val dataset = WithdrawMixerDataset

  def createWithdrawMixerObject: WithdrawMixer = new WithdrawMixer(
    ergoMixerUtils,
    networkUtils.getMocked,
    blockExplorer.getMocked,
    daoUtils,
    daoContext.withdrawDAO,
    daoContext.mixingRequestsDAO,
    daoContext.mixingGroupRequestDAO,
    daoContext.hopMixDAO
  )

  /**
   * Name: processWithdraw
   * Purpose: Testing updating data in database
   * Dependencies:
   *    blockExplorer
   *    mixingRequestsDAO
   *    mixingGroupRequestDAO
   */
  property("processWithdraw should set mixRequest as Withdrawn if withdraw transaction confirmed enough and set group as complete if group is done") {
    // get test data from dataset
    val testSample = dataset.confirmedTx_dbData
    val mixRequest: MixingRequest = testSample._1
    val mixGroupRequest: MixGroupRequest = testSample._2
    val withdrawTx: WithdrawTx = testSample._3

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    daoContext.mixingGroupRequestDAO.clear
    daoContext.withdrawDAO.clear
    daoContext.mixingRequestsDAO.insert(mixRequest)
    daoContext.mixingGroupRequestDAO.insert(mixGroupRequest)
    daoContext.withdrawDAO.insert(withdrawTx)

    val withdrawMixer = createWithdrawMixerObject
    withdrawMixer.processWithdrawals()

    // verify change of data in database
    val db_reqs: Seq[(String, MixStatus, String)] = daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (req.id, req.mixStatus, req.withdrawStatus))
    db_reqs should contain ((mixRequest.id, MixStatus.Complete, "withdrawn"))
    val db_groups: Seq[(String, String)] = daoUtils.awaitResult(daoContext.mixingGroupRequestDAO.all).map(req => (req.id, req.status))
    db_groups should contain ((mixGroupRequest.id, "complete"))
  }

  /**
   * Name: processInitiateHops
   * Purpose: Testing updating data in database
   * Dependencies:
   *    blockExplorer
   *    blockchainContext (no mock)
   *    hopMixDAO
   *    withdrawDAO
   *    mixingRequestsDAO
   */
  property("processInitiateHops should set mixRequest as Complete and Withdrawn if withdraw transaction confirmed enough and also insert hopMix and delete the withdrawTx") {
    // get test data from dataset
    val testSample = dataset.confirmedTxInitiateHop_dbData
    val mixRequest: MixingRequest = testSample._1
    val withdrawTx: WithdrawTx = testSample._2
    val hopMix: HopMix = testSample._3

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.hopMixDAO.clear)
    daoUtils.awaitResult(daoContext.withdrawDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.clear)
    daoUtils.awaitResult(daoContext.mixingRequestsDAO.insert(mixRequest))
    daoUtils.awaitResult(daoContext.withdrawDAO.insert(withdrawTx))

    val withdrawMixer = createWithdrawMixerObject
    val processInitiateHopsMethod = PrivateMethod[Unit]('processInitiateHops)
    networkUtils.getMocked.usingClient {implicit ctx =>
      withdrawMixer invokePrivate processInitiateHopsMethod(withdrawTx, ctx)
    }

    // verify change of data in database
    val db_reqs: Seq[(String, MixStatus, String)] = daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (req.id, req.mixStatus, req.withdrawStatus))
    db_reqs should contain ((mixRequest.id, MixStatus.Complete, MixWithdrawStatus.UnderHop.value))
    val db_txs: Seq[String] = daoUtils.awaitResult(daoContext.withdrawDAO.all).map(_.mixId)
    db_txs shouldBe empty
    val db_hops: Seq[(String, Int, String)] = daoUtils.awaitResult(daoContext.hopMixDAO.all).map(hop => (hop.mixId, hop.round, hop.boxId))
    db_hops should contain ((hopMix.mixId, hopMix.round, hopMix.boxId))
  }

  /**
   * Name: processInitiateHops
   * Purpose: Testing updating data in database
   * Dependencies:
   *    blockExplorer
   *    blockchainContext
   *    withdrawDAO
   */
  property("processInitiateHops should delete withdrawTx if withdraw transaction not mined and not valid anymore") {
    // get test data from dataset
    val withdrawTx = dataset.notMinedWithdrawTx._1

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.withdrawDAO.clear)
    daoUtils.awaitResult(daoContext.withdrawDAO.insert(withdrawTx))

    val withdrawMixer = createWithdrawMixerObject
    val processInitiateHopsMethod = PrivateMethod[Unit]('processInitiateHops)
    implicit val ctx = blockchainContext.getMocked
    withdrawMixer invokePrivate processInitiateHopsMethod(withdrawTx, ctx)

    // verify change of data in database
    val db_txs: Seq[String] = daoUtils.awaitResult(daoContext.withdrawDAO.all).map(_.mixId)
    db_txs shouldBe empty
  }

}
