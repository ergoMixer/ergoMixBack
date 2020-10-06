package controllers

import javax.inject.Inject
import play.api.mvc._

class MixerController @Inject()(assets: Assets, val controllerComponents: ControllerComponents) extends BaseController {

  def index = Action {
    Redirect("/dashboard")
  }

  def dashboard: Action[AnyContent] = assets.at("index.html")

  def assetOrDefault(resource: String): Action[AnyContent] = {
    if (resource.contains(".")) assets.at(resource) else dashboard
  }
}
