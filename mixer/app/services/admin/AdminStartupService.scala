package services.admin


import config.{AdminConfigs, MainConfigs}
import services.admin.AdminScheduledJobs.{AdminChargerJob, AdminIncomeJob, AdminParamJob, RefreshAdminStatsJob}
import akka.actor._

import javax.inject._
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait AdminHooks {
  def onStart(): Unit

  def onShutdown(): Unit
}


@Singleton
class AdminHooksImpl @Inject()(appLifecycle: ApplicationLifecycle, implicit val system: ActorSystem, adminJobs: AdminJobs)
                                (implicit executionContext: ExecutionContext) extends AdminHooks {

  private val logger: Logger = Logger(this.getClass)
  lazy val jobsActor: ActorRef = system.actorOf(AdminScheduledJobs.props(adminJobs), "scheduling-jobs-actor-admin")

  override def onStart(): Unit = {
    system.scheduler.scheduleAtFixedRate(
      initialDelay = 20.seconds,
      interval = MainConfigs.statisticJobsInterval.second,
      receiver = jobsActor,
      message = RefreshAdminStatsJob
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 10.seconds,
      interval = AdminConfigs.paramInterval.second,
      receiver = jobsActor,
      message = AdminParamJob
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 100.seconds,
      interval = AdminConfigs.chargeInterval.second,
      receiver = jobsActor,
      message = AdminChargerJob
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 300.seconds,
      interval = AdminConfigs.incomeInterval.second,
      receiver = jobsActor,
      message = AdminIncomeJob
    )

    logger.info("Admin Jobs: initialization done")
  }

  override def onShutdown(): Unit = {
    system.stop(jobsActor)
    logger.info("Admin Jobs: shutdown preparation done")
  }

  appLifecycle.addStopHook { () =>
    Future.successful(onShutdown())
  }

  onStart()
}
