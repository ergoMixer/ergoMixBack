package dao

import config.AdminConfigs
import models.Admin.IncomeState

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait IncomeStateComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._
    class IncomeStateTable(tag: Tag) extends Table[IncomeState](tag, "INCOME_STATE") {
        def txId = column[String]("TX_ID", O.PrimaryKey)

        def orderNum = column[Int]("ORDER_NUM")

        def retryNum = column[Int]("RETRY_NUM")

        def * = (orderNum, txId, retryNum) <> (IncomeState.tupled, IncomeState.unapply)
    }

}

@Singleton()
class IncomeStateDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends IncomeStateComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val incomeStates = TableQuery[IncomeStateTable]

    /**
     * return last income order
     */
    def getLastOrder: Future[Option[Int]] = db.run(incomeStates.map(_.orderNum).max.result)

    /**
     * insert a processed state
     */
    def insert(state: IncomeState): Future[Unit] = {
        db.run(incomeStates += state).map(_ => ())
    }

    /**
     * Update retryNum field to Input income state or change to -1 if reached the maxRetryToCalStat
     * @param state
     */
    def updateRetryNum(state: IncomeState): Future[Unit] = {
        db.run(incomeStates.filter(_.txId === state.txId).map(_.retryNum).update(
            if (state.retryNum >= AdminConfigs.maxRetryToCalStat) -1 else state.retryNum
        )).map(_ => ())
    }

    /**
     * deletes states according validity
     * @param validState 0 = all states | 1 = valid | -1 = invalid
     */
    def getStates(validState: Int = 0): Future[Seq[IncomeState]] = {
        var query: Option[DBIO[Seq[IncomeState]]] = Option.empty[DBIO[Seq[IncomeState]]]
        if (validState == 0) {
            query = Some(incomeStates.result)
        } else if (validState == 1) {
            query = Some(incomeStates.filter(_.retryNum === -1).result)
        } else
            query = Some(incomeStates.filter(_.retryNum >= 0).result)
        db.run(query.get)
    }

    /**
     * insert a state and remove valid states if doesn't exist in table before that  or update state if exist
     * @param state
     * @param retried
     * @return
     */
    def insertOrUpdate(state: IncomeState, retried: Boolean = false): Future[Unit] = {
        val query = for {
            _ <- if (!retried) incomeStates.filter(_.retryNum === -1).delete else DBIO.seq()
            stateQ <- DBIO.successful(incomeStates.filter(obj => obj.txId === state.txId))
            stateOption <- stateQ.result.headOption
            _ <- if (stateOption.isEmpty)
                incomeStates += state
            else {
                stateQ.map(obj => (obj.orderNum, obj.retryNum)).update((
                  state.orderNum,
                  state.retryNum
                ))
            }
        } yield { }

        db.run(query)
    }
}
