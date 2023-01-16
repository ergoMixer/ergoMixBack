package dao

import models.Admin.TokenIncome

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import wallet.WalletHelper

import scala.concurrent.{ExecutionContext, Future}

trait TokenIncomeComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class TokenIncomeTable(tag: Tag) extends Table[TokenIncome](tag, "TOKEN_INCOME") {
        def mixingLevel = column[Int]("MIXING_LEVEL")

        def numEntered = column[Int]("NUM_ENTERED")

        def amount = column[Long]("AMOUNT")

        def timestamp = column[Long]("TIMESTAMP")

        def * = (timestamp, mixingLevel, numEntered, amount) <> (TokenIncome.tupled, TokenIncome.unapply)

        def pk = primaryKey("pk_TOKEN", (timestamp, mixingLevel))
    }
}

@Singleton()
class TokenIncomeDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends TokenIncomeComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val tokenIncomes = TableQuery[TokenIncomeTable]

    /**
     * get token incomes between start and end time
     * @param start
     * @param end
     * @return
     */
    def getTokenSellingIncome(start: Long, end: Long): Future[(Map[Int, Int], Long)] = {
        val realEnd = if (end == 0) WalletHelper.now else end
        val query = for {
            tokenIncomeQ <- tokenIncomes.filter(token => { token.timestamp >= start && token.timestamp <= realEnd }).result
            levelStat <- DBIO.successful(tokenIncomeQ.groupBy(_.mixingLevel).mapValues(_.map(_.numEntered).sum))
            income <- DBIO.successful(tokenIncomeQ.map(_.amount).sum)
        } yield (levelStat, income)
        db.run(query)
    }
}
