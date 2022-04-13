package models

import java.nio.charset.StandardCharsets
import app.Configs
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json, parser}
import mixinterface.TokenErgoMix
import org.ergoplatform.appkit.{Address, ErgoToken, ErgoValue, InputBox, SignedTransaction}
import play.api.libs.json._
import sigmastate.Values.ErgoTree
import special.collection.Coll
import special.sigma.GroupElement
import wallet.WalletHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

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
        val fullMixBoxErgoTree = tokenErgoMix.fullMixScriptErgoTree.bytesHex
        val halfMixBoxErgoTree = tokenErgoMix.halfMixContract.getErgoTree.bytesHex
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

    def isAddressEqualTo(address: String): Boolean = {
      val addressErgoTree = Address.create(address).getErgoAddress.script.bytesHex
      addressErgoTree == ergoTree
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

    implicit val decodeMixStatus: Decoder[MixStatus] = (c: HCursor) => for {
      status <- c.as[String]
    } yield {
      MixStatus.fromString(status)
    }
  }

  implicit val decodeBigInt: Decoder[BigInt] = (c: HCursor) => for {
    bi <- c.as[String]
  } yield {
    BigInt(bi)
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

    object AgeUSDRequested extends MixWithdrawStatus("minting")

    object HopRequested extends MixWithdrawStatus("hopping")

    object UnderHop extends MixWithdrawStatus("under hop")

    object Withdrawn extends MixWithdrawStatus("withdrawn")

    private def all = Seq(NoWithdrawYet, WithdrawRequested, Withdrawn)

    def fromString(s: String): MixWithdrawStatus = all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
  }

  case class MixRequest(id: String, groupId: String, amount: Long, numRounds: Int, mixStatus: MixStatus, createdTime: Long, withdrawAddress: String, depositAddress: String, depositCompleted: Boolean, neededAmount: Long, numToken: Int, withdrawStatus: String, mixingTokenAmount: Long, neededTokenAmount: Long, tokenId: String) {


    def isErg: Boolean = tokenId.isEmpty

    def getAmount: Long = {
      if (isErg) amount
      else mixingTokenAmount
    }

    override def toString: String = this.asJson.toString
  }

  object CreateMixRequest {
    def apply(a: Array[Any]): MixRequest = {
      val i = a.toIterator
      new MixRequest(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Int],
        MixStatus.fromString(i.next().asInstanceOf[String]),
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String], // withdraw address
        i.next().asInstanceOf[String], // deposit address
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[Long], // needed
        i.next().asInstanceOf[Int], // token
        i.next().asInstanceOf[String], // withdraw status
        i.next().asInstanceOf[Long], // mixing token amount
        i.next().asInstanceOf[Long], // needed tokens
        i.next().asInstanceOf[String] // token id
      )
    }
  }

  // The only difference between this class and MixRequest is masterKey
  case class MixingRequest(
                              id: String,
                              groupId: String,
                              amount: Long,
                              numRounds: Int,
                              mixStatus: MixStatus,
                              createdTime: Long,
                              withdrawAddress: String,
                              depositAddress: String,
                              depositCompleted: Boolean,
                              neededAmount: Long,
                              numToken: Int,
                              withdrawStatus: String,
                              mixingTokenAmount: Long,
                              neededTokenAmount: Long,
                              tokenId: String,
                              masterKey: BigInt,
                          ) {

    def toMixRequest: MixRequest = MixRequest(
      id,
      groupId,
      amount,
      numRounds,
      mixStatus,
      createdTime,
      withdrawAddress,
      depositAddress,
      depositCompleted,
      neededAmount,
      numToken,
      withdrawStatus,
      mixingTokenAmount,
      neededTokenAmount,
      tokenId
    )
  }

  object CreateMixingRequest {
    def apply(a: Array[Any]): MixingRequest = {
      val i = a.toIterator
      new MixingRequest(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Int],
        MixStatus.fromString(i.next().asInstanceOf[String]),
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String], // withdraw address
        i.next().asInstanceOf[String], // deposit address
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[Long], // needed
        i.next().asInstanceOf[Int], // token
        i.next().asInstanceOf[String], // withdraw status
        i.next().asInstanceOf[Long], // mixing token amount
        i.next().asInstanceOf[Long], // needed tokens
        i.next().asInstanceOf[String], // token id
        i.next().asInstanceOf[BigInt] // master secret key
      )
    }

    implicit val mixingRequestDecoder: Decoder[MixingRequest] = deriveDecoder[MixingRequest]

    def apply(jsonString: String): MixingRequest = {
      parser.decode[MixingRequest](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixingRequest from Json: $e")
        case Right(req) => req
      }
    }
  }

  case class MixCovertRequest(nameCovert: String = "", id: String, createdTime: Long, depositAddress: String, numRounds: Int, isManualCovert: Boolean, masterKey: BigInt) {


    def getMinNeeded(ergRing: Long, tokenRing: Long): (Long, Long) = { // returns what is needed to distribute
      val needed = MixingBox.getPrice(ergRing, tokenRing, numRounds)
      (needed._1 + Configs.distributeFee, needed._2)
    }

    def getMixingNeed(ergRing: Long, tokenRing: Long): (Long, Long) = { // returns what is needed for a single mix box
      MixingBox.getPrice(ergRing, tokenRing, numRounds)
    }

    def toJson(assets: Seq[CovertAsset], currentMixing: Map[String, Long] = Map.empty, runningMixing: Map[String, Long] = Map.empty): String = {
      val sortedAssets = assets.sortBy(_.lastActivity).reverse.sortBy(!_.isErg).sortBy(_.ring == 0)
      val assetJsons = sortedAssets.map(asset => {
        val curMixingAmount = currentMixing.getOrElse(asset.tokenId, 0L)
        val runningMixingAmount = runningMixing.getOrElse(asset.tokenId, 0L)
        if (asset.isErg) asset.toJson(MixingBox.getPrice(asset.ring, 0, numRounds)._1, curMixingAmount, runningMixingAmount)
        else asset.toJson(MixingBox.getTokenPrice(asset.ring), curMixingAmount, runningMixingAmount)
      })
      s"""{
         |  "nameCovert": "${nameCovert}",
         |  "id": "${id}",
         |  "createdDate": ${createdTime},
         |  "deposit": "${depositAddress}",
         |  "numRounds": $numRounds,
         |  "assets": [${assetJsons.mkString(",")}],
         |  "isManualCovert": $isManualCovert
         |}""".stripMargin
    }
  }

  object CreateMixCovertRequest {
    def apply(a: Array[Any]): MixCovertRequest = {
      val iterator = a.toIterator
      new MixCovertRequest(
        iterator.next().asInstanceOf[String], // name Covert
        iterator.next().asInstanceOf[String], // id
        iterator.next().asInstanceOf[Long], // created time
        iterator.next().asInstanceOf[String], // deposit address
        iterator.next().asInstanceOf[Int], // num rounds
        iterator.next().asInstanceOf[Boolean], // isManualCovert
        iterator.next.asInstanceOf[BigDecimal].toBigInt() // master secret
      )
    }

    implicit val mixCovertDecoder: Decoder[MixCovertRequest] = deriveDecoder[MixCovertRequest]

    def apply(jsonString: String): MixCovertRequest = {
      parser.decode[MixCovertRequest](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixCovertRequest from Json: $e")
        case Right(req) => req
      }
    }
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
         |  "lastActivity": ${lastActivity},
         |  "currentMixingAmount": $currentMixing,
         |  "runningMixingAmount": $runningMixing
         |}""".stripMargin
    }

    def isErg: Boolean = tokenId.isEmpty


  }

  object CreateCovertAsset {
    def apply(a: Array[Any]): CovertAsset = {
      val i = a.toIterator
      new CovertAsset(
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

  //ixGroupIdCol, amountCol, mixStatusCol, createdTimeCol, depositAddressCol, depositCompletedCol
  case class MixGroupRequest(id: String, neededAmount: Long, status: String, createdTime: Long, depositAddress: String, doneDeposit: Long, tokenDoneDeposit: Long, mixingAmount: Long, mixingTokenAmount: Long, neededTokenAmount: Long, tokenId: String, masterKey: BigInt) {


    def toJson(statJson: String = null): String = {
      s"""
         |{
         |  "id": "${id}",
         |  "amount": ${neededAmount},
         |  "tokenAmount": ${neededTokenAmount},
         |  "createdDate": ${createdTime},
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

  object CreateMixGroupRequest {
    def apply(a: Array[Any]): MixGroupRequest = {
      val iterator = a.toIterator
      new MixGroupRequest(
        iterator.next().asInstanceOf[String], // id
        iterator.next().asInstanceOf[Long], // amount
        iterator.next().asInstanceOf[String], // mix status
        iterator.next().asInstanceOf[Long], // created time
        iterator.next().asInstanceOf[String], // deposit address
        iterator.next().asInstanceOf[Long], // done deposit
        iterator.next().asInstanceOf[Long], // token done deposit
        iterator.next().asInstanceOf[Long], // mixing amount
        iterator.next().asInstanceOf[Long], // mixing token amount
        iterator.next().asInstanceOf[Long], // needed tokens
        iterator.next().asInstanceOf[String], // token id
        iterator.next.asInstanceOf[BigDecimal].toBigInt() // master secret
      )
    }

    implicit val mixGroupRequestDecoder: Decoder[MixGroupRequest] = deriveDecoder[MixGroupRequest]

    def apply(jsonString: String): MixGroupRequest = {
      parser.decode[MixGroupRequest](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixGroupRequest from Json: $e")
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
      new MixState(
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
      new MixHistory(
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

  case class WithdrawTx(mixId: String, txId: String, time: Long, boxId: String, txBytes: Array[Byte], additionalInfo: String = "") {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)

    def getFeeBox: Option[String] = { // returns fee box used in this tx if available
      val inputs = boxId.split(",")
      if (inputs.size > 1) return Some(inputs(inputs.size - 1))
      Option.empty
    }

    def getJson: Json = io.circe.parser.parse(toString).getOrElse(Json.Null)

    def getDataInputs: Seq[String] = {
        getJson.hcursor.downField("dataInputs").as[Seq[Json]].getOrElse(Seq())
        .map(js => js.hcursor.downField("boxId").as[String].getOrElse(null))
    }

    def getOutputs: Seq[String] = {
      getJson.hcursor.downField("outputs").as[Seq[Json]].getOrElse(Seq())
        .map(js => js.hcursor.downField("boxId").as[String].getOrElse(null))
    }

    def getInputs: Seq[String] = {
      getJson.hcursor.downField("inputs").as[Seq[Json]].getOrElse(Seq())
        .map(js => js.hcursor.downField("boxId").as[String].getOrElse(null))
    }
  }

  implicit val decodeArrayByte: Decoder[Array[Byte]] = (c: HCursor) => for {
    s <- c.as[String]
  } yield {
    s.getBytes("utf-16")
  }

  object CreateWithdrawTx {
    def apply(a: Array[Any]): WithdrawTx = {
      val i = a.toIterator
      new WithdrawTx(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Array[Byte]],
        i.next().asInstanceOf[String],
      )
    }

    implicit val withdrawTxDecoder: Decoder[WithdrawTx] = deriveDecoder[WithdrawTx]

    def apply(jsonString: String): WithdrawTx = {
      parser.decode[WithdrawTx](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing WithdrawTx from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  case class MixTransaction(boxId: String, txId: String, txBytes: Array[Byte]) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }

  object CreateMixTransaction {
    def apply(a: Array[Any]): MixTransaction = {
      val i = a.toIterator
      new MixTransaction(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Array[Byte]]
      )
    }
  }

  case class DistributeTx(mixGroupId: String, txId: String, order: Int, time: Long, txBytes: Array[Byte], inputs: String) {
    override def toString: String = new String(txBytes, StandardCharsets.UTF_16)
  }

  object CreateDistributeTx {
    def apply(a: Array[Any]): DistributeTx = {
      val i = a.toIterator
      new DistributeTx(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Array[Byte]],
        i.next().asInstanceOf[String],
      )
    }
  }

  case class Deposit(address: String, boxId: String, amount: Long, createdTime: Long, tokenAmount: Long) {
    override def toString: String = this.asJson.toString
  }

  object CreateDeposit {
    def apply(a: Array[Any]): Deposit = {
      val i = a.toIterator
      new Deposit(
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
      new SpentDeposit(
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
      new HalfMix(
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
      new FullMix(
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

  case class Withdraw(mixId: String, txId: String, createdTime: Long, fullMixBoxId: String, tx: Json) {
    override def toString: String = this.asJson.toString
  }

  object CreateWithdraw {
    def apply(a: Array[Any]): Withdraw = {
      val i = a.toIterator
      new Withdraw(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[String],
        parseTx(i.next().asInstanceOf[Array[Byte]])
      )
    }

    private def parseTx(txBytes: Array[Byte]) = {
      parser.parse(new String(txBytes, "utf-16")) match {
        case Right(txJson) => txJson
        case Left(_) => Json.Null
      }
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

  object CreateFollowedMix {
    def apply(a: Array[Any]): FollowedMix = {
      val i = a.toIterator
      FollowedMix(
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[String],
        try {
          Option(i.next().asInstanceOf[String])
        } catch {
          case _: Throwable => Option.empty[String]
        }
      )
    }

    implicit val followedMixDecoder: Decoder[FollowedMix] = deriveDecoder[FollowedMix]

    def apply(jsonString: String): FollowedMix = {
      parser.decode[FollowedMix](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing FollowedMix from Json: $e")
        case Right(asset) => asset
      }
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

    def apply(jsonString: String): FollowedHop = {
      parser.decode[FollowedHop](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing FollowedHop from Json: $e")
        case Right(asset) => asset
      }
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

    def apply(jsonString: String): FollowedWithdraw = {
      parser.decode[FollowedWithdraw](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing FollowedWithdraw from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  case class PendingRescan(mixId: String, time: Long, round: Int, goBackward: Boolean, boxType: String, mixBoxId: String) {
    override def toString: String = this.asJson.toString
  }

  object CreatePendingRescan {
    def apply(a: Array[Any]): PendingRescan = {
      val i = a.toIterator
      new PendingRescan(
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[Long],
        i.next().asInstanceOf[Int],
        i.next().asInstanceOf[Boolean],
        i.next().asInstanceOf[String],
        i.next().asInstanceOf[String]
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
      val id = new String(box.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray, StandardCharsets.UTF_8).toLowerCase
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

    implicit val mixingBoxDecoder: Decoder[MixingBox] = deriveDecoder[MixingBox]

    def apply(jsonString: String): MixingBox = {
      parser.decode[MixingBox](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixingBox from Json: $e")
        case Right(asset) => asset
      }
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

  case class CovertAssetWithdrawTx(covertId: String, tokenId: String, withdrawAddress: String, createdTime: Long, withdrawStatus: String, txId: String, tx: Array[Byte]) {
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

  sealed abstract class CovertAssetWithdrawStatus(val value: String)

  object CovertAssetWithdrawStatus {

    object NoWithdrawYet extends CovertAssetWithdrawStatus("nothing")

    object Requested extends CovertAssetWithdrawStatus("requested")

    object Complete extends CovertAssetWithdrawStatus("complete")

    private def all = Seq(NoWithdrawYet, Requested, Complete)

    def fromString(s: String): CovertAssetWithdrawStatus = all.find(_.value == s).getOrElse(throw new Exception(s"Invalid status $s"))
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
