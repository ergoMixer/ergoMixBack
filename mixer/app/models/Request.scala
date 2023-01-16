package models

import config.MainConfigs
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import io.circe.{Decoder, parser}
import models.Box.MixingBox
import models.Models.CovertAsset
import models.Status.MixStatus

object Request {

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
      MixRequest(
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
      MixingRequest(
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
      (needed._1 + MainConfigs.distributeFee, needed._2)
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
         |  "nameCovert": "$nameCovert",
         |  "id": "$id",
         |  "createdDate": $createdTime,
         |  "deposit": "$depositAddress",
         |  "numRounds": $numRounds,
         |  "assets": [${assetJsons.mkString(",")}],
         |  "isManualCovert": $isManualCovert
         |}""".stripMargin
    }
  }

  object CreateMixCovertRequest {
    def apply(a: Array[Any]): MixCovertRequest = {
      val iterator = a.toIterator
      MixCovertRequest(
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

  //ixGroupIdCol, amountCol, mixStatusCol, createdTimeCol, depositAddressCol, depositCompletedCol
  case class MixGroupRequest(id: String, neededAmount: Long, status: String, createdTime: Long, depositAddress: String, doneDeposit: Long, tokenDoneDeposit: Long, mixingAmount: Long, mixingTokenAmount: Long, neededTokenAmount: Long, tokenId: String, masterKey: BigInt) {


    def toJson(statJson: String = null): String = {
      s"""
         |{
         |  "id": "$id",
         |  "amount": $neededAmount,
         |  "tokenAmount": $neededTokenAmount,
         |  "createdDate": $createdTime,
         |  "deposit": "$depositAddress",
         |  "status": "$status",
         |  "mixingAmount": $mixingAmount,
         |  "mixingTokenId": "$tokenId",
         |  "mixingTokenAmount": $mixingTokenAmount,
         |  "doneDeposit": $doneDeposit,
         |  "doneTokenDeposit": $tokenDoneDeposit,
         |  "groupStat": $statJson
         |}
         |""".stripMargin
    }
  }

  object CreateMixGroupRequest {
    def apply(a: Array[Any]): MixGroupRequest = {
      val iterator = a.toIterator
      MixGroupRequest(
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

}
