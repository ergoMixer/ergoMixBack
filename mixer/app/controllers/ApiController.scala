package controllers

import app.{Configs, Util => EUtil}
import info.BuildInfo
import cli.ErgoMixCLIUtil
import db.ScalaDB._
import io.circe.Json
import io.circe.syntax._
import javax.inject._
import mixer.Columns._
import mixer.Models.MixWithdrawStatus.WithdrawRequested
import mixer.{ErgoMixerUtils, MixBoxList, Stats}
import play.api.libs.json._
import play.api.mvc._
import scalaj.http.{Http, HttpResponse}
import services.ErgoMixingSystem

import scala.io.Source

/**
 * A controller inside of Mixer controller.
 */
class ApiController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  //  Define type for response of http request
  type Response = HttpResponse[Array[Byte]]
  private lazy val ergoMixer = ErgoMixingSystem.ergoMixer
  private val tables = ErgoMixingSystem.tables

  /**
   * A Get controller for redirect route /swagger
   *
   * @return route /swagger with query params {"url": "/swagger.conf"}
   */
  def redirectDocs = Action {
    Redirect(url = "/docs/index.html", queryStringParams = Map("url" -> Seq("/swagger.conf")))
  }

  /**
   * A Get controller for return doc-api from openapi.yaml and return OpenApi for swagger
   *
   * @return openapi.yaml with string format
   */
  def swagger: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(
      Source.fromResource("openapi.yaml").getLines.mkString("\n")
    ).as("application/json")
  }

  /**
   * A Post controller for generate wallet address in the amount of 'countAddress' for node 'nodeAddress' using
   * api '/wallet/deriveNextKey' from ergo node.
   */
  def generateAddress: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val apiKey = js.hcursor.downField("apiKey").as[String].getOrElse(null)
    val nodeAddress = js.hcursor.downField("nodeAddress").as[String].getOrElse(null)
    val countAddress: Int = js.hcursor.downField("countAddress").as[Int].getOrElse(0)
    // Validate input
    if (nodeAddress == null || countAddress == 0 || apiKey == null) {
      BadRequest(
        s"""
           |{
           |  "success": false,
           |  "message": "nodeAddress, countAddress, apiKey is required."
           |}
           |""".stripMargin
      ).as("application/json")
    } else {
      val authHeader: Seq[(String, String)] = Seq[(String, String)](("api_key", apiKey), ("Content-Type", "application/json"))
      var addresses: Array[String] = Array()
      try {
        for (i <- 1 to countAddress) {
          // Send request to nodeAddress for get new wallet address
          val responseNode: Response = Http(s"$nodeAddress/wallet/deriveNextKey").headers(authHeader).asBytes
          val resJson: Json = io.circe.parser.parse(responseNode.body.map(_.toChar).mkString).getOrElse(Json.Null)
          if (!(200 <= responseNode.code && responseNode.code <= 209)) {
            throw new Exception(resJson.hcursor.downField("detail").as[String].getOrElse(null))
          }
          addresses :+= resJson.hcursor.downField("address").as[String].getOrElse(null)
        }
        // Return a list of address in the amount of countAddress
        Ok(
          s"""
             |${addresses.asJson}
             |""".stripMargin
        ).as("application/json")
      } catch {
        case ex: Throwable =>
          ex.getStackTrace
          BadRequest(
            s"""
               |{
               |  "success": false,
               |  "message": "${ex.getMessage}"
               |}
               |""".stripMargin
          ).as("application/json")
      }
    }
  }

  /**
   * A Get controller for calculate number of unSpent halfBox and number of spent halfBox in `periodTime`.
   * Note : Number of spent halfBox is approximate.
   * outPut: {
   * 2000000000: {
   * "spentHalf": 0,
   * "unspentHalf": 3
   * }
   * }
   */
  def rings: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(
      s"""
         |${Stats.ringStats.asJson}
         |""".stripMargin
    ).as("application/json")
  }

  def exit: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    System.exit(0)
    Ok(
      s"""
         |{
         |  "success": true
         |}
         |""".stripMargin
    ).as("application/json")
  }

  /**
   * A Get controller for return information of Mixer
   *
   * @return {
   *         "versionMixer": ${info.version},
   *         "ergoExplorer": ${ErgoMixingSystem.explorerUrl},
   *         "ergoNode": ${ErgoMixingSystem.nodeUrl}
   *         }
   */
  def getInfo: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(
      s"""
         |{
         |  "versionMixer": "${BuildInfo.version}",
         |  "ergoExplorer": "${Configs.explorerUrl}",
         |  "ergoExplorerFront": "${Configs.explorerFrontend}",
         |  "ergoNode": "${Configs.nodeUrl}"
         |}
         |""".stripMargin
    ).as("application/json")
  }

  /**
   * A post controller to create covert address.
   */
  def covertRequest: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val numRounds: Int = js.hcursor.downField("numRounds").as[Int].getOrElse(-1)
    val ergRing: Long = js.hcursor.downField("ergRing").as[Long].getOrElse(-1)
    val tokenRing: Long = js.hcursor.downField("tokenRing").as[Long].getOrElse(-1)
    val tokenId: String = js.hcursor.downField("tokenId").as[String].getOrElse(null)
    if (numRounds == -1 || ergRing == -1 || tokenRing == -1 || tokenId == null) {
      BadRequest(
        s"""
           |{
           |  "success": false,
           |  "message": "all required fields must be present."
           |}
           |""".stripMargin
      ).as("application/json")
    } else {
      val id = ergoMixer.newCovertRequest(ergRing, tokenRing, tokenId, numRounds)
      Ok(
        s"""{
           |  "status": "success",
           |  "mixId": "$id"
           |}""".stripMargin
      ).as("application/json")
    }
  }

  /**
   * A post controller to store mix requests with tokens.
   */
  def mixRequest = Action(parse.json) { request =>
    request.body.validate[MixBoxList] match {
      case JsSuccess(value, _) => {
        val id = ergoMixer.newMixGroupRequest(value.items)
        Ok(
          s"""{
             |  "status": "success",
             |  "mixId": "$id"
             |}""".stripMargin).as("application/json")
      }
      case _ => BadRequest("{\"status\": \"error\"}")
    }
  }

  def mixGroupRequestList = Action {
    val res = "[" + (ergoMixer.getMixRequestGroups.map(_.toJson())).mkString(", ") + "]"
    Ok(res).as("application/json")
  }

  def mixGroupRequestActiveList = Action {
    val mixes = ergoMixer.getMixRequestGroupsActive
    val res = "[" + mixes.map(mix => {
      val doneMixes = ergoMixer.getFinishedForGroup(mix)
      val progress = ergoMixer.getProgressForGroup(mix)
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
  }

  def mixGroupRequestCompleteList = Action {
    val res = "[" + (ergoMixer.getMixRequestGroupsComplete.map(_.toJson())).mkString(", ") + "]"
    Ok(res).as("application/json")
  }

  def mixRequestList(groupId: String) = Action {
    val res = "[" + ergoMixer.getMixes(groupId).map(mix => {
      var withdrawTxId = ""
      if (mix.withdraw.isDefined) {
        withdrawTxId = mix.withdraw.get.txId
      }
      val lastMixTime = {
        if (mix.fullMix.isDefined) ErgoMixerUtils.prettyDate(mix.fullMix.get.createdTime)
        else if (mix.halfMix.isDefined) ErgoMixerUtils.prettyDate(mix.halfMix.get.createdTime)
        else "None"
      }

      s"""
         |{
         |  "id": "${mix.mixRequest.id}",
         |  "createdDate": "${mix.mixRequest.creationTimePrettyPrinted}",
         |  "amount": ${mix.mixRequest.amount},
         |  "rounds": ${mix.mixState.map(s => s.round).getOrElse(0)},
         |  "status": "${mix.mixRequest.mixStatus.value}",
         |  "deposit": "${mix.mixRequest.depositAddress}",
         |  "withdraw": "${mix.mixRequest.withdrawAddress}",
         |  "boxType": "${if (mix.fullMix.isDefined) "Full" else {if (mix.halfMix.isDefined) "Half" else "None"}}",
         |  "withdrawStatus": "${mix.mixRequest.withdrawStatus}",
         |  "withdrawTxId": "$withdrawTxId",
         |  "lastMixTime": "$lastMixTime",
         |  "mixingTokenId": "${mix.mixRequest.tokenId}",
         |  "mixingTokenAmount": ${mix.mixRequest.mixingTokenAmount}
         |}""".stripMargin
    }).mkString(",") + "]"
    Ok(res).as("application/json")
  }

  def supported() = Action {
    var params = Configs.params
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
      Ok(s"""
          |[${supported.map(_.toJson()).mkString(",")}]
          |""".stripMargin).as("application/json")
    }
  }

  def mixingFee() = Action {
    var res =
      s"""
         |{
         |  "boxInTransaction": ${Configs.maxOuts},
         |  "distributeFee": ${Configs.distributeFee},
         |  "startFee": ${Configs.startFee},""".stripMargin
    val tokenPrices = Stats.tokenPrices.orNull
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
      val rate = Stats.entranceFee.getOrElse(1000000)
      tokenPrices.foreach {
        element => res += s"""  "${element._1}": ${element._2},""".stripMargin
      }
      res +=
        s"""  "rate": $rate
           |}
           |""".stripMargin
      Ok(res).as("application/json")
    }
  }

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
    // Get Inputs
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val nonStayAtMix = js.hcursor.downField("nonStayAtMix").as[Boolean].getOrElse(null)
    val withdrawAddress = js.hcursor.downField("withdrawAddress").as[String].getOrElse(null)
    val mixId = js.hcursor.downField("mixId").as[String].getOrElse(null)

    /**
     * Validate input address
     *
     * @param address withdraw address
     * @return return a Boolean if address valid true else false
     */
    def checkAddress(address: String): Boolean = {
      ErgoMixCLIUtil.usingClient { implicit ctx =>
        val util = new EUtil()
        try {
          util.getAddress(withdrawAddress).script
          true
        }
        catch {
          case e: Throwable =>
            false
        }
      }
    }

    /**
     * Function for return response for this request
     *
     * @param status status of response
     * @param message message of response
     * @return response of request
     */
    def sendResponse(status: Boolean, message: String): Result = {
      if (status)
        Ok(
          s"""
             |{
             | "success": true,
             | "message": "$message"
             |}
             |""".stripMargin
        ).as("application/json")
      else
        BadRequest(
          s"""
             |{
             |  "success": false,
             |  "message": "$message"
             |}
             |""".stripMargin
        ).as("application/json")
    }

    try {
      // Validate input
      if (nonStayAtMix == null || mixId == null || withdrawAddress == null) {
        sendResponse(status = false, "specify all parameters!.")
      }
      else {
        if (withdrawAddress == "") {
          //  In-state that there is not withdrawAddress if nonStayAtMix was false must be entered withdrawAddress
          //  for an update or add withdraw address else if nonStayAtMix was true if mixId have withdrawAddress
          //  must be updated withdraw status.
          if (nonStayAtMix == true) {
            val withdrawAddress = tables.mixRequestsTable.select(withdrawAddressCol).where(mixIdCol === mixId).firstAsT[String].head
            if (checkAddress(withdrawAddress)) {
              tables.mixRequestsTable.update(mixWithdrawStatusCol <-- WithdrawRequested.value).where(mixIdCol === mixId)
              sendResponse(status = true, "will withdraw the requested mix.")
            } else {
              sendResponse(status = false, "No valid withdraw address for this mix! provide one to withdraw the mix.")
            }
          }
          else
            sendResponse(status = false, "provide a withdraw address.")
        }
        else {
          //  In-state that there is withdrawAddress if nonStayAtMix was false must be updated withdrawAddress
          //  else if nonStayAtMix was true must be updated withdraw status and withdrawAddress.
          if (checkAddress(withdrawAddress)) {
            if (nonStayAtMix == true) {
              tables.mixRequestsTable.update(mixWithdrawStatusCol <-- WithdrawRequested.value, withdrawAddressCol <-- withdrawAddress).where(mixIdCol === mixId)
              sendResponse(status = true, "will withdraw the requested mix with provided wihdraw address.")
            }
            else {
              tables.mixRequestsTable.update(withdrawAddressCol <-- withdrawAddress).where(mixIdCol === mixId)
              sendResponse(status = true, "withdraw address updated.")
            }
          }
          else sendResponse(status = false, "withdraw address is invalid.")
        }
      }
    } catch {
      //  catching the exception from the query of database
      case e: Throwable =>
        e.getStackTrace
        sendResponse(status = false, e.getMessage)
    }
  }
}

