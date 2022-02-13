package mixer

import wallet.WalletHelper
import dao._
import dataset.TestDataset
import mocked.MockedNetworkUtils
import models.Models.{CovertAsset, FullMix, HalfMix, MixCovertRequest, MixState, MixingRequest, WithdrawTx}
import models.Models.MixWithdrawStatus.WithdrawRequested
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import testHandlers.TestSuite

class ErgoMixerSpec
  extends TestSuite {

  // Defining ErgoMixer class parameters
  val networkUtils = new MockedNetworkUtils

  val mockedDBConfigProvider = mock[DatabaseConfigProvider]
  val daoUtils = new DAOUtils(mockedDBConfigProvider)

  private val dataset = TestDataset

  def createErgoMixerObject: ErgoMixer = new ErgoMixer(
    networkUtils.getMocked,
    daoUtils,
    daoContext.allMixDAO,
    daoContext.mixingCovertRequestDAO,
    daoContext.covertDefaultsDAO,
    daoContext.covertAddressesDAO,
    daoContext.mixingRequestsDAO,
    daoContext.mixingGroupRequestDAO,
    daoContext.mixStateDAO,
    daoContext.fullMixDAO,
    daoContext.withdrawDAO
  )

  /**
   * Name: newCovertRequest
   * Purpose: Testing function return type and validate it's value, testing insertion of data in database
   * Dependencies:
   *    networkUtils
   *    mixingCovertRequestDAO
   *    covertAddressesDAO
   *    covertDefaultsDAO
   */
  property("newCovertRequest should return valid address when privateKey is empty") {
    // get test data from dataset
    val testSample_newCovertData = dataset.newCovertData
    val covertName: String = testSample_newCovertData._1
    val roundNum: Int = testSample_newCovertData._2
    val addresses: Seq[String] = testSample_newCovertData._3

    // make dependency tables ready before test
    daoContext.mixingCovertRequestDAO.clear
    daoContext.covertAddressesDAO.clear
    daoContext.covertDefaultsDAO.clear

    val ergoMixer = createErgoMixerObject
    val address_withoutPK: String = ergoMixer.newCovertRequest(covertName, roundNum, addresses, "")

    // verify insertion of data in database
    val db_reqs: Seq[(String, Int, Boolean)] = daoUtils.awaitResult(daoContext.mixingCovertRequestDAO.all).map(req => (req.nameCovert, req.numRounds, req.isManualCovert))
    db_reqs should contain (covertName, roundNum, false)
    val db_addresses: Seq[String] = daoUtils.awaitResult(daoContext.covertAddressesDAO.all).map(_._2)
    db_addresses should equal (addresses)
    val db_assetsSize: Int = daoUtils.awaitResult(daoContext.covertDefaultsDAO.size)
    db_assetsSize should equal (1)

    // verify return value
    WalletHelper.okAddresses(Seq(address_withoutPK))
  }

  /**
   * Name: newCovertRequest
   * Purpose: Testing function return type, validate it's value, testing insertion of data in database
   * Dependencies:
   *    networkUtils
   *    mixingCovertRequestDAO
   *    covertAddressesDAO
   *    covertDefaultsDAO
   */
  property("newCovertRequest should return valid address when privateKey does not exists in database") {
    // get test data from dataset
    val testSample_newCovertData = dataset.newCovertData
    val testSample_masterKeyData = dataset.notExistsMasterSecretKey
    val covertName: String = testSample_newCovertData._1
    val roundNum: Int = testSample_newCovertData._2
    val addresses: Seq[String] = testSample_newCovertData._3
    val masterKey: BigInt = testSample_masterKeyData

    // make dependency tables ready before test
    daoContext.mixingCovertRequestDAO.clear
    daoContext.covertAddressesDAO.clear
    daoContext.covertDefaultsDAO.clear

    val ergoMixer = createErgoMixerObject
    val address_withPK = ergoMixer.newCovertRequest(covertName, roundNum, addresses, masterKey.toString(16))

    // verify insertion of data in database
    val db_reqs: Seq[(String, Int, Boolean, BigInt)] = daoUtils.awaitResult(daoContext.mixingCovertRequestDAO.all).map(req => (req.nameCovert, req.numRounds, req.isManualCovert, req.masterKey))
    db_reqs should contain (covertName, roundNum, true, masterKey)
    val db_addresses: Seq[String] = daoUtils.awaitResult(daoContext.covertAddressesDAO.all).map(_._2)
    db_addresses should equal (addresses)
    val db_assetsSize: Int = daoUtils.awaitResult(daoContext.covertDefaultsDAO.size)
    db_assetsSize should equal (1)

    // verify return value
    WalletHelper.okAddresses(Seq(address_withPK))
  }

  /**
   * Name: newCovertRequest
   * Purpose: Testing function Exception throwing
   * Dependencies:
   *    networkUtils
   *    mixingCovertRequestDAO
   */
  property("newCovertRequest should throw exception when privateKey exists in database") {
    // get test data from dataset
    val testSample_newCovertData = dataset.newCovertData
    val testSample_masterKeyData = dataset.existsMasterSecretKey
    val covertName: String = testSample_newCovertData._1
    val roundNum: Int = testSample_newCovertData._2
    val addresses: Seq[String] = testSample_newCovertData._3
    val masterKey: BigInt = testSample_masterKeyData._1

    // make dependency tables ready before test
    daoContext.mixingCovertRequestDAO.clear
    daoContext.mixingCovertRequestDAO.insert(testSample_masterKeyData._2)

    val ergoMixer = createErgoMixerObject

    // verify throwing exception
    try {
      ergoMixer.newCovertRequest(covertName, roundNum, addresses, masterKey.toString(16))
      fail("didn't throw any exceptions")
    }
    catch {
      case e: Exception => if (e.getMessage != "this private key exist") fail(s"Wrong exception: ${e.getMessage}")
    }
  }

  /**
   * Name: handleCovertSupport
   * Purpose: Testing updating database record
   * Dependencies:
   *    mixingCovertRequestDAO
   *    covertDefaultsDAO
   */
  property("handleCovertSupport should update ring when asset exists") {
    // get test data from dataset
    val testSample = dataset.existsCovertAsset
    val covertId: String = testSample._1
    val tokenId: String = testSample._2
    val ring: Long = testSample._3
    val existsCovert: MixCovertRequest = testSample._4
    val existsAsset: CovertAsset = testSample._5

    // make dependency tables ready before test
    daoContext.mixingCovertRequestDAO.clear
    daoContext.covertDefaultsDAO.clear
    daoContext.mixingCovertRequestDAO.insert(existsCovert)
    daoContext.covertDefaultsDAO.insert(existsAsset)

    val ergoMixer = createErgoMixerObject
    ergoMixer.handleCovertSupport(covertId, tokenId, ring)

    // verify the update in database
    val db_assets: Seq[(String, String, Long)] = daoUtils.awaitResult(daoContext.covertDefaultsDAO.all).map(asset => (asset.covertId, asset.tokenId, asset.ring))
    db_assets should contain ((covertId, tokenId, ring))
  }

  /**
   * Name: handleCovertSupport
   * Purpose: Testing insertion of data into database
   * Dependencies:
   *    mixingCovertRequestDAO
   *    covertDefaultsDAO
   */
  property("handleCovertSupport should insert if asset doesn't exist in database") {
    // get test data from dataset
    val testSample = dataset.notExistsCovertAsset
    val covertId: String = testSample._1
    val tokenId: String = testSample._2
    val ring: Long = testSample._3
    val existsCovert: MixCovertRequest = testSample._4

    // make dependency tables ready before test
    daoContext.mixingCovertRequestDAO.clear
    daoContext.covertDefaultsDAO.clear
    daoContext.mixingCovertRequestDAO.insert(existsCovert)

    val ergoMixer = createErgoMixerObject
    ergoMixer.handleCovertSupport(covertId, tokenId, ring)

    // verify insertion of data in database
    val db_assets: Seq[(String, String, Long)] = daoUtils.awaitResult(daoContext.covertDefaultsDAO.all).map(asset => (asset.covertId, asset.tokenId, asset.ring))
    db_assets should contain ((covertId, tokenId, ring))
  }

  /**
   * Name: getWithdrawAddress
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixingRequestsDAO
   */
  property("getWithdrawAddress should return string when mixId exists in database") {
    // get test data from dataset
    val testSample = dataset.withdrawAddressOfMixRequest
    val mixId: String = testSample._1
    val withdrawAddress: String = testSample._2
    val request: MixingRequest = testSample._3

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    daoContext.mixingRequestsDAO.insert(request)

    val ergoMixer = createErgoMixerObject
    val res: String = ergoMixer.getWithdrawAddress(mixId)

    // verify return value
    res should be (withdrawAddress)
  }

  /**
   * Name: getRoundNum
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixStateDAO
   */
  property("getRoundNum should return int when mixId exists in database") {
    // get test data from dataset
    val testSample = dataset.roundNumOfMixState
    val mixId: String = testSample._1
    val round: Int = testSample._2
    val state: MixState = testSample._3

    // make dependency tables ready before test
    daoContext.mixStateDAO.clear
    daoContext.mixStateDAO.insert(state)

    val ergoMixer = createErgoMixerObject
    val res = ergoMixer.getRoundNum(mixId)

    // verify return value
    res should be (round)
  }

  /**
   * Name: getIsAlice
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixStateDAO
   */
  property("getIsAlice should return Boolean when mixId exists in database") {
    // get test data from dataset
    val testSample = dataset.isAliceOfMixState
    val mixId: String = testSample._1
    val isAlice: Boolean = testSample._2
    val state: MixState = testSample._3

    // make dependency tables ready before test
    daoContext.mixStateDAO.clear
    daoContext.mixStateDAO.insert(state)

    val ergoMixer = createErgoMixerObject
    val res = ergoMixer.getIsAlice(mixId)

    // verify return value
    res should be (isAlice)
  }

  /**
   * Name: withdrawMixNow
   * Purpose: Testing function return type and value, Testing updating database record
   * Dependencies:
   *    mixingRequestsDAO
   */
  property("withdrawMixNow should throw exception if withdrawAddress is empty or call updateWithdrawStatus if it exists") {
    // get test data from dataset
    val testSample_emptyAddress = dataset.emptyAddressMixId
    val testSample_withAddress = dataset.withAddressMixId
    val mixId_emptyAddress = testSample_emptyAddress._1
    val request_emptyAddress = testSample_emptyAddress._2
    val mixId_withAddress = testSample_withAddress._1
    val request_withAddress = testSample_withAddress._2

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    daoContext.mixingRequestsDAO.insert(request_emptyAddress)
    daoContext.mixingRequestsDAO.insert(request_withAddress)

    val ergoMixer = createErgoMixerObject

    // verify throwing exception
    try {
      ergoMixer.withdrawMixNow(mixId_emptyAddress)
      fail("didn't throw any exceptions")
    }
    catch {
      case e: Exception => if (e.getMessage != "Set a valid withdraw address first!") fail(s"Wrong exception: ${e.getMessage}")
    }

    ergoMixer.withdrawMixNow(mixId_withAddress)

    // verify the update in database
    val db_reqs: Seq[(String, String)] = daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (req.id, req.withdrawStatus))
    db_reqs should contain ((mixId_withAddress, WithdrawRequested.value))
  }

  /**
   * Name: getCovertCurrentMixing
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixingRequestsDAO
   */
  property("getCovertCurrentMixing should return Map[String, Long] when covertId exists in database") {
    // get test data from dataset
    val testSample = dataset.notWithdrawnMixingRequests
    val groupId: String = testSample._1
    val mp: Map[String, Long] = testSample._2
    val reqs: Seq[MixingRequest] = testSample._3

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    reqs.map(req => daoContext.mixingRequestsDAO.insert(req))

    val ergoMixer = createErgoMixerObject
    val res = ergoMixer.getCovertCurrentMixing(groupId)

    // verify return value
    res should be (mp)
  }

  /**
   * Name: getCovertRunningMixing
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixingRequestsDAO
   */
  property("getCovertRunningMixing should return Map[String, Long] when covertId exists in database") {
    // get test data from dataset
    val testSample = dataset.notWithdrawnRunningMixRequests
    val groupId: String = testSample._1
    val mp: Map[String, Long] = testSample._2
    val reqs: Seq[MixingRequest] = testSample._3

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    reqs.map(req => daoContext.mixingRequestsDAO.insert(req))

    val ergoMixer = createErgoMixerObject
    val res = ergoMixer.getCovertRunningMixing(groupId)

    // verify return value
    res should be (mp)
  }

  /**
   * Name: newMixRequest
   * Purpose: Testing function return type, validate it's value, testing insertion of data in database
   * Dependencies:
   *    networkUtils.usingClient
   *    mixingRequestsDAO
   */
  property("newMixRequest should return valid address") {
    // get test data from dataset
    val testSample = dataset.newMixData

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear

    val ergoMixer = createErgoMixerObject
    val address = ergoMixer.newMixRequest(testSample._1, testSample._2, testSample._3, testSample._4, testSample._5, testSample._6, testSample._7, testSample._8)

    // verify insertion of data in database
    val db_reqs: Seq[(String, Int, Long, Long, Long, Long, String, String)] = daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (
      req.withdrawAddress,
      req.numRounds,
      req.amount,
      req.neededAmount,
      req.mixingTokenAmount,
      req.neededTokenAmount,
      req.tokenId,
      req.groupId))
    db_reqs should contain ((testSample._1, testSample._2, testSample._3, testSample._4, testSample._5, testSample._6, testSample._7, testSample._8))

    // verify return value
    WalletHelper.okAddresses(Seq(address))
  }

  /**
   * Name: newMixGroupRequest
   * Purpose: Testing function return type, testing insertion of data in database
   * Dependencies:
   *    networkUtils.usingClient
   *    newMixRequest
   *    mixingGroupRequestDAO
   */
  property("newMixGroupRequest should return string without any error") {
    // get test data from dataset
    val testSample = dataset.newGroupData
    val requestArray = testSample._1
    val requestPrices = testSample._2
    val request = testSample._3

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    daoContext.mixingGroupRequestDAO.clear

    val ergoMixer = createErgoMixerObject
    val mixId: String = ergoMixer.newMixGroupRequest(requestArray)

    // verify insertion of data in database
    val db_reqs: Seq[(String, Int, Long, Long, Long, Long, String)] = daoUtils.awaitResult(daoContext.mixingRequestsDAO.all).map(req => (
      req.withdrawAddress,
      req.numRounds,
      req.amount,
      req.neededAmount,
      req.mixingTokenAmount,
      req.neededTokenAmount,
      req.tokenId))
    (requestArray zip requestPrices).foreach { case (req, price) => db_reqs should contain ((
      req.withdraw,
      req.token,
      req.amount,
      price._1,
      req.mixingTokenAmount,
      price._2,
      req.mixingTokenId
    ))}

    val db_groupReqs: Seq[(Long, String, Long, Long, Long, Long, Long, String)] = daoUtils.awaitResult(daoContext.mixingGroupRequestDAO.all).map(req => (
      req.neededAmount,
      req.status,
      req.doneDeposit,
      req.tokenDoneDeposit,
      req.mixingAmount,
      req.mixingTokenAmount,
      req.neededTokenAmount,
      req.tokenId))
    db_groupReqs should contain ((
      request.neededAmount,
      request.status,
      request.doneDeposit,
      request.tokenDoneDeposit,
      request.mixingAmount,
      request.mixingTokenAmount,
      request.neededTokenAmount,
      request.tokenId))
  }

  /**
   * Name: getFinishedForGroup
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixingRequestsDAO
   */
  property("getFinishedForGroup should return (Long, Long, Long)") {
    // get test data from dataset
    val testSample = dataset.roundFinishedData
    val groupId: String = testSample._1
    val result: (Int, Int, Int) = (testSample._4, testSample._3, testSample._2)
    val reqs: Seq[MixingRequest] = testSample._5

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    reqs.map(req => daoContext.mixingRequestsDAO.insert(req))

    val ergoMixer = createErgoMixerObject
    val res = ergoMixer.getFinishedForGroup(groupId)

    // verify return value
    res should be (result)
  }

  /**
   * Name: getProgressForGroup
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixingRequestsDAO
   *    mixStateDAO
   */
  property("getProgressForGroup should return (Long, Long)") {
    // get test data from dataset
    val testSample = dataset.roundProgressData
    val groupId: String = testSample._1
    val result: (Int, Int) = testSample._2
    val reqs: Seq[MixingRequest] = testSample._3
    val states: Seq[MixState] = testSample._4

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    daoContext.mixStateDAO.clear
    reqs.map(req => daoContext.mixingRequestsDAO.insert(req))
    states.map(state => daoContext.mixStateDAO.insert(state))

    val ergoMixer = createErgoMixerObject
    val res = ergoMixer.getProgressForGroup(groupId)

    // verify return value
    res should be (result)
  }

  /**
   * Name: getMixes
   * Purpose: Testing function return type and value
   * Dependencies:
   *    mixingRequestsDAO
   *    mixStateDAO
   *    withdrawDAO
   *    allMixDAO
   */
  property("getMixes should return Seq[Mixes]") {
    // get test data from dataset
    val testSample_DBData = dataset.dbMixes
    val testSample_All = dataset.allMixes
    val testSample_Active = dataset.activeMixes
    val testSample_Withdrawn = dataset.withdrawnMixes
    val reqs: Seq[MixingRequest] = testSample_DBData._1
    val states: Seq[MixState] = testSample_DBData._2
    val withdraws: Seq[WithdrawTx] = testSample_DBData._3
    val halves: Seq[HalfMix] = testSample_DBData._4
    val fulls: Seq[FullMix] = testSample_DBData._5

    // make dependency tables ready before test
    daoContext.mixingRequestsDAO.clear
    daoContext.mixStateDAO.clear
    daoContext.withdrawDAO.clear
    daoContext.halfMixDAO.clear
    daoContext.fullMixDAO.clear
    reqs.map(req => daoContext.mixingRequestsDAO.insert(req))
    states.map(state => daoContext.mixStateDAO.insert(state))
    withdraws.map(mix => daoContext.withdrawDAO.insert(mix))
    halves.map(mix => daoContext.halfMixDAO.insert(mix))
    fulls.map(mix => daoContext.fullMixDAO.insert(mix))

    val ergoMixer = createErgoMixerObject

    // verify return value
    val res_All = ergoMixer.getMixes(testSample_All._1, "all")
    res_All should be (testSample_All._2)

    val res_Active = ergoMixer.getMixes(testSample_Active._1, "active")
    res_Active should be (testSample_Active._2)

    val res_Withdrawn = ergoMixer.getMixes(testSample_Withdrawn._1, "withdrawn")
    res_Withdrawn should be (testSample_Withdrawn._2)
  }

}
