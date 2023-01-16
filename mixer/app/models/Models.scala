package models

import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import io.circe.{Json, Encoder, Decoder, parser, HCursor}
import models.Request.MixRequest
import models.Transaction.Withdraw
import org.ergoplatform.appkit.{ErgoToken, InputBox}
import special.collection.Coll

import java.nio.charset.StandardCharsets
import scala.collection.mutable

object Models {

  implicit val decodeBigInt: Decoder[BigInt] = (c: HCursor) => for {
    bi <- c.as[String]
  } yield {
    BigInt(bi)
  }

  case class CovertAsset(covertId: String, tokenId: String, ring: Long, confirmedDeposit: Long, lastActivity: Long) {
    /**
     * @param needed        needed amount of this asset for the mix to start
     * @param currentMixing current mixing amount of this asset
     * @return json of the asset as string
     */
    def toJson(needed: Long, currentMixing: Long, runningMixing: Long): String = {
      s"""{
         |  "tokenId": "$tokenId",
         |  "ring": $ring,
         |  "need": $needed,
         |  "confirmedDeposit": $confirmedDeposit,
         |  "lastActivity": $lastActivity,
         |  "currentMixingAmount": $currentMixing,
         |  "runningMixingAmount": $runningMixing
         |}""".stripMargin
    }

    def isErg: Boolean = tokenId.isEmpty
  }

  object CreateCovertAsset {
    def apply(a: Array[Any]): CovertAsset = {
      val i = a.toIterator
      CovertAsset(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long]
      )
    }

    implicit val covertAssetDecoder: Decoder[CovertAsset] = deriveDecoder[CovertAsset]

    def apply(jsonString: String): CovertAsset = {
      parser.decode[CovertAsset](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing CovertAsset from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  implicit val encodeMixState: Encoder[MixState] = (a: MixState) => {
    Json.obj(
      "isAlice" -> Json.fromBoolean(a.isAlice),
      "round" -> Json.fromInt(a.round)
    )
  }

  case class MixState(id: String, round: Int, isAlice: Boolean) {
    override def toString: String = this.asJson.toString
  }

  object CreateMixState {
    def apply(a: Array[Any]): MixState = {
      val i = a.toIterator
      MixState(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Boolean]
      )
    }

    implicit val mixStateDecoder: Decoder[MixState] = deriveDecoder[MixState]

    def apply(jsonString: String): MixState = {
      parser.decode[MixState](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixState from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  case class MixHistory(id: String, round: Int, isAlice: Boolean, time: Long) {
    override def toString: String = this.asJson.toString
  }

  object CreateMixHistory {
    def apply(a: Array[Any]): MixHistory = {
      val i = a.toIterator
      MixHistory(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[Long]
      )
    }

    implicit val mixHistoryDecoder: Decoder[MixHistory] = deriveDecoder[MixHistory]

    def apply(jsonString: String): MixHistory = {
      parser.decode[MixHistory](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixHistory from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  case class Deposit(address: String, boxId: String, amount: Long, createdTime: Long, tokenAmount: Long) {
    override def toString: String = this.asJson.toString
  }

  object CreateDeposit {
    def apply(a: Array[Any]): Deposit = {
      val i = a.toIterator
      Deposit(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long]
      )
    }
  }

  case class SpentDeposit(address: String,
                          boxId: String,
                          amount: Long,
                          createdTime: Long,
                          tokenAmount: Long,
                          txId: String,
                          spentTime: Long,
                          purpose: String) {
    override def toString: String = this.asJson.toString
  }

  object CreateSpentDeposit {
    def apply(a: Array[Any]): SpentDeposit = {
      val i = a.toIterator
      SpentDeposit(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String]
      )
    }
  }

  implicit val encodeHalfMix: Encoder[HalfMix] = (a: HalfMix) => {
    Json.obj(
      "mixId" -> Json.fromString(a.mixId),
      "round" -> Json.fromInt(a.round),
      "halfMixBoxId" -> Json.fromString(a.halfMixBoxId),
      "createdTime" -> Json.fromLong(a.createdTime),
      "age" -> Json.fromString(s"${(System.currentTimeMillis() - a.createdTime) / (1000 * 60)} minutes")
    )
  }

  case class HalfMix(mixId: String, round: Int, createdTime: Long, halfMixBoxId: String, isSpent: Boolean) {
    override def toString: String = this.asJson.toString
  }

  object CreateHalfMix {
    def apply(a: Array[Any]): HalfMix = {
      val i = a.toIterator
      HalfMix(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Boolean]
      )
    }

    implicit val halfMixDecoder: Decoder[HalfMix] = deriveDecoder[HalfMix]

    def apply(jsonString: String): HalfMix = {
      parser.decode[HalfMix](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing HalfMix from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  implicit val encodeFullMix: Encoder[FullMix] = (a: FullMix) => {
    Json.obj(
      "mixId" -> Json.fromString(a.mixId),
      "round" -> Json.fromInt(a.round),
      "halfMixBoxId" -> Json.fromString(a.halfMixBoxId),
      "fullMixBoxId" -> Json.fromString(a.fullMixBoxId),
      "createdTime" -> Json.fromLong(a.createdTime),
      "age" -> Json.fromString(s"${(System.currentTimeMillis() - a.createdTime) / (1000 * 60)} minutes")
    )
  }

  case class FullMix(mixId: String, round: Int, createdTime: Long, halfMixBoxId: String, fullMixBoxId: String) {
    override def toString: String = this.asJson.toString
  }

  object CreateFullMix {
    def apply(a: Array[Any]): FullMix = {
      val i = a.toIterator
      FullMix(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String]
      )
    }

    implicit val fullMixDecoder: Decoder[FullMix] = deriveDecoder[FullMix]

    def apply(jsonString: String): FullMix = {
      parser.decode[FullMix](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing FullMix from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  implicit val encodeWithdraw: Encoder[Withdraw] = (a: Withdraw) => {
    Json.obj(
      "txId" -> Json.fromString(a.txId),
      "createdTime" -> Json.fromLong(a.createdTime)
    )
  }

  case class Mix(mixRequest: MixRequest, mixState: Option[MixState], halfMix: Option[HalfMix], fullMix: Option[FullMix], withdraw: Option[Withdraw]) {
    override def toString: String = this.asJson.toString
  }

  case class EntityInfo(name: String, id: String, rings: Seq[Long], decimals: Int, dynamicFeeRate: Long) {
    def toJson(more: String = ""): String = {
      s"""{
         |  $more
         |  "name": "$name",
         |  "id": "$id",
         |  "rings": [${rings.mkString(",")}],
         |  "decimals": $decimals
         |}""".stripMargin
    }
  }

  object EntityInfo {
    def apply(box: InputBox): EntityInfo = {
      val name = new String(box.getRegisters.get(0).getValue.asInstanceOf[Coll[Byte]].toArray, StandardCharsets.UTF_8)
      val id = new String(box.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray, StandardCharsets.UTF_8).toLowerCase
      val rings = box.getRegisters.get(2).getValue.asInstanceOf[Coll[Long]]
      val decimals = if (box.getRegisters.size() >= 4) box.getRegisters.get(3).getValue.asInstanceOf[Int] else 0
      val dynamicFeeRate = if (box.getRegisters.size() >= 5) box.getRegisters.get(4).getValue.asInstanceOf[Long] else 1000L // 1000 for 1e6 nano erg / byte
      new EntityInfo(name, id, rings.toArray, decimals, dynamicFeeRate)
    }
  }

  case class HopMix(mixId: String, round: Int, createdTime: Long, boxId: String) {
    override def toString: String = this.asJson.toString
  }

  object CreateHopMix {
    def apply(a: Array[Any]): HopMix = {
      val i = a.toIterator
      HopMix(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String]
      )
    }

    implicit val hopMixDecoder: Decoder[HopMix] = deriveDecoder[HopMix]

    def apply(jsonString: String): HopMix = {
      parser.decode[HopMix](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing HopMix from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  class TokenMap {
    val tokens = mutable.Map.empty[String, Long]

    def add(token: ErgoToken): Unit = {
      val tokenId = token.getId.toString
      val value = token.getValue
      if (tokens.contains(tokenId)) tokens(tokenId) += value
      else tokens(tokenId) = value
    }

    def toJavaArray: java.util.ArrayList[ErgoToken] = {
      val tokenList = new java.util.ArrayList[ErgoToken]()
      tokens.keys.foreach(tokenId => {
        tokenList.add(new ErgoToken(tokenId, tokens(tokenId)))
      })
      tokenList
    }

    def isEmpty: Boolean = tokens.isEmpty
  }

}
