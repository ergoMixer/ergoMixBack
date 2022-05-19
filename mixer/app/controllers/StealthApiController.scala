package controllers


import dao.DAOUtils
import helpers.ErrorHandler._
import io.circe.generic.auto._
import io.circe.parser.{parse => circeParse}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, parser}
import models.StealthModels.{StealthAddressModel, StealthSpendAddress}
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.ErgoBox
import org.ergoplatform.wallet.serialization.JsonCodecsWrapper
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import stealth.StealthContract

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * A controller inside of Mixer controller.
 */
class StealthApiController @Inject()(controllerComponents: ControllerComponents, daoUtils: DAOUtils, stealthContract: StealthContract,
                                     networkUtils: NetworkUtils, explorer: BlockExplorer,
                             )(implicit ec: ExecutionContext) extends AbstractController(controllerComponents) {

  import networkUtils._

  private val logger: Logger = Logger(this.getClass)

  /**
   * A post endpoint to create stealth from stealthName or secret,
   * (secret is optional and backend code not implemented complete (recovery mode) )
   * with given secret the code works in recovery mode and finds all boxes related to this stealthAddress.
   *
   * without secret the stealthAddress created by random 
   *
   * input template:
   * {
   * "stealthName":   // String,
   * "secret":  // String
   * }
   *
   * @return
   * {
   * "pk":   // String,
   * "stealthName":  // String,
   * "stealthId": // String
   * }
   *
   */
  def createStealthAddress: Action[JsValue] = Action(parse.json) { implicit request =>
    try {
      var result: Json = Json.Null
      circeParse(request.body.toString).toTry match {
        case Success(stealthJs) =>
          val stealthName = stealthJs.hcursor.downField("stealthName").as[String].getOrElse(throw new Exception("stealthName is required"))
          val secret = stealthJs.hcursor.downField("secret").as[String].getOrElse("00")
          val (pk, name, stealthId) = stealthContract.createStealthAddressByStealthName(stealthName, BigInt(secret, 16))
          result = Json.fromFields(List(
            ("pk", Json.fromString(pk)),
            ("stealthName", Json.fromString(name)),
            ("stealthId", Json.fromString(stealthId))
          ))
        case Failure(e) => throw new Exception(e)
      }
      Ok(result.asJson.toString()).as("application/json")
    }
    catch {
      case m: NotFoundException => notFoundResponse(m.getMessage)
      case e: Exception => errorResponse(e)
    }
  }

  /**
   * A get endpoint to get all stealthAddresses(PKs) with values
   *
   *  @return
   *  "stealthAddresses": [
   * {
   * "stealthId":   // String,
   * "stealthName":  // String,
   * "pk": // String
   * "value" // Long
   * }
   * ]
   */
  def getStealthAddresses: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      var result: Json = Json.Null
      val addresses = stealthContract.getStealthAddresses
      implicit val StealthAddressEncoder: Encoder[StealthAddressModel] = Encoder.instance({ stealthAddress: StealthAddressModel =>
        Map(
          "stealthId" -> stealthAddress.stealthId.asJson,
          "stealthName" -> stealthAddress.stealthName.asJson,
          "pk" -> stealthAddress.address.asJson,
          "value" -> stealthAddress.value.asJson,
        ).asJson
      })
      result = Json.obj("stealthAddresses" -> addresses.asJson)
      Ok(result.toString()).as("application/json")
    }
    catch {
      case m: NotFoundException => notFoundResponse(m.getMessage)
      case e: Exception => errorResponse(e)
    }
  }

  /**
   * A get endpoint to get stealthAddress(PKs) for given stealthId with values
   *
   *  @return
   * {
   * "stealthId":   // String,
   * "stealthName":  // String,
   * "pk": // String
   * "value" // Long
   * }
   */
  def getStealthAddress(stealthId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      var result: Json = Json.Null
      val stealthAddress = stealthContract.getStealthAddress(stealthId)
      result = Json.fromFields(List(
        "stealthId" -> stealthAddress.stealthId.asJson,
        "stealthName" -> stealthAddress.stealthName.asJson,
        "pk" -> stealthAddress.address.asJson,
        "value" -> stealthAddress.value.asJson,
      ))
      Ok(result.asJson.toString()).as("application/json")
    }
    catch {
      case m: NotFoundException => notFoundResponse(m.getMessage)
      case e: Exception => errorResponse(e)
    }
  }

  /**
   * A get endpoint to get unspent ErgoBoxes related to the stealthId
   *
   * @param stealthId stealth id
   * @return list of unspent ErgoBoxes related to the stealthId
   */
  def getUnspentBoxes(stealthId: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val unspentBoxes = stealthContract.getUnspentBoxes(stealthId)
      implicit val boxDecoder: Encoder[ErgoBox] = JsonCodecsWrapper.ergoBoxEncoder
      Ok(unspentBoxes.asJson.toString()).as("application/json")
    }
    catch {
      case m: NotFoundException => notFoundResponse(m.getMessage)
      case e: Exception => errorResponse(e)
    }
  }

  /**
   * A post endpoint to set spend address for unspentBoxes related to the stealthId
   *
   * @param stealthId stealth id
   * @return whether the processes was successful or not
   */
  def setSpendAddress(stealthId: String): Action[JsValue] = Action(parse.json) { implicit request =>
    try {
      var result: Json = Json.Null
      implicit val stealthWithdrawAddressDecoder = Decoder[List[StealthSpendAddress]].prepare(
        _.downField("data")
      )
      parser.decode(request.body.toString)(stealthWithdrawAddressDecoder).toTry match {
        case Success(data) =>
          stealthContract.setSpendAddress(stealthId, data)
          result = Json.obj("status" -> Json.fromString("Done!"))
        case Failure(e) => throw new Exception(e)
      }
      Ok(result.toString()).as("application/json")
    }
    catch {
      case m: NotFoundException => notFoundResponse(m.getMessage)
      case e: Exception => errorResponse(e)
    }
  }

  /**
   * A get endpoint to get generate stealth payment address related to stealth address (pk)
   *
   * @param pk public key ("stealth"+pk in base58 format)
   *  @return
   * {
   * "address":   // String,
   * }
   * */
  def generateStealthAddress(pk: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      var result: Json = Json.Null
      result = Json.fromFields(List(("address", Json.fromString(stealthContract.createStealthAddressByStealthPK(pk)))))

      Ok(result.toString()).as("application/json")
    }
    catch {
      case m: NotFoundException => notFoundResponse(m.getMessage)
      case e: Exception => errorResponse(e)
    }
  }
}

