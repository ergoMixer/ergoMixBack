package stealth

import dao.DAOUtils
import dataset.TestDataset.readJsonFile
import io.circe.parser.parse
import mocked.MockedNetworkUtils
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import scorex.util.encode.Base58
import wallet.WalletHelper
import wallet.RegexUtils._

import scala.language.postfixOps
import testHandlers.StealthTestSuite
import models.StealthModel
import models.StealthModel.{ExtractedOutputModel, StealthSpendAddress}
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.Header

import java.util.UUID
import scala.collection.mutable


class StealthContractSpec
  extends StealthTestSuite {

  // Defining stealth class parameters
  val networkUtils = new MockedNetworkUtils

  val mockedDBConfigProvider: DatabaseConfigProvider = mock[DatabaseConfigProvider]
  val daoUtils = new DAOUtils(mockedDBConfigProvider)

  def createStealthObject: StealthContract = new StealthContract(
    stealthDaoContext.stealthDAO,
    stealthDaoContext.outputDAO,
    daoUtils,
    networkUtils.getMocked,
  )

  def insertingNewStealth( random: Boolean, secretKey: String, stealthName: String): (String, String) = {
    val stealthId = UUID.randomUUID().toString
    var stealthModel = StealthModel.Stealth(stealthId, secretKey, stealthName)
    if (random) {
      val stealthName = "test name"
      val secretKey = WalletHelper.toHexString(WalletHelper.randBigInt.toByteArray)
      stealthModel = StealthModel.Stealth(stealthId, secretKey, stealthName)
    }
    // make dependency tables ready before test

    stealthDaoContext.stealthDAO.insert(stealthModel)
    (secretKey, stealthName)
  }

  def creatingNewScan(): (String, StealthModel.ScanControllerModel) = {
    val scan = readJsonFile("./test/dataset/SampleStealth_newScan.json")
    val scanJson = parse(scan).toOption.get
    val scanModel = StealthModel.Scan.scanDecoder.decodeJson(scanJson).toTry.get
    val addedScan = stealthDaoContext.scanDAO.create(StealthModel.Scan(scanModel))
    (addedScan.scanId.toString, scanModel)
  }

  def readingHeaderData(): Header = {
    val headerJson = parse(readJsonFile("./test/dataset/SampleStealth_ergoFullBlockHeader.json")).toOption.get
    headerJson.as[Header].toOption.get
  }

  def readingErgoFullBlockData(): StealthModel.ExtractionResultModel = {
    val txsAsJson = parse(readJsonFile("./test/dataset/SampleStealth_ergoFullBlock.json")).toOption.get
    val ergoFullBlock = txsAsJson.as[ErgoFullBlock].toOption.get
    val createdOutputs = mutable.Buffer[StealthModel.ExtractionOutputResultModel]()
    val extractedInputs = mutable.Buffer[StealthModel.ExtractionInputResultModel]()
    val scanId: StealthModel.Types.ScanId = 1
    ergoFullBlock.transactions.foreach { tx =>
      tx.inputs.zipWithIndex.map {
        case (input, index) =>
          extractedInputs += StealthModel.ExtractionInputResult(
            input,
            index.toShort,
            ergoFullBlock.header,
            tx
          )
      }
      tx.outputs.foreach { out =>
        val pattern = """(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)""".r
        if (pattern matches WalletHelper.toHexString(out.ergoTree.bytes)) {
          createdOutputs += StealthModel.ExtractionOutputResult(
            out,
            ergoFullBlock.header,
            tx,
            Seq(scanId)
          )
        }
      }
    }
    StealthModel.ExtractionResultModel(extractedInputs, createdOutputs)
  }

  /**
   *
   * Name: createStealthAddressByPK
   * Purpose: Testing function returns a stealth address based on given pk, testing the created address be valid.
   *
   */
  property("createStealthAddressByPK should returns valid address with given PK") {
    val stealthObj = createStealthObject
    val pk: String = "03b365599d8b7affb1e405d9aee464088135927b2c207055f8eb35e7da2bf4aa1a"
    val address: String = stealthObj.createStealthAddressByStealthPK(s"stealth:${pk}")
    val decodedAddress = Base58.decode(address).get
    val pattern = """[a-fA-F0-9]{2}(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)[a-fA-F0-9]{8}""".r
    assertResult(true) {
      pattern matches WalletHelper.toHexString(decodedAddress)
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
    val (_, stealthName) = insertingNewStealth(random = true, "", "")
    val stealthObj = createStealthObject

    // verify returns value

    val address: String = stealthObj.createStealthAddressByStealthName(stealthName, "")
    val decodedAddress = Base58.decode(address).get
    val pattern = """[a-fA-F0-9]{2}(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)[a-fA-F0-9]{8}""".r
    assertResult(true) {
      pattern matches WalletHelper.toHexString(decodedAddress)
    }
  }

  /**
   * Name: createStealthAddressByStealthName
   * Purpose: Testing function returns a stealth address based on given stealthName and with secret, testing the created address be valid.
   * Dependencies:
   * networkUtils
   * stealthDAO
   */
  property("createStealthAddressByStealthName should returns valid address with given name") {
    val (_, stealthName) = insertingNewStealth(random = true, "", "")
    val stealthObj = createStealthObject

    // verify returns value
    val secret = WalletHelper.toHexString(WalletHelper.randBigInt.toByteArray)
    val address: String = stealthObj.createStealthAddressByStealthName(stealthName, secret)
    val decodedAddress = Base58.decode(address).get
    val pattern = """[a-fA-F0-9]{2}(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)[a-fA-F0-9]{8}""".r
    assertResult(true) {
      pattern matches WalletHelper.toHexString(decodedAddress)
    }
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
    assertResult(true) {
      stealthObj.isSpendable(stealthAddress, secret)
    }
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
    assertResult(false) {
      stealthObj.isSpendable(stealthAddress, secret)
    }
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
    // inserting new stealth data
    val (_, stealthName) = insertingNewStealth(random = false, "00f225198449b56ef3ad51e37f120cdee0a1e75736510aa549e2b32f73123cabdc", "test1")
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = readingHeaderData()
    // reading ergo full block data
    val extractionResult = readingErgoFullBlockData()
    // storing outputs
    stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, StealthModel.ExtractedBlock(header))
    stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)

    val stealthObj = createStealthObject
    val addresses = stealthObj.getAddresses(stealthName, 500000)

    assertResult(true) {
      addresses.nonEmpty
    }
  }

  /**
   * Name: getUnspentBoxes
   * Purpose: Testing function returns a list of unspentBoxes related to a given address,
   * testing the return contain list of unspent boxes.
   * Dependencies:
   * networkUtils
   * extractionResultDAO (all stealth tables and dao related)
   */
  property("getUnspentBoxes should returns list of boxes related to an address") {
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = readingHeaderData()
    // reading ergo full block data
    val extractionResult = readingErgoFullBlockData()
    // storing outputs
    stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, StealthModel.ExtractedBlock(header))
    stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)

    val stealthObj = createStealthObject
    val address = "6QBPS6hFrveGWPXXJewGMVBty6upa2muunTgwisxKs2ACHiYDZJunNAYtLUBHpUDXMFnmaLUaBU8SZQhMLa8JBFZiD7Ra5QxjJzMYwFQJTBWWDEKRrynxL7vXijJas3Y3BWztDjoRzCQhhS9k8GPH4Uf7D7ckHiK9eUzJtqi7bQb12arYCJBm5J27aTXewNruhqtEG7LrN3HzypK5Jn5T7aiug"
    val boxes = stealthObj.getUnspentBoxes(address, 500000)
    assertResult(true) {
      boxes.nonEmpty
    }
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
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = readingHeaderData()
    // reading ergo full block data
    val extractionResult = readingErgoFullBlockData()
    // storing outputs
    stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, StealthModel.ExtractedBlock(header))
    stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)

    val stealthObj = createStealthObject

    val address = "9fWwFzQ9PxqCVrFAWyFsReu1HDU6k5CoxygWxpFXyi6hrcanmGa"
    val boxId = "af6d0f1e2036b9616c7872a03fd3bc23cc7b4d83049cca1e84873bf6a31b06e5"
    val data = List[StealthSpendAddress](StealthSpendAddress(boxId, address))
    val stealthId = UUID.randomUUID().toString

    stealthObj.setSpendAddress(stealthId, data)

    val box: ExtractedOutputModel = daoUtils.awaitResult(stealthDaoContext.outputDAO.getById(boxId)).get
    assertResult(true) {
      box.spendAddress === address
    }
  }

  /**
   * Name: spendStealthBoxes
   * Purpose: Testing function to spend stealth boxes
   * Dependencies:
   * networkUtils
   * extractionResultDAO (all stealth tables and dao related)
   */
  property("spendStealthBoxes should add Spend address to box with given boxId") {
    // insert new stealth
    insertingNewStealth(random = false, "00f225198449b56ef3ad51e37f120cdee0a1e75736510aa549e2b32f73123cabdc", "test1")
    insertingNewStealth(random = false, "0080bca89c5b232e04e9783420266b8916e4f92b9872518a01886838f0828f5edc", "test2")
    // creating new scan
    creatingNewScan()
    // reading header data
    val header = readingHeaderData()
    // reading ergo full block data
    val extractionResult = readingErgoFullBlockData()
    // storing outputs
    stealthDaoContext.extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, StealthModel.ExtractedBlock(header))
    stealthDaoContext.extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)

    val stealthObj = createStealthObject

    val address = "9fWwFzQ9PxqCVrFAWyFsReu1HDU6k5CoxygWxpFXyi6hrcanmGa"
    val boxId1 = "af340867e98d0f2a39cca64c970be1523d84be5315386b68937f91236b71ef6b"
    val boxId2 = "af6d0f1e2036b9616c7872a03fd3bc23cc7b4d83049cca1e84873bf6a31b06e5"
    val data = List[StealthSpendAddress](StealthSpendAddress(boxId1, address), StealthSpendAddress(boxId2, address))
    val stealthId = UUID.randomUUID().toString

    stealthObj.setSpendAddress(stealthId, data)

    val box: ExtractedOutputModel = daoUtils.awaitResult(stealthDaoContext.outputDAO.getById(boxId1)).get
    assertResult(true) {
      if (box.spendAddress === address) {
        try {
          stealthObj.spendStealthBoxes(sendTransaction = false)
          true
        } catch {
          case ex: Exception => {
            false
          }
        }
      }
      else false
    }


  }

}
