package controllers

import helpers.ErgoMixerUtils
import mixer.ErgoMixer
import mixinterface.AliceOrBob
import db.Tables
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.test.Helpers._
import play.api.mvc._
import play.api.test._
import play.api.libs.json.Json
import play.api.db.Database

import scala.concurrent.ExecutionContext
import network.{BlockExplorer, NetworkUtils}

class ApiControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach  {

  var controller: ApiController = _
  var mockErgoMixer: ErgoMixer = _
  var mockNetwork: NetworkUtils = _
  var mockErgoMixerUtils: ErgoMixerUtils = _
  var mockDb: Database = _
  var mockExplorer: BlockExplorer = _
  var mockAliceOrBob: AliceOrBob = _
  var mockTables: Tables = _
  var ec: ExecutionContext = _
  var asset: Assets = _

  override def beforeEach(): Unit = {
    mockErgoMixer = mock[ErgoMixer]
    mockNetwork = mock[NetworkUtils]
    mockErgoMixerUtils = mock[ErgoMixerUtils]
    mockDb = mock[Database]
    mockExplorer = mock[BlockExplorer]
    mockAliceOrBob = mock[AliceOrBob]
    mockTables = mock[Tables]

    ec = mock[ExecutionContext]
    asset = mock[Assets]
    controller = new ApiController(asset, stubControllerComponents(), mockDb, mockErgoMixerUtils, mockErgoMixer, mockNetwork,
      mockExplorer, mockAliceOrBob, mockTables)(ec)
    super.beforeEach()
  }
  /** Check ordinary routes */
  "ApiController generateAddress" should {
    /**
     * Purpose: Create an address in node with nodeAddress per countAddress
     * Scenario: It sends a fake POST request to `/address/generate/from_node` with:
     * * {
     * * "apiKey":"${apiKey}",
     * * "nodeAddress":"${nodeAddress}",
     * * "countAddress": 3
     * * }
     * * and expected to return 3 address from node
     * Test Conditions:
     * * status is `200`
     * * Content-Type is `application/json`
     */
    "return 200 status for a post request" in {

      val apiKey = "hello"
      val nodeAddress = "http://127.0.0.1:9053"
      val requestContent =s"""
                             |{
                             |  "apiKey":"${apiKey}",
                             |  "nodeAddress":"${nodeAddress}",
                             |  "countAddress": 3
                             |}
                             |""".stripMargin

      val resJson = Json.parse(requestContent)
      when(mockNetwork.deriveNextAddress(any(), any() )).thenReturn(s"""{
                                                                       |  "derivationPath" : "m/8",
                                                                       |  "address" : "9eeaz7qj2k6tPQTjc7uiDHQqJS7fNGdBj15FxRBDFgZuoJuWo6d"
                                                                       |}
                                                                       |""".stripMargin)
      val fakeRequest = FakeRequest(POST, "/address/generate/from_node").withHeaders("Content_type" -> "application/json").withJsonBody(resJson)
      val response = controller.generateAddress(fakeRequest)

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsString(response).replaceAll("\\s", "") mustBe
        """
          |[
          |"9eeaz7qj2k6tPQTjc7uiDHQqJS7fNGdBj15FxRBDFgZuoJuWo6d",
          |"9eeaz7qj2k6tPQTjc7uiDHQqJS7fNGdBj15FxRBDFgZuoJuWo6d",
          |"9eeaz7qj2k6tPQTjc7uiDHQqJS7fNGdBj15FxRBDFgZuoJuWo6d"
          |]
          |""".stripMargin.replaceAll("\\s", "")
    }

    "return false response with status 400 for a post request" in {
      // send request without key 'api-key'
      val nodeAddress = "http://127.0.0.1:9053"
      val requestContent =s"""
                             |{
                             |  "nodeAddress":"${nodeAddress}",
                             |  "countAddress": 3
                             |}
                             |""".stripMargin

      val resJson = Json.parse(requestContent)
      when(mockNetwork.deriveNextAddress(any(), any() )).thenReturn(s"""{
                                                                       |  "derivationPath" : "m/8",
                                                                       |  "address" : "9eeaz7qj2k6tPQTjc7uiDHQqJS7fNGdBj15FxRBDFgZuoJuWo6d"
                                                                       |}
                                                                       |""".stripMargin)
      val fakeRequest = FakeRequest(POST, "/address/generate/from_node").withHeaders("Content_type" -> "application/json").withJsonBody(resJson)
      val response = controller.generateAddress(fakeRequest)

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("application/json")
      contentAsString(response).replaceAll("\\s", "") mustBe
        """
          |{
          |  "success": false,
          |  "message": "nodeAddress, countAddress, apiKey is required."
          |}
          |""".stripMargin.replaceAll("\\s", "")
    }

    "return false response with status 400 and raise error for a post request" in {
      // deriveNextAddress has error and return null response
      val apiKey = "hello"
      val nodeAddress = "http://127.0.0.1:9053"
      val requestContent =s"""
                             |{
                             |  "apiKey":"${apiKey}",
                             |  "nodeAddress":"${nodeAddress}",
                             |  "countAddress": 3
                             |}
                             |""".stripMargin

      val resJson = Json.parse(requestContent)
      when(mockNetwork.deriveNextAddress(any(), any() )).thenReturn(null)
      val fakeRequest = FakeRequest(POST, "/address/generate/from_node").withHeaders("Content_type" -> "application/json").withJsonBody(resJson)
      val response = controller.generateAddress(fakeRequest)

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("application/json")
      contentAsString(response).replaceAll("\\s", "") mustBe
        """
          |{
          |  "success": false,
          |  "message": "null"
          |}
          |""".stripMargin.replaceAll("\\s", "")
    }
  }

  "ApiController withdraw" should {
    /**
     * Purpose: Add withdraw address to database or change status withdraw
     * Scenario: It sends a fake POST request to `/mix/withdraw/` with:
     * * Input: {
     * * "nonStayAtMix" : Bool
     * * "withdrawAddress": String
     * * "mixId": String
     * * }
     * and expected to return :
     * * Output: {
     * * "success": true or false,
     * * "message": ""
     * * }
     */

    "return 200 status for a post request, update withdraw address and change status withdraw to withdraw now" in {
      val mixId = "hello"
      val withdrawAddress = "9gFThgLnnLgnSr47ES9BaaXovDWTCBJJtFztSToMYw8Tc569ycT"
      val nonStayAtMix: Boolean = true
      val requestContent =s"""
                             |{
                             |  "nonStayAtMix" : ${nonStayAtMix},
                             |  "withdrawAddress": "${withdrawAddress}",
                             |  "mixId": "${mixId}"
                             |}
                             |""".stripMargin

      val resJson = Json.parse(requestContent)
      val fakeRequest = FakeRequest(POST, "/mix/withdraw/").withHeaders("Content_type" -> "application/json").withJsonBody(resJson)
      val response = controller.withdraw(fakeRequest)

      verify(mockErgoMixer).updateMixWithdrawAddress(mixId, withdrawAddress)
      verify(mockErgoMixer).withdrawMixNow(mixId)
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      contentAsString(response).replaceAll("\\s", "") mustBe
        """
          |{
          | "success": true
          |}
          |""".stripMargin.replaceAll("\\s", "")
    }

    "return 400 status for a post request and never call functions updateMixWithdrawAddress and withdrawMixNow" in {
      val mixId = "hello"
      val withdrawAddress = "9gFThgLnnLgnSr47ES9BaaXovDWTCBJJtFztSToMYw8Tc569ycT"
      val nonStayAtMix: Boolean = false
      val requestContent =s"""
                             |{
                             |  "nonStayAtMix" : ${nonStayAtMix},
                             |  "withdrawAddress": "",
                             |  "mixId": "${mixId}"
                             |}
                             |""".stripMargin

      val resJson = Json.parse(requestContent)
      val fakeRequest = FakeRequest(POST, "/mix/withdraw/").withHeaders("Content_type" -> "application/json").withJsonBody(resJson)
      val response = controller.withdraw(fakeRequest)

      verify(mockErgoMixer, never).updateMixWithdrawAddress(mixId, withdrawAddress)
      verify(mockErgoMixer, never).withdrawMixNow(mixId)
    }
  }
}
