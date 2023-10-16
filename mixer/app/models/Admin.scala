package models

import io.circe.{parser, Decoder, Encoder, Json}
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._

object Admin {

  implicit val encodeTokenIncome: Encoder[TokenIncome] = (a: TokenIncome) =>
    Json.obj(
      "timestamp"   -> Json.fromLong(a.timestamp),
      "mixingLevel" -> Json.fromInt(a.mixingLevel),
      "numEntered"  -> Json.fromLong(a.numEntered),
      "amount"      -> Json.fromLong(a.amount)
    )

  case class TokenIncome(timestamp: Long, mixingLevel: Int, numEntered: Int, amount: Long) {
    override def toString: String = this.asJson.toString
  }

  object CreateTokenIncome {
    def apply(a: Array[Any]): TokenIncome = {
      val i = a.toIterator
      TokenIncome(
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long]
      )
    }

    implicit val tokenIncomeDecoder: Decoder[TokenIncome] = deriveDecoder[TokenIncome]

    def apply(jsonString: String): TokenIncome =
      parser.decode[TokenIncome](jsonString) match {
        case Left(e)      => throw new Exception(s"Error while parsing TokenIncome from Json: $e")
        case Right(asset) => asset
      }
  }

  implicit val encodeCommissionIncome: Encoder[CommissionIncome] = (a: CommissionIncome) =>
    Json.obj(
      "timestamp"  -> Json.fromLong(a.timestamp),
      "tokenId"    -> Json.fromString(a.tokenId),
      "ring"       -> Json.fromLong(a.ring),
      "numEntered" -> Json.fromInt(a.numEntered),
      "commission" -> Json.fromLong(a.commission),
      "donation"   -> Json.fromLong(a.donation)
    )

  case class CommissionIncome(
    timestamp: Long,
    tokenId: String,
    ring: Long,
    numEntered: Int,
    commission: Long,
    donation: Long
  ) {
    override def toString: String = this.asJson.toString
  }

  object CreateCommissionIncome {
    def apply(a: Array[Any]): CommissionIncome = {
      val i = a.toIterator
      CommissionIncome(
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Long]
      )
    }

    implicit val commissionIncomeDecoder: Decoder[CommissionIncome] = deriveDecoder[CommissionIncome]

    def apply(jsonString: String): CommissionIncome =
      parser.decode[CommissionIncome](jsonString) match {
        case Left(e)      => throw new Exception(s"Error while parsing CommissionIncome from Json: $e")
        case Right(asset) => asset
      }
  }

  case class IncomeState(orderNum: Int, txId: String, retryNum: Int = -1)

  object CreateIncomeState {
    def apply(a: Array[Any]): IncomeState = {
      val i = a.toIterator
      IncomeState(
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int]
      )
    }
  }

}
