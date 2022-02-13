package dao

import javax.inject.{Inject, Singleton}
import models.Models.DistributeTx
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait DistributeTransactionComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class DistributeTransactionTable(tag: Tag) extends Table[DistributeTx](tag, "DISTRIBUTE_TRANSACTIONS") {
        def mixGroupId = column[String]("MIX_GROUP_ID")

        def txId = column[String]("TX_ID", O.PrimaryKey)

        def order = column[Int]("ORDER_NUM")

        def time = column[Long]("CREATED_TIME")

        def txBytes = column[Array[Byte]]("TX")

        def inputs = column[String]("INPUTS")

        def * = (mixGroupId, txId, order, time, txBytes, inputs) <> (DistributeTx.tupled, DistributeTx.unapply)
    }

}

@Singleton()
class DistributeTransactionsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends DistributeTransactionComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val distributeTransactions = TableQuery[DistributeTransactionTable]

    /**
     * inserts a transaction into DISTRIBUTE_TRANSACTIONS table
     *
     * @param tx DistributeTx
     */
    def insert(tx: DistributeTx): Future[Unit] = db.run(distributeTransactions += tx).map(_ => ())

    /**
     * selects spent transactions inputs by covertId
     *
     * @param covertId String
     */
    def selectSpentTransactionsInputs(covertId: String): Future[Seq[String]] = {
        val query = for {
            tx <- distributeTransactions if tx.mixGroupId === covertId && tx.order > 0
        } yield tx.inputs
        db.run(query.result)
    }

    /**
     * selects spent transactions by covertId
     *
     * @param covertId String
     */
    def selectSpentTransactions(covertId: String): Future[Seq[DistributeTx]] = db.run(distributeTransactions.filter(tx => tx.mixGroupId === covertId && tx.order > 0).result)

    /**
     * selects transactions by mixGroupId
     *
     * @param mixGroupId String
     */
    def selectByMixGroupId(mixGroupId: String): Future[Seq[DistributeTx]] = db.run(distributeTransactions.filter(tx => tx.mixGroupId === mixGroupId).sortBy(_.order).result)

    /**
     * selects input boxes by mixGroupId
     *
     * @param mixGroupId String
     */
    def selectInputsByMixGroupId(mixGroupId: String): Future[Seq[String]] = db.run(distributeTransactions.filter(tx => tx.mixGroupId === mixGroupId).map(_.inputs).result)

    /**
     * updates order of the transaction to 0 by transactionId
     *
     * @param txId String
     */
    def setOrderToZeroByTxId(txId: String): Future[Unit] = {
        val query = for {
            tx <- distributeTransactions if tx.txId === txId
        } yield tx.order
        db.run(query.update(0)).map(_ => ())
    }

    /**
     * checks if the mixGroupId exists in table or not
     *
     * @param mixGroupId String
     */
    def existsByMixGroupId(mixGroupId: String): Future[Boolean] = db.run(distributeTransactions.filter(req => req.mixGroupId === mixGroupId).exists.result)

    /**
     * selects transactions with 0 chain order by mixGroupId and createdTime
     *
     * @param mixGroupId String
     * @param createdTime Long
     */
    def zeroChainByMixGroupIdAndTime(mixGroupId: String, createdTime: Long): Future[Seq[DistributeTx]] = db.run(distributeTransactions.filter(tx => tx.mixGroupId === mixGroupId && tx.order === 0 && tx.time <= createdTime).result)

    /**
     * deletes transaction by txId
     *
     * @param txId String
     */
    def delete(txId: String): Unit = db.run(distributeTransactions.filter(tx => tx.txId === txId).delete).map(_ => ())

    /**
     * deletes transactions by groupId
     *
     * @param groupId String
     */
    def deleteByGroupId(groupId: String): Unit = db.run(distributeTransactions.filter(tx => tx.mixGroupId === groupId).delete).map(_ => ())
}
