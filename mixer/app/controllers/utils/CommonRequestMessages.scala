package controllers.utils

object CommonRequestMessages {
  lazy val BAD_REQUEST_IF_DATA_DOES_NOT_LOAD: String = s"""
                                             |{
                                             |  "success": false,
                                             |  "message": "Loading from blockchain..."
                                             |}
                                             |""".stripMargin
}
