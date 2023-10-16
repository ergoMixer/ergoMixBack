package mixer

import javax.inject.{Inject, Singleton}

import config.AdminConfigs
import dao.admin.{AllAdminDAO, CommissionIncomeDAO, IncomeStateDAO, TokenIncomeDAO}
import dao.DAOUtils
import helpers.ErgoMixerUtils
import io.circe.syntax._
import io.circe.Json
import mixinterface.TokenErgoMix
import models.Admin.{CommissionIncome, IncomeState, TokenIncome}
import models.Box.{InBox, OutBox}
import models.Transaction.SpendTx
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.ErgoToken
import play.api.Logger
import special.collection.Coll

// TODO: need write unit test (#83)
@Singleton
class AdminStat @Inject() (
  networkUtils: NetworkUtils,
  ergoMixerUtils: ErgoMixerUtils,
  explorer: BlockExplorer,
  incomeStateDAO: IncomeStateDAO,
  tokenIncomeDAO: TokenIncomeDAO,
  commissionIncomeDAO: CommissionIncomeDAO,
  daoUtils: DAOUtils,
  allAdminDAO: AllAdminDAO
) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * scans entry transaction based on current state of DB, meaning only scans new transactions
   * saves statistics in DB for administration usages
   */
  def scanEntryTxs(): Unit = {
    val incomeAddress = TokenErgoMix.mixerIncome.getErgoAddress.toString
    val processed     = daoUtils.awaitResult(incomeStateDAO.getLastOrder).getOrElse(0)
    val total         = explorer.getTransactionNum(incomeAddress)
    val needToProcess =
      if ((total - processed) >= AdminConfigs.maxTxToStat) AdminConfigs.maxTxToStat else total - processed
    val txs = explorer.getTransactionsByAddress(incomeAddress, needToProcess, total - processed - needToProcess)
    logger.info(s"we will process $needToProcess transactions, got ${txs.size} transactions")
    var txInd = 1
    txs.reverse.foreach { tx =>
      try
        processTransaction(tx, processed + txInd)
      catch {
        case a: Throwable =>
          daoUtils.awaitResult(incomeStateDAO.insert(IncomeState(processed + txInd, tx.id, 0)))
          logger.error(s"Admin: An error occurred while processing tx ${tx.id}. Stacktrace below")
          logger.error(ergoMixerUtils.getStackTraceStr(a))
      }
      txInd += 1
    }
    if (total > processed + needToProcess) scanEntryTxs() else processInvalidTxs()
  }

  def processInvalidTxs(): Unit = {
    val invalidStats = daoUtils.awaitResult(incomeStateDAO.getStates(-1))
    invalidStats.foreach { stat =>
      val tx = explorer.getTransaction(stat.txId)
      if (tx.isDefined) {
        try
          processTransaction(tx.get, stat.orderNum, retried = true)
        catch {
          case a: Throwable =>
            daoUtils.awaitResult(
              incomeStateDAO.updateRetryNum(IncomeState(stat.orderNum, stat.txId, stat.retryNum + 1))
            )
            logger.error(
              s"Admin: An error occurred while processing tx ${tx.get.id} for ${stat.retryNum + 1} time. Stacktrace below"
            )
            logger.error(ergoMixerUtils.getStackTraceStr(a))
        }
      }
    }
  }

  /**
   * Process a TX as an Income tx
   * @param tx tx information
   * @param orderNum order of tx between income txs
   * @param retried number of retries for process a tx
   */
  def processTransaction(tx: SpendTx, orderNum: Int, retried: Boolean = false): Unit = {
    var processedObj: Option[(Option[TokenIncome], Option[CommissionIncome], Option[CommissionIncome])] = Option.empty
    if (tx.outboxes.head.address == networkUtils.tokenErgoMix.get.halfMixAddress.toString) {
      processedObj = Option(
        processIncomeBoxes(tx.timestamp, tx.inboxes(1), tx.outboxes(2), tx.outboxes(1), tx.outboxes.head)
      )
      logger.info(s"processed tx ${tx.id} with order num $orderNum")
    } else if (tx.outboxes.head.address == networkUtils.tokenErgoMix.get.fullMixAddress.toString) {
      processedObj = Option(
        processIncomeBoxes(tx.timestamp, tx.inboxes(2), tx.outboxes(3), tx.outboxes(2), tx.outboxes.head)
      )
      logger.info(s"processed tx ${tx.id} with order num $orderNum")
    } else logger.info(s"doesn't need process tx ${tx.id} with order num $orderNum")
    if (processedObj.isDefined) {
      val processed = processedObj.get
      daoUtils.awaitResult(
        allAdminDAO.updateStats(
          IncomeState(orderNum, tx.id),
          processed._1.get,
          processed._2.get,
          processed._3,
          retried
        )
      )
    } else daoUtils.awaitResult(incomeStateDAO.insertOrUpdate(IncomeState(orderNum, tx.id), retried))
  }

  /**
   * @param timestamp
   * @param tokenInBox
   * @param tokenOutBox
   * @param incomeBox
   * @param mixBox
   * @return (Option[TokenIncome] -> Token Income, Option[CommissionIncome] -> Erg Commission, Option[CommissionIncome] -> Token Commission)
   */
  def processIncomeBoxes(
    timestamp: Long,
    tokenInBox: InBox,
    tokenOutBox: OutBox,
    incomeBox: OutBox,
    mixBox: OutBox
  ): (Option[TokenIncome], Option[CommissionIncome], Option[CommissionIncome]) = {
    val defToken = new ErgoToken("", 0L)

    val inTokenBox     = explorer.getBoxById(tokenInBox.id)
    val tokenRegisters = inTokenBox.getRegs

    val tokenPrices = tokenRegisters.head.getValue.asInstanceOf[Coll[(Int, Long)]].toMap
    val rate        = tokenRegisters(1).getValue.asInstanceOf[Int]

    val tokenBatch      = inTokenBox.tokens.head.getValue - tokenOutBox.tokens.head.getValue
    val tokenBatchPrice = tokenPrices(tokenBatch.toInt)

    val ergRing       = mixBox.amount
    val mixingTokenId = mixBox.tokens.lift(1).getOrElse(defToken).getId.toString
    val tokenRing     = mixBox.tokens.lift(1).getOrElse(defToken).getValue

    val ergCommission = ergRing / rate
    assert(incomeBox.amount - tokenBatchPrice >= ergCommission)
    val ergExcess = incomeBox.amount - tokenBatchPrice - ergCommission

    val tokenCommission = tokenRing / rate
    assert(incomeBox.tokens.headOption.getOrElse(defToken).getValue >= tokenCommission)
    val tokenExcess = incomeBox.tokens.headOption.getOrElse(defToken).getValue - tokenCommission

    val tokenIncomeObj = Some(
      TokenIncome(ergoMixerUtils.getBegOfDayTimestamp(timestamp), tokenBatch.toInt, 1, tokenBatchPrice)
    )
    val ergCommissionIncomeObj = Some(CommissionIncome(timestamp, "", ergRing, 1, ergCommission, ergExcess))
    val tokenCommissionIncomeObj =
      if (mixingTokenId.nonEmpty)
        Some(CommissionIncome(timestamp, mixingTokenId, tokenRing, 1, tokenCommission, tokenExcess))
      else Option.empty

    (tokenIncomeObj, ergCommissionIncomeObj, tokenCommissionIncomeObj)
  }

  /**
   * calculate incomes between start and end time
   * @param start (timestamp) start of a period time
   * @param end (timestamp) end of a period time
   * @return (json of commission incomes, json of token selling)
   */
  def getIncome(start: Long, end: Long): (Json, Json) = {
    val tokenSellingIncome = daoUtils.awaitResult(tokenIncomeDAO.getTokenSellingIncome(start, end))

    val tokenSellingJson = Json.obj(
      "levelStat" -> tokenSellingIncome._1.asJson,
      "total"     -> tokenSellingIncome._2.asJson
    )

    val commissionIncome     = daoUtils.awaitResult(commissionIncomeDAO.getCommissionIncome(start, end))
    var commissionIncomeJson = Json.Null
    commissionIncome.foreach { obj =>
      commissionIncomeJson = commissionIncomeJson.deepMerge(
        Json.obj(
          obj._1 ->
            Json.obj(
              "ringStat"        -> obj._2._1.asJson,
              "totalCommission" -> obj._2._2.asJson,
              "totalDonation"   -> obj._2._3.asJson,
              "totalEntered"    -> obj._2._4.asJson
            )
        )
      )
    }

    (commissionIncomeJson, tokenSellingJson)
  }
}
