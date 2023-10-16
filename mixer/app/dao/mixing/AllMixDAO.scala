package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import dao.DAOUtils
import models.Models.{Deposit, FullMix, HalfMix, HopMix, MixState}
import models.Status.MixStatus
import models.Status.MixStatus.{Queued, Running}
import models.Status.MixWithdrawStatus.{HopRequested, UnderHop, WithdrawRequested}
import network.BlockExplorer
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.Logger
import slick.jdbc.JdbcProfile

@Singleton()
class AllMixDAO @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  daoUtils: DAOUtils,
  explorer: BlockExplorer
)(implicit executionContext: ExecutionContext)
  extends HalfMixComponent
  with FullMixComponent
  with MixStateComponent
  with MixingRequestsComponent
  with MixStateHistoryComponent
  with MixTransactionsComponent
  with EmissionComponent
  with TokenEmissionComponent
  with UnspentDepositsComponent
  with SpentDepositsComponent
  with HopMixComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._
  private val logger: Logger = Logger(this.getClass)

  val halfMixes = TableQuery[HalfMixTable]

  val fullMixes = TableQuery[FullMixTable]

  val mixes = TableQuery[MixStateTable]

  val mixHistories = TableQuery[MixStateHistoryTable]

  val mixingRequests = TableQuery[MixingRequestsTable]

  val mixTransactions = TableQuery[MixTransactionsTable]

  val emissions = TableQuery[EmissionTable]

  val tokenEmissions = TableQuery[TokenEmissionTable]

  val unspentDeposits = TableQuery[UnspentDepositsTable]

  val spentDeposits = TableQuery[SpentDepositsTable]

  val hopMixes = TableQuery[HopMixTable]

  /**
   * selects unspent halfBoxes
   */
  def groupRequestsProgress: Future[Seq[(String, Int, String, BigInt, String, String)]] = {
    val query = for {
      ((halfMix, state), request) <-
        halfMixes
          .filter(mix => mix.isSpent === false)
          .join(mixes)
          .on((mix, state) => mix.mixId === state.mixId && mix.round === state.round)
          .join(
            mixingRequests.filter(request =>
              request.mixStatus === MixStatus.fromString(Running.value) ||
                (request.withdrawStatus === WithdrawRequested.value || request.withdrawStatus === HopRequested.value)
            )
          )
          .on(_._1.mixId === _.mixId)
    } yield (
      halfMix.mixId,
      state.round,
      halfMix.halfMixBoxId,
      request.masterKey,
      request.withdrawStatus,
      request.withdrawAddress
    )
    db.run(query.result)
  }

  /**
   * selects unspent fullBoxes
   */
  def groupFullMixesProgress
    : Future[Seq[(String, Int, String, BigInt, Boolean, String, Int, String, String, String)]] = {
    val query = for {
      ((fullMix, state), request) <-
        fullMixes
          .join(mixes)
          .on((mix, state) => mix.mixId === state.mixId && mix.round === state.round)
          .join(
            mixingRequests.filter(request =>
              request.mixStatus === MixStatus.fromString(Running.value) ||
                (request.withdrawStatus === WithdrawRequested.value || request.withdrawStatus === HopRequested.value)
            )
          )
          .on(_._1.mixId === _.mixId)
    } yield (
      fullMix.mixId,
      request.numRounds,
      request.withdrawAddress,
      request.masterKey,
      state.isAlice,
      fullMix.fullMixBoxId,
      state.round,
      fullMix.halfMixBoxId,
      request.withdrawStatus,
      request.tokenId
    )
    db.run(query.result)
  }

  /**
   * selects unspent hopBoxes
   */
  def groupHopMixesProgress: Future[Seq[HopMix]] = {
    val lastHops = hopMixes.groupBy(hop => hop.mixId).map { case (id, group) => (id, group.map(_.round).max) }
    val lastHopMixes =
      hopMixes.join(lastHops).on((hopMix, lastHop) => hopMix.mixId === lastHop._1 && hopMix.round === lastHop._2).map {
        case (hopMix, _) => hopMix
      }
    val query = for {
      (hopMix, _) <- lastHopMixes
                       .join(mixingRequests.filter(request => request.withdrawStatus === UnderHop.value))
                       .on(_.mixId === _.mixId)
    } yield hopMix
    db.run(query.result)
  }

  /**
   * Undos mix step for a deposits
   *
   * @param deposit Deposit
   */
  def undoSpend(deposit: Deposit): Unit = {
    logger.info(s"[UndoDeposit ${deposit.address}]")
    try {
      if (daoUtils.awaitResult(db.run(unspentDeposits.filter(depo => depo.boxId === deposit.boxId).exists.result)))
        throw new Exception(s"Unspent deposit already exists ${deposit.boxId}")
      if (explorer.getBoxById(deposit.boxId).spendingTxId.isDefined)
        throw new Exception(s"Cannot undo spend for already spend box ${deposit.boxId}")

      db.run(
        DBIO.seq(
          unspentDeposits += deposit,
          spentDeposits.filter(depo => depo.boxId === deposit.boxId).delete
        )
      )
    } catch {
      case a: Throwable =>
        logger.error(s" [UndoDeposit ${deposit.address}] Error " + a.getMessage)
    }
  }

  /**
   * Undos mix step in deposits
   *
   * @param mixId String
   */
  def undoMixSpends(mixId: String): Unit = {
    val depositQuery = for {
      address <- mixingRequests.filter(req => req.mixId === mixId).map(_.depositAddress).result.headOption
      deposits <-
        spentDeposits
          .filter(deposit => deposit.address === address)
          .map(deposit => (deposit.address, deposit.boxId, deposit.amount, deposit.createdTime, deposit.tokenAmount))
          .result
    } yield deposits
    daoUtils.awaitResult(db.run(depositQuery)).foreach {
      case (address, boxId, amount, createdTime, tokenAmount) =>
        undoSpend(Deposit(address, boxId, amount, tokenAmount, createdTime))
    }
  }

  /**
   * Undos mix step
   *
   * @param mixId String
   * @param round Int
   * @param boxId String
   */
  def undoMixStep(mixId: String, round: Int, boxId: String, isFullMix: Boolean): Unit = {
    val currRound = daoUtils
      .awaitResult(db.run(mixes.filter(mix => mix.mixId === mixId).map(_.round).result.headOption))
      .getOrElse(throw new Exception(s"No entry exists for $mixId in mixStateTable"))
    if (currRound != round) throw new Exception(s"Current round ($currRound) != undo round ($round)")

    val maxRound = daoUtils
      .awaitResult(db.run(mixHistories.filter(history => history.mixId === mixId).map(_.round).max.result))
      .getOrElse(throw new Exception(s"No entry exists for $mixId in mixHistoryTable"))
    if (currRound != maxRound) throw new Exception(s"Current round ($currRound) != max round ($maxRound)")

    db.run(
      DBIO.seq(
        mixHistories.filter(history => history.mixId === mixId && history.round === round).delete,
        mixTransactions.filter(tx => tx.boxId === boxId).delete
      )
    )

    if (round == 0) {
      // delete from mixStateTable
      // delete from mixStateHistoryTable
      // set mixRequest as Queued

      db.run(
        DBIO.seq(
          mixes.filter(mix => mix.mixId === mixId).delete,
          tokenEmissions.filter(box => box.id === mixId).delete,
          emissions.filter(box => box.mixId === mixId && box.round === round).delete,
          mixingRequests.filter(req => req.mixId === mixId).map(_.mixStatus).update(Queued)
        )
      )

      undoMixSpends(mixId)
    } else {
      val prevRound = round - 1
      val prevMixState = daoUtils
        .awaitResult(
          db.run(
            mixHistories.filter(history => history.mixId === mixId && history.round === prevRound).result.headOption
          )
        )
        .getOrElse(throw new Exception(s"No history found for previous round $prevRound and mixId $mixId"))
      db.run(
        DBIO.seq(
          mixes
            .filter(mix => mix.mixId === mixId)
            .map(mix => (mix.round, mix.isAlice))
            .update(prevRound, prevMixState.isAlice),
          emissions.filter(box => box.mixId === mixId && box.round === round).delete
        )
      )
    }

    if (isFullMix)
      db.run(fullMixes.filter(mix => mix.mixId === mixId && mix.round === round).delete)
    else
      db.run(halfMixes.filter(mix => mix.mixId === mixId && mix.round === round).delete)
  }

  /**
   * selects haldMix and fullMix by mixId and round of mixState
   *
   * @param mixId String
   * @param state Option[MixState]
   */
  def selectMixes(mixId: String, state: Option[MixState]): Future[(Option[HalfMix], Option[FullMix])] = {
    val round: Int = state.getOrElse {
      val emptyHalfMix: Option[HalfMix] = None
      val emptyFullMix: Option[FullMix] = None
      return Future(emptyHalfMix, emptyFullMix)
    }.round
    val query = for {
      half <- halfMixes.filter(mix => mix.mixId === mixId && mix.round === round).result.headOption
      full <- fullMixes.filter(mix => mix.mixId === mixId && mix.round === round).result.headOption
    } yield (half, full)
    db.run(query)
  }
}
