package services

import akka.actor._
import app.Configs
import network.Client
import helpers.TrayUtils

import javax.inject._
import mixer.ErgoMixer
import play.api.Logger
import play.api.db.Database
import play.api.inject.ApplicationLifecycle
import play.api.mvc._
import services.ScheduledJobs.{RefreshMixingStats, RefreshPoolStats, UpdateGroupMixStates}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait ErgoMixHooks {
  def onStart(): Unit

  def onShutdown(): Unit
}


@Singleton
class ErgomixHooksImpl @Inject()(appLifecycle: ApplicationLifecycle, implicit val system: ActorSystem,
                                 cc: ControllerComponents, client: Client, ergoMixer: ErgoMixer,
                                 ergoMixerJobs: ErgoMixerJobs)
                                (implicit executionContext: ExecutionContext) extends ErgoMixHooks {

  private val logger: Logger = Logger(this.getClass)
  lazy val jobsActor: ActorRef = system.actorOf(ScheduledJobs.props(ergoMixerJobs), "scheduling-jobs-actor")

  client.setClient(isMainnet = Configs.isMainnet, Configs.explorerUrl)

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

    system.scheduler.scheduleOnce(
      delay = 5.seconds,
      receiver = jobsActor,
      message = UpdateGroupMixStates
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