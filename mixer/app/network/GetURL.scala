package network

import java.io.{BufferedWriter, InputStream, OutputStreamWriter}
import java.net.{HttpURLConnection, URL, URLConnection}

import scala.io.Source
import scala.util.{Failure, Success, Try}

import config.MainConfigs
import io.circe._
import io.circe.parser._

object GetURL {

  val requestProperties = Map(
    "User-Agent" -> "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)",
    "Accept"     -> "application/json"
  )

  private def is2Str(is: InputStream) =
    Try(Source.fromInputStream(is).getLines.mkString("\n")) match {
      case Success(s)         => s
      case Failure(exception) => exception.getMessage
    }

  /**
   * @param url           url
   * @param headers       headers
   * @param useProxyIfSet whether to use proxy if possible or not
   * @return connection (potentially with going through proxy)
   */
  def getConnection(url: String, headers: Map[String, String], useProxyIfSet: Boolean = true): URLConnection = {
    val connection =
      if (!useProxyIfSet || MainConfigs.proxy == null) new URL(url).openConnection
      else new URL(url).openConnection(MainConfigs.proxy)
    headers.foreach { case (name, value) => connection.setRequestProperty(name, value) }
    connection.setConnectTimeout(MainConfigs.connectionTimeout * 1000)
    connection.setReadTimeout(MainConfigs.connectionTimeout * 1000)
    connection
  }

  def getOrError(url: String): Either[Throwable, Option[Json]] =
    Try {
      val connection = getConnection(url, requestProperties)
      val httpConn   = connection.asInstanceOf[HttpURLConnection]
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(parse(is2Str(httpConn.getInputStream)).right.get)).toEither
      case Success((404, _)) =>
        Right(
          None
        ) // not found; we want to consider this as a "good" case (implies box has 0 confirmation or does not exist)
      case Success((httpCode, httpConn)) =>
        Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }

  def getOrErrorStr(
    url: String,
    headers: Map[String, String],
    useProxyIfSet: Boolean = true
  ): Either[Throwable, Option[String]] =
    Try {
      val connection = getConnection(url, headers, useProxyIfSet)
      val httpConn   = connection.asInstanceOf[HttpURLConnection]
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(is2Str(httpConn.getInputStream))).toEither
      case Success((404, _)) =>
        Right(
          None
        ) // not found; we want to consider this as a "good" case (implies box has 0 confirmation or does not exist)
      case Success((httpCode, httpConn)) =>
        Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }

  def getOrErrorStr(url: String): Either[Throwable, Option[String]] =
    getOrErrorStr(url, requestProperties)

  def get(url: String): Json =
    getOrError(url) match {
      case Right(Some(json)) => json
      case Right(None)       => throw new Exception("Explorer returned error 404")
      case Left(ex)          => throw ex
    }

  def getStr(url: String): String =
    getOrErrorStr(url) match {
      case Right(Some(string)) => string
      case Right(None)         => throw new Exception("Returned error 404")
      case Left(ex)            => throw ex
    }

  def getStr(url: String, additionalHeaders: Map[String, String], useProxyIfSet: Boolean = true): String =
    getOrErrorStr(url, additionalHeaders ++ requestProperties, useProxyIfSet) match {
      case Right(Some(string)) => string
      case Right(None)         => throw new Exception("Returned error 404")
      case Left(ex)            => throw ex
    }

  def postConnection(url: String, headers: Map[String, String], useProxyIfSet: Boolean = true): HttpURLConnection = {
    val connection = {
      if (!useProxyIfSet || MainConfigs.proxy == null) new URL(url).openConnection
      else new URL(url).openConnection(MainConfigs.proxy)
    }.asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    headers.foreach { case (name, value) => connection.setRequestProperty(name, value) }
    connection.setConnectTimeout(20000)
    connection
  }

  def postOrError(url: String, jsonString: String): Either[Throwable, Option[Json]] =
    Try {
      val httpConn                     = postConnection(url, Map("Content-Type" -> "application/json") ++ requestProperties)
      val outputBuffer: BufferedWriter = new BufferedWriter(new OutputStreamWriter(httpConn.getOutputStream))
      outputBuffer.write(jsonString)
      outputBuffer.close()
      (httpConn.getResponseCode, httpConn)
    } match {
      case Success((200, httpConn)) => Try(Some(parse(is2Str(httpConn.getInputStream)).right.get)).toEither
      case Success((httpCode, httpConn)) =>
        Left(new Exception(s"http:$httpCode,error:${is2Str(httpConn.getErrorStream)}"))
      case Failure(ex) => Left(ex)
    }

  def post(url: String, jsonString: String): Json =
    postOrError(url, jsonString) match {
      case Right(Some(json)) => json
      case Right(None)       => throw new Exception("Explorer returned error 404")
      case Left(ex) =>
        throw ex
    }

}
