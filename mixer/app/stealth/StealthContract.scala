package stealth

import app.Configs
import org.ergoplatform.appkit.{Address, ConstantsBuilder, InputBox}
import scorex.util.encode.{Base58, Base64}
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants.dlogGroup
import special.sigma.GroupElement
import wallet.WalletHelper
import dao.stealth._
import dao.DAOUtils
import models.StealthModel.{ExtractionOutputResultModel, StealthAddress, StealthAddressModel, StealthModel, StealthSpendAddress}
import network.NetworkUtils
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import play.api.Logger
import sigmastate.serialization.ErgoTreeSerializer
import wallet.WalletHelper.addressEncoder

import java.util.UUID
import javax.inject.Inject
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.collection.JavaConverters._
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
   * selects record with this stealthName and makes stealth address
   *
   * @param stealthName String
   * @return stealth address - String
   */
  def createStealthAddressByStealthName(stealthName: String, x: String): (String, String, String) = {
    var secret = x
    val g: GroupElement = dlogGroup.generator
    if (x.isEmpty) {
      val x = WalletHelper.randBigInt
      secret = WalletHelper.toHexString(x.toByteArray)
    }
    val stealthId = UUID.randomUUID().toString
    daoUtils.awaitResult(stealthDAO.insert(StealthModel(stealthId, secret, stealthName)))
    val u = s"stealth:${Base58.encode(g.exp(BigInt(secret, 16).bigInteger).getEncoded.toArray)}"
    (u, stealthName, stealthId)
  }


  /**
   * selects request stealthName
   *
   * @param pk String - stealth:pk(hex)
   * @return stealth address - String
   */
  def createStealthAddressByStealthPK(pk: String): String = {
    val stealthArray = pk.split(":")
    if (stealthArray(0) != "stealth" || stealthArray.length != 2) {
      throw new Throwable("wrong format pk")
    }
    val pkHex = WalletHelper.toHexString(Base58.decode(stealthArray(1)).get)
    createStealthAddress(pkHex)
  }

  /**
   * creates stealth address base on pk
   *
   * @param pk String - stealth:pk(hex)
   * @return stealth address - String
   */
  def createStealthAddress(pkHex: String): String = {
    var stealthAddress = ""
    breakable {
      while (true) {
        val g: GroupElement = dlogGroup.generator
        val r = WalletHelper.randBigInt
        val y = WalletHelper.randBigInt
        val u: GroupElement = WalletHelper.hexToGroupElement(pkHex)

        val gr = Base64.encode(g.exp(r.bigInteger).getEncoded.toArray)
        val gy = Base64.encode(g.exp(y.bigInteger).getEncoded.toArray)
        val ur = Base64.encode(u.exp(r.bigInteger).getEncoded.toArray)
        val uy = Base64.encode(u.exp(y.bigInteger).getEncoded.toArray)

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
            import RegexUtils._
            val decodedAddress = Base58.decode(stealthAddress).get
            val pattern = """[a-fA-F0-9]{2}(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)[a-fA-F0-9]{8}""".r
            if(pattern matches WalletHelper.toHexString(decodedAddress)){
              break
            }
        }
      }
    }
    stealthAddress
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
    val x = BigInt(secret, 16)

    val hexErgoTree = WalletHelper.toHexString(stealthAddress.getErgoAddress.script.bytes)
    val gr = WalletHelper.hexToGroupElement(hexErgoTree.slice(8, 74))
    val gy = WalletHelper.hexToGroupElement(hexErgoTree.slice(78, 144))
    val ur = WalletHelper.hexToGroupElement(hexErgoTree.slice(148, 214))
    val uy = WalletHelper.hexToGroupElement(hexErgoTree.slice(218, 284))

    try {
      if (gr.exp(x.bigInteger) == ur && gy.exp(x.bigInteger) == uy) {
        true
      }
      else false
    }
    catch {
      case e: Exception => logger.error(e.getMessage)
        false
    }
  }

  def getStealthAddresses(): mutable.ListBuffer[StealthAddressModel] = {
    val addresses = mutable.ListBuffer[StealthAddressModel]()

    val stealthArray: Seq[StealthModel] = daoUtils.awaitResult(stealthDAO.all)
    stealthArray.foreach(stealth => {
      val value: Long = daoUtils.awaitResult(outputDAO.getUnspentBoxesValuesByStealthId(stealth.stealthId)).getOrElse(0L)
      val g: GroupElement = dlogGroup.generator
      val x = BigInt(stealth.sk, 16)
      val pk = Base58.encode(g.exp(x.bigInteger).getEncoded.toArray)
      addresses += StealthAddress(stealth.stealthId, stealth.stealthName, s"stealth:${pk}", value)
    })
    addresses
  }

  def getStealthAddress(stealthId: String): StealthAddressModel = {
    val stealth = daoUtils.awaitResult(stealthDAO.selectByStealthId(stealthId)).get
    val value: Long = daoUtils.awaitResult(outputDAO.getUnspentBoxesValuesByStealthId(stealthId)).getOrElse(0L)
    val g: GroupElement = dlogGroup.generator
    val x = BigInt(stealth.sk, 16)
    val pk = Base58.encode(g.exp(x.bigInteger).getEncoded.toArray)
    StealthAddress(stealth.stealthId, stealth.stealthName, s"stealth:${pk}", value)
  }

  def getUnspentBoxes(stealthId: String): Seq[ErgoBox] = {
    outputDAO.selectUnspentBoxesByStealthId(stealthId)
  }

  def setSpendAddress(stealthId: String, data: List[StealthSpendAddress]): Unit = {
    try {
      val stealth = daoUtils.awaitResult(stealthDAO.selectByStealthId(stealthId)).get
      for (item <- data) {
        outputDAO.addSpendAddressIfExist(item.boxId, item.address)
      }
    }
    catch {
      case e: Exception =>
        logger.error(e.getMessage)
        throw new Exception(e.getMessage)
    }
  }

  def spendStealthBoxes(sendTransaction: Boolean = true): Unit = {
    def convertToInputBox(box: ErgoBox): InputBox = {
      networkUtils.usingClient {
        implicit ctx =>
          val txB = ctx.newTxBuilder()
          val input = txB.outBoxBuilder()
            .value(box.value)
            .contract(new ErgoTreeContract(box.ergoTree))
            .build().convertToInputWith(WalletHelper.randomId(), 1)
          input
      }
    }

    def feeCalculator(inputList: List[InputBox]): Long = {
      val values = inputList.map(_.getValue.toLong).sum
      (values * Configs.stealthStaticFee).toLong + Configs.stealthImplementorFee
    }

    case class SpendBox(box: ErgoBox, SpendAddress: String)

    val SpendBoxList = new ListBuffer[SpendBox]()
    val SpendAddressList = new ListBuffer[String]()
    networkUtils.usingClient {
      implicit ctx =>

        var proverBuilder = ctx.newProverBuilder()

        // get all unspent boxes have Spend address
        val unspentOutputList = daoUtils.awaitResult(outputDAO.selectUnspentBoxesHaveSpendAddress())
        unspentOutputList.foreach(box => {
          SpendBoxList += SpendBox(ErgoBoxSerializer.parseBytes(box.bytes), box.spendAddress)
          SpendAddressList += box.spendAddress
        })

        // create provers
        val secrets = daoUtils.awaitResult(stealthDAO.all).map(_.sk)
        SpendBoxList.foreach(SpendBox => {
          val stealthAddress = WalletHelper.addressEncoder.fromProposition(SpendBox.box.ergoTree).get.toString
          val ergoTreeBytes = SpendBox.box.ergoTree.bytes
          secrets.foreach(sk => {
            if (isSpendable(stealthAddress, sk)) {
              val gr = WalletHelper.hexToGroupElement(WalletHelper.toHexString(ergoTreeBytes).slice(8, 74))
              val gy = WalletHelper.hexToGroupElement(WalletHelper.toHexString(ergoTreeBytes).slice(78, 144))
              val ur = WalletHelper.hexToGroupElement(WalletHelper.toHexString(ergoTreeBytes).slice(148, 214))
              val uy = WalletHelper.hexToGroupElement(WalletHelper.toHexString(ergoTreeBytes).slice(218, 284))
              proverBuilder = proverBuilder.withDHTData(gr, gy, ur, uy, BigInt(sk, 16).bigInteger)
            }
          })
        })

        // create transaction
        SpendAddressList.foreach(address => {
          val inputBoxList = SpendBoxList
            .filter(item => item.SpendAddress == address)
            .map(_.box)
            .map(convertToInputBox)

          val txB = ctx.newTxBuilder()
          val fee = feeCalculator(inputBoxList.toList)

          val output = txB.outBoxBuilder()
            .value(inputBoxList.map(_.getValue.toLong).sum - fee)
            .contract(new ErgoTreeContract(WalletHelper.getAddress(address).script))
            .build()

          val tx = txB
            .boxesToSpend(inputBoxList.toList.asJava)
            .fee(fee)
            .outputs(output)
            .sendChangeTo(Address.create(address).getErgoAddress)
            .build()

          val prover = proverBuilder.build()

          val signedTx = prover.sign(tx)
          logger.debug(s"stealth Spend signed data ${
            signedTx.toJson(true)
          }")
          if (sendTransaction) {
            val txId = ctx.sendTransaction(signedTx)
            logger.debug(s"sending stealth Spend tx $txId")
            println(signedTx.toJson(true))
          }
        })
    }
  }

  def updateBoxesStealthId(ExtractionOutputResult: Seq[ExtractionOutputResultModel]): Unit = {
    try {
      ExtractionOutputResult.foreach(output => {
        val stealthList = daoUtils.awaitResult(stealthDAO.all)
        breakable {
          stealthList.foreach(stealth => {
            val box = ErgoBoxSerializer.parseBytes(output.extractedOutput.bytes)
            val address = addressEncoder.fromProposition(box.ergoTree).get.toString
            if (isSpendable(address, stealth.sk)) {
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
