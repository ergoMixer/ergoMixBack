package services

import app.Configs.readJsonFile
import io.circe.parser.parse
import play.api.Logger

import java.io.{PrintWriter, StringWriter}
import javax.inject.Inject
import scala.annotation.tailrec
import scala.util.{Failure, Success}
import dao.stealth._
import stealth.{NodeProcess, StealthContract}
import models.StealthModels._
import helpers.ErrorHandler.{errorResponse, getStackTraceStr}


class ScannerTask @Inject()(extractedBlockDAO: ExtractedBlockDAO, extractionResultDAO: ExtractionResultDAO,
                            forkedResultDAO: ForkedResultDAO, scanDAO: ScanDAO, stealthContract :StealthContract,
                            nodeProcess: NodeProcess) {

  private val logger: Logger = Logger(this.getClass)

  @tailrec
  private def step(lastHeight: Int): Unit = {
    val localId = extractedBlockDAO.getHeaderIdByHeight(lastHeight)
    if (localId == nodeProcess.mainChainHeaderIdAtHeight(lastHeight).get) {
      // no fork
      val newHeight = lastHeight + 1
      nodeProcess.mainChainHeaderAtHeight(newHeight) match {
        case Some(header) =>
          logger.debug(s"Processing block at height: $newHeight, id: ${header.id}")
          val extractionResult = nodeProcess.processTransactions(header.id, scanDAO.selectAll)
          extractionResultDAO.storeOutputsAndRelatedData(extractionResult.createdOutputs, ExtractedBlock(header))
          extractionResultDAO.spendOutputsAndStoreRelatedData(extractionResult.extractedInputs)
          stealthContract.updateBoxesStealthId(extractionResult.createdOutputs)
          val extractedCount = extractionResult.createdOutputs.length
          logger.info("Extracted: " + extractedCount + " outputs")
          step(newHeight)
        case None =>
          logger.debug(s"No block found @ height $newHeight")
      }
    } else {
      var syncHeight = lastHeight - 1
      while (extractedBlockDAO.getHeaderIdByHeight(lastHeight) !=
        nodeProcess.mainChainHeaderIdAtHeight(syncHeight).get) {
        syncHeight -= 1
      }
      for (height <- syncHeight + 1 until lastHeight) {
        forkedResultDAO.migrateBlockByHeight(height)
      }
      step(syncHeight)
    }
  }



  def scanRegister(): Unit = {
    try {
      if (scanDAO.selectAll.scans.isEmpty) {
        val scan = readJsonFile("../conf/Stealth_newScan.json").stripMargin
        var result: String = ""
        val scanJson = parse(scan).toOption.get
        logger.info("scan register request:\n")
        logger.info(scanJson.toString())
        Scan.scanDecoder.decodeJson(scanJson).toTry match {
          case Success(scan) =>
            val addedScan = scanDAO.create(Scan(scan))
            result = addedScan.scanId.toString
          case Failure(e) => throw new Exception(e)
        }
        logger.info("scan registered:\r")
        logger.info(result)
      }
    }
    catch {
      case e: Exception => errorResponse(e)
    }
  }

  def start(): Unit = {
    try {
      val lastHeight = extractedBlockDAO.getLastHeight
      step(lastHeight)
    }
    catch {
      case a: Throwable =>
        logger.error(getStackTraceStr(a))
    }
  }

  def spendStealth(): Unit = {
    try {
      stealthContract.spendStealthBoxes()
    }
    catch {
      case a: Throwable =>
        logger.error(getStackTraceStr(a))
    }
  }
}
