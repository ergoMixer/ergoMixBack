package controllers

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import akka.util.ByteString
import controllers.utils.CommonRequestResponse
import dao.stealth.StealthDAO
import dao.DAOUtils
import helpers.{ErgoMixerUtils, StealthUtils}
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import models.StealthTrait
import play.api.http.HttpEntity
import play.api.mvc._
import play.api.Logger
import stealth.StealthContract

/**
 * A controller inside of Mixer controller with stealth APIs.
 */
class StealthController @Inject() (
  controllerComponents: ControllerComponents,
  ergoMixerUtils: ErgoMixerUtils,
  stealthContract: StealthContract,
  daoUtils: DAOUtils,
  stealthDAO: StealthDAO
)(implicit ec: ExecutionContext)
  extends AbstractController(controllerComponents)
  with StealthTrait
  with CommonRequestResponse {

  private val logger: Logger = Logger(this.getClass)

  /**
   * A post endpoint to create stealth address from stealthName or secret,
   * secret is optional (without secret the stealthAddress created by random)
   *
   * input template:
   * {
   * "name":   // String,
   * "secret":  // String
   * }
   *
   * @return
   * {
   * "pk":   // String,
   * "stealthName":  // String,
   * "stealthId": // String
   * }
   */
  def createStealthAddress: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js          = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val stealthName = js.hcursor.downField("name").as[String].getOrElse(throw BadRequestException("name"))
      val secret      = js.hcursor.downField("secret").as[String].getOrElse("")
      val stealth     = stealthContract.createStealthAddress(stealthName, secret)
      Future {
        stealthContract.updateUnspentOutsIfSpendableByStealth(stealth)
      }
      Ok(stealth.toStealthInfo.asJson.toString()).as("application/json")
    } catch {
      case e: BadRequestException =>
        BadRequest(errorResult(e.getMessage).toString()).as("application/json")
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A get endpoint to show list of stealth
   *
   * @return
   * [{
   * "pk":   // String,
   * "stealthName":  // String,
   * "stealthId": // String,
   * "value": //Long,
   * "assetsSize": // Int
   * }]
   */
  def stealthList: Action[AnyContent] = Action {
    try {
      val stealthObjs = stealthContract.getAllStealthWithUnspentAssets
      Ok(stealthObjs.asJson.toString()).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A get endpoint to fetch stealth Info
   *
   * @return
   * {
   * "currentNetworkHeight":   // Long,
   * "lastProcessedBlock":  // Long
   * }
   */
  def stealthInfo: Action[AnyContent] = Action {
    try {
      val stealthInfo = stealthContract.getStealthInfo
      val response = Json.obj(
        "lastProcessedBlock"   -> Json.fromLong(stealthInfo._1),
        "currentNetworkHeight" -> Json.fromLong(stealthInfo._2),
      )
      Ok(response.toString()).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A get endpoint to show list of stealth's boxes
   *
   * @param stealthId stealth id
   * @param status all, unspent
   * @return
   * [{
   * "boxId" : // String,
   * "value" : // Long,
   * "ergoTree" : // String,
   * "assets" : [],
   * "creationHeight" : Int,
   * "additionalRegisters" : {},
   * "transactionId" : // String,
   * "index" : // Int,
   * "withdrawAddress" : // String,
   * "withdrawTxId" : // String,
   * }]
   */
  def getStealthBoxes(stealthId: String, status: String): Action[AnyContent] = Action {
    try {
      val outputs = stealthContract.getOutputs(stealthId, status)
      Ok(outputs.asJson.toString()).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A get endpoint to get generate payment address related to stealth address
   *
   * @param stealthAddress ("stealth"+pk in base58 format)
   * @return
   * {
   * "address":   // String,
   * }
   */
  def generatePaymentAddress(stealthAddress: String): Action[AnyContent] = Action {
    implicit request: Request[AnyContent] =>
      try {
        val result: Json = Json.obj(
          ("address", Json.fromString(stealthContract.generatePaymentAddressByStealthAddress(stealthAddress)))
        )
        Ok(result.toString()).as("application/json")
      } catch {
        case e: Throwable =>
          logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
          ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
      }
  }

  /**
   * A GET endpoint to download the stealth private keys from database
   *
   * @return a csv file include all stealth's key
   */
  def exportAllStealth: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val keys = StealthUtils.exportAllStealth(daoUtils.awaitResult(stealthDAO.all))
      Result(
        header = ResponseHeader(OK, Map(CONTENT_DISPOSITION → "attachment; filename=stealthKeys.csv")),
        body   = HttpEntity.Strict(ByteString(keys.mkString("\n")), Some("text/csv"))
      )
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A GET returns the stealth object of the corresponding stealthId
   *
   * @param stealthId stealth id
   * @return
   * {
   * "pk":   // String,
   * "stealthName":  // String,
   * "stealthId": // String,
   * "secret": // String (hex)
   * }
   */
  def getStealthPrivateKey(stealthId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val stealthInfo = stealthContract.stealthById(stealthId)
      Ok(stealthInfo.asJson.toString()).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A post endpoint to change a stealth's name
   * example input:
   * {
   * "name": ""
   * }
   * Output: {
   * "success": true or false,
   * "message": ""
   * }
   *
   * @param stealthId stealth id
   */
  def changeStealthName(stealthId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js           = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val name: String = js.hcursor.downField("name").as[String].getOrElse("")
      stealthContract.updateStealthName(stealthId, name)
      Ok(jsonResult(success = true).toString()).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }

  /**
   * A POST Api for Add withdraw address to database and withdraw stealth boxes
   * in route `/stealth/withdraw/`
   * Input: {
   * "withdrawAddress": String
   * "boxId": String
   * }
   * Output: {
   * "success": true or false,
   * "message": ""
   * }
   */
  def withdraw: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val withdrawAddress =
        js.hcursor.downField("withdrawAddress").as[String].getOrElse(throw BadRequestException("withdrawAddress"))
      val boxId = js.hcursor.downField("boxId").as[String].getOrElse(throw BadRequestException("boxId"))
      if (withdrawAddress.nonEmpty) stealthContract.setWithdrawAddress(boxId, withdrawAddress)
      Ok(jsonResult(success = true).toString()).as("application/json")
    } catch {
      case e: BadRequestException =>
        BadRequest(errorResult(e.getMessage).toString()).as("application/json")
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        ServiceUnavailable(errorResult(e.getMessage).toString()).as("application/json")
    }
  }
}
