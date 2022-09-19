package dao

import javax.inject.{Inject, Singleton}
import models.Transaction.WithdrawTx
import models.Status.MixWithdrawStatus.{AgeUSDRequested, HopRequested, UnderHop, WithdrawRequested}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait WithdrawComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class WithdrawTable(tag: Tag) extends Table[WithdrawTx](tag, "WITHDRAW") {
        def mixId = column[String]("MIX_ID", O.PrimaryKey)

        def txId = column[String]("TX_ID")

        def createdTime = column[Long]("CREATED_TIME")

        def boxId = column[String]("BOX_ID")

        def tx = column[Array[Byte]]("TX")

        def additionalInfo = column[String]("ADDITIONAL_INFO")

        def * = (mixId, txId, createdTime, boxId, tx, additionalInfo) <> (WithdrawTx.tupled, WithdrawTx.unapply)
    }

    class WithdrawArchivedTable(tag: Tag) extends Table[(String, String, Long, String, Array[Byte], String, String)](tag, "WITHDRAW_ARCHIVED") {
        def mixId = column[String]("MIX_ID")

        def txId = column[String]("TX_ID")

        def createdTime = column[Long]("CREATED_TIME")

        def boxId = column[String]("FULL_MIX_BOX_ID")

        def tx = column[Array[Byte]]("TX")

        def additionalInfo = column[String]("ADDITIONAL_INFO")

        def reason = column[String]("REASON")

        def * = (mixId, txId, createdTime, boxId, tx, additionalInfo, reason)
    }

}

@Singleton()
class WithdrawDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, mixingRequestsDAO: MixingRequestsDAO, daoUtils: DAOUtils)(implicit executionContext: ExecutionContext)
    extends WithdrawComponent with MixingRequestsComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val withdraws = TableQuery[WithdrawTable]

    val withdrawsArchive = TableQuery[WithdrawArchivedTable]

    val mixingRequests = TableQuery[MixingRequestsTable]

    /**
     * inserts a mix state into MIX_STATE table
     *
     * @param tx WithdrawTx
     */
    def insert(tx: WithdrawTx): Future[Unit] = db.run(withdraws += tx).map(_ => ())

    /**
     * returns all withdraws
     *
     */
    def all: Future[Seq[WithdrawTx]] = db.run(withdraws.result)

    /**
     * deletes all of withdraws
     *
     */
    def clear: Future[Unit] = db.run(withdraws.delete).map(_ => ())

    /**
     * selects withdraw by mixID
     *
     * @param mixID String
     */
    def selectByMixId(mixID: String): Future[Option[WithdrawTx]] = db.run(withdraws.filter(tx => tx.mixId === mixID).result.headOption)

    /**
     * inserts withdraw into withdraw and withdrawArchived tables and updates mixingRequests table
     * checks if the txId exists in table or not
     *
     * @param txId String
     */
    def existsByTxId(txId: String): Future[Boolean] = db.run(withdraws.filter(tx => tx.txId === txId).exists.result)

    /**
     * updates withdraw in withdraw and withdrawArchived tables and updates mixingRequests table by mixId
     *
     * @param new_withdraw WithdrawTx
     * @param withdraw_stat String
     */
    def updateById(new_withdraw: WithdrawTx, withdraw_stat: String)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        withdraws.filter(withdraw => withdraw.mixId === new_withdraw.mixId).delete,
        withdraws += new_withdraw,
        withdrawsArchive += (new_withdraw.mixId, new_withdraw.txId, new_withdraw.time, new_withdraw.boxId, new_withdraw.txBytes, new_withdraw.additionalInfo, insertReason),
        mixingRequestsDAO.updateQueryWithMixId(new_withdraw.mixId, withdraw_stat)
    ))

    /**
     * updates withdraw in withdraw and withdrawArchived tables
     *
     * @param new_withdraw WithdrawTx
     */
    def insertAndArchive(new_withdraw: WithdrawTx)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        withdraws.filter(withdraw => withdraw.mixId === new_withdraw.mixId).delete,
        withdraws += new_withdraw,
        withdrawsArchive += (new_withdraw.mixId, new_withdraw.txId, new_withdraw.time, new_withdraw.boxId, new_withdraw.txBytes, new_withdraw.additionalInfo, insertReason)
    ))

    /**
     * selects withdraw requests
     *
     */
    def getWithdrawals: Future[(Seq[WithdrawTx], Seq[WithdrawTx], Seq[WithdrawTx])] = {
        val query = for {
            withdraw <- (mixingRequests.filter(req => req.withdrawStatus === WithdrawRequested.value || req.withdrawStatus === UnderHop.value) join withdraws on(_.mixId === _.mixId)).map(_._2).result
            mint <- (mixingRequests.filter(req => req.withdrawStatus === AgeUSDRequested.value) join withdraws on(_.mixId === _.mixId)).map(_._2).result
            hop <- (mixingRequests.filter(req => req.withdrawStatus === HopRequested.value) join withdraws on(_.mixId === _.mixId)).map(_._2).result
        } yield (withdraw, mint, hop)
        db.run(query)
    }

    /**
     * selects withdraw requests (only mintingTxs)
     *
     */
    def getMintings: Future[Seq[WithdrawTx]] = {
        val query = for {
            mint <- (mixingRequests.filter(req => req.withdrawStatus === AgeUSDRequested.value) join withdraws on(_.mixId === _.mixId)).map(_._2).result
        } yield mint
        db.run(query)
    }

    /**
     * delete withdraw request by mixId
     *
     * @param mixId String
     */
    def delete(mixId: String): Future[Unit] = db.run(withdraws.filter(withdraw => withdraw.mixId === mixId).delete).map(_ => ())

    /**
     * delete withdraw request by mixId
     *
     * @param mixId String
     */
    def deleteWithArchive(mixId: String): Future[Unit] = db.run(DBIO.seq(
        withdraws.filter(withdraw => withdraw.mixId === mixId).delete,
        withdrawsArchive.filter(withdraw => withdraw.mixId === mixId).delete
    ))

    /**
     * returns true if mixId is not in table or the corresponding mix does not contain boxId
     *
     * @param mixId String
     * @param boxId String
     */
    def shouldWithdraw(mixId: String, boxId: String)(implicit insertReason: String): Boolean = {
        val withdrawMix = daoUtils.awaitResult(db.run(withdraws.filter(withdraw => withdraw.mixId === mixId).result.headOption)).getOrElse(return true)
        !withdrawMix.boxId.contains(boxId)
    }

    /**
     * selects createdTime of withdraw by mixID
     *
     * @param mixID String
     */
    def selectCreatedTimeByMixId(mixID: String): Future[Option[Long]] = db.run(withdraws.filter(tx => tx.mixId === mixID).map(_.createdTime).result.headOption)
}
