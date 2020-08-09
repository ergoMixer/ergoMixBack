package mixer

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json, parser}
import db.core.DataStructures.anyToAny
import cli.ErgoMixCLIUtil
import app.{ErgoMix, TokenErgoMix}
import scorex.util.encode.Base16
import special.sigma.GroupElement

object Models {

  case class SpendTx(inboxes:Seq[InBox], outboxes:Seq[OutBox], id:String, address:String, timestamp:Long)

  case class InBox(id: String, address: String, createdTxId: String, value: Long) {
    def isHalfMixBox: Boolean = ErgoMixCLIUtil.usingClient { implicit ctx =>
      address == new TokenErgoMix(ctx).halfMixAddress.toString
    }
  }

  case class OutBox(id: String, amount: Long, registers: Map[String, String], ergoTree: String, spendingTxId: Option[String]) {
    def ge(regId: String): GroupElement = ErgoMix.hexToGroupElement(registers(regId).drop(2))

    def mixBox: Option[Either[HBox, FBox]] = try ErgoMixCLIUtil.usingClient { implicit ctx =>
      val ergoMix = new TokenErgoMix(ctx)
      val fullMixBoxErgoTree = Base16.encode(ergoMix.fullMixScriptErgoTree.bytes).trim
      val halfMixBoxErgoTree = Base16.encode(ergoMix.halfMixContract.getErgoTree.bytes).trim
      ergoTree match {
        case `fullMixBoxErgoTree` =>
          Some(Right(FBox(id, ge("R4"), ge("R5"), ge("R6"))))
        case `halfMixBoxErgoTree` =>
          Some(Left(HBox(id, ge("R4"))))
        case any =>
          None
      }
    } catch {
      case a: Throwable =>
        a.printStackTrace()
        None
    }

    def getFBox: Option[FBox] = mixBox.flatMap {
      case Right(fBox) => Some(fBox)
      case _ => None
    }

    def getHBox: Option[HBox] = mixBox.flatMap {
      case Left(hBox) => Some(hBox)
      case _ => None
    }

    override def toString: String = this.asJson.toString
  }

  sealed abstract class MixStatus(val value: String)

  implicit val encodeMixStatus: Encoder[MixStatus] = (a: MixStatus) => Json.fromString(a.value)

  object MixStatus {

    object Queued extends MixStatus("queued")

    object Running extends MixStatus("running")

    object Complete extends MixStatus("complete")

    private def all = Seq(Queued, Running, Complete)

    def fromString(s: String): MixStatus = all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

  sealed abstract class GroupMixStatus(val value: String)

  object GroupMixStatus {

    object Queued extends GroupMixStatus("queued")

    object Starting extends GroupMixStatus("starting")

    object Running extends GroupMixStatus("running")

    object Complete extends GroupMixStatus("complete")

    private def all = Seq(Queued, Running, Complete)

    def fromString(s: String): GroupMixStatus = all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

  sealed abstract class MixWithdrawStatus(val value: String)

  object MixWithdrawStatus {

    object NoWithdrawYet extends MixWithdrawStatus("nothing")

    object WithdrawRequested extends MixWithdrawStatus("withdrawing")

    object Withdrawn extends MixWithdrawStatus("withdrawn")

    private def all = Seq(NoWithdrawYet, WithdrawRequested, Withdrawn)

    def fromString(s: String): MixWithdrawStatus = all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

  case class MixRequest(id: String, groupId: String, amount: Long, numRounds: Int, mixStatus: MixStatus, createdTime: Long, withdrawAddress: String, depositAddress: String, depositCompleted: Boolean, neededAmount: Long, numToken: Int, withdrawStatus: String) {
    def creationTimePrettyPrinted: String = {
      import java.text.SimpleDateFormat

      val date = new Date(createdTime)
      val formatter = new SimpleDateFormat("HH:mm:ss")
      formatter.format(date)
    }

    override def toString: String = this.asJson.toString
  }

  object MixRequest {
    def apply(a: Array[Any]): MixRequest = {
      val i = a.toIterator
      new MixRequest(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Long],
        i.next().as[Int],
        MixStatus.fromString(i.next().as[String]),
        i.next().as[Long],
        i.next().as[String], // withdraw address
        i.next().as[String], // deposit address
        i.next().as[Boolean],
        i.next().as[Long], // needed
        i.next().as[Int], // token
        i.next().as[String] // withdraw status
      )
    }
  }

  //ixGroupIdCol, amountCol, mixStatusCol, createdTimeCol, depositAddressCol, depositCompletedCol
  case class MixGroupRequest(id: String, neededAmount: Long, status: String, createdTime: Long, depositAddress: String, doneDeposit: Long, mixingAmount: Long, masterKey: BigInt) {
    def creationTimePrettyPrinted: String = {
      new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date(createdTime))
    }

    def toJson(statJson: String = null): String = {
      s"""
         |{
         |  "id": "${id}",
         |  "amount": ${neededAmount},
         |  "createdDate": "${creationTimePrettyPrinted}",
         |  "deposit": "${depositAddress}",
         |  "status": "${status}",
         |  "mixingAmount": $mixingAmount,
         |  "doneDeposit": ${doneDeposit},
         |  "groupStat": $statJson
         |}
         |""".stripMargin
    }
  }

  object MixGroupRequest {
    def apply(a: Array[Any]): MixGroupRequest = {
      val iterator = a.toIterator
      new MixGroupRequest(
        iterator.next().as[String], // id
        iterator.next().as[Long], // amount
        iterator.next().as[String], // mix status
        iterator.next().as[Long], // created time
        iterator.next().as[String], // deposit address
        iterator.next().as[Long], // done deposit
        iterator.next().as[Long], // mixing amount
        iterator.next.as[BigDecimal].toBigInt() // master secret
      )
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

  object MixState {
    def apply(a: Array[Any]): MixState = {
      val i = a.toIterator
      new MixState(
        i.next().as[String],
        i.next().as[Int],
        i.next().as[Boolean]
      )
    }
  }

  case class MixHistory(id: String, round: Int, isAlice: Boolean, time: Long) {
    override def toString: String = this.asJson.toString
  }

  object MixHistory {
    def apply(a: Array[Any]): MixHistory = {
      val i = a.toIterator
      new MixHistory(
        i.next().as[String],
        i.next().as[Int],
        i.next().as[Boolean],
        i.next().as[Long]
      )
    }
  }

  case class WithdrawTx(mixId: String, txId: String, time: Long, boxId: String, txBytes: Array[Byte]) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }
  object WithdrawTx {
    def apply(a: Array[Any]): WithdrawTx = {
      val i = a.toIterator
      new WithdrawTx(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Long],
        i.next().as[String],
        i.next().as[Array[Byte]]
      )
    }
  }

  case class MixTransaction(boxId: String, txId: String, txBytes: Array[Byte]) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }
  object MixTransaction {
    def apply(a: Array[Any]): MixTransaction = {
      val i = a.toIterator
      new MixTransaction(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Array[Byte]]
      )
    }
  }

  case class DistributeTx(mixGroupId: String, txId: String, order: Int, time: Long, txBytes: Array[Byte]) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }
  object DistributeTx {
    def apply(a: Array[Any]): DistributeTx = {
      val i = a.toIterator
      new DistributeTx(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Int],
        i.next().as[Long],
        i.next().as[Array[Byte]]
      )
    }
  }

  case class Deposit(address: String, boxId: String, amount: Long, createdTime: Long) {
    override def toString: String = this.asJson.toString
  }

  object Deposit {
    def apply(a: Array[Any]): Deposit = {
      val i = a.toIterator
      new Deposit(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Long],
        i.next().as[Long]
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

  object HalfMix {
    def apply(a: Array[Any]): HalfMix = {
      val i = a.toIterator
      new HalfMix(
        i.next().as[String],
        i.next().as[Int],
        i.next().as[Long],
        i.next().as[String],
        i.next().as[Boolean]
      )
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

  object FullMix {
    def apply(a: Array[Any]): FullMix = {
      val i = a.toIterator
      new FullMix(
        i.next().as[String],
        i.next().as[Int],
        i.next().as[Long],
        i.next().as[String],
        i.next().as[String]
      )
    }
  }

  implicit val encodeWithdraw: Encoder[Withdraw] = (a: Withdraw) => {
    Json.obj(
      "txId" -> Json.fromString(a.txId),
      "createdTime" -> Json.fromLong(a.createdTime)
    )
  }

  case class Withdraw(mixId: String, txId: String, createdTime: Long, fullMixBoxId: String, tx: Json) {
    override def toString: String = this.asJson.toString
  }

  object Withdraw {
    def apply(a: Array[Any]): Withdraw = {
      val i = a.toIterator
      new Withdraw(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Long],
        i.next().as[String],
        parser.parse(new String(i.next().as[Array[Byte]], "utf-16")).right.get
      )
    }
  }

  case class Mix(mixRequest: MixRequest, mixState: Option[MixState], halfMix: Option[HalfMix], fullMix: Option[FullMix], withdraw: Option[Withdraw]) {
    override def toString: String = this.asJson.toString
  }

  // for scanning blockchain
  case class FBox(id: String, r4: GroupElement, r5: GroupElement, r6: GroupElement)

  case class HBox(id: String, r4: GroupElement)

  case class FollowedMix(round: Int, isAlice: Boolean, halfMixBoxId: String, fullMixBoxId: Option[String]) {
    override def toString: String = this.asJson.toString
  }

  case class PendingRescan(mixId: String, time: Long, round: Int, goBackward: Boolean, isHalfMixTx: Boolean, mixBoxId: String) {
    override def toString: String = this.asJson.toString
  }

  object PendingRescan {
    def apply(a: Array[Any]): PendingRescan = {
      val i = a.toIterator
      new PendingRescan(
        i.next().as[String],
        i.next().as[Long],
        i.next().as[Int],
        i.next().as[Boolean],
        i.next().as[Boolean],
        i.next().as[String]
      )
    }
  }

}
