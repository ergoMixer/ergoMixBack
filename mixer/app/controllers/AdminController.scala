package controllers

import config._
import utils.CommonRequestMessages
import helpers.ErgoMixerUtils
import mixer.AdminStat
import models.Models.EntityInfo
import io.circe.Json
import play.api.Logger
import play.api.mvc._
import special.collection.Coll

import javax.inject._
import scala.concurrent.ExecutionContext

/**
 * A controller inside of Mixer controller with mix and group mix APIs.
 */
class AdminController @Inject()(
                                 controllerComponents: ControllerComponents,
                                 ergoMixerUtils: ErgoMixerUtils,
                                 adminStat: AdminStat
                             )(implicit ec: ExecutionContext) extends AbstractController(controllerComponents) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * calculates commission and token selling income from start to end inclusive
   *
   * @param start timestamp in mili-seconds
   * @param end   timestamp in mili-seconds
   * @return commission and token selling income in json
   */
  def income(start: Long, end: Long): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val stats = adminStat.getIncome(start, end)
    Ok(
      s"""{
         |  "commission": ${stats._1.noSpaces},
         |  "tokenSelling": ${stats._2.noSpaces}
         |}""".stripMargin
    ).as("application/json")
  }

  /**
   * A post endpoint to set income parameters by admin
   */
  def setIncomeParams(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val commissionFee = js.hcursor.downField("commissionFee").as[Int].getOrElse(throw new Exception("commissionFee param is needed"))
      val levels: Seq[(Int, Long)] = js.hcursor.downField("levels").as[Seq[Json]]
        .getOrElse(throw new Exception("levels param is needed")).map(level => {
        (level.hcursor.downField("amount").as[Int].getOrElse(throw new Exception("amount param is needed")),
          level.hcursor.downField("price").as[Long].getOrElse(throw new Exception("price param is needed")))
      })
      AdminConfigs.commissionFee = commissionFee
      AdminConfigs.tokenLevels = levels.sorted
      Ok(s"""{"success": true}""".stripMargin).as("application/json")

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint to get current income parameters
   */
  def getIncomeParams: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    if (AdminConfigs.tokenLevels.isEmpty) {
      BadRequest(CommonRequestMessages.BAD_REQUEST_IF_DATA_DOES_NOT_LOAD).as("application/json")
    } else {
      val numOk = AdminConfigs.tokens.count(token => {
        token.getRegisters.get(0).getValue.asInstanceOf[Coll[(Int, Long)]].toArray.toSeq.sorted == AdminConfigs.tokenLevels &&
          token.getRegisters.get(1).getValue.asInstanceOf[Int] == AdminConfigs.commissionFee
      })
      val levels = AdminConfigs.tokenLevels.map(level =>
        s"""{
           |  "amount": ${level._1},
           |  "price": ${level._2}
           |}""".stripMargin).mkString(",")
      Ok(
        s"""{
           |  "commissionFee": ${AdminConfigs.commissionFee},
           |  "levels": [$levels],
           |  "total": ${AdminConfigs.tokens.size},
           |  "numOk": $numOk
           |}""".stripMargin).as("application/json")
    }
  }

  /**
   * A post endpoint to set fee parameters by admin
   */
  def setFeeParams(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val maxFee = js.hcursor.downField("maxFee").as[Long].getOrElse(throw new Exception("maxFee param is needed"))
      val dynamicFee = js.hcursor.downField("dynamicFee").as[Long].getOrElse(throw new Exception("dynamicFee param is needed"))
      AdminConfigs.desiredFee = (maxFee, dynamicFee)
      Ok(s"""{"success": true}""".stripMargin).as("application/json")

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A get endpoint to get current fee parameters
   */
  def getFeeParams: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    if (AdminConfigs.desiredFee._1 == 0L && AdminConfigs.desiredFee._2 == 0L) {
      BadRequest(CommonRequestMessages.BAD_REQUEST_IF_DATA_DOES_NOT_LOAD).as("application/json")
    } else {
      val dynamicFee =
        if (AdminConfigs.supports.head.getRegisters.size() >= 5)
          AdminConfigs.supports.head.getRegisters.get(4).getValue.asInstanceOf[Long]
        else 1000L
      val dynamicOk = dynamicFee == AdminConfigs.desiredFee._2
      val maxFeeOkNum =
        AdminConfigs.fees.count(fee => {
          val desiredFeeBox = fee.getRegisters.get(0).getValue.asInstanceOf[Long]
          desiredFeeBox == AdminConfigs.desiredFee._1
        })
      Ok(
        s"""{
           |  "maxFee": ${AdminConfigs.desiredFee._1},
           |  "dynamicFee": ${AdminConfigs.desiredFee._2},
           |  "dynamicOk": $dynamicOk,
           |  "numOk": $maxFeeOkNum,
           |  "total": ${AdminConfigs.fees.length}
           |}""".stripMargin).as("application/json")
    }
  }

  /**
   * A get endpoint to get current supported assets
   */
  def getSupport: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    if (AdminConfigs.supports.isEmpty) {
      BadRequest(CommonRequestMessages.BAD_REQUEST_IF_DATA_DOES_NOT_LOAD).as("application/json")
    } else {
      val res = AdminConfigs.supports.map(sup => {
        val supEntity = EntityInfo(sup)
        val more =
          s"""
             |  "pendingRemoval": ${AdminConfigs.toRemoveSupport.exists(e => e.equals(EntityInfo(sup)))},
             |  "pendingToAdd": false,
             |""".stripMargin
        supEntity.toJson(more)
      }) ++ AdminConfigs.toAddSupport.map(sup => {
        val more =
          s"""
             |  "pendingRemoval": false,
             |  "pendingToAdd": true,
             |""".stripMargin
        sup._1.toJson(more)
      })
      Ok(s"""[${res.mkString(",")}]""".stripMargin).as("application/json")
    }
  }

  /**
   * A post endpoint to add support
   */
  def addSupport(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val name = js.hcursor.downField("name").as[String].getOrElse(throw new Exception("name param is needed"))
      val id = js.hcursor.downField("id").as[String].getOrElse(throw new Exception("id param is needed"))
      val decimals = js.hcursor.downField("decimals").as[Int].getOrElse(throw new Exception("decimals param is needed"))
      val rings = js.hcursor.downField("rings").as[Seq[Long]].getOrElse(throw new Exception("rings param is needed"))
      AdminConfigs.toAddSupport = AdminConfigs.toAddSupport :+ (new EntityInfo(name, id, rings, decimals, AdminConfigs.desiredFee._2), null)
      Ok(s"""{"success": true}""".stripMargin).as("application/json")

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A post endpoint to remove support
   */
  def removeSupport(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val name = js.hcursor.downField("name").as[String].getOrElse(throw new Exception("name param is needed"))
      val id = js.hcursor.downField("id").as[String].getOrElse(throw new Exception("id param is needed"))
      val decimals = js.hcursor.downField("decimals").as[Int].getOrElse(throw new Exception("decimals param is needed"))
      val rings = js.hcursor.downField("rings").as[Seq[Long]].getOrElse(throw new Exception("rings param is needed"))
      AdminConfigs.toRemoveSupport = AdminConfigs.toRemoveSupport :+ new EntityInfo(name, id, rings, decimals, AdminConfigs.desiredFee._2)
      Ok(s"""{"success": true}""".stripMargin).as("application/json")

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

}
