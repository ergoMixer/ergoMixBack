package stealth

import dao.DAOUtils
import dataset.TestDataset
import wallet.WalletHelper
import stealth.RegexUtils.RichRegex
import mocked.{MockedNetworkUtils, MockedStealthContract}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import scorex.util.encode.{Base16, Base58}
import org.mockito.Mockito.{times, verify}

import scala.language.postfixOps
import models.StealthModels._

import testHandlers.TestSuite

import java.util.UUID
import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}


class StealthContractSpec
  extends TestSuite {

  // Defining stealth class parameters
  val networkUtils = new MockedNetworkUtils
  private val dataset = TestDataset

  val mockedDBConfigProvider: DatabaseConfigProvider = mock[DatabaseConfigProvider]

  val daoUtils = new DAOUtils(mockedDBConfigProvider)

  def createStealthObject: StealthContract = new StealthContract(
    stealthDaoContext.stealthDAO,
    stealthDaoContext.outputDAO,
    daoUtils,
    networkUtils.getMocked,
  )

  def insertingNewStealth(random: Boolean, secretKey: String, stealthName: String): (String, String) = {
    val stealthId = UUID.randomUUID().toString
    var stealthModel = Stealth(stealthId, BigInt(secretKey, 16), stealthName)
    if (random) {
      breakable{
        while(true){
          val stealthName = "test name"
          val secretKey = WalletHelper.randBigInt
          if (secretKey.bitLength == 256){
            stealthModel = Stealth(stealthId, secretKey, stealthName)
            break
          }
        }
      }
    }

    stealthDaoContext.stealthDAO.insert(stealthModel)
    (secretKey, stealthName)
  }

  def creatingNewScan(): (String, ScanControllerModel) = {
    val scan = dataset.newScan
    val addedScan = stealthDaoContext.scanDAO.create(Scan(scan))
    (addedScan.scanId.toString, scan)
  }


  def readingErgoFullBlockData(): ExtractionResultModel = {
    val ergoFullBlock = dataset.newErgoBlock._1
    val createdOutputs = mutable.Buffer[ExtractionOutputResultModel]()
    val extractedInputs = mutable.Buffer[ExtractionInputResultModel]()
    val scanId: Types.ScanId = 1
    ergoFullBlock.transactions.foreach { tx =>
      tx.inputs.zipWithIndex.map {
        case (input, index) =>
          extractedInputs += ExtractionInputResult(
            input,
            index.toShort,
            ergoFullBlock.header,
            tx
          )
      }
      tx.outputs.foreach { out =>
        val pattern = """(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)""".r
        if (pattern matches Base16.encode(out.ergoTree.bytes)) {
          createdOutputs += ExtractionOutputResult(
            out,
            ergoFullBlock.header,
            tx,
            Seq(scanId)
          )
        }
      }
    }
    ExtractionResultModel(extractedInputs, createdOutputs)
  }


  property("check real stealth address") {
    val mockedStealthContract = new MockedStealthContract
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)

    val outPuts = mockedStealthContract.getStealthBoxes(dataset.stealthScripts._2)
    for (outPut <- outPuts) {
      nodeProcess.checkStealth(outPut) should be (true)
    }
  }

  property("check fake stealth address") {
    val mockedStealthContract = new MockedStealthContract
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)

    val outPuts = mockedStealthContract.getStealthBoxes(dataset.stealthScripts._1)

    for (outPut <- outPuts) {
      nodeProcess.checkStealth(outPut) should be(false)
    }
  }


  /**
   *
   * Name: createStealthAddressByPK
   * Purpose: Testing function returns a stealth address based on given pk, testing the created address be valid.
   *
   */
  property("createStealthAddressByPK should returns valid address with given PK") {
    val stealthObj = createStealthObject
    val pk: String = "sgLzPrbxA7rYhFGzqLVkmLzgashVeaF1egQnCP2RLDPB"
    val address: String = stealthObj.createStealthAddressByStealthPK(s"stealth$pk")
    val decodedAddress = Base58.decode(address).get
    val pattern = """[a-fA-F0-9]{2}(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)[a-fA-F0-9]{8}""".r
    assertResult(true) {
      pattern matches Base16.encode(decodedAddress)
    }
  }

  /**
   * Name: createStealthAddressByStealthName
   * Purpose: Testing function returns a stealth address based on given stealthName and without secret, testing the created address be valid.
   * Dependencies:
   * networkUtils
   * stealthDAO
   */
  property("createStealthAddressByStealthName should returns valid address with given name") {
    val (_, stealthName) = insertingNewStealth(random = true, "00", "")
    val stealthObj = createStealthObject

    // verify returns value

    val (_, _, stealthId) = stealthObj.createStealthAddressByStealthName(stealthName, 0)
    val stealth = daoUtils.awaitResult(stealthDaoContext.stealthDAO.selectByStealthId(stealthId)).get

    stealth.stealthName shouldEqual stealthName

  }

  /**
   * Name: isSpendable
   * Purpose: Testing function returns a Boolean value that the address is spendable by given secret key or not,
   * testing with valid data.
   * Dependencies:
   * networkUtils
   */
  property("check related stealth address is spendable") {
    val stealthObj = createStealthObject
    val stealthAddress: String = "6QBPS6hEtpbkVWWcTpcumA8Jzx8U4sKC65WrRmwz4q72sr724e7W3fHB1JmDKNiJooJEF1Ker2WLvTbEQq8im33WDX73ryUUHg2h5kswrkxGwda2tV3h4DRcDB1WtMC2ETtiia4ovAewFKd5bvsSDhVxBUa3o4Sw1p4D6zKeY26hhPgTXnYA9gnWEoW38zym5XH9DJfGcAqCEwdqyeEqGryUzK"
    val secret: String = "00dba44461055a057b6889aa33e1f1167525a1e9064658ae14ff692c96b61a1d12"

    stealthObj.isSpendable(stealthAddress, secret) should be (true)

  }

  /**
   * Name: isSpendable
   * Purpose: Testing function returns an Boolean value that the address is spendable by given secret key or not,
   * testing with invalid data.
   * Dependencies:
   * networkUtils
   */
  property("check unrelated stealth address is not spendable") {
    val stealthObj = createStealthObject
    val stealthAddress: String = "6QBPS6hGGHcXLBsmGVebkWtfWnSxLxG83kqXnaY9ukrrhZYVBu1UGweXUaNBD2Ekd67SHvjqMWcV8Lm8zB9j6Jr9XN8w24euGPxBzXWwijc5yhrUmY8evbtELQhZRS8goc8ZuMmzLJnhUHfofMABwXBq4QPDZkVTcmKCukMFWRjYY7mwGvLUCqNGiUgwbbiYVLuYHALzpK9LFHWLrVsUuCEauQ"
    val secret: String = "00dba44461055a057b6889aa33e1f1167525a1e9064658ae14ff692c96b61a1d12"

    stealthObj.isSpendable(stealthAddress, secret) should be (false)

  }

  /**
   * Name: getAllAddresses
   * Purpose: Testing function returns a list of stealth address based on given stealthName,
   * testing the returns contain all addresses with given stealthName.
   * Dependencies:
   * networkUtils
   * extractionResultDAO (all stealth tables and dao related)
   */
  property("getAllAddresses should returns list of address with given stealthName") {
    val stealthObj = createStealthObject

    // inserting new stealth data
    val (_, _) = insertingNewStealth(random = false, "00f225198449b56ef3ad51e37f120cdee0a1e75736510aa549e2b32f73123cabdc", "test1")
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = dataset.newHeader._1
    // reading ergo full block data
    val extractionResult = readingErgoFullBlockData()
    // storing outputs
    stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, ExtractedBlock(header))
    stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)
    stealthObj.updateBoxesStealthId(extractionResult.createdOutputs)

    val addresses = stealthObj.getStealthAddresses

    addresses.nonEmpty should be (true)
  }

  /**
   * Name: getUnspentBoxes
   * Purpose: Testing function returns a list of unspentBoxes related to a given address,
   * testing the return contain list of unspent boxes.
   * Dependencies:
   * networkUtils
   * extractionResultDAO (all stealth tables and dao related)
   */
  property("getUnspentBoxes should returns list of boxes related to a stealthId") {
    val (_, stealthName) = insertingNewStealth(random = false, "00f225198449b56ef3ad51e37f120cdee0a1e75736510aa549e2b32f73123cabdc", "test1")
    val stealthObj = createStealthObject

    // creating new scan
    creatingNewScan()

    // reading header data
    val header = dataset.newHeader._1
    if(!stealthDaoContext.extractedBlockDAO.exists(header.id)) {
      // reading ergo full block data
      val extractionResult = readingErgoFullBlockData()

      // storing outputs
      stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, ExtractedBlock(header))
      stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)
      stealthObj.updateBoxesStealthId(extractionResult.createdOutputs)
    }

    val stealth = daoUtils.awaitResult(stealthDaoContext.stealthDAO.selectByStealthName(stealthName)).head
    val boxes = stealthObj.getUnspentBoxes(stealth.stealthId)

    boxes.nonEmpty should be (true)

  }

  /**
   * Name: setSpendAddress
   * Purpose: Testing function set Spend address to the box with given boxId,
   * testing the box data in table be updated.
   * Dependencies:
   * networkUtils
   * extractionResultDAO (all stealth tables and dao related)
   */
  property("setSpendAddress should add Spend address to box with given boxId") {
    val stealthObj = createStealthObject

    // insert new stealth
    insertingNewStealth(random = false, "00f225198449b56ef3ad51e37f120cdee0a1e75736510aa549e2b32f73123cabdc", "test1")
    val (_, stealthName) = insertingNewStealth(random = false, "0080bca89c5b232e04e9783420266b8916e4f92b9872518a01886838f0828f5edc", "test2")
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = dataset.newHeader._1
    if(!stealthDaoContext.extractedBlockDAO.exists(header.id)) {

      // reading ergo full block data
      val extractionResult = readingErgoFullBlockData()
      // storing outputs
      stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, ExtractedBlock(header))
      stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)
      stealthObj.updateBoxesStealthId(extractionResult.createdOutputs)

    }

    val address = "9fWwFzQ9PxqCVrFAWyFsReu1HDU6k5CoxygWxpFXyi6hrcanmGa"
    val boxId = "af6d0f1e2036b9616c7872a03fd3bc23cc7b4d83049cca1e84873bf6a31b06e5"
    val data = List[StealthSpendAddress](StealthSpendAddress(boxId, address))
    val stealthId = daoUtils.awaitResult(stealthDaoContext.stealthDAO.selectByStealthName(stealthName)).head.stealthId

    stealthObj.setSpendAddress(stealthId, data)

    val box: ExtractedOutputModel = daoUtils.awaitResult(stealthDaoContext.outputDAO.getById(boxId)).get

    box.spendAddress shouldEqual address

  }

  /**
   * Name: spendStealthBoxes
   * Purpose: Testing function to spend stealth boxes
   * Dependencies:
   * networkUtils
   * extractionResultDAO (all stealth tables and dao related)
   */
  property("spendStealthBoxes should sign and Spend boxes with spend address") {
    val stealthObj = createStealthObject


    // insert new stealth
    insertingNewStealth(random = false, "00f225198449b56ef3ad51e37f120cdee0a1e75736510aa549e2b32f73123cabdc", "test1")
    val (_, stealthName) = insertingNewStealth(random = false, "0080bca89c5b232e04e9783420266b8916e4f92b9872518a01886838f0828f5edc", "test2")
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = dataset.newHeader._1
    if(!stealthDaoContext.extractedBlockDAO.exists(header.id)) {

      // reading ergo full block data
      val extractionResult = readingErgoFullBlockData()
      // storing outputs
      stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, ExtractedBlock(header))
      stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)
      stealthObj.updateBoxesStealthId(extractionResult.createdOutputs)
    }
    val address = "9fWwFzQ9PxqCVrFAWyFsReu1HDU6k5CoxygWxpFXyi6hrcanmGa"
    val boxId1 = "af340867e98d0f2a39cca64c970be1523d84be5315386b68937f91236b71ef6b"
    val boxId2 = "af6d0f1e2036b9616c7872a03fd3bc23cc7b4d83049cca1e84873bf6a31b06e5"
    val data = List[StealthSpendAddress](StealthSpendAddress(boxId1, address), StealthSpendAddress(boxId2, address))
    val stealthId = daoUtils.awaitResult(stealthDaoContext.stealthDAO.selectByStealthName(stealthName)).head.stealthId

    stealthObj.setSpendAddress(stealthId, data)

    verify(stealthObj.spendStealthBoxes(sendTransaction = false), times(1))

  }

}
