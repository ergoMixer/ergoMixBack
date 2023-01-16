package controllers

import config.MainConfigs
import helpers.ErgoMixerUtils
import io.circe.Json
import mixer.ErgoMixer
import models.Box.MixBoxList
import network.NetworkUtils
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext

/**
 * A controller inside of Mixer controller with mix and group mix APIs.
 */
class MixController @Inject()(controllerComponents: ControllerComponents, ergoMixerUtils: ErgoMixerUtils,
                              ergoMixer: ErgoMixer, networkUtils: NetworkUtils
                             )(implicit ec: ExecutionContext) extends AbstractController(controllerComponents) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * A POST Api for Add withdraw address to database or change status withdraw
   * in route `/mix/withdraw/`
   * Input: {
   * "nonStayAtMix" : Bool
   * "withdrawAddress": String
   * "mixId": String
   * }
   * Output: {
   * "success": true or false,
   * "message": ""
   * }
   */
  def withdraw: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val withdrawNow = js.hcursor.downField("nonStayAtMix").as[Boolean].getOrElse(false)
    val withdrawAddress = js.hcursor.downField("withdrawAddress").as[String].getOrElse("")
    val mixId = js.hcursor.downField("mixId").as[String].getOrElse("")

    try {
      if (withdrawAddress.nonEmpty) ergoMixer.updateMixWithdrawAddress(mixId, withdrawAddress)
      if (withdrawNow) ergoMixer.withdrawMixNow(mixId)
      Ok(s"""{"success": true}""".stripMargin).as("application/json")

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A post controller to create a mix request with/without tokens.
   */
  def mixRequest: Action[JsValue] = Action(parse.json) { request =>
    request.body.validate[MixBoxList] match {
      case JsSuccess(value, _) =>
        try {
          val id = ergoMixer.newMixGroupRequest(value.items)
          Ok(
            s"""{
               |  "success": true,
               |  "mixId": "$id"
               |}""".stripMargin).as("application/json")
        } catch {
          case e: Throwable =>
            logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
            BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
        }
      case _ => BadRequest("{\"status\": \"error\"}").as("application/json")
    }
  }

  /**
   * A get endpoint which returns list of group mixes
   */
  def mixGroupRequestList: Action[AnyContent] = Action {
    try {
      val res = "[" + ergoMixer.getMixRequestGroups.map(_.toJson()).mkString(", ") + "]"
      Ok(res).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint which returns active group mixes. contains more info to be shown about deposits and ...
   */
  def mixGroupRequestActiveList: Action[AnyContent] = Action {
    try {
      val mixes = ergoMixer.getMixRequestGroupsActive
      val res = "[" + mixes.map(mix => {
        val doneMixes = ergoMixer.getFinishedForGroup(mix.id)
        val progress = ergoMixer.getProgressForGroup(mix.id)
        val stat =
          s"""{
             |    "numBoxes": ${doneMixes._1},
             |    "numComplete": ${doneMixes._2},
             |    "numWithdrawn": ${doneMixes._3},
             |    "totalMixRound": ${progress._1},
             |    "doneMixRound": ${progress._2}
             |  }""".stripMargin
        mix.toJson(stat)
      }).mkString(", ") + "]"
      Ok(res).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint which returns complete list of group mixes
   */
  def mixGroupRequestCompleteList: Action[AnyContent] = Action {
    try {
      val res = "[" + ergoMixer.getMixRequestGroupsComplete.reverse.map(_.toJson()).mkString(", ") + "]"
      Ok(res).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint which returns mix boxes of a specific group or covert request
   * @param id mix group ID (covert ID in case of covert mixes)
   * @param status mix withdraw status (all, active, withdrawn)
   */
  def mixRequestList(id: String, status: String): Action[AnyContent] = Action {
    try {
      val res = "[" + ergoMixer.getMixes(id, status).map(mix => {
        var withdrawTxId = ""
        if (mix.withdraw.isDefined) {
          withdrawTxId = mix.withdraw.get.txId
        }
        val lastMixTime = {
          if (mix.fullMix.isDefined) mix.fullMix.get.createdTime
          else if (mix.halfMix.isDefined) mix.halfMix.get.createdTime
          else "None"
        }
        val lastHopRound = ergoMixer.getHopRound(mix.mixRequest.id)

        s"""
           |{
           |  "id": "${mix.mixRequest.id}",
           |  "createdDate": ${mix.mixRequest.createdTime},
           |  "amount": ${mix.mixRequest.amount},
           |  "rounds": ${mix.mixState.map(s => s.round).getOrElse(0)},
           |  "status": "${mix.mixRequest.mixStatus.value}",
           |  "deposit": "${mix.mixRequest.depositAddress}",
           |  "withdraw": "${mix.mixRequest.withdrawAddress}",
           |  "boxType": "${
          if (mix.fullMix.isDefined) "Full" else {
            if (mix.halfMix.isDefined) "Half" else "None"
          }
        }",
           |  "withdrawStatus": "${mix.mixRequest.withdrawStatus}",
           |  "withdrawTxId": "$withdrawTxId",
           |  "lastMixTime": "$lastMixTime",
           |  "mixingTokenId": "${mix.mixRequest.tokenId}",
           |  "mixingTokenAmount": ${mix.mixRequest.mixingTokenAmount},
           |  "hopRounds": $lastHopRound
           |}""".stripMargin
      }).mkString(",") + "]"
      Ok(res).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * a get endpoint for get InputBox of a mixId
   * @return InputBox of a mixId
   */
  def getMixBox(mixId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val boxId = ergoMixer.getFullBoxId(mixId)

      networkUtils.usingClient(ctx => {
        val ourInBoxes = ctx.getBoxesById(boxId)
        if (ourInBoxes.nonEmpty) {
          val ourInBox = ourInBoxes.head
          val burnTokens = ergoMixer.calcNeededBurnTokensForAgeUsdMint(ourInBox)
          val burnTokenObj = burnTokens.map(token => {
            s"""
               |{
               |  "tokenId": "${token._1}",
               |  "amount": ${token._2}
               |}
               |""".stripMargin
          })
          Ok(
            s"""
               | {
               |  "box": ${ourInBox.toJson(false)},
               |  "burnTokens": [${burnTokenObj.mkString(",")}]
               | }
               |""".stripMargin).as("application/json")
        } else throw new Exception(s"box with id $boxId for mixId $mixId not found")
      })

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint which returns info about current fee parameters
   */
  def mixingFee(): Action[AnyContent] = Action {
    try {
      var res =
        s"""
           |{
           |  "boxInTransaction": ${MainConfigs.maxOuts},
           |  "distributeFee": ${MainConfigs.distributeFee},
           |  "startFee": ${MainConfigs.startFee},""".stripMargin
      val tokenPrices = MainConfigs.tokenPrices.orNull
      if (tokenPrices == null) {
        BadRequest(
          s"""
             |{
             |  "success": false,
             |  "message": "token stats are not ready."
             |}
             |""".stripMargin
        ).as("application/json")
      } else {
        val rate = MainConfigs.entranceFee.getOrElse(1000000)
        tokenPrices.foreach {
          element => res += s"""  "${element._1}": ${element._2},""".stripMargin
        }
        res +=
          s"""  "rate": $rate
             |}
             |""".stripMargin
        Ok(res).as("application/json")
      }
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  def supported(): Action[AnyContent] = Action {
    try {
      val params = MainConfigs.params
      if (params.isEmpty) {
        BadRequest(
          s"""
             |{
             |  "success": false,
             |  "message": "params are not ready yet."
             |}
             |""".stripMargin
        ).as("application/json")
      } else {
        val supported = params.values.toList.sortBy(f => f.id)
        Ok(
          s"""
             |[${supported.map(_.toJson()).mkString(",")}]
             |""".stripMargin).as("application/json")
      }
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

}
