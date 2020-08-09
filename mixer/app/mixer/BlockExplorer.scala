package mixer

import java.io.InputStream
import java.net.HttpURLConnection

import io.circe.Json
import mixer.Models.{InBox, OutBox, SpendTx}
import app.Configs
import org.ergoplatform.appkit.BlockchainContext

import scala.util.{Failure, Success, Try}

class BlockExplorer(implicit ctx:BlockchainContext) extends UTXOReader {

  private val baseUrl = Configs.explorerUrl

  private val unspentUrl = s"$baseUrl/transactions/boxes/byAddress/unspent/"
  private val blockUrl = s"$baseUrl/blocks"
  private val boxUrl = s"$baseUrl/transactions/boxes/"
  private val txUrl = s"$baseUrl/transactions/"
  private val urlAddress = s"$baseUrl/addresses/"

  private def getOutBoxFromJson(j: Json) = {
    val id = getId(j)
    val value = (j \\ "value").map(v => v.asNumber.get).apply(0)
    val registers = (j \\ "additionalRegisters").flatMap { r =>
      r.asObject.get.toList.map {
        case (key, value) => (key, value.asString.get)
      }
    }.toMap
    val ergoTree = (j \\ "ergoTree").map(v => v.asString.get).apply(0)
    val spendingTxId = (j \\ "spentTransactionId").map(v => v.asString).apply(0)
    OutBox(id, value.toLong.get, registers, ergoTree, spendingTxId)
  }

  private def getId(j: Json) = (j \\ "id").map(v => v.asString.get).apply(0)

  private def getInBoxFromJson(j: Json) = {
    val id = getId(j)
    val address = (j \\ "address").map(v => v.asString.get).apply(0)
    val value = (j \\ "value").map(v => v.asNumber.get).apply(0)
    val createdTxId = (j \\ "outputTransactionId").map(v => v.asString.get).apply(0)
    InBox(id, address, createdTxId, value.toLong.get)
  }

  private def getOutBoxesFromJson(json: Json): Seq[OutBox] = {
    json.asArray.get.map(getOutBoxFromJson)
  }

  private def getInBoxesFromJson(json: Json): Seq[InBox] = {
    json.asArray.get.map(getInBoxFromJson)
  }

  def getHeight: Int = {
    val json = GetURL.get(blockUrl)
    ((json \\ "items") (0).asArray.get(0) \\ "height") (0).toString().toInt
  }

  override def getUnspentBoxes(address: String): Seq[OutBox] = {
    getOutBoxesFromJson(GetURL.get(unspentUrl + address))
  }

  def getBoxById(boxId: String) = {
    getOutBoxFromJson(GetURL.get(boxUrl + boxId))
  }

  def getSpendingTxId(boxId: String) = try {
    getBoxById(boxId).spendingTxId
  } catch {
    case a: Throwable => None
  }

  def getTransactionsByAddress(address:String, limit:Long = 0, offset:Long = 0): List[SpendTx] = try {
    val json = GetURL.get(urlAddress + address + s"/transactions?limit=$limit&offset=$offset")
    val items = (json \\ "items").headOption.get.hcursor.as[List[Json]].getOrElse(null)
    items.map(item => SpendTx(
      getInBoxesFromJson((item \\ "inputs").headOption.get),
      getOutBoxesFromJson((item \\ "outputs").headOption.get),
      "", address, (item \\ "timestamp").headOption.get.hcursor.as[Long].getOrElse(0)
    ))
  } catch {
    case a:Throwable => List()
  }

  def getConfirmationsForBoxId(boxId: String): Int = try {
    val json = (GetURL.get(boxUrl + boxId))
    val isOnMainChain = (json \\ "mainChain").head.asBoolean.getOrElse(false)
    if (!isOnMainChain) return 0
    (json \\ "creationHeight").headOption.map { creationHeight =>
      val height = getHeight
      height - creationHeight.asNumber.get.toInt.get
    }.getOrElse(0)
  } catch {
    case any: Throwable => 0
  }

  def doesBoxExist(boxId: String) = {
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

  def getTransaction(txId: String): Option[SpendTx] = try {
    val json = (GetURL.get(txUrl + txId))
    val outputs = (json \\ "outputs").headOption.get
    val inputs = (json \\ "inputs").headOption.get
    val timestamp:Long = (json \\ "timestamp").headOption.get.hcursor.as[Long].getOrElse(0)
    Some(SpendTx(getInBoxesFromJson(inputs), getOutBoxesFromJson(outputs), txId, "", timestamp))
  } catch {
    case a: Throwable => None
  }

  def getTxNumConfirmations(txId: String): Int = try {
    val json = GetURL.get(txUrl + txId)
    (json \\ "confirmationsCount").head.asNumber.get.toInt.get
  } catch {
    case _: Throwable => -1 // not mined yet
  }

  def getPoolTransactionsStr: String = try { // useful for just checking if it contains a certain id, seems faster.
    GetURL.getStr(txUrl + "unconfirmed?limit=200") // just check 200 pool txs, decrease the chance of double spend anyway
  } catch {
    case a: Throwable => ""
  }
}

object GetURL {

  import java.net.URL

  import io.circe._
  import io.circe.parser._

  import scala.io.Source

  val requestProperties = Map(
    "User-Agent" -> "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)"
  )

  private def is2Str(is: InputStream) = {
    Try(Source.fromInputStream(is).getLines.mkString("\n")) match {
      case Success(s) => s
      case Failure(exception) => exception.getMessage
    }
  }

  def getOrError(url: String): Either[Throwable, Option[Json]] = {
    Try {
      val connection = new URL(url).openConnection
      requestProperties.foreach { case (name, value) => connection.setRequestProperty(name, value) }
      val httpConn = connection.asInstanceOf[HttpURLConnection]
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(parse(is2Str(httpConn.getInputStream)).right.get)).toEither
      case Success((404, _)) => Right(None) // not found; we want to consider this as a "good" case (implies box has 0 confirmation or does not exist)
      case Success((httpCode, httpConn)) => Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }
  }

  def getOrErrorStr(url: String): Either[Throwable, Option[String]] = {
    Try {
      val connection = new URL(url).openConnection
      requestProperties.foreach { case (name, value) => connection.setRequestProperty(name, value) }
      val httpConn = connection.asInstanceOf[HttpURLConnection]
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(is2Str(httpConn.getInputStream))).toEither
      case Success((404, _)) => Right(None) // not found; we want to consider this as a "good" case (implies box has 0 confirmation or does not exist)
      case Success((httpCode, httpConn)) => Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }
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
      case Right(None) => throw new Exception("Explorer returned error 404")
      case Left(ex) => throw ex
    }
  }
}