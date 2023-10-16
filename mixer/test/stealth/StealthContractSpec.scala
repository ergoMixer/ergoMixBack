package stealth

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.language.postfixOps

import config.MainConfigs
import dao.DAOUtils
import helpers.ErgoMixerUtils
import mixinterface.TokenErgoMix
import mocked.{MockedBlockExplorer, MockedBlockchainContext, MockedNetworkUtils, MockedStealthContract}
import models.StealthModels.AssetFunds
import org.ergoplatform.appkit.{BlockchainContext, ErgoToken, SignedTransaction}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{spy, times, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import special.sigma.GroupElement
import testHandlers.{StealthDataset, TestSuite}
import wallet.WalletHelper

class StealthContractSpec extends TestSuite with BeforeAndAfterEach {

  // Defining stealth class parameters
  val networkUtils                                   = new MockedNetworkUtils
  val blockExplorer                                  = new MockedBlockExplorer
  val ergoMixerUtils: ErgoMixerUtils                 = mock[ErgoMixerUtils]
  val mockedDBConfigProvider: DatabaseConfigProvider = mock[DatabaseConfigProvider]
  val daoUtils                                       = new DAOUtils(mockedDBConfigProvider)
  private val stealthDataset                         = StealthDataset
  private val mockedStealthContract                  = new MockedStealthContract

  def createStealthAddressObject: StealthContract = new StealthContract(
    daoContext.stealthDAO,
    daoContext.outputDAO,
    daoContext.tokenInformationDAO,
    daoContext.extractedBlockDAO,
    blockExplorer.getMocked,
    daoUtils,
    ergoMixerUtils
  )

  override protected def beforeEach(): Unit =
    clearDatabase()

  def clearDatabase(): Unit = {
    daoContext.outputDAO.clear()
    daoContext.stealthDAO.clear()
  }

  /**
   * Name: createStealthAddress
   * Purpose: Testing function returns a stealth Obj based on given stealthName and without secret
   * Dependencies:
   * stealthDAO
   */
  property(
    "createStealthAddress should returns valid stealthName with given name, a random secret and store stealth obj in table"
  ) {
    val stealthName = this.stealthDataset.stealthSpecData._1._1.stealthName
    val stealthObj  = createStealthAddressObject
    val stealth     = stealthObj.createStealthAddress(stealthName, "")
    val stealthInDB = daoUtils.awaitResult(daoContext.stealthDAO.selectByStealthId(stealth.stealthId)).get

    stealth.secret should not be BigInt(0)
    stealth.stealthName shouldEqual stealthName
    stealth shouldEqual stealthInDB
  }

  /**
   * Name: createStealthAddress
   * Purpose: Testing function returns a stealth Obj based on given stealthName and specific secret
   * Dependencies:
   * stealthDAO
   */
  property("createStealthAddress should returns valid secret with given name and secret") {
    val specData    = this.stealthDataset.stealthSpecData._1._1
    val stealthObj  = createStealthAddressObject
    val stealth     = stealthObj.createStealthAddress(specData.stealthName, specData.secret.toString(16))
    val stealthInDB = daoUtils.awaitResult(daoContext.stealthDAO.selectByStealthId(stealth.stealthId)).get

    stealth.secret shouldEqual specData.secret
    stealth shouldEqual stealthInDB
  }

  /**
   * Name: generatePaymentAddressByStealthAddress
   * Purpose: Testing function invokes createPaymentAddress with expected GE based on given stealth address
   * Dependencies:
   * -
   */
  property("generatePaymentAddressByStealthAddress should invoke createPaymentAddress with expected GE") {
    val specData   = this.stealthDataset.stealthSpecData._1
    val expectedGE = WalletHelper.hexToGroupElement(specData._3)
    val stealthSpy = spy(createStealthAddressObject)
    stealthSpy.generatePaymentAddressByStealthAddress(specData._1.pk)
    val refCapture = ArgumentCaptor.forClass(classOf[GroupElement])
    verify(stealthSpy, times(1)).createPaymentAddress(refCapture.capture())
    refCapture.getValue.asInstanceOf[GroupElement] shouldBe expectedGE
  }

  /**
   * Name: validateStealthAddress
   * Purpose: Testing function throws an Throwable exception
   * Dependencies:
   * -
   */
  property("validateStealthAddress should throw wrong format address exception by given wrong stealthAddress") {
    val stealthAddress = this.stealthDataset.wrongAddressSpecData
    val stealthObj     = createStealthAddressObject

    val thrown = the[Exception] thrownBy stealthObj.validateStealthAddress(stealthAddress)
    thrown.getMessage shouldEqual s"wrong format stealthAddress $stealthAddress"
  }

  /**
   * Name: validateStealthAddress
   * Purpose: Testing function throws an Throwable exception for wrong checksum
   * Dependencies:
   * -
   */
  property("validateStealthAddress should throw wrong format exception by given wrong stealthAddress") {
    val stealthAddress = this.stealthDataset.wrongChecksumAddressSpecData
    val stealthObj     = createStealthAddressObject

    val thrown = the[Exception] thrownBy stealthObj.validateStealthAddress(stealthAddress)
    thrown.getMessage shouldEqual s"stealthAddress $stealthAddress is invalid (wrong checksum)"
  }

  /**
   * Name: createPaymentAddress
   * Purpose: Testing function returns a valid stealth address based on given stealth GroupElement
   * Dependencies:
   * NetworkUtils
   */
  property("createPaymentAddress should returns valid stealth address") {
    val specData        = this.stealthDataset.stealthSpecData._1
    val inGE            = WalletHelper.hexToGroupElement(specData._3)
    val stealthObj      = createStealthAddressObject
    val stealthAddress  = stealthObj.createPaymentAddress(inGE)
    val stealthErgoTree = WalletHelper.getErgoAddress(stealthAddress).script.bytesHex
    stealthAddress should not be ""
    (stealthErgoTree should fullyMatch).regex(stealthObj.stealthPattern)
  }

  /**
   * Name: isSpendable
   * Purpose: Testing function returns true based on given address and related secret
   * Dependencies:
   * -
   */
  property("isSpendable should returns true") {
    val specData       = this.stealthDataset.stealthSpecData._1
    val stealthSecret  = specData._1.secret
    val stealthAddress = specData._2

    val stealthObj  = createStealthAddressObject
    val isSpendable = stealthObj.isSpendable(stealthAddress, stealthSecret)
    isSpendable shouldEqual true
  }

  /**
   * Name: isSpendable
   * Purpose: Testing function returns false based on given address and irrelevant secret
   * Dependencies:
   * -
   */
  property("isSpendable should returns false") {
    val specData       = this.stealthDataset.stealthSpecData
    val stealthSecret  = specData._1._1.secret
    val stealthAddress = specData._2._2

    val stealthObj  = createStealthAddressObject
    val isSpendable = stealthObj.isSpendable(stealthAddress, stealthSecret)
    isSpendable shouldEqual false
  }

  /**
   * Name: getAllStealthWithUnspentValue
   * Purpose: Testing function returns StealthAddresses with total unspent value
   * Dependencies:
   * StealthDAO
   * OutputDAO
   */
  property("getAllStealthWithUnspentValue should returns expected value") {
    val stealthObj            = createStealthAddressObject
    val stealthSpecData       = this.stealthDataset.stealthSpecData
    val mockedErgValueInBoxes = 10000L
    val extractedOutputs      = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1._1))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._2._1))
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(
        Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3, extractedOutputs._5)
      )
    )

    val stealthAssetsList = stealthObj.getAllStealthWithUnspentAssets
    stealthAssetsList.length shouldEqual 2
    stealthAssetsList.head.value shouldEqual 10000
    stealthAssetsList.last.value shouldEqual 20000
    stealthAssetsList.last.assetsSize shouldEqual 1
  }

  /**
   * Name: getOutputs
   * Purpose: Testing function returns unspent ExtractedOutput by given stealthId
   * Dependencies:
   * StealthDAO
   * OutputDAO
   */
  property("getOutputs should returns unspent ExtractedOutput by given stealthId") {
    val stealthObj            = createStealthAddressObject
    val stealthSpecData       = this.stealthDataset.stealthSpecData._2
    val mockedErgValueInBoxes = 10000L
    val extractedOutputs      = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1))
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3))
    )

    val expectedStealthId = stealthSpecData._1.stealthId
    val outputs           = stealthObj.getOutputs(expectedStealthId, "unspent")
    outputs.length shouldEqual 1
    outputs.head.extractedOutput.withdrawTxId.isEmpty shouldEqual true
  }

  /**
   * Name: getOutputs
   * Purpose: Testing function should returns unspent ExtractedOutput and related TokenInformation by given stealthId
   * Dependencies:
   * StealthDAO
   * OutputDAO
   * TokenInformationDAO
   */
  property("getOutputs should returns unspent ExtractedOutput and related TokenInformation by given stealthId") {
    val stealthObj            = createStealthAddressObject
    val stealthSpecData       = this.stealthDataset.stealthSpecData._1
    val mockedErgValueInBoxes = 10000L
    val extractedOutputs      = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)
    val tokenInformation      = this.stealthDataset.tokenInformationSpecData

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1))
    daoUtils.awaitResult(daoContext.outputDAO.insert(Seq(extractedOutputs._5)))
    daoUtils.awaitResult(daoContext.tokenInformationDAO.insert(tokenInformation))

    val expectedStealthId = stealthSpecData._1.stealthId
    val outputs           = stealthObj.getOutputs(expectedStealthId, "unspent")
    outputs.length shouldEqual 1
    outputs.head.extractedOutput.withdrawTxId.isEmpty shouldEqual true
    outputs.head.tokensInformation.head shouldEqual tokenInformation
  }

  /**
   * Name: getOutputs
   * Purpose: Testing function returns all ExtractedOutput by given stealthId
   * Dependencies:
   * StealthDAO
   * OutputDAO
   */
  property("getOutputs should returns all ExtractedOutput by given stealthId") {
    val stealthObj            = createStealthAddressObject
    val stealthSpecData       = this.stealthDataset.stealthSpecData._1
    val mockedErgValueInBoxes = 10000L
    val extractedOutputs      = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1))
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3))
    )

    val expectedStealthId = stealthSpecData._1.stealthId
    val outputs           = stealthObj.getOutputs(expectedStealthId, "all")
    outputs.length shouldEqual 2
    outputs.head.extractedOutput.withdrawTxId.isEmpty shouldEqual true
    outputs.last.extractedOutput.withdrawTxId.get shouldEqual extractedOutputs._2.withdrawTxId.get
  }

  /**
   * Name: getOutputs
   * Purpose: Testing function returns an exception for unexpected status
   * Dependencies:
   * StealthDAO
   */
  property("getOutputs should returns an exception for unexpected status") {
    val stealthObj      = createStealthAddressObject
    val stealthSpecData = this.stealthDataset.stealthSpecData._1

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1))

    val unexpectedStatus  = "unexpected"
    val expectedStealthId = stealthSpecData._1.stealthId
    val thrown            = the[Exception] thrownBy stealthObj.getOutputs(expectedStealthId, unexpectedStatus)
    thrown.getMessage shouldEqual s"status $unexpectedStatus is invalid"
  }

  /**
   * Name: getOutputs
   * Purpose: Testing function returns an exception by given a fake stealthId
   * Dependencies:
   * StealthDAO
   * OutputDAO
   */
  property("getOutputs should returns an exception") {
    val stealthObj            = createStealthAddressObject
    val fakeStealthIdSpecData = this.stealthDataset.fakeStealthIdSpecData

    val thrown = the[Exception] thrownBy stealthObj.getOutputs(fakeStealthIdSpecData, "unspent")
    thrown.getMessage shouldEqual s"no such stealth id $fakeStealthIdSpecData"
  }

  /**
   * Name: setSpendAddress
   * Purpose: Testing function should set spendAddress in table of outputDAO
   * Dependencies:
   * OutputDAO
   */
  property("setSpendAddress should set expected spendAddress for boxes") {
    val stealthObj            = createStealthAddressObject
    val stealthSpecData       = this.stealthDataset.stealthSpecData._1
    val spendAddressSpecData  = this.stealthDataset.spendAddressesSpecData._1
    val mockedErgValueInBoxes = 10000L
    val extractedOutputs      = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3))
    )

    val expectedStealthId = stealthSpecData._1.stealthId
    val expectedBoxId     = extractedOutputs._1.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData)

    val expectedErgoBox = daoUtils.awaitResult(daoContext.outputDAO.getById(expectedBoxId)).get

    expectedErgoBox.stealthId.get shouldEqual expectedStealthId
    expectedErgoBox.withdrawAddress.get shouldEqual spendAddressSpecData
    expectedErgoBox.withdrawTxId shouldEqual Option.empty
  }

  /**
   * Name: setSpendAddress
   * Purpose: Testing function should throw expected exception
   * Dependencies:
   * OutputDAO
   */
  property("setSpendAddress should throw expected exception") {
    val stealthObj            = createStealthAddressObject
    val spendAddressSpecData  = this.stealthDataset.wrongWithdrawAddressSpecData
    val mockedErgValueInBoxes = 10000L
    val extractedOutputs      = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3))
    )

    val expectedBoxId = extractedOutputs._1.boxId

    val thrown = the[Exception] thrownBy stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData)
    thrown.getMessage shouldEqual "Invalid withdraw address"
  }

  /**
   * Name: spendStealthBoxes
   * Purpose: Testing function should create expected tx with erg and store in db for all unspent boxes that have spendAddress
   * Dependencies:
   * OutputDAO
   * BlockchainContext
   */
  property("spendStealthBoxes should create expected tx with erg in user box and store in db") {
    val stealthObj       = spy(createStealthAddressObject)
    val stealthSpecData  = this.stealthDataset.stealthSpecData
    val stealth1SpecData = stealthSpecData._1
    val stealth2SpecData = stealthSpecData._2

    val spendAddressSpecData        = this.stealthDataset.spendAddressesSpecData
    val mockedErgValueInBoxes: Long = 70000000000L
    val extractedOutputs            = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test and set spendAddresses
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealth1SpecData._1))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealth2SpecData._1))
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(
        Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3, extractedOutputs._4)
      )
    )

    var expectedBoxId = extractedOutputs._1.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._1)
    expectedBoxId = extractedOutputs._3.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._2)
    expectedBoxId = extractedOutputs._4.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._1)

    val totalInput1    = (extractedOutputs._3.value, mutable.Map.empty[String, Long])
    val systemErgFund1 = totalInput1._1 * MainConfigs.stealthImplementorFeePercent / 10000
    val userErgFund1   = totalInput1._1 - systemErgFund1 - MainConfigs.stealthFee
    val expectedOutFunds1 = AssetFunds.apply(
      user    = (userErgFund1, Seq()),
      service = (systemErgFund1, Seq())
    )

    val totalInput2    = (extractedOutputs._1.value + extractedOutputs._4.value, mutable.Map.empty[String, Long])
    val systemErgFund2 = totalInput2._1 * MainConfigs.stealthImplementorFeePercent / 10000
    val userErgFund2   = totalInput2._1 - systemErgFund2 - MainConfigs.stealthFee
    val expectedOutFunds2 = AssetFunds.apply(
      user    = (userErgFund2, Seq()),
      service = (systemErgFund2, Seq())
    )
    val blockchainContext = new MockedBlockchainContext

    implicit val spyCtx: BlockchainContext = blockchainContext.getMocked
    this.mockedStealthContract.mockObjectsForSpendStealthBoxes(stealthObj, createStealthAddressObject, networkUtils)
    // Testing function
    stealthObj.spendStealthBoxes()

    val refCapture = ArgumentCaptor.forClass(classOf[SignedTransaction])
    verify(spyCtx, times(2)).sendTransaction(refCapture.capture())
    val refCaptureList = refCapture.getAllValues
    val firstTime      = refCaptureList.get(0).asInstanceOf[SignedTransaction]
    val userBox1       = firstTime.getOutputsToSpend.get(0)
    val serviceBox1    = firstTime.getOutputsToSpend.get(1)
    userBox1.getErgoTree shouldEqual WalletHelper.getAddress(spendAddressSpecData._2).toErgoContract.getErgoTree
    userBox1.getValue shouldEqual expectedOutFunds1.user._1
    serviceBox1.getErgoTree shouldEqual TokenErgoMix.stealthIncome.toErgoContract.getErgoTree
    serviceBox1.getValue shouldEqual expectedOutFunds1.service._1

    val secondTime  = refCaptureList.get(1).asInstanceOf[SignedTransaction]
    val userBox2    = secondTime.getOutputsToSpend.get(0)
    val serviceBox2 = secondTime.getOutputsToSpend.get(1)

    userBox2.getErgoTree shouldEqual WalletHelper.getAddress(spendAddressSpecData._1).toErgoContract.getErgoTree
    userBox2.getValue shouldEqual expectedOutFunds2.user._1
    serviceBox2.getErgoTree shouldEqual TokenErgoMix.stealthIncome.toErgoContract.getErgoTree
    serviceBox2.getValue shouldEqual expectedOutFunds2.service._1

    daoUtils.awaitResult(daoContext.outputDAO.all).count(_.withdrawTxId.nonEmpty) shouldEqual 4
  }

  /**
   * Name: spendStealthBoxes
   * Purpose: Testing function should create expected tx with erg and token and store in db for all unspent boxes that have spendAddress
   * Dependencies:
   * OutputDAO
   * BlockchainContext
   */
  property("spendStealthBoxes should create expected tx with erg and token in user box and store in db") {
    val stealthObj       = spy(createStealthAddressObject)
    val stealthSpecData  = this.stealthDataset.stealthSpecData
    val stealth1SpecData = stealthSpecData._1
    val stealth2SpecData = stealthSpecData._2

    val spendAddressSpecData        = this.stealthDataset.spendAddressesSpecData
    val mockedErgValueInBoxes: Long = 3500000L
    val extractedOutputs            = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test and set spendAddresses
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealth1SpecData._1))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealth2SpecData._1))
    daoUtils.awaitResult(daoContext.outputDAO.insert(Seq(extractedOutputs._1, extractedOutputs._5)))

    var expectedBoxId = extractedOutputs._1.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._1)
    expectedBoxId = extractedOutputs._5.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._1)

    val totalInput1 =
      (extractedOutputs._1.value + extractedOutputs._5.value, mutable.Map(stealthDataset.tokenIdSpecData -> 3000))
    val systemErgFund           = MainConfigs.minPossibleErgInBox
    val userErgFund             = totalInput1._1 - systemErgFund - MainConfigs.stealthFee
    val implAssetFeeValue: Long = MainConfigs.stealthImplementorFeePercent * totalInput1._2.head._2 / 10000
    val userAssetValue          = totalInput1._2.head._2 - implAssetFeeValue
    val expectedOutFunds1 = AssetFunds.apply(
      user    = (userErgFund, Seq(new ErgoToken(stealthDataset.tokenIdSpecData, userAssetValue))),
      service = (systemErgFund, Seq(new ErgoToken(stealthDataset.tokenIdSpecData, implAssetFeeValue)))
    )

    val blockchainContext                  = new MockedBlockchainContext
    implicit val spyCtx: BlockchainContext = blockchainContext.getMocked
    this.mockedStealthContract.mockObjectsForSpendStealthBoxes(stealthObj, createStealthAddressObject, networkUtils)
    // Testing function
    stealthObj.spendStealthBoxes()

    val refCapture = ArgumentCaptor.forClass(classOf[SignedTransaction])
    verify(spyCtx, times(1)).sendTransaction(refCapture.capture())
    val refCaptureList = refCapture.getValue
    val firstTime      = refCaptureList.asInstanceOf[SignedTransaction]
    val userBox1       = firstTime.getOutputsToSpend.get(0)
    val serviceBox1    = firstTime.getOutputsToSpend.get(1)
    userBox1.getErgoTree shouldEqual WalletHelper.getAddress(spendAddressSpecData._1).toErgoContract.getErgoTree
    userBox1.getValue shouldEqual expectedOutFunds1.user._1
    userBox1.getTokens.asScala shouldEqual expectedOutFunds1.user._2
    serviceBox1.getErgoTree shouldEqual TokenErgoMix.stealthIncome.toErgoContract.getErgoTree
    serviceBox1.getValue shouldEqual expectedOutFunds1.service._1
    serviceBox1.getTokens.asScala shouldEqual expectedOutFunds1.service._2

    daoUtils.awaitResult(daoContext.outputDAO.all).count(_.withdrawTxId.nonEmpty) shouldEqual 2
  }

  /**
   * Name: spendStealthBoxes
   * Purpose: Testing function spendStealthBoxes should fail tx creation and store reason of that in db
   * Dependencies:
   * OutputDAO
   * BlockchainContext
   */
  property("spendStealthBoxes should fail tx creation and store reason of that in db") {
    val stealthObj       = spy(createStealthAddressObject)
    val stealthSpecData  = this.stealthDataset.stealthSpecData
    val stealth1SpecData = stealthSpecData._1
    val stealth2SpecData = stealthSpecData._2

    val spendAddressSpecData        = this.stealthDataset.spendAddressesSpecData
    val mockedErgValueInBoxes: Long = 10000L
    val extractedOutputs            = this.stealthDataset.extractedOutputs(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test and set spendAddresses
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealth1SpecData._1))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealth2SpecData._1))
    daoUtils.awaitResult(daoContext.outputDAO.insert(Seq(extractedOutputs._1, extractedOutputs._5)))

    var expectedBoxId = extractedOutputs._1.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._1)
    expectedBoxId = extractedOutputs._5.boxId
    stealthObj.setWithdrawAddress(expectedBoxId, spendAddressSpecData._1)

    val blockchainContext                  = new MockedBlockchainContext
    implicit val spyCtx: BlockchainContext = blockchainContext.getMocked
    this.mockedStealthContract.mockObjectsForSpendStealthBoxes(stealthObj, createStealthAddressObject, networkUtils)
    // Testing function
    stealthObj.spendStealthBoxes()

    val outputs = daoUtils.awaitResult(daoContext.outputDAO.all).filter(_.withdrawFailedReason.nonEmpty)
    val failReason =
      "Not enough ERG for transaction fee. Selected boxes have 0.00002 ERG (Required: 0.007 ERG).\nSelect and withdraw more boxes with enough ERG to the same withdraw address."

    outputs.length shouldEqual 2
    outputs.head.withdrawFailedReason.get shouldEqual failReason
  }

  /**
   * Name: checkStealth
   * Purpose: Testing function returns true based on given ergoTree when ergoTree matches with stealthPattern
   * Dependencies:
   * -
   */
  property("checkStealth should returns true when ergoTree matches with stealthPattern") {
    val specData        = this.stealthDataset.stealthSpecData._1
    val stealthErgoTree = specData._2.toErgoContract.getErgoTree

    val stealthObj  = createStealthAddressObject
    val isSpendable = stealthObj.checkStealth(stealthErgoTree)
    isSpendable shouldEqual true
  }

  /**
   * Name: checkStealth
   * Purpose: Testing function returns false based on given ergoTree when ergoTree doesn't match with stealthPattern
   * Dependencies:
   * -
   */
  property("checkStealth should returns false when ergoTree doesn't match with stealthPattern") {
    val fakeStealthErgoTree = this.stealthDataset.fakeStealthErgoTreeSpecData

    val stealthObj  = createStealthAddressObject
    val isSpendable = stealthObj.checkStealth(fakeStealthErgoTree)
    isSpendable shouldEqual false
  }

  /**
   * Name: updateBoxesIfSpendable
   * Purpose: Testing function should update stealthId for expected boxes that are spendable by specific stealth
   * Dependencies:
   * OutputDAO
   * StealthDAO
   */
  property("updateBoxesIfSpendable should update stealthId for expected boxes") {
    val stealthObj                   = createStealthAddressObject
    val updateBoxesIfSpendableMethod = PrivateMethod[Unit]('updateBoxesIfSpendable)
    val stealthSpecData              = this.stealthDataset.stealthSpecData
    val mockedErgValueInBoxes        = 10000L
    val extractedOutputs             = this.stealthDataset.extractedOutputsWithoutStealthId(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(
      daoContext.outputDAO.insert(
        Seq(extractedOutputs._1, extractedOutputs._2, extractedOutputs._3, extractedOutputs._4)
      )
    )
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1._1))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._2._1))
    // Testing function
    stealthObj.invokePrivate(
      updateBoxesIfSpendableMethod(
        Seq(stealthSpecData._1._1, stealthSpecData._2._1),
        Seq(extractedOutputs._1, extractedOutputs._2)
      )
    )

    val expectedErgoBox = daoUtils.awaitResult(daoContext.outputDAO.all)
    expectedErgoBox.count(_.stealthId.isDefined) shouldEqual 2
    expectedErgoBox.count(_.stealthId == Option(stealthSpecData._2._1.stealthId)) shouldEqual 2
  }

  /**
   * Name: updateBoxesIfSpendable
   * Purpose: Testing function should not update stealthId for boxes that those aren't spendable by stealth in db
   * Dependencies:
   * OutputDAO
   * StealthDAO
   */
  property(
    "updateBoxesIfSpendable should not update stealthId for boxes that those aren't spendable by stealth in db"
  ) {
    val stealthObj                   = createStealthAddressObject
    val updateBoxesIfSpendableMethod = PrivateMethod[Unit]('updateBoxesIfSpendable)
    val stealthSpecData              = this.stealthDataset.stealthSpecData
    val mockedErgValueInBoxes        = 10000L
    val extractedOutputs             = this.stealthDataset.extractedOutputsWithoutStealthId(stealthObj, mockedErgValueInBoxes)

    // make dependency tables ready before test
    daoUtils.awaitResult(daoContext.outputDAO.insert(Seq(extractedOutputs._4)))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._1._1))
    daoUtils.awaitResult(daoContext.stealthDAO.insert(stealthSpecData._2._1))
    // Testing function
    stealthObj.invokePrivate(
      updateBoxesIfSpendableMethod(
        Seq(stealthSpecData._1._1, stealthSpecData._2._1),
        Seq(extractedOutputs._4)
      )
    )

    val expectedErgoBox = daoUtils.awaitResult(daoContext.outputDAO.all)
    expectedErgoBox.count(_.stealthId.isDefined) shouldEqual 0
  }
}
