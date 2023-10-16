package models

import io.circe.{parser, Decoder}
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._

object Rescan {

  case class FollowedMix(round: Int, isAlice: Boolean, halfMixBoxId: String, fullMixBoxId: Option[String]) {
    override def toString: String = this.asJson.toString
  }

  object CreateFollowedMix {
    def apply(a: Array[Any]): FollowedMix = {
      val i = a.toIterator
      FollowedMix(
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[String],
        try
          Option(i.next().asInstanceOf[String])
        catch {
          case _: Throwable => Option.empty[String]
        }
      )
    }

    implicit val followedMixDecoder: Decoder[FollowedMix] = deriveDecoder[FollowedMix]

    def apply(jsonString: String): FollowedMix =
      parser.decode[FollowedMix](jsonString) match {
        case Left(e)      => throw new Exception(s"Error while parsing FollowedMix from Json: $e")
        case Right(asset) => asset
      }
  }

  case class FollowedHop(round: Int, hopMixBoxId: String) {
    override def toString: String = this.asJson.toString
  }

  object CreateFollowedHop {
    def apply(a: Array[Any]): FollowedHop = {
      val i = a.toIterator
      FollowedHop(
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[String]
      )
    }

    implicit val followedHopDecoder: Decoder[FollowedHop] = deriveDecoder[FollowedHop]

    def apply(jsonString: String): FollowedHop =
      parser.decode[FollowedHop](jsonString) match {
        case Left(e)      => throw new Exception(s"Error while parsing FollowedHop from Json: $e")
        case Right(asset) => asset
      }
  }

  case class FollowedWithdraw(txId: String, boxId: String) {
    override def toString: String = this.asJson.toString
  }

  object CreateFollowedWithdraw {
    def apply(a: Array[Any]): FollowedWithdraw = {
      val i = a.toIterator
      FollowedWithdraw(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String]
      )
    }

    implicit val followedWithdrawDecoder: Decoder[FollowedWithdraw] = deriveDecoder[FollowedWithdraw]

    def apply(jsonString: String): FollowedWithdraw =
      parser.decode[FollowedWithdraw](jsonString) match {
        case Left(e)      => throw new Exception(s"Error while parsing FollowedWithdraw from Json: $e")
        case Right(asset) => asset
      }
  }

  case class PendingRescan(
    mixId: String,
    time: Long,
    round: Int,
    goBackward: Boolean,
    boxType: String,
    mixBoxId: String
  ) {
    override def toString: String = this.asJson.toString
  }

  object CreatePendingRescan {
    def apply(a: Array[Any]): PendingRescan = {
      val i = a.toIterator
      PendingRescan(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String]
      )
    }
  }

}
