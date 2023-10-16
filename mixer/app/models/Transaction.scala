package models

import java.nio.charset.StandardCharsets

import io.circe.{parser, Decoder, HCursor, Json}
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import mixinterface.TokenErgoMix
import models.Box.{FullMixBox, HalfMixBox, InBox, OutBox}
import org.ergoplatform.appkit.SignedTransaction

object Transaction {

  case class SpendTx(inboxes: Seq[InBox], outboxes: Seq[OutBox], id: String, address: String, timestamp: Long)

  case class WithdrawTx(
    mixId: String,
    txId: String,
    time: Long,
    boxId: String,
    txBytes: Array[Byte],
    additionalInfo: String = ""
  ) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)

    def getFeeBox: Option[String] = { // returns fee box used in this tx if available
      val inputs = boxId.split(",")
      if (inputs.size > 1) return Some(inputs(inputs.size - 1))
      Option.empty
    }

    def getJson: Json = io.circe.parser.parse(toString).getOrElse(Json.Null)

    def getDataInputs: Seq[String] =
      getJson.hcursor
        .downField("dataInputs")
        .as[Seq[Json]]
        .getOrElse(Seq())
        .map(js => js.hcursor.downField("boxId").as[String].getOrElse(null))

    def getOutputs: Seq[String] =
      getJson.hcursor
        .downField("outputs")
        .as[Seq[Json]]
        .getOrElse(Seq())
        .map(js => js.hcursor.downField("boxId").as[String].getOrElse(null))

    def getInputs: Seq[String] =
      getJson.hcursor
        .downField("inputs")
        .as[Seq[Json]]
        .getOrElse(Seq())
        .map(js => js.hcursor.downField("boxId").as[String].getOrElse(null))
  }

  implicit val decodeArrayByte: Decoder[Array[Byte]] = (c: HCursor) =>
    for {
      s <- c.as[String]
    } yield s.getBytes("utf-16")

  object CreateWithdrawTx {
    def apply(a: Array[Any]): WithdrawTx = {
      val i = a.toIterator
      WithdrawTx(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Array[Byte]],
        i.next().asInstanceOf[String],
      )
    }

    implicit val withdrawTxDecoder: Decoder[WithdrawTx] = deriveDecoder[WithdrawTx]

    def apply(jsonString: String): WithdrawTx =
      parser.decode[WithdrawTx](jsonString) match {
        case Left(e)      => throw new Exception(s"Error while parsing WithdrawTx from Json: $e")
        case Right(asset) => asset
      }
  }

  case class MixTransaction(boxId: String, txId: String, txBytes: Array[Byte]) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }

  object CreateMixTransaction {
    def apply(a: Array[Any]): MixTransaction = {
      val i = a.toIterator
      MixTransaction(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Array[Byte]]
      )
    }
  }

  case class DistributeTx(
    mixGroupId: String,
    txId: String,
    order: Int,
    time: Long,
    txBytes: Array[Byte],
    inputs: String
  ) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }

  object CreateDistributeTx {
    def apply(a: Array[Any]): DistributeTx = {
      val i = a.toIterator
      DistributeTx(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Array[Byte]],
        i.next().asInstanceOf[String],
      )
    }
  }

  case class Withdraw(mixId: String, txId: String, createdTime: Long, fullMixBoxId: String, tx: Json) {
    override def toString: String = this.asJson.toString
  }

  object CreateWithdraw {
    def apply(a: Array[Any]): Withdraw = {
      val i = a.toIterator
      Withdraw(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        parseTx(i.next().asInstanceOf[Array[Byte]])
      )
    }

    private def parseTx(txBytes: Array[Byte]) =
      parser.parse(new String(txBytes, "utf-16")) match {
        case Right(txJson) => txJson
        case Left(_)       => Json.Null
      }
  }

  case class HalfMixTx(tx: SignedTransaction)(implicit ergoMix: TokenErgoMix) {
    val getHalfMixBox: HalfMixBox = HalfMixBox(tx.getOutputsToSpend.get(0))
    require(getHalfMixBox.inputBox.getErgoTree == ergoMix.halfMixContract.getErgoTree)
  }

  case class FullMixTx(tx: SignedTransaction)(implicit ergoMix: TokenErgoMix) {
    val getFullMixBoxes: (FullMixBox, FullMixBox) =
      (FullMixBox(tx.getOutputsToSpend.get(0)), FullMixBox(tx.getOutputsToSpend.get(1)))
    require(getFullMixBoxes._1.inputBox.getErgoTree == ergoMix.fullMixScriptErgoTree)
    require(getFullMixBoxes._2.inputBox.getErgoTree == ergoMix.fullMixScriptErgoTree)
  }

  case class CovertAssetWithdrawTx(
    covertId: String,
    tokenId: String,
    withdrawAddress: String,
    createdTime: Long,
    withdrawStatus: String,
    txId: String,
    tx: Array[Byte]
  ) {
    override def toString: String = new String(tx, StandardCharsets.UTF_16)

    def getJson: Json = io.circe.parser.parse(toString).getOrElse(Json.Null)
  }

  object CreateCovertAssetWithdrawTx {
    def apply(a: Array[Any]): CovertAssetWithdrawTx = {
      val i = a.toIterator
      CovertAssetWithdrawTx(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Array[Byte]]
      )
    }
  }

}
