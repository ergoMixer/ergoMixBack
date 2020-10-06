package services

import akka.actor._
import app.Configs
import db.Tables
import helpers.TrayUtils
import javax.inject._
import mixer.{ErgoMixer, ErgoMixerJobs}
import play.api.Logger
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.mvc._
import services.ErgoMixingSystem.{ergoMixerJobs, tables}
import services.ScheduledJobs.{RefreshMixingStats, RefreshPoolStats}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait ErgoMixHooks {
  def onStart(): Unit

  def onShutdown(): Unit
}


@Singleton
class ErgomixHooksImpl @Inject()(appLifecycle: ApplicationLifecycle, system: ActorSystem,
                                 cc: ControllerComponents, db: Database)
                                (implicit executionContext: ExecutionContext) extends ErgoMixHooks {

  private val logger: Logger = Logger(this.getClass)
  ErgoMixingSystem.tables = new Tables(db)
  ErgoMixingSystem.ergoMixer = new ErgoMixer(tables)
  ErgoMixingSystem.ergoMixerJobs = new ErgoMixerJobs(tables)
  implicit val actorSystem = system

  lazy val jobsActor = system.actorOf(ScheduledJobs.props(ergoMixerJobs), "scheduling-jobs-actor")

  override def onStart(): Unit = {
    TrayUtils.showNotification("Starting ErgoMixer...", "Everything will be ready in a few seconds!")
    system.scheduler.scheduleAtFixedRate(
      initialDelay = 10.seconds,
      interval = Configs.jobInterval.seconds,
      receiver = jobsActor,
      message = RefreshMixingStats
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 0.seconds,
      interval = Configs.statisticJobsInterval.second,
      receiver = jobsActor,
      message = RefreshPoolStats
    )

    logger.info("Initialization done")
  }

  override def onShutdown(): Unit = {
    system.stop(jobsActor)
    logger.info("Shutdown preparation done")
  }

  appLifecycle.addStopHook { () =>
    onShutdown()
    Future.successful(())
  }

  onStart()
}