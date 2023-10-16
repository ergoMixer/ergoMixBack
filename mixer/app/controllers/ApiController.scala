package controllers

import java.nio.file.Paths
import javax.inject._

import scala.concurrent.ExecutionContext

import config.MainConfigs
import dao.DAOUtils
import helpers.{ErgoMixerUtils, TrayUtils}
import info.BuildInfo
import io.circe.syntax._
import io.circe.Json
import network.{BlockExplorer, NetworkUtils}
import org.apache.commons.lang3._
import play.api.libs.Files
import play.api.mvc._
import play.api.Logger

/**
 * A controller inside of Mixer controller.
 */
class ApiController @Inject() (
  assets: Assets,
  controllerComponents: ControllerComponents,
  ergoMixerUtils: ErgoMixerUtils,
  networkUtils: NetworkUtils,
  daoUtils: DAOUtils,
  explorer: BlockExplorer,
  trayUtils: TrayUtils
)(implicit ec: ExecutionContext)
  extends AbstractController(controllerComponents) {

  import networkUtils._

  private val logger: Logger = Logger(this.getClass)

  /**
   * A Get controller for redirect route /swagger
   *
   * @return route /swagger with query params {"url": "/swagger.conf"}
   */
  def redirectDocs: Action[AnyContent] = Action {
    Redirect(url = "/docs/index.html?url=/swagger.conf")
  }

  /**
   * A Get controller for return doc-api from openapi.scala.txt and return OpenApi for swagger
   *
   * @return openapi.yaml with string format
   */
  def swagger: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(
      views.txt.openapi(MainConfigs.isAdmin).toString()
    ).as("application/json")
  }

  /**
   * A Post controller for generate wallet address in the amount of 'countAddress' for node 'nodeAddress' using
   * api '/wallet/deriveNextKey' from ergo node.
   */
  def generateAddress: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val js                = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
    val apiKey            = js.hcursor.downField("apiKey").as[String].getOrElse(null)
    val nodeAddress       = js.hcursor.downField("nodeAddress").as[String].getOrElse(null)
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
      var addresses: Array[String] = Array()
      try {
        for (_ <- 1 to countAddress) {
          val res           = deriveNextAddress(nodeAddress, apiKey)
          val resJson: Json = io.circe.parser.parse(res).getOrElse(Json.Null)
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
          logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(ex)}")
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
         |${MainConfigs.ringStats.asJson}
         |""".stripMargin
    ).as("application/json")
  }

  /**
   * A post get endpoint to exit the app
   */
  def exit: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    trayUtils.showNotification(
      "Shutdown",
      "Please wait, may take a few seconds for ErgoMixer to peacefully shutdown..."
    )
    daoUtils.shutdown(true)
    Ok(
      s"""
         |{
         |  "success": true
         |}
         |""".stripMargin
    ).as("application/json")
  }

  /**
   * A GET endpoint to download the backup of database
   */
  def backup: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (SystemUtils.IS_OS_WINDOWS) daoUtils.shutdown(true)
      val res = ergoMixerUtils.backup()
      Ok.sendFile(new java.io.File(res))
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A post endpoint to upload a backup and restore it
   */
  def restore: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
    try {
      val (_, baseDbUrl) = daoUtils.getDbUrl
      request.body
        .file("myFile")
        .map { backup =>
          daoUtils.shutdown(true)
          backup.ref.copyTo(Paths.get(s"${baseDbUrl}ergoMixerRestore.zip"), replace = true)
          ergoMixerUtils.restore()
          Ok("Backup restored")
        }
        .getOrElse {
          BadRequest(s"""{"success": false, "message": "No uploaded backup found."}""").as("application/json")
        }

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
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
    val nodes = MainConfigs.nodes.map(url => s"""{
                                                |  "url": "$url",
                                                |  "canConnect": ${prunedClients.contains(url)}
                                                |}""".stripMargin)
    Ok(
      s"""
         |{
         |  "isWindows": ${SystemUtils.IS_OS_WINDOWS},
         |  "versionMixer": "${BuildInfo.version}",
         |  "ergoExplorer": "${MainConfigs.explorerUrl}",
         |  "ergoExplorerFront": "${MainConfigs.explorerFrontend}",
         |  "ergoNode": [${nodes.mkString(",")}]
         |}
         |""".stripMargin
    ).as("application/json")
  }

  def index: Action[AnyContent] = Action {
    Redirect("/dashboard")
  }

  def dashboard: Action[AnyContent] = assets.at("index.html")

  def assetOrDefault(resource: String): Action[AnyContent] =
    if (resource.contains(".")) assets.at(resource) else dashboard

  /**
   * fetch blocks from blockchain
   * @param offset
   * @param limit
   */
  def getBlocks(offset: Long, limit: Long): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val blocks = explorer.getBlocks(offset, limit)
      Ok(
        s"""{
           |  "blocks": $blocks
           |}""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

}
