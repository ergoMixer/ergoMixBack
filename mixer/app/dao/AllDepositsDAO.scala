package dao

import javax.inject.{Inject, Singleton}
import models.Models.Deposit
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class AllDepositsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends UnspentDepositsComponent with SpentDepositsComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val unspentDeposits = TableQuery[UnspentDepositsTable]

    val spentDeposits = TableQuery[SpentDepositsTable]

    /**
     * selects unspent and spent deposits by address
     *
     * @param address String
     */
    def knownIds(address: String): Future[Seq[String]] = {
        val query = for {
            unspent <- unspentDeposits.filter(deposit => deposit.address === address).result
            spent <- spentDeposits.filter(deposit => deposit.address === address).result
            all <- DBIO.successful(unspent.map(_.boxId) ++ spent.map(_.boxId))
        } yield all
        db.run(query)
    }
}
