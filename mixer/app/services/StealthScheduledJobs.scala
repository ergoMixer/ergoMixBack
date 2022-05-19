package services


import akka.actor.{Actor, ActorLogging}
import play.api.Logger
import scala.util.Try

object StealthJobsInfo {
  val initBestBlockInDb = "store best block in db"
  val blockChainScan = "block scanned"
  val spendStealth = "spend stealth boxes"
  val registerScan = "registering new scan"
}

class StealthJobs(scanner: ScannerTask, initBestBlock: InitBestBlockTask) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  /**
   * periodically start scanner, task.
   */
  def receive: PartialFunction[Any, Unit] = {
    case StealthJobsInfo.initBestBlockInDb =>
      logger.info("Start job Store Best Block task.")
      Try(initBestBlock.store_block())
    case StealthJobsInfo.registerScan =>
      logger.info("Start job register scan task.")
      Try(scanner.scanRegister())
    case StealthJobsInfo.blockChainScan =>
      logger.info("Start job scanner task.")
      Try(scanner.start())
    case StealthJobsInfo.spendStealth =>
      logger.info("Start spending stealth boxes.")
      Try(scanner.spendStealth())
  }
}