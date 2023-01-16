package dao

import models.Admin.{CommissionIncome, IncomeState, TokenIncome}

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AllAdminDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends CommissionIncomeComponent
    with TokenIncomeComponent
    with IncomeStateComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    private val commissions = TableQuery[CommissionIncomeTable]
    private val tokens = TableQuery[TokenIncomeTable]
    private val incomeStates = TableQuery[IncomeStateTable]

    /**
     * update or insert erg commission income and token commission income and token income
     * @param state
     * @param tokenIncome
     * @param ergCommissionIncome
     * @param tokenCommissionIncomeOption (Optional)
     * @param retried if be true doesn't delete valid income states
     */
    def updateStats(
                     state: IncomeState,
                     tokenIncome: TokenIncome,
                     ergCommissionIncome: CommissionIncome,
                     tokenCommissionIncomeOption: Option[CommissionIncome],
                     retried: Boolean = false
                   ): Future[Unit] = {

        val tokenIncomeQuery = for {
            incomeQ <- DBIO.successful(tokens.filter(obj => obj.timestamp === tokenIncome.timestamp && obj.mixingLevel === tokenIncome.mixingLevel))
            incomeOption <- incomeQ.result.headOption
            _ <- if (incomeOption.isEmpty)
                tokens += tokenIncome
            else {
                val value = incomeOption.get
                incomeQ.map(obj => (obj.numEntered, obj.amount)).update((
                  value.numEntered + tokenIncome.numEntered,
                  value.amount + tokenIncome.amount
                ))
            }
        } yield { }

        val ergCommisionIncomeQuery = for {
            commissionQ <- DBIO.successful(commissions.filter(obj =>
                obj.timestamp === ergCommissionIncome.timestamp && obj.tokenId === ergCommissionIncome.tokenId && obj.ring === ergCommissionIncome.ring
            ))
            commissionOption <- commissionQ.result.headOption
            _ <- if (commissionOption.isEmpty)
                    commissions += ergCommissionIncome
                else {
                    val value = commissionOption.get
                    commissionQ.map(obj => (obj.commission, obj.donation, obj.numEntered)).update((
                      value.commission + ergCommissionIncome.commission,
                      value.donation + ergCommissionIncome.donation,
                      value.numEntered + ergCommissionIncome.numEntered,
                    ))
                }
        } yield { }

        val incomeStateQuery = for {
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

        if (tokenCommissionIncomeOption.isDefined) {
            val tokenCommissionIncome = tokenCommissionIncomeOption.get
            val tokenCommisionIncomeQuery = for {
                commissionQ <- DBIO.successful(commissions.filter(obj =>
                    obj.timestamp === tokenCommissionIncome.timestamp && obj.tokenId === tokenCommissionIncome.tokenId && obj.ring === tokenCommissionIncome.ring
                ))
                commissionOption <- commissionQ.result.headOption
                _ <- if (commissionOption.isEmpty)
                    commissions += tokenCommissionIncome
                else {
                    val value = commissionOption.get
                    commissionQ.map(obj => (obj.commission, obj.donation, obj.numEntered)).update((
                      value.commission + tokenCommissionIncome.commission,
                      value.donation + tokenCommissionIncome.donation,
                      value.numEntered + tokenCommissionIncome.numEntered,
                    ))
                }
            } yield { }
            db.run(DBIO.seq(tokenIncomeQuery ,ergCommisionIncomeQuery, tokenCommisionIncomeQuery, incomeStateQuery).transactionally)
        }
        else {
            db.run(DBIO.seq(tokenIncomeQuery ,ergCommisionIncomeQuery, incomeStateQuery).transactionally)
        }
    }
}
