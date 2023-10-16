package network

import javax.inject.Singleton

import config.MainConfigs
import io.circe.Json
import models.Box.{InBox, OutBox}
import models.StealthModels.{CreateTokenInformation, TokenInformation}
import models.Transaction.SpendTx
import org.ergoplatform.appkit.ErgoToken
import play.api.Logger

@Singleton
class BlockExplorer() {

  private val logger: Logger = Logger(this.getClass)
  private val baseUrl        = MainConfigs.explorerUrl

  private val unspentUrl          = s"$baseUrl/transactions/boxes/byAddress/unspent/"
  private val boxUrl              = s"$baseUrl/transactions/boxes/"
  private val txUrl               = s"$baseUrl/transactions/"
  private val tokenInformationUrl = s"$baseUrl/api/v1/tokens/"
  private val urlAddress          = s"$baseUrl/addresses/"
  private val infoUrl             = s"$baseUrl/api/v1/info"
  private val unspentSearchUrl    = s"$baseUrl/api/v1/boxes/unspent/search/"
  private val unspentTokenUrl     = s"$baseUrl/api/v1/boxes/unspent/byTokenId/"
  private val boxesByAddressUtl   = s"$baseUrl/api/v1/boxes/byAddress/"
  private val blocksUrl           = s"$baseUrl/api/v1/blocks/headers"

  /**
   * @param j json representation of box
   * @return box as OutBox
   */
  private def getOutBoxFromJson(j: Json): OutBox = {
    val id                = getId(j)
    val value             = (j \\ "value").map(v => v.asNumber.get).head
    val creationHeight    = (j \\ "creationHeight").map(v => v.asNumber.get).head
    val assets: Seq[Json] = (j \\ "assets").map(v => v.asArray.get).head
    val tokens: Seq[ErgoToken] = assets.map { asset =>
      val tokenID = (asset \\ "tokenId").map(v => v.asString.get).head
      val value   = (asset \\ "amount").map(v => v.asNumber.get).head.toLong.get
      new ErgoToken(tokenID, value)
    }
    val registers = (j \\ "additionalRegisters").flatMap { r =>
      r.asObject.get.toList.map { case (key, value) => (key, value.asString.get) }
    }.toMap
    val ergoTree     = (j \\ "ergoTree").map(v => v.asString.get).head
    val address      = (j \\ "address").map(v => v.asString.get).head
    val spendingTxId = (j \\ "spentTransactionId").map(v => v.asString).head
    OutBox(id, value.toLong.get, registers, ergoTree, tokens, creationHeight.toInt.get, address, spendingTxId)
  }

  /**
   * @param j json representation of box
   * @return id of box
   */
  private def getId(j: Json): String = (j \\ "id").map(v => v.asString.get).head

  /**
   * @param j json representation of box
   * @return box as InBox
   */
  private def getInBoxFromJson(j: Json) = {
    val id          = getId(j)
    val address     = (j \\ "address").map(v => v.asString.get).head
    val value       = (j \\ "value").map(v => v.asNumber.get).head
    val createdTxId = (j \\ "outputTransactionId").map(v => v.asString.get).head
    InBox(id, address, createdTxId, value.toLong.get)
  }

  /**
   * @param json json representation of box
   * @return box as OutBox
   */
  private def getOutBoxesFromJson(json: Json): Seq[OutBox] =
    json.asArray.get.map(getOutBoxFromJson)

  /**
   * @param json json representation of box
   * @return box as InBox
   */
  private def getInBoxesFromJson(json: Json): Seq[InBox] =
    json.asArray.get.map(getInBoxFromJson)

  /**
   * @param address ergo address
   * @return number of transactions relating the address
   */
  def getTransactionNum(address: String): Int = try {
    val json = GetURL.get(urlAddress + address + s"/transactions?limit=1")
    (json \\ "total").head.asNumber.get.toInt.get
  } catch {
    case _: Throwable => 0
  }

  /**
   * @return current height of blockchain
   */
  def getHeight: Int = {
    val json = GetURL.get(infoUrl)
    (json \\ "height").head.toString().toInt
  }

  /**
   * @return blocks of blockchain
   */
  def getBlocks(offset: Long = 0, limit: Long = 100): String = {
    val json = GetURL.get(blocksUrl + s"?limit=$limit&offset=$offset")
    (json \\ "items").head.toString()
  }

  /**
   * @param address address to get unspent boxes of
   * @return list of unspent boxes
   */
  def getUnspentBoxes(address: String): Seq[OutBox] =
    getOutBoxesFromJson(GetURL.get(unspentUrl + address))

  /**
   * @param address address to get boxes of
   * @return list of box IDs
   */
  def getBoxIdsByAddress(address: String): Seq[String] = {
    val json = GetURL.get(boxesByAddressUtl + address)
    (json \\ "items").headOption.get.asArray.get.map(j => (j \\ "boxId").head.asString.get)
  }

  /**
   * @param boxId box id
   * @return box as OutBox
   */
  def getBoxById(boxId: String): OutBox =
    getOutBoxFromJson(GetURL.get(boxUrl + boxId))

  /**
   * @param tokenId token id
   * @return box as OutBox
   */
  def getBoxByTokenId(tokenId: String): Json =
    GetURL.get(unspentTokenUrl + tokenId)

  /**
   * @param boxId box id
   * @return spending tx of box id if spent at all
   */
  def getSpendingTxId(boxId: String): Option[String] = try
    getBoxById(boxId).spendingTxId
  catch {
    case a: Throwable => None
  }

  /**
   * @param address address
   * @param limit   max number of txs
   * @param offset  offset
   * @return list of transaction of address in specified range
   */
  def getTransactionsByAddress(address: String, limit: Long = 0, offset: Long = 0): List[SpendTx] = try {
    val json  = GetURL.get(urlAddress + address + s"/transactions?limit=$limit&offset=$offset")
    val items = (json \\ "items").headOption.get.hcursor.as[List[Json]].getOrElse(null)
    items.map(item =>
      SpendTx(
        getInBoxesFromJson((item \\ "inputs").headOption.get),
        getOutBoxesFromJson((item \\ "outputs").headOption.get),
        (item \\ "id").headOption.get.hcursor.as[String].getOrElse(""),
        address,
        (item \\ "timestamp").headOption.get.hcursor.as[Long].getOrElse(0)
      )
    )
  } catch {
    case e: Throwable =>
      logger.warn(s"An error occurred in fetching boxes from explorer for address: $address, Error: $e")
      List()
  }

  /**
   * @param boxId box id
   * @return confirmation num of box
   */
  def getConfirmationsForBoxId(boxId: String): Int = try {
    val json = GetURL.get(boxUrl + boxId)
    (json \\ "creationHeight").headOption
      .map { creationHeight =>
        val height = getHeight
        height - creationHeight.asNumber.get.toInt.get
      }
      .getOrElse(0)
  } catch {
    case any: Throwable => 0
  }

  /**
   * @param boxId box id
   * @return whether box exists on blockchain or not
   */
  def doesBoxExist(boxId: String): Option[Boolean] =
    GetURL.getOrError(boxUrl + boxId) match {
      case Right(Some(_)) => Some(true)
      case Right(None)    => Some(false)
      case Left(ex)       => None
    }

  def doesTxExistInPool(txId: String): Boolean =
    GetURL.getOrError(txUrl + "unconfirmed/" + txId) match {
      case Right(Some(_)) => true
      case _              => false
    }

  /**
   * @param txId transaction id
   * @return transaction specified by txId
   */
  def getTransaction(txId: String): Option[SpendTx] = try {
    val json            = GetURL.get(txUrl + txId)
    val outputs         = (json \\ "outputs").headOption.get
    val inputs          = (json \\ "inputs").headOption.get
    val timestamp: Long = (json \\ "timestamp").headOption.get.hcursor.as[Long].getOrElse(0)
    Some(SpendTx(getInBoxesFromJson(inputs), getOutBoxesFromJson(outputs), txId, "", timestamp))
  } catch {
    case a: Throwable => None
  }

  /**
   * @param txId transaction id
   * @return confirmation number of transaction, -1 in case it is not mined at all
   */
  def getTxNumConfirmations(txId: String): Int = try {
    val json = GetURL.get(txUrl + txId)
    (json \\ "confirmationsCount").head.asNumber.get.toInt.get
  } catch {
    case _: Throwable => -1 // not mined yet
  }

  /**
   * @param tokenId token id
   * @return information of token if exist
   */
  def getTokenInformation(tokenId: String): Option[TokenInformation] = try {
    val jsonStr = GetURL.getStr(tokenInformationUrl + tokenId)
    CreateTokenInformation(jsonStr)
  } catch {
    case _: Throwable => Option.empty // not mined yet
  }

  /**
   * @return transactions currently in mempool (at most 200)
   */
  def getPoolTransactionsStr: String = try // useful for just checking if it contains a certain id, seems faster.
    GetURL.getStr(
      txUrl + "unconfirmed?limit=200"
    ) // just check 200 pool txs, decrease the chance of double spend anyway
  catch {
    case a: Throwable => ""
  }

  /**
   * @return unconfirmed txs in mempool for a particular address
   */
  def getUnconfirmedTxsFor(address: String): Json = try
    GetURL.get(txUrl + s"unconfirmed/byAddress/$address")
  catch {
    case a: Throwable => Json.Null
  }

  /**
   * @param ergoTreeTemplateHash ergo tree template hash for the address of unspent box
   * @param tokenId corresponding token id
   * @return list of id of unspent boxes
   */
  def getUnspentBoxIdsWithAsset(ergoTreeTemplateHash: String, tokenId: String): Seq[String] = {
    val jsonString = s"""{
                        | "ergoTreeTemplateHash": "$ergoTreeTemplateHash",
                        | "assets": [
                        |     "$tokenId"
                        | ]
                        |}""".stripMargin
    val res = GetURL.post(unspentSearchUrl, jsonString)
    res.hcursor.downField("items").as[Seq[Json]] match {
      case Left(e) => throw new Exception(s"Error while parsing Json: $e")
      case Right(arr) =>
        arr.map(js =>
          js.hcursor.downField("boxId").as[String] match {
            case Left(e)   => throw new Exception(s"Error while parsing Json: $e")
            case Right(id) => id
          }
        )
    }
  }
}
