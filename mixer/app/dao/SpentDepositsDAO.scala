package dao

import models.Models.SpentDeposit

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait SpentDepositsComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class SpentDepositsTable(tag: Tag) extends Table[SpentDeposit](tag, "SPENT_DEPOSITS") {
        def address = column[String]("ADDRESS")

        def boxId = column[String]("BOX_ID", O.PrimaryKey)

        def amount = column[Long]("AMOUNT")

        def createdTime = column[Long]("CREATED_TIME")

        def tokenAmount = column[Long]("MIXING_TOKEN_AMOUNT")

        def txId = column[String]("TX_ID")

        def spentTime = column[Long]("SPENT_TIME")

        def purpose = column[String]("PURPOSE")

        def * = (address, boxId, amount, createdTime, tokenAmount, txId, spentTime, purpose) <> (SpentDeposit.tupled, SpentDeposit.unapply)
    }

    class SpentDepositsArchivedTable(tag: Tag) extends Table[(String, String, Long, Long, Long, String, Long, String, String)](tag, "SPENT_DEPOSITS_ARCHIVED") {
        def address = column[String]("ADDRESS")

        def boxId = column[String]("BOX_ID", O.PrimaryKey)

        def amount = column[Long]("AMOUNT")

        def createdTime = column[Long]("CREATED_TIME")

        def tokenAmount = column[Long]("MIXING_TOKEN_AMOUNT")

        def txId = column[String]("TX_ID")

        def spentTime = column[Long]("SPENT_TIME")

        def purpose = column[String]("PURPOSE")

        def reason = column[String]("REASON")

        def * = (address, boxId, amount, createdTime, tokenAmount, txId, spentTime, purpose, reason)
    }

}

@Singleton()
class SpentDepositsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends SpentDepositsComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val spentDeposits = TableQuery[SpentDepositsTable]

    val spentDepositsArchive = TableQuery[SpentDepositsArchivedTable]

    /**
    * checks if the boxId exists in table or not
    *
    * @param boxId String
    */
    def existsByBoxId(boxId: String): Future[Boolean] = db.run(spentDeposits.filter(deposit => deposit.boxId === boxId).exists.result)

    /**
     * selects by address
     *
     * @param address String
     */
    def selectBoxIdByAddress(address: String): Future[Option[String]] = {
        val query = for {
            deposit <- spentDeposits if deposit.address === address
        } yield deposit.boxId
        db.run(query.result.headOption)
    }

    /**
     * selects by purpose
     *
     * @param purpose String
     */
    def selectBoxIdByPurpose(purpose: String): Future[Option[String]] = {
        val query = for {
            deposit <- spentDeposits if deposit.purpose === purpose
        } yield deposit.boxId
        db.run(query.result.headOption)
    }

    /**
     * deletes the deposit by deposit address
     *
     * @param address String
     */
    def deleteByAddress(address: String): Future[Unit] = db.run(DBIO.seq(
        spentDeposits.filter(deposit => deposit.address === address).delete,
        spentDepositsArchive.filter(deposit => deposit.address === address).delete
    ))

    /**
     * inserts a deposit into spentDeposits and spentDepositsArchived tables
     *
     * @param deposit SpentDeposit
     */
    def insertDeposit(deposit: SpentDeposit)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        spentDeposits += deposit,
        spentDepositsArchive += (deposit.address, deposit.boxId, deposit.amount, deposit.createdTime, deposit.tokenAmount, deposit.txId, deposit.spentTime, deposit.purpose, insertReason)
    ))
}
