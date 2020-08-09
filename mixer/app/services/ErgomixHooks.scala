package services

import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import play.api.mvc._
import akka.actor._
import javax.inject._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import ScheduledJobs.{RefreshMixingStats, RefreshPoolStats}
import ErgoMixingSystem.ergoMixerJobs
import app.Configs

trait ErgoMixHooks {

  def onStart(): Unit

  def onShutdown(): Unit

}



@Singleton
class ErgomixHooksImpl @Inject()(appLifecycle: ApplicationLifecycle,
                                 system: ActorSystem,
                                 cc: ControllerComponents)
                            (implicit executionContext: ExecutionContext) extends ErgoMixHooks {

  implicit val actorSystem = system

  lazy val jobsActor = system.actorOf(ScheduledJobs.props(ergoMixerJobs), "scheduling-jobs-actor")

  override def onStart(): Unit = {
    system.scheduler.scheduleAtFixedRate(
      initialDelay = 30.seconds,
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

    println("Initialization done")
  }

  override def onShutdown(): Unit = {
    system.stop(jobsActor)
    println("Shutdown preparation done")
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