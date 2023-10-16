package controllers

import javax.inject._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

import config.MainConfigs
import dao.mixing.WithdrawDAO
import dao.DAOUtils
import helpers.ErgoMixerUtils
import io.circe.Json
import mixer.ErgoMixer
import mixinterface.AliceOrBob
import models.Status.MixWithdrawStatus.AgeUSDRequested
import models.Transaction.WithdrawTx
import network.{BlockExplorer, NetworkUtils}
import play.api.mvc._
import play.api.Logger
import wallet.{Wallet, WalletHelper}

/**
 * A controller inside of Mixer controller with age usd APIs.
 */
class AgeUSDController @Inject() (
  controllerComponents: ControllerComponents,
  ergoMixerUtils: ErgoMixerUtils,
  ergoMixer: ErgoMixer,
  networkUtils: NetworkUtils,
  explorer: BlockExplorer,
  aliceOrBob: AliceOrBob,
  daoUtils: DAOUtils,
  withdrawDAO: WithdrawDAO
)(implicit ec: ExecutionContext)
  extends AbstractController(controllerComponents) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * returns current blockchain height
   */
  def currentHeight(): Action[AnyContent] = Action {
    try
      networkUtils.usingClient { ctx =>
        val res =
          s"""{
             |  "height": ${ctx.getHeight}
             |}""".stripMargin
        Ok(res).as("application/json")
      }
    catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * returns tx fee needed for ageUSD minting
   */
  def ageusdFee(): Action[AnyContent] = Action {
    try {
      val res =
        s"""{
           |  "fee": ${MainConfigs.ageusdFee}
           |}""".stripMargin
      Ok(res).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * A POST Api minting AgeUSD (sigusd, sigrsv)
   *
   * returns minting tx json
   */
  def mint: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val js = io.circe.parser.parse(request.body.asJson.get.toString()).getOrElse(Json.Null)
      val reducedTransaction = js.hcursor
        .downField("reducedTransaction")
        .as[String]
        .getOrElse(throw new Exception("reducedTransaction is required"))
      val mixId = js.hcursor.downField("mixId").as[String].getOrElse(throw new Exception("mixId is required"))

      val boxId   = ergoMixer.getFullBoxId(mixId)
      val isAlice = ergoMixer.getIsAlice(mixId)
      val wallet  = new Wallet(ergoMixer.getMasterSecret(mixId))
      val secret  = wallet.getSecret(ergoMixer.getRoundNum(mixId))

      val tx = aliceOrBob.mint(reducedTransaction, boxId, secret.bigInteger, isAlice, sendTx = true)
      // finding first bank box and tx in the chain
      val mintTxsChain      = daoUtils.awaitResult(withdrawDAO.getMintings)
      var inBank            = tx.getSignedInputs.get(0).getId.toString
      var firstTxId: String = tx.getId
      var endOfChain        = false
      while (!endOfChain) {
        val prevTxInChain = mintTxsChain.find(tx => tx.getOutputs.exists(_.equals(inBank)))
        if (prevTxInChain.isEmpty) {
          endOfChain = true
        } else {
          inBank    = prevTxInChain.get.getInputs.head
          firstTxId = prevTxInChain.get.txId
        }
      }
      val additionalInfo                = s"$inBank,$firstTxId"
      val inps                          = tx.getSignedInputs.asScala.map(inp => inp.getId.toString).mkString(",")
      val txBytes                       = tx.toJson(false).getBytes("utf-16")
      implicit val insertReason: String = "Minting AgeUSD"
      val new_withdraw                  = WithdrawTx(mixId, tx.getId, WalletHelper.now, inps, txBytes, additionalInfo)
      withdrawDAO.updateById(new_withdraw, AgeUSDRequested.value)

      Ok(tx.toJson(false)).as("application/json")

    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * returns current oracle box
   */
  def oracleBox(tokenId: String): Action[AnyContent] = Action {
    try {
      val oracle = explorer.getBoxByTokenId(tokenId)
      Ok((oracle \\ "items").head.asArray.get(0).noSpaces).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

  /**
   * returns current bank box - unconfirmed if available
   */
  def bankBox(tokenId: String): Action[AnyContent] = Action {
    try {
      val bank = explorer.getBoxByTokenId(tokenId)
      Ok((bank \\ "items").head.asArray.get(0).noSpaces).as("application/json")
    } catch {
      case e: Throwable =>
        logger.error(s"error in controller ${ergoMixerUtils.getStackTraceStr(e)}")
        BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
    }
  }

}
