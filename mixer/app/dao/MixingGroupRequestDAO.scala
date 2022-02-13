package dao

import javax.inject.{Inject, Singleton}
import models.Models.{MixGroupRequest, GroupMixStatus}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait MixingGroupRequestComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    implicit def BigInt2StringMapper =
        MappedColumnType.base[BigInt, String](
            bi => bi.toString,
            s => BigInt(s)
        )

    class MixingGroupRequestTable(tag: Tag) extends Table[MixGroupRequest](tag, "MIXING_GROUP_REQUEST") {
        def id = column[String]("MIX_GROUP_ID", O.PrimaryKey)

        def neededAmount = column[Long]("AMOUNT")

        def status = column[String]("STATUS")

        def createdTime = column[Long]("CREATED_TIME")

        def depositAddress = column[String]("DEPOSIT_ADDRESS")

        def doneDeposit = column[Long]("DEPOSIT_DONE")

        def tokenDoneDeposit = column[Long]("DEPOSIT_DONE_TOKEN")

        def mixingAmount = column[Long]("MIXING_AMOUNT")

        def mixingTokenAmount = column[Long]("MIXING_TOKEN_AMOUNT")

        def neededTokenAmount = column[Long]("MIXING_TOKEN_NEEDED")

        def tokenId = column[String]("TOKEN_ID")

        def masterKey = column[BigInt]("MASTER_SECRET_GROUP")

        def * = (id, neededAmount, status, createdTime, depositAddress, doneDeposit, tokenDoneDeposit, mixingAmount, mixingTokenAmount, neededTokenAmount, tokenId, masterKey) <> (MixGroupRequest.tupled, MixGroupRequest.unapply)
    }

}

@Singleton()
class MixingGroupRequestDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends MixingGroupRequestComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val groupRequests = TableQuery[MixingGroupRequestTable]

    /**
     * inserts a request into MIXING_GROUP_REQUEST table
     *
     * @param req MixGroupRequest
     */
    def insert(req: MixGroupRequest): Future[Unit] = db.run(groupRequests += req).map(_ => ())

    /**
     * selects all groupRequests
     *
     */
    def all: Future[Seq[MixGroupRequest]] = db.run(groupRequests.result)

    /**
     * deletes all of requests
     *
     */
    def clear: Future[Unit] = db.run(groupRequests.delete).map(_ => ())

    /**
     * selects queued groupRequests
     *
     */
    def queued: Future[Seq[MixGroupRequest]] = db.run(groupRequests.filter(req => req.status === GroupMixStatus.Queued.value).result)

    /**
     * selects starting groupRequests
     *
     */
    def starting: Future[Seq[MixGroupRequest]] = db.run(groupRequests.filter(req => req.status === GroupMixStatus.Starting.value).result)

    /**
     * selects active groupRequests
     *
     */
    def active: Future[Seq[MixGroupRequest]] = db.run(groupRequests.filterNot(req => req.status === GroupMixStatus.Complete.value).result)

    /**
     * selects completed groupRequests
     *
     */
    def completed: Future[Seq[MixGroupRequest]] = db.run(groupRequests.filter(req => req.status === GroupMixStatus.Complete.value).result)

    /**
     * deletes groupRequest by groupId
     *
     * @param groupId String
     */
    def delete(groupId: String): Unit = db.run(groupRequests.filter(req => req.id === groupId).delete).map(_ => ())

    /**
     * TODO: Remove this function later, because it's also in refactor-fullMixer
     * updates deposit and tokenDeposit by mixGroupId
     *
     * @param mixGroupId String
     * @param deposit Long
     * @param tokenDeposit Long
     */
    def updateDepositById(mixGroupId: String, deposit: Long, tokenDeposit: Long): Unit = {
        val query = for {
            request <- groupRequests.filter(req => req.id === mixGroupId)
        } yield (request.doneDeposit, request.tokenDoneDeposit)
        db.run(query.update(deposit, tokenDeposit))
    }

    /**
     * updates deposit and tokenDeposit by mixGroupId
     *
     * @param mixGroupId String
     * @param status String
     */
    def updateStatusById(mixGroupId: String, status: String): Unit = {
        val query = for {
            request <- groupRequests.filter(req => req.id === mixGroupId)
        } yield request.status
        db.run(query.update(status))
    }
}
