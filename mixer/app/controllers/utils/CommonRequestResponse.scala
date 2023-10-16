package controllers.utils

import io.circe.Json

trait CommonRequestResponse {
  lazy val BAD_REQUEST_IF_DATA_DOES_NOT_LOAD: String = s"""
                                                          |{
                                                          |  "success": false,
                                                          |  "message": "Loading from blockchain..."
                                                          |}
                                                          |""".stripMargin

  final case class BadRequestException(parameter: String) extends Exception(s"$parameter not found")

  def jsonResult(success: Boolean, json: (String, Json) = ("", Json.Null)): Json = {
    val response = Json.obj(
      ("success", Json.fromBoolean(success)),
    )
    if (json._1.nonEmpty) response.deepMerge(Json.obj(json)) else response
  }

  def errorResult(message: String): Json = jsonResult(success = false, ("message", Json.fromString(message)))
}
