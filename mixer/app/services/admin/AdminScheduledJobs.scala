package services.admin

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import mixer.{AdminCharger, AdminIncome, AdminParams, AdminStat}
import network.NetworkUtils
import play.api.Logger
import services.admin.AdminScheduledJobs.{AdminChargerJob, AdminIncomeJob, AdminParamJob, RefreshAdminStatsJob}

import javax.inject.Inject
import scala.util.Try


class AdminJobs @Inject()(val networkUtils: NetworkUtils, val adminStat: AdminStat, val adminParams: AdminParams,
                          val adminIncome: AdminIncome, val adminCharger: AdminCharger)

class AdminScheduledJobs(mixerJobs: AdminJobs) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)
  import mixerJobs.networkUtils


  override def receive: Receive = {
    case RefreshAdminStatsJob =>
      logger.info("Admin stats: jobs started")

      networkUtils.pruneClients()
      if (networkUtils.clientsAreOk) logger.info("admin stats: " + Try(mixerJobs.adminStat.scanEntryTxs()))

      logger.info("Admin stats: jobs finished")

    case AdminParamJob =>
      logger.info("Admin Params: jobs started")

      if(networkUtils.clientsAreOk) {
        mixerJobs.adminParams.handleParams()
      }

      logger.info("Admin Params: jobs finished")

    case AdminChargerJob =>
      logger.info("Admin Charger: jobs started")

      if(networkUtils.clientsAreOk) {
        mixerJobs.adminCharger.handleCharging()
      }

      logger.info("Admin Charger: jobs finished")

    case AdminIncomeJob =>
      logger.info("Admin Income: jobs started")

      if(networkUtils.clientsAreOk) {
        mixerJobs.adminIncome.handleIncome()
      }

      logger.info("Admin Income: jobs finished")
  }
}

object AdminScheduledJobs {

  def props(mixerJobs: AdminJobs)(implicit system: ActorSystem): Props =
    Props.create(classOf[AdminScheduledJobs], mixerJobs)

  case object RefreshAdminStatsJob

  case object AdminParamJob

  case object AdminChargerJob

  case object AdminIncomeJob
}
