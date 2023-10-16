package services.mixer

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import akka.actor._
import config.MainConfigs
import play.api.inject.ApplicationLifecycle
import play.api.Logger
import services.mixer.ScheduledJobs.RefreshMixingStats

trait ErgoMixHooks {
  def onStart(): Unit

  def onShutdown(): Unit
}

@Singleton
class ErgoMixHooksImpl @Inject() (
  appLifecycle: ApplicationLifecycle,
  implicit val system: ActorSystem,
  ergoMixerJobs: ErgoMixerJobs
)(implicit executionContext: ExecutionContext)
  extends ErgoMixHooks {

  private val logger: Logger   = Logger(this.getClass)
  lazy val jobsActor: ActorRef = system.actorOf(ScheduledJobs.props(ergoMixerJobs), "scheduling-jobs-actor-mixer")

  override def onStart(): Unit = {
    system.scheduler.scheduleAtFixedRate(
      initialDelay = 20.seconds,
      interval     = MainConfigs.jobInterval.seconds,
      receiver     = jobsActor,
      message      = RefreshMixingStats
    )

    logger.info("Mixer Jobs: initialization done")
  }

  override def onShutdown(): Unit = {
    system.stop(jobsActor)
    logger.info("Mixer Jobs: shutdown preparation done")
  }

  appLifecycle.addStopHook(() => Future.successful(onShutdown()))

  onStart()
}
