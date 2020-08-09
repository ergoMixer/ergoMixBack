package controllers

import javax.inject.Inject
import play.api.data._
import play.api.i18n._
import play.api.mvc._
import scalaj.http.HttpResponse

import scala.util.Try

/**
 * Instead of MessagesAbstractController, you can use the I18nSupport trait,
 * which provides implicits that create a Messages instance from a request
 * using implicit conversion.
 *
 * See https://www.playframework.com/documentation/2.8.x/ScalaForms#passing-messagesprovider-to-form-helpers
 * for details.
 */

class MixerController @Inject()(assets: Assets, val controllerComponents: ControllerComponents) extends BaseController {

  type Response = HttpResponse[Array[Byte]]

  def index = Action {
    Redirect("/dashboard")
  }

  def dashboard: Action[AnyContent] = assets.at("index.html")

  def assetOrDefault(resource: String): Action[AnyContent] = {
    if (resource.contains(".")) assets.at(resource) else dashboard
  }
}
