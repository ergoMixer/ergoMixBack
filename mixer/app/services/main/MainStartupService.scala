package services.main

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import akka.actor._
import config.MainConfigs
import dao.DAOUtils
import helpers.TrayUtils
import network.Client
import play.api.inject.ApplicationLifecycle
import play.api.Logger
import services.admin.AdminHooks
import services.main.MainScheduledJobs._
import services.mixer.ErgoMixHooks

trait MainHooks {
  def onStart(): Unit

  def onShutdown(): Unit
}

@Singleton
class MainHooksImpl @Inject() (
  appLifecycle: ApplicationLifecycle,
  implicit val system: ActorSystem,
  client: Client,
  mainJobs: MainJobs,
  daoUtils: DAOUtils,
  trayUtils: TrayUtils
)(implicit executionContext: ExecutionContext)
  extends MainHooks
  with AdminHooks
  with ErgoMixHooks {

  private val logger: Logger   = Logger(this.getClass)
  lazy val jobsActor: ActorRef = system.actorOf(MainScheduledJobs.props(mainJobs), "scheduling-jobs-actor-main")

  client.setClient(MainConfigs.networkType, MainConfigs.explorerUrl)

  override def onStart(): Unit = {
    trayUtils.showNotification("Starting ErgoMixer...", "Everything will be ready in a few seconds!")
    system.scheduler.scheduleAtFixedRate(
      initialDelay = 0.seconds,
      interval     = MainConfigs.jobInterval.seconds,
      receiver     = jobsActor,
      message      = CheckClients
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 5.seconds,
      interval     = MainConfigs.statisticJobsInterval.second,
      receiver     = jobsActor,
      message      = RefreshPoolStats
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 10.seconds,
      interval     = MainConfigs.statisticJobsInterval.seconds,
      receiver     = jobsActor,
      message      = UpdateRingStats
    )

    system.scheduler.scheduleOnce(
      delay    = 20.seconds,
      receiver = jobsActor,
      message  = InitBestBlock
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 30.seconds,
      interval     = MainConfigs.stealthScanInterval.seconds,
      receiver     = jobsActor,
      message      = StealthScanner
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 35.seconds,
      interval     = MainConfigs.stealthScanInterval.seconds,
      receiver     = jobsActor,
      message      = FetchTokenInformation
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 40.seconds,
      interval     = MainConfigs.stealthSpendInterval.seconds,
      receiver     = jobsActor,
      message      = SpendStealth
    )

    logger.info("Main Jobs: Initialization done")
  }

  override def onShutdown(): Unit = {
    daoUtils.awaitResult(daoUtils.shutdown())
    system.stop(jobsActor)
    logger.info("Main Jobs: Shutdown preparation done")
    Runtime.getRuntime.halt(0)
  }

  appLifecycle.addStopHook(() => Future.successful(onShutdown()))

  onStart()
}
