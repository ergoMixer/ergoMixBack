package services

import play.api.Logger

import javax.inject.Inject
import scala.annotation.tailrec
import dao.stealth._
import stealth.{NodeProcess, StealthContract}
import helpers.ErrorHandler.getStackTraceStr
import models.StealthModels.ExtractedBlock


class ScannerTask @Inject()(extractedBlockDAO: ExtractedBlockDAO, extractionResultDAO: ExtractionResultDAO,
                            forkedResultDAO: ForkedResultDAO, stealthContract :StealthContract,
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
          val extractionResult = nodeProcess.processTransactions(header.id)
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
