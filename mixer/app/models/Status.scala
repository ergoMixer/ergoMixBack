package models

import io.circe.{Decoder, Encoder, HCursor, Json}

object Status {

  sealed abstract class MixStatus(val value: String)

  implicit val encodeMixStatus: Encoder[MixStatus] = (a: MixStatus) => Json.fromString(a.value)

  object MixStatus {

    object Queued extends MixStatus("queued")

    object Running extends MixStatus("running")

    object Complete extends MixStatus("complete")

    private def all = Seq(Queued, Running, Complete)

    def fromString(s: String): MixStatus = all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))

    implicit val decodeMixStatus: Decoder[MixStatus] = (c: HCursor) =>
      for {
        status <- c.as[String]
      } yield MixStatus.fromString(status)
  }

  sealed abstract class GroupMixStatus(val value: String)

  object GroupMixStatus {

    object Queued extends GroupMixStatus("queued")

    object Starting extends GroupMixStatus("starting")

    object Running extends GroupMixStatus("running")

    object Complete extends GroupMixStatus("complete")

    private def all = Seq(Queued, Running, Complete)

    def fromString(s: String): GroupMixStatus =
      all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

  sealed abstract class MixWithdrawStatus(val value: String)

  object MixWithdrawStatus {

    object NoWithdrawYet extends MixWithdrawStatus("nothing")

    object WithdrawRequested extends MixWithdrawStatus("withdrawing")

    object AgeUSDRequested extends MixWithdrawStatus("minting")

    object HopRequested extends MixWithdrawStatus("hopping")

    object UnderHop extends MixWithdrawStatus("under hop")

    object Withdrawn extends MixWithdrawStatus("withdrawn")

    private def all = Seq(NoWithdrawYet, WithdrawRequested, Withdrawn)

    def fromString(s: String): MixWithdrawStatus =
      all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

  sealed abstract class CovertAssetWithdrawStatus(val value: String)

  object CovertAssetWithdrawStatus {

    object NoWithdrawYet extends CovertAssetWithdrawStatus("nothing")

    object Requested extends CovertAssetWithdrawStatus("requested")

    object Complete extends CovertAssetWithdrawStatus("complete")

    private def all = Seq(NoWithdrawYet, Requested, Complete)

    def fromString(s: String): CovertAssetWithdrawStatus =
      all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

}
