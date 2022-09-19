package dao

import javax.inject.{Inject, Singleton}
import models.Transaction.MixTransaction
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait MixTransactionsComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class MixTransactionsTable(tag: Tag) extends Table[MixTransaction](tag, "MIX_TRANSACTIONS") {
        def boxId = column[String]("BOX_ID", O.PrimaryKey)

        def txId = column[String]("TX_ID")

        def tx = column[Array[Byte]]("TX")

        def * = (boxId, txId, tx) <> (MixTransaction.tupled, MixTransaction.unapply)
    }

}

@Singleton()
class MixTransactionsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends MixTransactionsComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val mixTransactions = TableQuery[MixTransactionsTable]

    /**
     * selects mixTransaction by boxId
     *
     * @param boxId String
     */
    def selectByBoxId(boxId: String): Future[Option[MixTransaction]] = db.run(mixTransactions.filter(tx => tx.boxId === boxId).result.headOption)

    /**
     * delete mixTransaction by boxId
     *
     * @param boxId String
     */
    def delete(boxId: String): Future[Unit] = db.run(mixTransactions.filter(tx => tx.boxId === boxId).delete).map(_ => ())

    /**
     * updates mixTransaction by boxId
     *
     * @param tx MixTransaction
     */
    def updateById(tx: MixTransaction)(implicit insertReason: String): Future[Unit] =  db.run(DBIO.seq(
        mixTransactions.filter(mixTx => mixTx.boxId === tx.boxId).delete,
        mixTransactions += tx
    ))
}
