package network

import java.io.{BufferedWriter, InputStream, OutputStreamWriter}
import java.net.{HttpURLConnection, URLConnection}
import app.Configs
import io.circe.Json

import javax.inject.Singleton
import models.Box.{InBox, OutBox}
import models.Transaction.SpendTx
import org.ergoplatform.appkit.ErgoToken

import scala.util.{Failure, Success, Try}


@Singleton
class BlockExplorer() {

  private val baseUrl = Configs.explorerUrl

  private val unspentUrl = s"$baseUrl/transactions/boxes/byAddress/unspent/"
  private val blockUrl = s"$baseUrl/blocks?limit=1"
  private val boxUrl = s"$baseUrl/transactions/boxes/"
  private val txUrl = s"$baseUrl/transactions/"
  private val urlAddress = s"$baseUrl/addresses/"
  private val unspentSearchUrl = s"$baseUrl/api/v1/boxes/unspent/search/"
  private val boxesByAddressUtl = s"$baseUrl/api/v1/boxes/byAddress/"

  /**
   * @param j json representation of box
   * @return box as OutBox
   */
  private def getOutBoxFromJson(j: Json): OutBox = {
    val id = getId(j)
    val value = (j \\ "value").map(v => v.asNumber.get).head
    val creationHeight = (j \\ "creationHeight").map(v => v.asNumber.get).head
    val assets: Seq[Json] = (j \\ "assets").map(v => v.asArray.get).head
    val tokens: Seq[ErgoToken] = assets.map { asset =>
      val tokenID = (asset \\ "tokenId").map(v => v.asString.get).head
      val value = (asset \\ "amount").map(v => v.asNumber.get).head.toLong.get
      new ErgoToken(tokenID, value)
    }
    val registers = (j \\ "additionalRegisters").flatMap { r =>
      r.asObject.get.toList.map {
        case (key, value) => (key, value.asString.get)
      }
    }.toMap
    val ergoTree = (j \\ "ergoTree").map(v => v.asString.get).apply(0)
    val address = (j \\ "address").map(v => v.asString.get).apply(0)
    val spendingTxId = (j \\ "spentTransactionId").map(v => v.asString).apply(0)
    OutBox(id, value.toLong.get, registers, ergoTree, tokens, creationHeight.toInt.get, address, spendingTxId)
  }

  /**
   * @param j json representation of box
   * @return id of box
   */
  private def getId(j: Json): String = (j \\ "id").map(v => v.asString.get).apply(0)

  /**
   * @param j json representation of box
   * @return box as InBox
   */
  private def getInBoxFromJson(j: Json) = {
    val id = getId(j)
    val address = (j \\ "address").map(v => v.asString.get).apply(0)
    val value = (j \\ "value").map(v => v.asNumber.get).apply(0)
    val createdTxId = (j \\ "outputTransactionId").map(v => v.asString.get).apply(0)
    InBox(id, address, createdTxId, value.toLong.get)
  }

  /**
   * @param json json representation of box
   * @return box as OutBox
   */
  private def getOutBoxesFromJson(json: Json): Seq[OutBox] = {
    json.asArray.get.map(getOutBoxFromJson)
  }

  /**
   * @param json json representation of box
   * @return box as InBox
   */
  private def getInBoxesFromJson(json: Json): Seq[InBox] = {
    json.asArray.get.map(getInBoxFromJson)
  }

  /**
   * @return current height of blockchain
   */
  def getHeight: Int = {
    val json = GetURL.get(blockUrl)
    ((json \\ "items").head.asArray.get(0) \\ "height").head.toString().toInt
  }

  /**
   * @param address address to get unspent boxes of
   * @return list of unspent boxes
   */
  def getUnspentBoxes(address: String): Seq[OutBox] = {
    getOutBoxesFromJson(GetURL.get(unspentUrl + address))
  }

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
  def getBoxById(boxId: String): OutBox = {
    getOutBoxFromJson(GetURL.get(boxUrl + boxId))
  }

  /**
   * @param tokenId token id
   * @return box as OutBox
   */
  def getBoxByTokenId(tokenId: String): Json = {
    GetURL.get(s"https://api.ergoplatform.com/api/v1/boxes/unspent/byTokenId/$tokenId")
  }

  /**
   * @param boxId box id
   * @return spending tx of box id if spent at all
   */
  def getSpendingTxId(boxId: String): Option[String] = try {
    getBoxById(boxId).spendingTxId
  } catch {
    case a: Throwable => None
  }

  /**
   * @param address address
   * @param limit   max number of txs
   * @param offset  offset
   * @return list of transaction of address in specified range
   */
  def getTransactionsByAddress(address: String, limit: Long = 0, offset: Long = 0): List[SpendTx] = try {
    val json = GetURL.get(urlAddress + address + s"/transactions?limit=$limit&offset=$offset")
    val items = (json \\ "items").headOption.get.hcursor.as[List[Json]].getOrElse(null)
    items.map(item => SpendTx(
      getInBoxesFromJson((item \\ "inputs").headOption.get),
      getOutBoxesFromJson((item \\ "outputs").headOption.get),
      "", address, (item \\ "timestamp").headOption.get.hcursor.as[Long].getOrElse(0)
    ))
  } catch {
    case a: Throwable => List()
  }

  /**
   * @param boxId box id
   * @return confirmation num of box
   */
  def getConfirmationsForBoxId(boxId: String): Int = try {
    val json = (GetURL.get(boxUrl + boxId))
    (json \\ "creationHeight").headOption.map { creationHeight =>
      val height = getHeight
      height - creationHeight.asNumber.get.toInt.get
    }.getOrElse(0)
  } catch {
    case any: Throwable => 0
  }

  /**
   * @param boxId box id
   * @return whether box exists on blockchain or not
   */
  def doesBoxExist(boxId: String): Option[Boolean] = {
    GetURL.getOrError(boxUrl + boxId) match {
      case Right(Some(_)) => Some(true)
      case Right(None) => Some(false)
      case Left(ex) => None
    }
  }

  def doesTxExistInPool(txId: String): Boolean = {
    GetURL.getOrError(txUrl + "unconfirmed/" + txId) match {
      case Right(Some(_)) => true
      case _ => false
    }
  }

  /**
   * @param txId transaction id
   * @return transaction specified by txId
   */
  def getTransaction(txId: String): Option[SpendTx] = try {
    val json = (GetURL.get(txUrl + txId))
    val outputs = (json \\ "outputs").headOption.get
    val inputs = (json \\ "inputs").headOption.get
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
   * @return transactions currently in mempool (at most 200)
   */
  def getPoolTransactionsStr: String = try { // useful for just checking if it contains a certain id, seems faster.
    GetURL.getStr(txUrl + "unconfirmed?limit=200") // just check 200 pool txs, decrease the chance of double spend anyway
  } catch {
    case a: Throwable => ""
  }

  /**
   * @return unconfirmed txs in mempool for a particular address
   */
  def getUnconfirmedTxsFor(address: String): Json = try {
    GetURL.get(txUrl + s"unconfirmed/byAddress/$address")
  } catch {
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
    val res = (GetURL.post(unspentSearchUrl, jsonString))
    res.hcursor.downField("items").as[Seq[Json]] match {
      case Left(e) => throw new Exception(s"Error while parsing Json: $e")
      case Right(arr) => arr.map(js => js.hcursor.downField("boxId").as[String] match {
        case Left(e) => throw new Exception(s"Error while parsing Json: $e")
        case Right(id) => id
      })
    }
  }

}

object GetURL {

  import java.net.URL

  import io.circe._
  import io.circe.parser._

  import scala.io.Source

  val requestProperties = Map(
    "User-Agent" -> "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)",
    "Accept"-> "application/json"
  )

  private def is2Str(is: InputStream) = {
    Try(Source.fromInputStream(is).getLines.mkString("\n")) match {
      case Success(s) => s
      case Failure(exception) => exception.getMessage
    }
  }

  /**
   * @param url           url
   * @param headers       headers
   * @param useProxyIfSet whether to use proxy if possible or not
   * @return connection (potentially with going through proxy)
   */
  def getConnection(url: String, headers: Map[String, String], useProxyIfSet: Boolean = true): URLConnection = {
    val connection = {
      if (!useProxyIfSet || Configs.proxy == null) new URL(url).openConnection
      else new URL(url).openConnection(Configs.proxy)
    }
    headers.foreach { case (name, value) => connection.setRequestProperty(name, value) }
    connection.setConnectTimeout(20000)
    connection
  }

  def getOrError(url: String): Either[Throwable, Option[Json]] = {
    Try {
      val connection = getConnection(url, requestProperties)
      val httpConn = connection.asInstanceOf[HttpURLConnection]
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(parse(is2Str(httpConn.getInputStream)).right.get)).toEither
      case Success((404, _)) => Right(None) // not found; we want to consider this as a "good" case (implies box has 0 confirmation or does not exist)
      case Success((httpCode, httpConn)) => Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }
  }

  def getOrErrorStr(url: String, headers: Map[String, String], useProxyIfSet: Boolean = true): Either[Throwable, Option[String]] = {
    Try {
      val connection = getConnection(url, headers, useProxyIfSet)
      val httpConn = connection.asInstanceOf[HttpURLConnection]
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(is2Str(httpConn.getInputStream))).toEither
      case Success((404, _)) => Right(None) // not found; we want to consider this as a "good" case (implies box has 0 confirmation or does not exist)
      case Success((httpCode, httpConn)) => Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }
  }

  def getOrErrorStr(url: String): Either[Throwable, Option[String]] = {
    getOrErrorStr(url, requestProperties)
  }

  def get(url: String): Json = {
    getOrError(url) match {
      case Right(Some(json)) => json
      case Right(None) => throw new Exception("Explorer returned error 404")
      case Left(ex) => throw ex
    }
  }

  def getStr(url: String): String = {
    getOrErrorStr(url) match {
      case Right(Some(string)) => string
      case Right(None) => throw new Exception("Returned error 404")
      case Left(ex) => throw ex
    }
  }

  def getStr(url: String, additionalHeaders: Map[String, String], useProxyIfSet: Boolean = true): String = {
    getOrErrorStr(url, additionalHeaders ++ requestProperties, useProxyIfSet) match {
      case Right(Some(string)) => string
      case Right(None) => throw new Exception("Returned error 404")
      case Left(ex) => throw ex
    }
  }

  def postConnection(url: String, headers: Map[String, String], useProxyIfSet: Boolean = true): HttpURLConnection = {
    val connection = {
      if (!useProxyIfSet || Configs.proxy == null) new URL(url).openConnection
      else new URL(url).openConnection(Configs.proxy)
    }.asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    headers.foreach { case (name, value) => connection.setRequestProperty(name, value) }
    connection.setConnectTimeout(20000)
    connection
  }

  def postOrError(url: String, jsonString: String): Either[Throwable, Option[Json]] = {
    Try {
      val httpConn = postConnection(url, Map("Content-Type" -> "application/json") ++ requestProperties)
      val outputBuffer: BufferedWriter = new BufferedWriter(new OutputStreamWriter(httpConn.getOutputStream))
      outputBuffer.write(jsonString)
      outputBuffer.close()
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(parse(is2Str(httpConn.getInputStream)).right.get)).toEither
      case Success((httpCode, httpConn)) => Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }
  }

  def post(url: String, jsonString: String): Json = {
    postOrError(url, jsonString) match {
      case Right(Some(json)) => json
      case Right(None) => throw new Exception("Explorer returned error 404")
      case Left(ex) =>
        throw ex
    }
  }

}
