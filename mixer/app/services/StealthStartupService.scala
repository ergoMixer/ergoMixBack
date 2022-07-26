package services

import akka.actor._
import app.Configs

import javax.inject._
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait StealthStartupServiceHooks {
  def onStart(): Unit

  def onShutdown(): Unit
}

@Singleton
class StealthStartupService @Inject()(appLifecycle: ApplicationLifecycle,
                                      system: ActorSystem, scannerTask: ScannerTask, initBestBlockTask: InitBestBlockTask)
                                     (implicit ec: ExecutionContext) extends StealthStartupServiceHooks{

  private val logger: Logger = Logger(this.getClass)

  logger.info("Scanner started!")


  val jobs: ActorRef = system.actorOf(Props(new StealthJobs(scannerTask, initBestBlockTask)), "scheduler")

  override def onStart(): Unit ={
    system.scheduler.scheduleOnce(
      delay = 0.seconds,
      receiver = jobs,
      message = StealthJobsInfo.initBestBlockInDb
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 10.seconds,
      interval = Configs.stealthScanInterval.seconds,
      receiver = jobs,
      message = StealthJobsInfo.blockChainScan
    )

    system.scheduler.scheduleAtFixedRate(
      initialDelay = 60.seconds,
      interval = Configs.stealthSpendInterval.seconds,
      receiver = jobs,
      message = StealthJobsInfo.spendStealth
    )
  }

  override def onShutdown(): Unit = {
    system.stop(jobs)
    logger.info("Scanner stopped")
  }

  appLifecycle.addStopHook { () =>
    onShutdown()
    Future.successful(())
  }

  onStart()
}
