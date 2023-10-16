package stealth

import java.util.UUID
import javax.inject.{Inject, Singleton}

import scala.util.matching.Regex

import config.MainConfigs
import dao.stealth.{ExtractedBlockDAO, OutputDAO, StealthDAO, TokenInformationDAO}
import dao.DAOUtils
import helpers.{ErgoMixerUtils, ErrorHandler, StealthUtils}
import helpers.RegexUtils._
import mixinterface.TokenErgoMix
import models.StealthModels._
import network.BlockExplorer
import org.ergoplatform.appkit._
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import play.api.Logger
import scorex.util.encode.{Base16, Base58}
import sigmastate.eval._
import sigmastate.Values.ErgoTree
import special.sigma.GroupElement
import wallet.WalletHelper

@Singleton()
class StealthContract @Inject() (
  stealthDAO: StealthDAO,
  outputDAO: OutputDAO,
  tokenInformationDAO: TokenInformationDAO,
  extractedBlockDAO: ExtractedBlockDAO,
  explorer: BlockExplorer,
  daoUtils: DAOUtils,
  ergoMixerUtils: ErgoMixerUtils
) extends ErrorHandler {

  private val logger: Logger = Logger(this.getClass)

  val stealthPattern: Regex = """(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)""".r

  /**
   * generate StealthErgoTree
   *
   * @param grHex - hex of GR
   * @param gyHex - hex of GY
   * @param urHex - hex of UR
   * @param uyHex - hex of UY
   * @return - ErgoTree
   */
  def generateStealthErgoTree(grHex: String, gyHex: String, urHex: String, uyHex: String): ErgoTree =
    JavaHelpers.decodeStringToErgoTree(
      s"10040e21${grHex}0e21${gyHex}0e21${urHex}0e21${uyHex}ceee7300ee7301ee7302ee7303"
    )

  /**
   * creating stealthAddress with given stealth name and secret. (secret is optional)
   *  stealthAddress = "stealth" + Base58 encoded of (pk + leftmost 4 bytes blake2b256(pk))
   *
   * @param stealthName String
   * @param secret      hex of secret
   * @return Stealth
   */
  def createStealthAddress(stealthName: String, secret: String): Stealth = {
    var secretTmp: BigInt = BigInt(0)
    val g: GroupElement   = WalletHelper.g
    if (secret.isEmpty) secretTmp = WalletHelper.randBigInt
    else secretTmp                = BigInt(secret, 16)
    val stealthId            = UUID.randomUUID().toString
    val orgPK                = g.exp(secretTmp.bigInteger).getEncoded.toArray
    val leftMost4BytesHashPK = WalletHelper.getHash(orgPK).take(4)
    val base58EncodedPK      = Base58.encode(orgPK ++ leftMost4BytesHashPK)
    val address              = s"stealth$base58EncodedPK"
    val stealth              = Stealth(stealthId, stealthName, address, secretTmp)
    daoUtils.awaitResult(stealthDAO.insert(stealth))
    stealth
  }

  /**
   * validate stealthAddress should be in format: "stealth" + Base58 encoded of (pk + leftmost 4 bytes blake2b256(pk))
   *
   * @param stealthAddress - String
   */
  def validateStealthAddress(stealthAddress: String): Unit = {
    val stealthTuple = stealthAddress.splitAt(7)
    if (stealthTuple._1 != "stealth" || stealthTuple._2.isEmpty)
      throw new Exception(s"wrong format stealthAddress $stealthAddress")
    else if (Base58.decode(stealthTuple._2).isFailure)
      throw new Exception(s"wrong format stealthAddress $stealthAddress")
    val byteArraysPK         = Base58.decode(stealthTuple._2).get
    val (mainPK, checksumPK) = byteArraysPK.splitAt(byteArraysPK.length - 4)
    if (!WalletHelper.getHash(mainPK).take(4).sameElements(checksumPK))
      throw new Exception(s"stealthAddress $stealthAddress is invalid (wrong checksum)")
  }

  /**
   * generate payment address by given a stealthAddress
   *
   * @param stealthAddress String - "stealth" + Base58 encoded of (pk + leftmost 4 bytes blake2b256(pk))
   * @return stealth payment address - String
   */
  def generatePaymentAddressByStealthAddress(stealthAddress: String): String = {
    validateStealthAddress(stealthAddress)
    val stealthTuple    = stealthAddress.splitAt(7)
    val byteArraysPK    = Base58.decode(stealthTuple._2).get.dropRight(4)
    val pkHex           = Base16.encode(byteArraysPK)
    val u: GroupElement = WalletHelper.hexToGroupElement(pkHex)
    createPaymentAddress(u)
  }

  /**
   * creates stealth payment address base on stealthAddress
   *
   * @param u GroupElement - GroupElement encoded PK
   * @return stealth payment address - String
   */
  def createPaymentAddress(u: GroupElement): String = {
    val g: GroupElement = WalletHelper.g

    val r = WalletHelper.randBigInt
    val y = WalletHelper.randBigInt

    val gr = WalletHelper.getDHTDataInBase16(g, r)
    val gy = WalletHelper.getDHTDataInBase16(g, y)
    val ur = WalletHelper.getDHTDataInBase16(u, r)
    val uy = WalletHelper.getDHTDataInBase16(u, y)

    val ergoTree       = generateStealthErgoTree(gr, gy, ur, uy)
    val stealthAddress = WalletHelper.getErgoAddress(ergoTree).toString
    if (checkStealth(ergoTree)) stealthAddress
    else throw new Exception("this is impossible behavior, creating stealth address was impossible try again")
  }

  /**
   * check stealth is belong to a secret or not
   *
   * @param address Address - stealthAddress
   * @param secret  BigInt
   * @return Boolean
   */
  def isSpendable(address: Address, secret: BigInt): Boolean =
    this.isSpendable(address.getErgoAddress.script, secret)

  /**
   * check stealth is belong to a secret or not
   *
   * @param hexErgoTree String - hexErgoTree
   * @param secret      BigInt
   * @return Boolean
   */
  def isSpendable(hexErgoTree: ErgoTree, secret: BigInt): Boolean = {
    val dhtData = StealthUtils.getDHTDataFromErgoTree(hexErgoTree.bytesHex)
    if (dhtData.gr.exp(secret.bigInteger) == dhtData.ur && dhtData.gy.exp(secret.bigInteger) == dhtData.uy) {
      true
    } else false
  }

  /**
   * get list of all stealth with total unspent value and number of assets
   *
   * @return a list of StealthAssets with total unspent value and number of assets
   */
  def getAllStealthWithUnspentAssets: Seq[StealthAssets] =
    daoUtils.awaitResult(outputDAO.selectAllStealthWithTotalUnspentAssets())

  /**
   * get stealth info
   *
   * @return (lastProcessedBlock, currentNetworkHeight)
   */
  def getStealthInfo: (Long, Long) = {
    val lastProcessedBlock: Long   = notFoundHandle(daoUtils.awaitResult(extractedBlockDAO.getLastHeight)).toLong
    val currentNetworkHeight: Long = explorer.getHeight
    (lastProcessedBlock, currentNetworkHeight)
  }

  /**
   * get ExtractedOutputs of a stealth by given status
   *
   * @param stealthId - string
   * @param status - string
   * @return list of ExtractedOutput that deposited for this stealthId
   */
  def getOutputs(stealthId: String, status: String): Seq[ExtractedOutputController] = {
    val stealthObj = daoUtils.awaitResult(stealthDAO.selectByStealthId(stealthId))
    if (stealthObj.isDefined) {
      var extractedOutputs: Seq[ExtractedOutput] = Seq.empty
      if (status == "unspent")
        extractedOutputs = daoUtils.awaitResult(outputDAO.selectUnspentExtractedOutputsByStealthId(stealthId))
      else if (status == "all")
        extractedOutputs = daoUtils.awaitResult(outputDAO.selectExtractedOutputsByStealthId(stealthId))
      else throw new Exception(s"status $status is invalid")
      val outputs = extractedOutputs.map { box =>
        val tokenIds          = ErgoBoxSerializer.parseBytes(box.bytes).tokens.keys.toSeq
        val tokensInformation = daoUtils.awaitResult(tokenInformationDAO.selectByTokenIds(tokenIds))
        ExtractedOutputController(box, tokensInformation)
      }
      outputs
    } else throw new Exception(s"no such stealth id $stealthId")
  }

  /**
   * sets spend address for stealth box
   *
   * @param boxId   - string
   * @param address - string
   * @return
   */
  def setWithdrawAddress(boxId: String, address: String): Unit = {
    WalletHelper.okAddresses(Seq(address))
    daoUtils.awaitResult(outputDAO.updateWithdrawAddressIfBoxExist(boxId, address))
  }

  /**
   * create dht prover for spendBoxes
   *
   * @param stealthId  - string
   * @param spendBoxes - Seq[SpendBoxModel]
   * @return an ErgoProver for all spendBoxes by given stealthId
   */
  def createProver(stealthId: String, spendBoxes: Seq[SpendBox])(implicit ctx: BlockchainContext): ErgoProver = {
    var proverBuilder = ctx.newProverBuilder()
    val stealth       = daoUtils.awaitResult(stealthDAO.selectByStealthId(stealthId)).get
    spendBoxes.foreach { spendBox =>
      val ergoTreeHex = spendBox.box.getErgoTree.bytesHex
      val dhtData     = StealthUtils.getDHTDataFromErgoTree(ergoTreeHex)
      proverBuilder =
        proverBuilder.withDHTData(dhtData.gr, dhtData.gy, dhtData.ur, dhtData.uy, stealth.secret.bigInteger)
    }
    proverBuilder.build()
  }

  /**
   * spending all boxes with withdraw address.
   *
   * @return
   */
  def spendStealthBoxes()(implicit ctx: BlockchainContext): Unit =
    try {
      // get all unspent boxes have Spend address
      val spendBoxList = daoUtils.awaitResult(outputDAO.selectUnspentBoxes(true)).map(CreateSpendBox(_))
      // a map between (stealthId, withdrawAddress) to spendBoxes
      val categorizedBoxes = spendBoxList.groupBy(spendBox => (spendBox.stealthId, spendBox.withdrawAddress))
      categorizedBoxes.foreach { req =>
        val inputBoxList = req._2.map(_.box)
        val prover       = createProver(req._1._1, req._2)
        val totalAssets  = StealthUtils.getTotalAssets(inputBoxList)
        try {
          val outAssets            = StealthUtils.fundCalculator(totalAssets)
          val txB                  = ctx.newTxBuilder()
          var outputs: Seq[OutBox] = Seq.empty
          val userOutBuilder = txB
            .outBoxBuilder()
            .contract(WalletHelper.getAddress(req._1._2).toErgoContract)
            .value(outAssets.user._1)
          if (outAssets.user._2.nonEmpty) userOutBuilder.tokens(outAssets.user._2: _*)
          val userOutBox = userOutBuilder.build()

          val serviceBoxBuilder = txB
            .outBoxBuilder()
            .value(outAssets.service._1)
            .contract(TokenErgoMix.stealthIncome.toErgoContract)
          if (outAssets.service._2.nonEmpty) serviceBoxBuilder.tokens(outAssets.service._2: _*)
          val serviceOutBox = serviceBoxBuilder.build()
          outputs = Seq(userOutBox, serviceOutBox)

          val tx = txB
            .addInputs(inputBoxList: _*)
            .fee(MainConfigs.stealthFee)
            .addOutputs(outputs: _*)
            .sendChangeTo(WalletHelper.getAddress(req._1._2))
            .build()

          val signedTx = prover.sign(tx)
          logger.info(s"stealth: singed stealth tx ${signedTx.getId}")
          logger.debug(s"stealth: singed stealth tx is ${signedTx.toJson(false, true)}")

          val txId = ctx.sendTransaction(signedTx)
          if (txId == null) throw new Exception(s"failed to broadcast signed tx ${signedTx.getId}")
          else {
            daoUtils.awaitResult(
              outputDAO.updateWithdrawTxInformation(inputBoxList.map(_.getId.toString), signedTx.getId)
            )
            logger.info(s"stealth: broadcast stealth spend tx with txId: ${signedTx.getId}")
          }
        } catch {
          case e: NotEnoughErgException =>
            logger.warn(e.getMessage)
            daoUtils.awaitResult(outputDAO.setWithdrawFailedReason(inputBoxList.map(_.getId.toString), e.getMessage))
          case e: Throwable =>
            logger.error(e.getMessage)
            daoUtils.awaitResult(
              outputDAO.setWithdrawFailedReason(inputBoxList.map(_.getId.toString), e.getMessage.take(255))
            )
        }
      }
    } catch {
      case e: Throwable =>
        logger.error(s" [stealth] An error occurred. Stacktrace below:")
        logger.error(ergoMixerUtils.getStackTraceStr(e))
        logger.debug(s" Exception: ${e.getMessage}")
    }

  /**
   * check ErgoTree of a box to be stealth.
   *
   * @param ergoTree - ErgoTree
   * @return Boolean
   */
  def checkStealth(ergoTree: ErgoTree): Boolean = {
    if (stealthPattern.matches(ergoTree.bytesHex)) {
      return true
    }
    false
  }

  /**
   * update extracted outputs that have same !! ErgoTree !! and are spendable by given secret
   *
   * @param stealthList - Seq[Stealth]
   * @param outputs     - Seq[ExtractedOutput] with same ErgoTree
   */
  private def updateBoxesIfSpendable(stealthList: Seq[Stealth], outputs: Seq[ExtractedOutput]): Unit =
    stealthList.foreach { stealth =>
      if (isSpendable(JavaHelpers.decodeStringToErgoTree(outputs.head.ergoTree), stealth.secret)) {
        daoUtils.awaitResult(outputDAO.updateStealthId(outputs.map(_.boxId), stealth.stealthId))
      }
    }

  /**
   * sets stealth Id to extracted box
   *
   * @param extractedOutputs - Seq[ExtractedOutput]
   */
  def updateBoxesStealthId(extractedOutputs: Seq[ExtractedOutput]): Unit =
    try {
      val stealthList = daoUtils.awaitResult(stealthDAO.all)
      extractedOutputs.groupBy(_.ergoTree).foreach(output => updateBoxesIfSpendable(stealthList, output._2))
    } catch {
      case e: Throwable =>
        logger.error(s" [stealth] An error occurred. Stacktrace below:")
        logger.error(ergoMixerUtils.getStackTraceStr(e))
        logger.debug(s" Exception: ${e.getMessage}")
    }

  /**
   * update all unspent extracted outputs if are spendable with given stealth
   *
   * @param stealth - Stealth
   */
  def updateUnspentOutsIfSpendableByStealth(stealth: Stealth): Unit = {
    val extractedOutputs: Seq[ExtractedOutput] = daoUtils.awaitResult(outputDAO.selectUnspentBoxes())
    extractedOutputs.groupBy(_.ergoTree).foreach(output => updateBoxesIfSpendable(Seq(stealth), output._2))
  }

  /**
   * export stealth data
   *
   * @param stealthId - String
   * @return the a stealth object
   */
  def stealthById(stealthId: String): Stealth =
    daoUtils
      .awaitResult(stealthDAO.selectByStealthId(stealthId))
      .getOrElse(
        throw new Exception(s"stealthId $stealthId not found")
      )

  /**
   * Adds or updates name for a stealth
   *
   * @param stealthId   stealth id
   * @param stealthName name for stealth
   */
  def updateStealthName(stealthId: String, stealthName: String): Unit =
    if (!daoUtils.awaitResult(stealthDAO.existsByStealthId(stealthId))) {
      throw new Exception(s"stealthId $stealthId not found")
    } else {
      stealthDAO.updateNameStealth(stealthId, stealthName)
    }

}
