package services

import akka.actor._
import app.Configs
import javax.inject._
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.mvc._
import services.ErgoMixingSystem.ergoMixerJobs
import services.ScheduledJobs.{RefreshMixingStats, RefreshPoolStats}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait ErgoMixHooks {

  def onStart(): Unit

  def onShutdown(): Unit

}



@Singleton
class ErgomixHooksImpl @Inject()(appLifecycle: ApplicationLifecycle,
                                 system: ActorSystem,
                                 cc: ControllerComponents)
                            (implicit executionContext: ExecutionContext) extends ErgoMixHooks {

  private val logger: Logger = Logger(this.getClass)
  implicit val actorSystem = system

  lazy val jobsActor = system.actorOf(ScheduledJobs.props(ergoMixerJobs), "scheduling-jobs-actor")

  override def onStart(): Unit = {
    TrayUtils.showNotification("Starting ErgoMixer...", "Everything will be ready in a few seconds!")
    system.scheduler.scheduleAtFixedRate(
      initialDelay = 60.seconds,
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

  // When the application starts, register a stop hook with the
  // ApplicationLifecycle object. The code inside the stop hook will
  // be run when the application stops.
  appLifecycle.addStopHook { () =>
    onShutdown()
    Future.successful(())
  }

  // Called when this singleton is constructed
  onStart()

}