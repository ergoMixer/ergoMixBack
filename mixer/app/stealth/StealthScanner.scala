package stealth

import javax.inject.Inject

import scala.annotation.tailrec

import config.MainConfigs
import dao.stealth.{ExtractedBlockDAO, ExtractionResultDAO, TokenInformationDAO}
import dao.DAOUtils
import helpers.{ErgoMixerUtils, ErrorHandler}
import models.StealthModels.{
  CreateExtractedInput,
  CreateExtractedOutput,
  ExtractedInput,
  ExtractedOutput,
  TokenInformation
}
import network.NetworkUtils
import play.api.Logger

class StealthScanner @Inject() (
  networkUtils: NetworkUtils,
  stealthContract: StealthContract,
  daoUtils: DAOUtils,
  extractedBlockDAO: ExtractedBlockDAO,
  extractionResultDAO: ExtractionResultDAO,
  tokenInformationDAO: TokenInformationDAO,
  ergoMixerUtils: ErgoMixerUtils
) extends ErrorHandler {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Add best block into db if don't exist data in table
   */
  def storeBlock(): Unit =
    try {
      val storedBlockCount = daoUtils.awaitResult(extractedBlockDAO.count())
      if (storedBlockCount == 0) {
        logger.info("No block found. Need to insert best block.")
        val header = networkUtils.mainChainHeaderWithHeaderId(MainConfigs.stealthBestBlockId).get
        daoUtils.awaitResult(extractedBlockDAO.insert(header))
      } else {
        logger.info("Already found a block data. No need to insert any blocks")
      }
    } catch {
      case e: Throwable => logger.error(s"problem in store best block in db due to: ${e.getMessage}")
    }

  /**
   * start scanning of blockchain
   */
  def start(): Unit =
    try {
      val lastHeight = notFoundHandle(daoUtils.awaitResult(extractedBlockDAO.getLastHeight))
      logger.info(s"last processed block is: $lastHeight")
      step(lastHeight)
    } catch {
      case e: Throwable =>
        logger.error(ergoMixerUtils.getStackTraceStr(e))
    }

  /**
   * @param headerId - String
   * @return extractedOutputs that match the pattern of Stealth
   */
  def processTransactions(headerId: String): (Seq[ExtractedOutput], Seq[String], Seq[ExtractedInput]) = {
    var createdOutputs  = Seq[ExtractedOutput]()
    var extractedInputs = Seq[ExtractedInput]()
    var tokenIds        = Set[String]()

    val ergoFullBlock = networkUtils.mainChainFullBlockWithHeaderId(headerId).get
    ergoFullBlock.transactions.foreach { tx =>
      tx.inputs.foreach { input =>
        extractedInputs :+= CreateExtractedInput(
          input,
          ergoFullBlock.header.id,
          ergoFullBlock.header.height,
          tx.id
        )
      }
      tx.outputs.foreach { out =>
        if (stealthContract.checkStealth(out.ergoTree)) {
          out.tokens.foreach(tokenIds += _._1)
          createdOutputs :+= CreateExtractedOutput(
            out,
            ergoFullBlock.header.id,
            ergoFullBlock.header.timestamp
          )
        }
      }
    }
    (createdOutputs, tokenIds.toSeq, extractedInputs)
  }

  /**
   * Verifying if the block ID at the specified height in the blockchain matches the stored block at that height in the database.
   * @param height - int
   * @return - Boolean
   */
  def checkHeaderIdByHeight(height: Int): Boolean = {
    val headers = networkUtils.chainSliceHeaderAtHeight(height - 1, height)
    if (headers.nonEmpty && headers.head.height == height)
      notFoundHandle(daoUtils.awaitResult(extractedBlockDAO.getHeaderIdByHeight(height))) ==
        headers.head.id
    else false
  }

  /**
   * scan blockchain by given height
   *
   * @param lastHeight - Int last processed height
   */
  @tailrec
  private def step(lastHeight: Int): Unit =
    if (checkHeaderIdByHeight(lastHeight)) {
      // no fork
      val newHeight = lastHeight + 1
      networkUtils.mainChainHeaderAtHeight(newHeight) match {
        case Some(header) =>
          logger.info(s"Processing block at height: $newHeight, id: ${header.id}")
          val processedTransactions = processTransactions(header.id)
          val tokensInformation = processedTransactions._2
            .filterNot(token => daoUtils.awaitResult(tokenInformationDAO.existsByTokenId(token)))
            .map(TokenInformation(_))
          daoUtils.awaitResult(
            extractionResultDAO.storeOutputsAndRelatedData(
              processedTransactions._1,
              header,
              tokensInformation,
              processedTransactions._3
            )
          )
          stealthContract.updateBoxesStealthId(processedTransactions._1)
          val extractedCount = processedTransactions._1.length
          logger.info("Extracted: " + extractedCount + " outputs")
          step(newHeight)
        case None =>
          logger.info(s"No block found @ height $newHeight")
      }
    } else {
      logger.info(s"Fork detected @ height $lastHeight")
      var syncHeight = lastHeight - 1
      while (!checkHeaderIdByHeight(syncHeight))
        syncHeight -= 1
      for (height <- syncHeight + 1 until lastHeight + 1)
        daoUtils.awaitResult(extractionResultDAO.migrateBlockByHeight(height))
      step(syncHeight)
    }
}
