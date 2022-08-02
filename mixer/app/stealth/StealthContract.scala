package stealth

import app.Configs
import org.ergoplatform.appkit.{Address, ConstantsBuilder, ErgoToken, ErgoValue, InputBox}
import scorex.util.encode.{Base16, Base58, Base64}
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants.dlogGroup
import special.sigma.GroupElement
import wallet.WalletHelper
import dao.stealth._
import dao.DAOUtils
import models.StealthModels.{ExtractionOutputResultModel, SpendBox, SpendBoxModel, StealthAddress, StealthAddressModel, StealthModel, StealthSpendAddress}
import network.NetworkUtils
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import play.api.Logger
import wallet.WalletHelper.addressEncoder
import RegexUtils._
import helpers.StealthUtils
import slick.dbio.{DBIO, DBIOAction}

import java.util.UUID
import javax.inject.Inject
import scala.collection.mutable
import scala.language.postfixOps
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.util.control.Breaks._

class StealthContract @Inject()(stealthDAO: StealthDAO,
                                outputDAO: OutputDAO,
                                daoUtils: DAOUtils,
                                networkUtils: NetworkUtils) {
  private val logger: Logger = Logger(this.getClass)

  val stealthScript: String = {
    s"""{
       |  val gr = decodePoint(fromBase64("GR"))
       |  val gy = decodePoint(fromBase64("GY"))
       |  val ur = decodePoint(fromBase64("UR"))
       |  val uy = decodePoint(fromBase64("UY"))
       |  proveDHTuple(gr,gy,ur,uy)
       |}
       |""".stripMargin

  }

  /**
   * creating stealth with given stealth name and secret. (secret is optional)
   *
   * @param stealthName String
   * @param secret      BigInt
   * @return stealth pk, stealth name, stealth db Id
   */
  def createStealthAddressByStealthName(stealthName: String, secret: BigInt): (String, String, String) = {
    var secretTmp = secret
    val g: GroupElement = dlogGroup.generator
    if (secret == 0) {
      breakable {
        while (true) {
          secretTmp = WalletHelper.randBigInt
          if (secretTmp.bitLength == 256) {
            break
          }
        }
      }
    }
    val stealthId = UUID.randomUUID().toString
    daoUtils.awaitResult(stealthDAO.insert(StealthModel(stealthId, secretTmp, stealthName)))
    val base58EncodedPK = Base58.encode(g.exp(secretTmp.bigInteger).getEncoded.toArray)
    val u = s"stealth$base58EncodedPK"
    (u, stealthName, stealthId)
  }


  /**
   * selects request stealthName
   *
   * @param pk String - "stealth"+pk(base58)
   * @return stealth address - String
   */
  def createStealthAddressByStealthPK(pk: String): String = {
    val stealthArray = List[String](pk.slice(0, 7), pk.slice(7, pk.length))
    if (stealthArray.head != "stealth" || stealthArray.length != 2) {
      throw new Throwable("wrong format pk")
    }
    val pkHex = Base16.encode(Base58.decode(stealthArray(1)).get)
    createStealthAddress(pkHex)
  }

  /**
   * creates stealth address base on pk
   *
   * @param pkHex String - "stealth"+pk(base58)
   * @return stealth address - String
   */
  def createStealthAddress(pkHex: String): String = {
    var stealthAddress = ""
    val g: GroupElement = dlogGroup.generator
    val u: GroupElement = WalletHelper.hexToGroupElement(pkHex)
    val pattern = """[a-fA-F0-9]{2}(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)[a-fA-F0-9]{8}""".r

    def getDHTDataInHex(base: GroupElement, power: BigInt): String = {
      Base64.encode(base.exp(power.bigInteger).getEncoded.toArray)
    }

    while (true) {
      val r = WalletHelper.randBigInt
      val y = WalletHelper.randBigInt

      val gr = getDHTDataInHex(g, r)
      val gy = getDHTDataInHex(g, y)
      val ur = getDHTDataInHex(u, r)
      val uy = getDHTDataInHex(u, y)

      networkUtils.usingClient {
        implicit ctx =>
          val newScript = stealthScript
            .replace("GR", gr)
            .replace("GY", gy)
            .replace("UR", ur)
            .replace("UY", uy)
          val contract = ctx.compileContract(
            ConstantsBuilder.create()
              .build()
            , newScript)
          val ergoTree = contract.getErgoTree
          stealthAddress = WalletHelper.addressEncoder.fromProposition(ergoTree).get.toString
          val decodedAddress = Base58.decode(stealthAddress).get
          if (pattern matches Base16.encode(decodedAddress)) {
            return stealthAddress
          }
      }
    }
    ""
  }

  /**
   * check stealth is belong to a secret or not
   *
   * @param address String - stealthAddress
   * @param secret  String
   * @return Boolean
   */
  def isSpendable(address: String, secret: String): Boolean = {
    val stealthAddress = Address.create(address)
    val secretDigits = BigInt(secret, 16)

    val hexErgoTree = Base16.encode(stealthAddress.getErgoAddress.script.bytes)
    val gr = WalletHelper.hexToGroupElement(hexErgoTree.slice(8, 74))
    val gy = WalletHelper.hexToGroupElement(hexErgoTree.slice(78, 144))
    val ur = WalletHelper.hexToGroupElement(hexErgoTree.slice(148, 214))
    val uy = WalletHelper.hexToGroupElement(hexErgoTree.slice(218, 284))

    if (gr.exp(secretDigits.bigInteger) == ur && gy.exp(secretDigits.bigInteger) == uy) {
      true
    }
    else false
  }

  /**
   * get list of all stealth addresses with total value
   *
   * @return a list of StealthAddress objects contains: stealthId, stealthName, stealthPk and total value received by this stealth pk
   * */
  def getStealthAddresses: mutable.ListBuffer[StealthAddressModel] = {
    val addresses = mutable.ListBuffer[StealthAddressModel]()

    val stealthArray: Seq[StealthModel] = daoUtils.awaitResult(stealthDAO.all)
    stealthArray.foreach(stealth => {
      val value: Long = daoUtils.awaitResult(outputDAO.getUnspentBoxesValuesByStealthId(stealth.stealthId)).getOrElse(0L)
      val g: GroupElement = dlogGroup.generator
      val x = stealth.secret
      val pk = Base58.encode(g.exp(x.bigInteger).getEncoded.toArray)
      addresses += StealthAddress(stealth.stealthId, stealth.stealthName, s"stealth$pk", value)
    })
    addresses
  }

  /**
   * get a stealth address with total value by given stealth Id
   *
   * @param stealthId - string
   * @return an StealthAddress object contains: stealthId, stealthName, stealthPk and total value received by this stealth pk
   * */
  def getStealthAddress(stealthId: String): StealthAddressModel = {
    val stealth = daoUtils.awaitResult(stealthDAO.selectByStealthId(stealthId)).get
    val value: Long = daoUtils.awaitResult(outputDAO.getUnspentBoxesValuesByStealthId(stealthId)).getOrElse(0L)
    val g: GroupElement = dlogGroup.generator
    val x = stealth.secret
    val pk = Base58.encode(g.exp(x.bigInteger).getEncoded.toArray)
    StealthAddress(stealth.stealthId, stealth.stealthName, s"stealth$pk", value)
  }

  /**
   * get seq of all ergo boxes relate to given stealth Id
   *
   * @param stealthId - string
   * @return a seq of all ergo boxes relate to given stealth Id
   * */
  def getUnspentBoxes(stealthId: String): Seq[ErgoBox] = {
    outputDAO.selectUnspentBoxesByStealthId(stealthId)
  }

  /**
   * sets spend address for all stealth boxes
   *
   * @param stealthId - string
   * @param data      - List[StealthSpendAddress] (each one contains boxId and spend address)
   * @return
   * */
  def setSpendAddress(stealthId: String, data: List[StealthSpendAddress]): Unit = {
    try {
      val boxIds = data.map(_.boxId)
      val stealthIds = daoUtils.awaitResult(outputDAO.getStealthIdByIds(boxIds))
      stealthIds.foreach(sId => if (sId != stealthId) throw new Exception("box's stealthId is not correct"))
      val action =
        for (item <- data)
          yield DBIO.successful({
            outputDAO.addSpendAddressIfExist(item.boxId, item.address)
          })
      val response = daoUtils.execTransact(DBIO.sequence(action))
      Await.result(response, Duration.Inf)
    } catch {
      case e: Exception =>
        logger.error(e.getMessage)
    }
  }

  /**
   * spending all boxes with spend address.
   *
   * @param sendTransaction - boolean
   * @return
   * */
  def spendStealthBoxes(sendTransaction: Boolean = true): Unit = {
    val stealthUtils = new StealthUtils(networkUtils)
    val spendBoxList = mutable.Seq[SpendBoxModel]()
    val spendAddressList = mutable.Set[String]()
    networkUtils.usingClient { implicit ctx =>
      try {
        var proverBuilder = ctx.newProverBuilder()

        // get all unspent boxes have Spend address
        val unspentOutputList = daoUtils.awaitResult(outputDAO.selectUnspentBoxesHaveSpendAddress())
        unspentOutputList.foreach(box => {
          spendBoxList :+ SpendBox(box)
          spendAddressList += box.spendAddress
        })

        // create provers
        spendBoxList.foreach(spendBox => {
          val stealth = daoUtils.awaitResult(stealthDAO.selectByStealthId(spendBox.stealthId)).get
          val ergoTreeHex = Base16.encode(spendBox.box.ergoTree.bytes)
          val gr = stealthUtils.getDHTDataFromErgoTree(ergoTreeHex, 8, 74)
          val gy = stealthUtils.getDHTDataFromErgoTree(ergoTreeHex, 78, 144)
          val ur = stealthUtils.getDHTDataFromErgoTree(ergoTreeHex, 148, 214)
          val uy = stealthUtils.getDHTDataFromErgoTree(ergoTreeHex, 218, 284)
          proverBuilder = proverBuilder.withDHTData(gr, gy, ur, uy, stealth.secret.bigInteger)

        })

        // create transaction
        spendAddressList.foreach(address => {
          val inputs = spendBoxList
            .filter(item => item.spendAddress == address)
            .map(_.box)
            .map(stealthUtils.convertToInputBox)
          (0 until inputs.size / Configs.maxIns).foreach(i => {
            val start = i * Configs.maxIns
            val inputBoxList = inputs.slice(start, start + Configs.maxIns)

            val tokens = stealthUtils.getTokens(inputBoxList.toList)
            val txB = ctx.newTxBuilder()
            val fee = stealthUtils.feeCalculator(inputBoxList.toList)

            val output = txB.outBoxBuilder()
              .value(inputBoxList.map(_.getValue.toLong).sum - fee)
              .tokens(tokens: _*)
              .contract(new ErgoTreeContract(WalletHelper.getAddress(address).script))
              .build()

            val tx = txB.boxesToSpend(inputBoxList.toList.asJava)
              .fee(fee)
              .outputs(output)
              .sendChangeTo(Address.create(address).getErgoAddress)
              .build()

            val prover = proverBuilder.build()

            val signedTx = prover.sign(tx)
            if (sendTransaction) {
              val txId = ctx.sendTransaction(signedTx)
              logger.info(s"sending stealth Spend tx $txId")
            }
          })
        })
      }
      catch {
        case e: Throwable => e.getMessage
      }
    }
  }

  /**
   * sets stealth Id to extracted box
   *
   * @param extractionOutputResult - Seq[ExtractionOutputResultModel]
   * @return
   * */
  def updateBoxesStealthId(extractionOutputResult: Seq[ExtractionOutputResultModel]): Unit = {
    try {
      val stealthList = daoUtils.awaitResult(stealthDAO.all)
      extractionOutputResult.foreach(output => {
        val box = ErgoBoxSerializer.parseBytes(output.extractedOutput.bytes)
        val address = addressEncoder.fromProposition(box.ergoTree).get.toString
        breakable {
          stealthList.foreach(stealth => {
            if (isSpendable(address, stealth.secret.toString(16))) {
              outputDAO.updateStealthId(output.extractedOutput.boxId, stealth.stealthId)
              break
            }
          })
        }
      })
    }
    catch {
      case e: Throwable => e.getMessage
    }
  }
}
