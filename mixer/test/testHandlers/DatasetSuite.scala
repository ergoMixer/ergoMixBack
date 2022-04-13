package testHandlers

import app.Configs
import io.circe.parser.parse
import io.circe.{Json, parser}
import models.Models._
import org.ergoplatform.appkit.{ErgoToken, SignedTransaction}

import scala.collection.mutable
import scala.io.Source.fromFile


class DatasetSuite {

  private val rings: Seq[EntityInfo] = Seq(
    EntityInfo("ERG", "", Seq(1000000000L, 10000000000L, 100000000000L), 9, 1000L)
  )

  def readJsonFile(filePath: String): String = {
    val sourceFile = fromFile(filePath)
    val jsonString = sourceFile.getLines.mkString
    sourceFile.close()
    jsonString
  }

  def setConfigData: Unit = {
    val idToParam = mutable.Map.empty[String, EntityInfo]
    rings.foreach(param => idToParam(param.id) = param)
    Configs.params = idToParam
    Configs.tokenPrices = Some(Map(180 -> 720000000, 90 -> 360000000, 60 -> 240000000, 30 -> 120000000))
    Configs.entranceFee = Some(200)
  }

  def jsonToObjectList[T](jsonString: String, f: String => T): Seq[T] = {
    parser.parse(jsonString) match {
      case Left(e) => throw new Exception(s"Error while parsing Object list from Json: $e")
      case Right(js) => js.hcursor.downField("items").as[Seq[Json]] match {
        case Left(e) => throw new Exception(s"Error while parsing Object list from Json: $e")
        case Right(arr) => arr.map(js => f(js.toString))
      }
    }
  }

  private def getId(json: Json, key: String = "id"): String = (json \\ key).map(v => v.asString.get).head

  def jsonToOutBox(json: Json, key: String = "id"): OutBox = {
    val id = getId(json, key)
    val value = (json \\ "value").map(v => v.asNumber.get).head
    val creationHeight = (json \\ "creationHeight").map(v => v.asNumber.get).head
    val assets: Seq[Json] = (json \\ "assets").map(v => v.asArray.get).head
    val tokens: Seq[ErgoToken] = assets.map { asset =>
      val tokenID = (asset \\ "tokenId").map(v => v.asString.get).head
      val value = (asset \\ "amount").map(v => v.asNumber.get).head.toLong.get
      new ErgoToken(tokenID, value)
    }
    val registers = (json \\ "additionalRegisters").flatMap { r =>
      r.asObject.get.toList.map {
        case (key, value) => (key, value.asString.get)
      }
    }.toMap
    val ergoTree = (json \\ "ergoTree").map(v => v.asString.get).head
    val address = (json \\ "address").map(v => v.asString.get).head
    val spendingTxId = (json \\ "spentTransactionId").map(v => v.asString).head
    OutBox(id, value.toLong.get, registers, ergoTree, tokens, creationHeight.toInt.get, address, spendingTxId)
  }

  /**
   * only Id and address of the box
   */
  def jsonToInBox(json: Json): InBox = {
    val id = getId(json)
    val address = (json \\ "address").map(v => v.asString.get).head
    InBox(id, address, "", 0L)
  }

  /**
   * only id, value and ergoTree of the box
   */
  def jsonToSignedOutBox(json: Json): OutBox = {
    val id = getId(json, "boxId")
    val value = (json \\ "value").map(v => v.asNumber.get).head
    val ergoTree = (json \\ "ergoTree").map(v => v.asString.get).head
    OutBox(id, value.toLong.get, Map.empty[String, String], ergoTree, Seq.empty[ErgoToken],0, "", Option.empty[String])
  }

  /**
   * only Id of the box
   */
  def jsonToSignedInBox(json: Json): InBox = {
    val id = getId(json, "boxId")
    InBox(id, "", "", 0L)
  }

  def jsonToSpentTx(jsonString: String): SpendTx = {
    val json = parse(jsonString).getOrElse(throw new Exception("Error while converting SpendTx jsonString to Json object"))
    val id = (json \\ "id").map(v => v.asString.get).head
    val outBoxes: Seq[OutBox] = (json \\ "outBoxes").map(v => v.asArray.get).head.map(jsonToOutBox(_))
    val inBoxes: Seq[InBox] = (json \\ "inBoxes").map(v => v.asArray.get).head.map(jsonToInBox)
    SpendTx(inBoxes, outBoxes, id, "", 0L)
  }

  def jsonToSignedTransaction(jsonString: String): SignedTransaction = {
    val json = parse(jsonString).getOrElse(throw new Exception("Error while converting SignedTransaction jsonString to Json object"))
    val id = (json \\ "id").map(v => v.asString.get).head
    val inBoxes: Seq[InBox] = (json \\ "inputs").map(v => v.asArray.get).head.map(jsonToSignedInBox)
    val outBoxes: Seq[OutBox] = (json \\ "outputs").map(v => v.asArray.get).head.map(jsonToSignedOutBox)
    TestSignedTransaction(id, inBoxes, outBoxes)
  }

  setConfigData
}
