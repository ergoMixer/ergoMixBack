package helpers

import io.circe.Json
import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._

import java.io.{PrintWriter, StringWriter}
import scala.util.Try

object ErrorHandler{
  private val logger: Logger = Logger(this.getClass)

  def getStackTraceStr(e: Throwable): String = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    sw.toString
  }

  def errorResponse(e: Exception): Result = {
    logger.info(getStackTraceStr(e))
    val result = Json.fromFields(List(
          ("status", Json.fromBoolean(false)),
          ("detail", Json.fromString(e.getMessage))
          ))
    BadRequest(result.toString()).as("application/json")
  }

  def notFoundResponse(m: String): Result = {
    val result = Json.fromFields(List(
          ("status", Json.fromBoolean(false)),
          ("detail", Json.fromString(m))
          ))
    NotFound(result.toString()).as("application/json")
  }
  final case class NotFoundException(private val message: String = "object not found") extends Exception(message)

  /**
   * exception handling for get any object from db
   * @param inp
   * @tparam T
   * @return
   */
  def notFoundHandle[T](inp: Try[Option[T]]): T = {
    inp.toEither match {
      case Right(Some(result)) => result
      case Right(None) =>  throw NotFoundException()
      case Left(ex) => throw ex
    }
  }
}
