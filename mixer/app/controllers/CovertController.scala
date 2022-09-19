package controllers

import akka.util.ByteString
import helpers.ErgoMixerUtils
import io.circe.Json
import mixer.{CovertMixer, ErgoMixer}
import play.api.Logger
import play.api.http.HttpEntity
import play.api.mvc._

import javax.inject._
import scala.concurrent.ExecutionContext

/**
 * A controller inside of Mixer controller with covert APIs.
 */
class CovertController @Inject()(controllerComponents: ControllerComponents, ergoMixerUtils: ErgoMixerUtils,
                                 ergoMixer: ErgoMixer, covertMixer: CovertMixer
                             )(implicit ec: ExecutionContext) extends AbstractController(controllerComponents) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * A post controller to create covert address.
   */
  def covertRequest: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val numRounds: Int = js.hcursor.downField("numRounds").as[Int].getOrElse(-1)
    val addresses: Seq[String] = js.hcursor.downField("addresses").as[Seq[String]].getOrElse(Nil).map(_.trim)
    val privateKey: String = js.hcursor.downField("privateKey").as[String].getOrElse("")
    val nameCovert: String = js.hcursor.downField("nameCovert").as[String].getOrElse("")
    if (numRounds == -1) {
      BadRequest(
        s"""
           |{
           |  "success": false,
           |  "message": "all required fields must be present."
           |}
           |""".stripMargin
      ).as("application/json")
    } else {
      try {
        val addr = ergoMixer.newCovertRequest(nameCovert, numRounds, addresses, privateKey)
        Ok(
          s"""{
             |  "success": true,
             |  "depositAddress": "$addr"
             |}""".stripMargin
        ).as("application/json")
      } catch {
        case e: Throwable =>
          logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
          BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
      }

    }
  }

  /**
   * A post endpoint to add or update a covert's assets
   * example input:
   * {
   * "tokenId": "",
   * "ring": 1000000000
   * }
   *
   * @param covertId covert id
   */
  def covertAddOrUpdate(covertId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val tokenId: String = js.hcursor.downField("tokenId").as[String].getOrElse(null)
    val ring: Long = js.hcursor.downField("ring").as[Long].getOrElse(-1)
    if (tokenId == null || ring == -1) {
      BadRequest(
        s"""
           |{
           |  "success": false,
           |  "message": "all required fields must be present."
           |}
           |""".stripMargin
      ).as("application/json")
    } else {
      try {
        ergoMixer.handleCovertSupport(covertId, tokenId, ring)
        Ok(
          s"""{
             |  "success": true
             |}""".stripMargin
        ).as("application/json")
      } catch {
        case e: Throwable =>
          logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
          BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
      }
    }
  }

  /**
   * A post endpoint to change a covert's name
   * example input:
   * {
   * "name": ""
   * }
   *
   * @param covertId covert id
   */
  def covertChangeName(covertId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val name: String = js.hcursor.downField("nameCovert").as[String].getOrElse("")
    try {
      ergoMixer.handleNameCovert(covertId, name)
      Ok(
        s"""{
           |  "success": true
           |}""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint which returns list covet info to be shown, includes covert's supported assets sorted based on latest activity
   */
  def covertList: Action[AnyContent] = Action {
    try {
      val coverts = ergoMixer.getCovertList.map(covert => {
        val curMixing = ergoMixer.getCovertCurrentMixing(covert.id)
        val curRunning = ergoMixer.getCovertRunningMixing(covert.id)
        covert.toJson(ergoMixer.getCovertAssets(covert.id), curMixing, curRunning)
      }).mkString(",")
      Ok(
        s"""
           |[$coverts]
           |""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint which returns list of a covert's assets
   */
  def covertAssetList(covertId: String): Action[AnyContent] = Action {
    try {
      val assets = ergoMixer.getCovertAssets(covertId)
      val curMixing = ergoMixer.getCovertCurrentMixing(covertId)
      val curRunning = ergoMixer.getCovertRunningMixing(covertId)
      Ok(
        s"""
           |${ergoMixer.getCovertById(covertId).toJson(assets, currentMixing = curMixing, runningMixing = curRunning)}
           |""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"status": "error", "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * adds a list of addresses to withdraw addresses of a covert request
   *
   * @param covertId covert id
   * @return whether the processs was successful or not
   */
  def setCovertAddresses(covertId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val addresses: Seq[String] = js.hcursor.downField("addresses").as[Seq[String]].getOrElse(Nil).map(_.trim)
    try {
      ergoMixer.addCovertWithdrawAddress(covertId, addresses)
      Ok(
        s"""{
           |  "success": true
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * adds a list of addresses to withdraw addresses of a covert request
   *
   * @param covertId covert id
   * @return whether the processs was successful or not
   */
  def getCovertAddresses(covertId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val addresses = ergoMixer.getCovertAddresses(covertId).map(add => s""""$add"""")
      Ok(s"[${addresses.mkString(",")}]".stripMargin).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A GET endpoint to download the covert private keys from database
   */
  def covertKeys: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val keys = ergoMixer.covertKeys
      Result(
        header = ResponseHeader(OK, Map(CONTENT_DISPOSITION → "attachment; filename=covertKeys.csv")),
        body = HttpEntity.Strict(ByteString(keys.mkString("\n")), Some("text/csv"))
      )
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * returns the private key and address of the corresponding covert
   *
   * @param covertId covert id
   * @return whether the processs was successful or not
   */
  def getCovertPrivateKey(covertId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val covertInfo = ergoMixer.covertInfoById(covertId)
      Ok(
        s"""{
           |  "address": "${covertInfo._1}",
           |  "privateKey": "${covertInfo._2.toString(16)}"
           |}""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A post endpoint to withdraw a covert's assets
   * example input:
   * {
   * "tokenIds": [  // Seq[String]
   *     "",
   *     ""
   *  ],
   *  "withdrawAddress": "" // String
   * }
   *
   * @param covertId covert id
   */
  def withdrawCovertAsset(covertId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val tokenIds: Seq[String] = js.hcursor.downField("tokenIds").as[Seq[String]].getOrElse(throw new Exception(s"tokenIds is required"))
      val withdrawAddress: String = js.hcursor.downField("withdrawAddress").as[String].getOrElse(throw new Exception(s"withdrawAddress is required"))
      covertMixer.queueWithdrawAsset(covertId, tokenIds, withdrawAddress)
      Ok(
        s"""{
           |  "success": true
           |}""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

}
