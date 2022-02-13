package dao.stealth

import javax.inject.Inject
import models.StealthModel.{ExtractedAssetModel, ExtractedBlockModel, ExtractedDataInputModel, ExtractedInputModel, ExtractedOutputModel, ExtractedRegisterModel, ExtractedTransactionModel, ExtractionInputResultModel, ExtractionOutputResultModel, StealthModel}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import helpers.DbUtils
import dao.DAOUtils

import com.google.common.hash.{BloomFilter, Funnels}
import models.StealthModel.Types.ScanId

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}


class ExtractionResultDAO @Inject() (extractedBlockDAO: ExtractedBlockDAO, transactionDAO: TransactionDAO, dataInputDAO: DataInputDAO, inputDAO: InputDAO, outputDAO: OutputDAO, assetDAO: AssetDAO, registerDAO: RegisterDAO, scanDAO: ScanDAO, stealthDAO: StealthDAO, daoUtils: DAOUtils , protected val dbConfigProvider: DatabaseConfigProvider) (implicit executionContext: ExecutionContext)
    extends DbUtils with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._
  /**
  * Spend outputs, that have appeared here as inputs and store these inputs and transactions into db as transactionally.
   * @param extractedInputs : ExtractionInputResultModel extracted Inputs in one block
   */
  def spendOutputsAndStoreRelatedData(extractedInputs: Seq[ExtractionInputResultModel]): Unit = {
    var transactions: Seq[ExtractedTransactionModel] = Seq()
    var dataInputs: Seq[ExtractedDataInputModel] = Seq()
    var inputs: Seq[ExtractedInputModel] = Seq()
    var inputsIds: Seq[String] = Seq()

    extractedInputs.foreach(obj => {
      inputs = inputs :+ obj.extractedInput
      inputsIds = inputsIds :+ obj.extractedInput.boxId
      dataInputs = dataInputs ++ obj.extractedDataInput
      transactions = transactions :+ obj.extractedTransaction
    })

    val updateAndGetQuery = for {
      outputs <- DBIO.successful(outputDAO.outputs.filter(_.boxId inSet inputsIds))
      _ <- outputs.map(_.spent).update(true)
      result <- outputs.map(_.boxId).result
    } yield result

    var inputsTxIds: Seq[String] = Seq()

    val action = for {
      responseUpdateAndGetQuery <- updateAndGetQuery.transactionally
      filteredInputs <- DBIO.successful({
        val needInputs = inputs.filter( input => {
          if (responseUpdateAndGetQuery.contains(input.boxId)) {
            inputsTxIds = inputsTxIds :+ input.txId
            true
          }
          else false
        })
        inputsTxIds = inputsTxIds.distinct
        needInputs
      })
      transactions <- DBIO.successful(transactions.distinct.filter(n => inputsTxIds.contains(n.id)))
      _ <- transactionDAO.insertIfDoesNotExist(transactions)
      dataInputs <- DBIO.successful(dataInputs.distinct.filter(n => inputsTxIds.contains(n.txId)))
      _ <- dataInputDAO.insertIfDoesNotExist(dataInputs)
      _ <- inputDAO.insert(filteredInputs)
    } yield {

    }
    val response = execTransact(action)
    Await.result(response, Duration.Inf)
  }

  /**
  * Store extracted Block also store outputs, transactions are scanned according to rules into db as transactionally.
   * @param createdOutputs : ExtractionOutputResultModel extracted outputs
   * @param extractedBlockModel: ExtractedBlockModel extracted block
   */
  def storeOutputsAndRelatedData(createdOutputs: Seq[ExtractionOutputResultModel], extractedBlockModel: ExtractedBlockModel): Unit = {
    var transactions: Seq[ExtractedTransactionModel] = Seq()
    var dataInputs: Seq[ExtractedDataInputModel] = Seq()
    var outputs: Seq[ExtractedOutputModel] = Seq()
    var assets: Seq[ExtractedAssetModel] = Seq()
    var registers: Seq[ExtractedRegisterModel] = Seq()
    var scan_FKs: Seq[(ScanId, String, String)] = Seq()

    createdOutputs.foreach(obj => {
      outputs = outputs :+ obj.extractedOutput
      transactions = transactions :+ obj.extractedTransaction
      dataInputs = dataInputs ++ obj.extractedDataInput
      assets = assets ++ obj.extractedAssets
      registers = registers ++ obj.extractedRegisters
      scan_FKs = scan_FKs ++ obj.scanIds.map((_, obj.extractedOutput.boxId, obj.extractedOutput.headerId))
    })

    val action = for {
        _ <- extractedBlockDAO.insert(Seq(extractedBlockModel))
        _ <- transactionDAO.insertIfDoesNotExist(transactions.distinct)
        _ <- dataInputDAO.insertIfDoesNotExist(dataInputs.distinct)
        _ <- outputDAO.insert(outputs)
        _ <- assetDAO.insert(assets)
        _ <- registerDAO.insert(registers)
        _ <- scanDAO.insertFKs(scan_FKs)
    } yield {

    }
    val response = execTransact(action)
    Await.result(response, Duration.Inf)
  }

}
