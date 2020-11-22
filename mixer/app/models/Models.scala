package models

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date

import app.Configs
import db.core.DataStructures.anyToAny
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json, parser}
import mixinterface.TokenErgoMix
import org.ergoplatform.appkit.{ErgoToken, ErgoValue, InputBox, SignedTransaction}
import play.api.libs.json._
import scorex.util.encode.Base16
import sigmastate.Values.ErgoTree
import special.collection.Coll
import special.sigma.GroupElement
import wallet.WalletHelper

import scala.jdk.CollectionConverters._

object Models {

  case class SpendTx(inboxes: Seq[InBox], outboxes: Seq[OutBox], id: String, address: String, timestamp: Long)

  case class InBox(id: String, address: String, createdTxId: String, value: Long)

  case class OutBox(id: String, amount: Long, registers: Map[String, String], ergoTree: String, tokens: Seq[ErgoToken], creationHeight: Int, address: String, spendingTxId: Option[String]) {
    def ge(regId: String): GroupElement = WalletHelper.hexToGroupElement(registers(regId).drop(2))

    def getToken(tokenId: String): Long = {
      tokens.filter(_.getId.toString.equals(tokenId)).map(_.getValue.longValue()).sum
    }

    def mixBox(tokenErgoMix: TokenErgoMix): Option[Either[HBox, FBox]] = {
      try {
        val fullMixBoxErgoTree = Base16.encode(tokenErgoMix.fullMixScriptErgoTree.bytes).trim
        val halfMixBoxErgoTree = Base16.encode(tokenErgoMix.halfMixContract.getErgoTree.bytes).trim
        ergoTree match {
          case `fullMixBoxErgoTree` =>
            Some(Right(FBox(id, ge("R4"), ge("R5"), ge("R6"))))
          case `halfMixBoxErgoTree` =>
            Some(Left(HBox(id, ge("R4"))))
          case any =>
            None
        }
      } catch {
        case _: Throwable =>
          None
      }
    }

    def getFBox(tokenErgoMix: TokenErgoMix): Option[FBox] = mixBox(tokenErgoMix).flatMap {
      case Right(fBox) => Some(fBox)
      case _ => None
    }
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

  case class MixRequest(id: String, groupId: String, amount: Long, numRounds: Int, mixStatus: MixStatus, createdTime: Long, withdrawAddress: String, depositAddress: String, depositCompleted: Boolean, neededAmount: Long, numToken: Int, withdrawStatus: String, mixingTokenAmount: Long, neededTokenAmount: Long, tokenId: String) {
    def creationTimePrettyPrinted: String = {
      import java.text.SimpleDateFormat

      val date = new Date(createdTime)
      val formatter = new SimpleDateFormat("HH:mm:ss")
      formatter.format(date)
    }

    def isErg: Boolean = tokenId.isEmpty

    def getAmount: Long = {
      if (isErg) amount
      else mixingTokenAmount
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
        i.next().as[String], // withdraw status
        i.next().as[Long], // mixing token amount
        i.next().as[Long], // needed tokens
        i.next().as[String] // token id
      )
    }
  }

  case class MixCovertRequest(nameCovert: String = "", id: String, createdTime: Long, depositAddress: String, numRounds: Int, isManualCovert: Boolean, masterKey: BigInt) {
    def creationTimePrettyPrinted: String = {
      new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date(createdTime))
    }

    def getMinNeeded(ergRing: Long, tokenRing: Long): (Long, Long) = { // returns what is needed to distribute
      val needed = MixingBox.getPrice(ergRing, tokenRing, numRounds)
      (needed._1 + Configs.distributeFee, needed._2)
    }

    def getMixingNeed(ergRing: Long, tokenRing: Long): (Long, Long) = { // returns what is needed for a single mix box
      MixingBox.getPrice(ergRing, tokenRing, numRounds)
    }

    def toJson(assets: Seq[CovertAsset], currentMixing: Map[String, Long] = Map.empty): String = {
      val sortedAssets = assets.sortBy(_.lastActivity).reverse.sortBy(!_.isErg).sortBy(_.ring == 0)
      val assetJsons = sortedAssets.map(asset => {
        val curMixingAmount = currentMixing.getOrElse(asset.tokenId, 0L)
        if (asset.isErg) asset.toJson(MixingBox.getPrice(asset.ring, 0, numRounds)._1, curMixingAmount)
        else asset.toJson(MixingBox.getTokenPrice(asset.ring), curMixingAmount)
      })
      s"""{
         |  "nameCovert": "${nameCovert}",
         |  "id": "${id}",
         |  "createdDate": "${creationTimePrettyPrinted}",
         |  "deposit": "${depositAddress}",
         |  "numRounds": $numRounds,
         |  "assets": [${assetJsons.mkString(",")}],
         |  "isManualCovert": $isManualCovert
         |}""".stripMargin
    }
  }

  object MixCovertRequest {
    def apply(a: Array[Any]): MixCovertRequest = {
      val iterator = a.toIterator
      new MixCovertRequest(
        iterator.next().as[String], // name Covert
        iterator.next().as[String], // id
        iterator.next().as[Long], // created time
        iterator.next().as[String], // deposit address
        iterator.next().as[Int], // num rounds
        iterator.next().as[Boolean], // isManualCovert
        iterator.next.as[BigDecimal].toBigInt() // master secret
      )
    }
  }

  case class CovertAsset(covertId: String, tokenId: String, ring: Long, confirmedDeposit: Long, lastActivity: Long) {
    /**
     * @param needed        needed amount of this asset for the mix to start
     * @param currentMixing current mixing amount of this asset
     * @return json of the asset as string
     */
    def toJson(needed: Long, currentMixing: Long): String = {
      s"""{
         |  "tokenId": "$tokenId",
         |  "ring": $ring,
         |  "need": $needed,
         |  "confirmedDeposit": $confirmedDeposit,
         |  "lastActivity": "${prettyDate(lastActivity)}",
         |  "currentMixingAmount": $currentMixing
         |}""".stripMargin
    }

    def isErg: Boolean = tokenId.isEmpty

    def prettyDate(timestamp: Long): String = {
      new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date(timestamp))
    }
  }

  object CovertAsset {
    def apply(a: Array[Any]): CovertAsset = {
      val i = a.toIterator
      new CovertAsset(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Long],
        i.next().as[Long],
        i.next().as[Long]
      )
    }
  }

  //ixGroupIdCol, amountCol, mixStatusCol, createdTimeCol, depositAddressCol, depositCompletedCol
  case class MixGroupRequest(id: String, neededAmount: Long, status: String, createdTime: Long, depositAddress: String, doneDeposit: Long, tokenDoneDeposit: Long, mixingAmount: Long, mixingTokenAmount: Long, neededTokenAmount: Long, tokenId: String, masterKey: BigInt) {
    def creationTimePrettyPrinted: String = {
      new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date(createdTime))
    }

    def toJson(statJson: String = null): String = {
      s"""
         |{
         |  "id": "${id}",
         |  "amount": ${neededAmount},
         |  "tokenAmount": ${neededTokenAmount},
         |  "createdDate": "${creationTimePrettyPrinted}",
         |  "deposit": "${depositAddress}",
         |  "status": "${status}",
         |  "mixingAmount": $mixingAmount,
         |  "mixingTokenId": "$tokenId",
         |  "mixingTokenAmount": $mixingTokenAmount,
         |  "doneDeposit": ${doneDeposit},
         |  "doneTokenDeposit": ${tokenDoneDeposit},
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
        iterator.next().as[Long], // token done deposit
        iterator.next().as[Long], // mixing amount
        iterator.next().as[Long], // mixing token amount
        iterator.next().as[Long], // needed tokens
        iterator.next().as[String], // token id
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

    def getFeeBox: Option[String] = { // returns fee box used in this tx if available
      val inputs = boxId.split(",")
      if (inputs.size > 1) return Some(inputs(inputs.size - 1))
      Option.empty
    }

    def getFirstInput: String = {
      boxId.split(",").head
    }
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

  case class DistributeTx(mixGroupId: String, txId: String, order: Int, time: Long, txBytes: Array[Byte], inputs: String) {
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
        i.next().as[Array[Byte]],
        i.next().as[String],
      )
    }
  }

  case class Deposit(address: String, boxId: String, amount: Long, createdTime: Long, tokenAmount: Long) {
    override def toString: String = this.asJson.toString
  }

  object Deposit {
    def apply(a: Array[Any]): Deposit = {
      val i = a.toIterator
      new Deposit(
        i.next().as[String],
        i.next().as[String],
        i.next().as[Long],
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

  case class EntityInfo(name: String, id: String, rings: Seq[Long], decimals: Int, dynamicFeeRate: Long) {
    def toJson(): String = {
      s"""{
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
      val id = new String(box.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray, StandardCharsets.UTF_8)
      val rings = box.getRegisters.get(2).getValue.asInstanceOf[Coll[Long]]
      val decimals = if (box.getRegisters.size() >= 4) box.getRegisters.get(3).getValue.asInstanceOf[Int] else 0
      val dynamicFeeRate = if (box.getRegisters.size() >= 5) box.getRegisters.get(4).getValue.asInstanceOf[Long] else 1000L // 1000 for 1e6 nano erg / byte
      new EntityInfo(name, id, rings.toArray, decimals, dynamicFeeRate)
    }
  }

  object MixingBox {

    /**
     * calculates needed token for a given ring
     *
     * @return token needed to enter mixing, i.e. ring + tokenFee
     */
    def getTokenPrice(ring: Long): Long = {
      val rate: Int = Configs.entranceFee.getOrElse(1000000)
      ring + (if (rate > 0 && rate < 1000000) ring / rate else 0)
    }

    /**
     * calculates needed amount with current fees for a specific mix box
     *
     * @param ergRing   erg ring of mix
     * @param tokenRing token ring of mix
     * @param mixRounds number of mixing rounds i.e. token num
     * @return (erg needed, token needed)
     */
    def getPrice(ergRing: Long, tokenRing: Long, mixRounds: Int): (Long, Long) = {
      val rate: Int = Configs.entranceFee.getOrElse(1000000)
      val tokenPrice: Long = Configs.tokenPrices.get.getOrElse(mixRounds, -1)
      assert(tokenPrice != -1)
      val ergVal = if (rate > 0 && rate < 1000000) ergRing / rate else 0
      (ergRing + Configs.startFee + tokenPrice + ergVal, getTokenPrice(tokenRing))
    }
  }

  case class MixingBox(withdraw: String, amount: Long, token: Int, mixingTokenAmount: Long, mixingTokenId: String) {
    def price: (Long, Long) = {
      MixingBox.getPrice(amount, mixingTokenAmount, token)
    }
  }

  case class MixBoxList(items: Iterable[MixingBox])

  object MixBoxList {
    implicit val ReadsMixBoxList: Reads[MixBoxList] = new Reads[MixBoxList] {
      override def reads(json: JsValue): JsResult[MixBoxList] = {
        JsSuccess(MixBoxList(json.as[JsArray].value.map(item => {
          val withdraw = (item \ "withdraw").as[String]
          val amount = (item \ "amount").as[Long]
          val token = (item \ "token").as[Int]
          val mixingTokenId = (item \ "mixingTokenId").as[String]
          val mixingTokenAmount = (item \ "mixingTokenAmount").as[Long]
          MixingBox(withdraw, amount, token, mixingTokenAmount, mixingTokenId)
        })))
      }
    }
  }

  case class EndBox(receiverBoxScript: ErgoTree, receiverBoxRegs: Seq[ErgoValue[_]] = Nil, value: Long, tokens: Seq[ErgoToken] = Nil) // box spending full mix box

  case class HalfMixTx(tx: SignedTransaction)(implicit ergoMix: TokenErgoMix) {
    val getHalfMixBox: HalfMixBox = HalfMixBox(tx.getOutputsToSpend.get(0))
    require(getHalfMixBox.inputBox.getErgoTree == ergoMix.halfMixContract.getErgoTree)
  }

  case class FullMixTx(tx: SignedTransaction)(implicit ergoMix: TokenErgoMix) {
    val getFullMixBoxes: (FullMixBox, FullMixBox) = (FullMixBox(tx.getOutputsToSpend.get(0)), FullMixBox(tx.getOutputsToSpend.get(1)))
    require(getFullMixBoxes._1.inputBox.getErgoTree == ergoMix.fullMixScriptErgoTree)
    require(getFullMixBoxes._2.inputBox.getErgoTree == ergoMix.fullMixScriptErgoTree)
  }

  abstract class MixBox(inputBox: InputBox) {
    def getRegs = inputBox.getRegisters.asScala

    def getR4 = getRegs.head

    def getR5 = getRegs(1)

    def getR6 = getRegs(2)
  }

  case class HalfMixBox(inputBox: InputBox) extends MixBox(inputBox) {
    def id = inputBox.getId.toString

    val gX: GroupElement = getR4.getValue match {
      case g: GroupElement => g
      case any => throw new Exception(s"Invalid value in R4: $any of type ${any.getClass}")
    }
  }

  case class FullMixBox(inputBox: InputBox) extends MixBox(inputBox) {
    def id = inputBox.getId.toString

    val (r4, r5, r6) = (getR4.getValue, getR5.getValue, getR6.getValue) match {
      case (c1: GroupElement, c2: GroupElement, gX: GroupElement) => (c1, c2, gX) //Values.GroupElementConstant(c1), Values.GroupElementConstant(c2)) => (c1, c2)
      case (r4, r5, r6) => throw new Exception(s"Invalid registers R4:$r4, R5:$r5, R6:$r6")
    }
  }


}
