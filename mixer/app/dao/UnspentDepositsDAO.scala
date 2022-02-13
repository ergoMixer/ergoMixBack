package dao

import javax.inject.{Inject, Singleton}
import models.Models.Deposit
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait UnspentDepositsComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class UnspentDepositsTable(tag: Tag) extends Table[Deposit](tag, "UNSPENT_DEPOSITS") {
        def address = column[String]("ADDRESS")

        def boxId = column[String]("BOX_ID", O.PrimaryKey)

        def amount = column[Long]("AMOUNT")

        def createdTime = column[Long]("CREATED_TIME")

        def tokenAmount = column[Long]("MIXING_TOKEN_AMOUNT")

        def * = (address, boxId, amount, createdTime, tokenAmount) <> (Deposit.tupled, Deposit.unapply)
    }

    class UnspentDepositsArchivedTable(tag: Tag) extends Table[(String, String, Long, Long, Long, String)](tag, "UNSPENT_DEPOSITS_ARCHIVED") {
        def address = column[String]("ADDRESS")

        def boxId = column[String]("BOX_ID", O.PrimaryKey)

        def amount = column[Long]("AMOUNT")

        def createdTime = column[Long]("CREATED_TIME")

        def tokenAmount = column[Long]("MIXING_TOKEN_AMOUNT")

        def reason = column[String]("REASON")

        def * = (address, boxId, amount, createdTime, tokenAmount, reason)
    }

}

@Singleton()
class UnspentDepositsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends UnspentDepositsComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val unspentDeposits = TableQuery[UnspentDepositsTable]

    val unspentDepositsArchive = TableQuery[UnspentDepositsArchivedTable]

    /**
    * checks if the boxId exists in table or not
    *
    * @param boxId String
    */
    def existsByBoxId(boxId: String): Future[Boolean] = db.run(unspentDeposits.filter(deposit => deposit.boxId === boxId).exists.result)

    /**
    * selects currentSum and tokenAmount of the deposits by their address
    *
    * @param address String
    */
    def amountByAddress(address: String): Future[(Option[Long], Option[Long])] = {
        val query = for {
            amount <- unspentDeposits.filter(deposit => deposit.address === address).map(_.amount).sum.result
            tokenAmount <- unspentDeposits.filter(deposit => deposit.address === address).map(_.tokenAmount).sum.result
        } yield (amount, tokenAmount)
        db.run(query)
    }

    /**
     * selects the deposits by address
     *
     * @param address String
     */
    def selectByAddress(address: String): Future[Seq[Deposit]] = db.run(unspentDeposits.filter(deposit => deposit.address === address).result)

    /**
     * deletes the deposit by boxId
     *
     * @param boxId String
     */
    def delete(boxId: String): Future[Unit] = db.run(unspentDeposits.filter(deposit => deposit.boxId === boxId).delete).map(_ => ())

    /**
     * deletes the deposit by deposit address
     *
     * @param address String
     */
    def deleteByAddress(address: String): Future[Unit] = db.run(DBIO.seq(
        unspentDeposits.filter(deposit => deposit.address === address).delete,
        unspentDepositsArchive.filter(deposit => deposit.address === address).delete
    ))

    /**
     * inserts a deposit into unspentDeposits and unspentDepositsArchived tables
     *
     * @param deposit Deposit
     */
    def insertDeposit(deposit: Deposit)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        unspentDeposits += deposit,
        unspentDepositsArchive += (deposit.address, deposit.boxId, deposit.amount, deposit.createdTime, deposit.tokenAmount, insertReason)
    ))
}
