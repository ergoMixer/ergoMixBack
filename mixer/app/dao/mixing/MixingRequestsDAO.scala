package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.Request.{MixRequest, MixingRequest}
import models.Status.MixStatus
import models.Status.MixStatus.{Complete, Queued}
import models.Status.MixWithdrawStatus.Withdrawn
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait MixingRequestsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  implicit def MixStatus2StringMapper =
    MappedColumnType.base[MixStatus, String](
      ms => ms.value,
      s => MixStatus.fromString(s)
    )

  implicit def BigInt2StringMapper =
    MappedColumnType.base[BigInt, String](
      bi => bi.toString,
      s => BigInt(s)
    )

  class MixingRequestsTable(tag: Tag) extends Table[MixingRequest](tag, "MIXING_REQUESTS") {
    def mixId = column[String]("MIX_ID", O.PrimaryKey)

    def groupId = column[String]("MIX_GROUP_ID")

    def amount = column[Long]("AMOUNT")

    def numRounds = column[Int]("NUM_ROUNDS")

    def mixStatus = column[MixStatus]("STATUS")

    def createdTime = column[Long]("CREATED_TIME")

    def withdrawAddress = column[String]("WITHDRAW_ADDRESS")

    def depositAddress = column[String]("DEPOSIT_ADDRESS")

    def depositCompleted = column[Boolean]("DEPOSIT_COMPLETED")

    def neededAmount = column[Long]("NEEDED_AMOUNT")

    def numToken = column[Int]("NUM_TOKEN")

    def withdrawStatus = column[String]("WITHDRAW_STATUS")

    def mixingTokenAmount = column[Long]("MIXING_TOKEN_AMOUNT")

    def neededTokenAmount = column[Long]("MIXING_TOKEN_NEEDED")

    def tokenId = column[String]("TOKEN_ID")

    def masterKey = column[BigInt]("MASTER_SECRET")

    def * = (
      mixId,
      groupId,
      amount,
      numRounds,
      mixStatus,
      createdTime,
      withdrawAddress,
      depositAddress,
      depositCompleted,
      neededAmount,
      numToken,
      withdrawStatus,
      mixingTokenAmount,
      neededTokenAmount,
      tokenId,
      masterKey
    ) <> (MixingRequest.tupled, MixingRequest.unapply)

    def mixRequest = (
      mixId,
      groupId,
      amount,
      numRounds,
      mixStatus,
      createdTime,
      withdrawAddress,
      depositAddress,
      depositCompleted,
      neededAmount,
      numToken,
      withdrawStatus,
      mixingTokenAmount,
      neededTokenAmount,
      tokenId
    ) <> (MixRequest.tupled, MixRequest.unapply)
  }

}

@Singleton()
class MixingRequestsDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends MixingRequestsComponent
  with MixStateComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val mixingRequests = TableQuery[MixingRequestsTable]

  val mixes = TableQuery[MixStateTable]

  /**
   * returns all requests
   */
  def all: Future[Seq[MixingRequest]] = db.run(mixingRequests.result)

  /**
   * deletes all of requests
   */
  def clear: Future[Unit] = db.run(mixingRequests.delete).map(_ => ())

  /**
   * inserts a request into MIXING_REQUESTS table
   *
   * @param req MixingRequest
   */
  def insert(req: MixingRequest): Future[Unit] = db.run(mixingRequests += req).map(_ => ())

  /**
   * selects request (without masterKey) by mixID
   *
   * @param mixID String
   */
  def selectByMixId(mixID: String): Future[Option[MixRequest]] = {
    val query = for {
      req <- mixingRequests if req.mixId === mixID
    } yield req.mixRequest
    db.run(query.result.headOption)
  }

  /**
   * selects request (with masterKey) by mixID
   *
   * @param mixID String
   */
  def selectAllByMixId(mixID: String): Future[Option[MixingRequest]] =
    db.run(mixingRequests.filter(req => req.mixId === mixID).result.headOption)

  /**
   * selects all requests (without masterKey) by groupId
   *
   * @param groupId String
   */
  def selectByMixGroupId(groupId: String): Future[Seq[MixRequest]] = {
    val query = for {
      req <- mixingRequests if req.groupId === groupId
    } yield req.mixRequest
    db.run(query.result)
  }

  /**
   * selects depositAddress, neededAmount and mixingTokenAmount of requests by groupId
   *
   * @param groupId String
   */
  def selectPartsByMixGroupId(groupId: String): Future[Seq[(String, Long, Long)]] = {
    val query = for {
      req <- mixingRequests if req.groupId === groupId
    } yield (req.depositAddress, req.neededAmount, req.neededTokenAmount)
    db.run(query.result)
  }

  /**
   * selects masterKey by mixID
   *
   * @param mixID String
   */
  def selectMasterKey(mixID: String): Future[Option[BigInt]] = {
    val query = for {
      req <- mixingRequests if req.mixId === mixID
    } yield req.masterKey
    db.run(query.result.headOption)
  }

  /**
   * selects requests by mixGroupId and withdrawStatus
   *
   * @param groupId String
   * @param status String
   */
  def selectAllByWithdrawStatus(groupId: String, status: String): Future[Seq[MixRequest]] = {
    val query = for {
      req <- mixingRequests if req.groupId === groupId && req.withdrawStatus === status
    } yield req.mixRequest
    db.run(query.result)
  }

  /**
   * selects active requests by mixGroupId
   *
   * @param groupId String
   * @param withdrawnValue String
   */
  def selectActiveRequests(groupId: String, withdrawnValue: String): Future[Seq[MixRequest]] = {
    val query = for {
      req <- mixingRequests if req.groupId === groupId && req.withdrawStatus =!= withdrawnValue
    } yield req.mixRequest
    db.run(query.result)
  }

  /**
   * selects requests by mixGroupId and withdrawStatus and mixStatus
   *
   * @param groupId String
   * @param mixStatus String
   * @param withdrawStatus String
   */
  def selectAllByMixAndWithdrawStatus(
    groupId: String,
    mixStatus: MixStatus,
    withdrawStatus: String
  ): Future[Seq[MixRequest]] = {
    val query = for {
      req <- mixingRequests
      if req.groupId === groupId && req.mixStatus === mixStatus && req.withdrawStatus === withdrawStatus
    } yield req.mixRequest
    db.run(query.result)
  }

  /**
   * selects new mix queue
   */
  def selectAllQueued: Future[Seq[MixingRequest]] = db.run(
    mixingRequests
      .filter(req => req.mixStatus === MixStatus.fromString(Queued.value) && req.depositCompleted === true)
      .result
  )

  /**
   * selects progress of a request by mixGroupId and mixStatus
   *
   * @param groupId String
   * @param mixStatus String
   */
  def groupRequestsProgress(groupId: String, mixStatus: MixStatus): Future[Seq[(Int, Int)]] = {
    val query = for {
      (request, state) <- mixingRequests
                            .filter(req => req.groupId === groupId && req.mixStatus === mixStatus)
                            .join(mixes)
                            .on(_.mixId === _.mixId)
    } yield (request.numRounds, state.round)
    db.run(query.result)
  }

  /**
   * updates address by mixID
   *
   * @param mixID String
   * @param address String
   */
  def updateAddress(mixID: String, address: String): Future[Unit] = {
    val query = for {
      req <- mixingRequests if req.mixId === mixID
    } yield req.withdrawAddress
    db.run(query.update(address)).map(_ => ())
  }

  /**
   * returns a query for updating withdraw status by mixID
   *
   * @param mixId String
   * @param status String
   */
  def updateQueryWithMixId(mixId: String, status: String) =
    mixingRequests.filter(req => req.mixId === mixId).map(_.withdrawStatus).update(status)

  /**
   * updates withdraw status by mixID
   *
   * @param mixID String
   * @param withdrawStatus String
   */
  def updateWithdrawStatus(mixID: String, withdrawStatus: String): Future[Unit] = {
    val query = for {
      req <- mixingRequests if req.mixId === mixID
    } yield req.withdrawStatus
    db.run(query.update(withdrawStatus)).map(_ => ())
  }

  /**
   * updates mix status by mixID
   *
   * @param mixID String
   * @param mixStatus String
   */
  def updateMixStatus(mixID: String, mixStatus: MixStatus): Future[Unit] = {
    val query = for {
      req <- mixingRequests if req.mixId === mixID
    } yield req.mixStatus
    db.run(query.update(mixStatus)).map(_ => ())
  }

  /**
   * updates request to withdrawn
   *
   * @param mixID String
   */
  def withdrawTheRequest(mixID: String): Future[Unit] = {
    val query = for {
      req <- mixingRequests if req.mixId === mixID
    } yield (req.mixStatus, req.withdrawStatus)
    db.run(query.update((Complete, Withdrawn.value))).map(_ => ())
  }

  /**
   * counts number of withdrawn requests with mixGroupID
   *
   * @param groupId String
   */
  def countWithdrawn(groupId: String): Future[Int] =
    db.run(mixingRequests.filter(req => req.groupId === groupId && req.withdrawStatus === Withdrawn.value).size.result)

  /**
   * counts number of requests in group that didn't withdrawn yet by mixGroupID
   *
   * @param groupId String
   */
  def countNotWithdrawn(groupId: String): Future[Int] =
    db.run(mixingRequests.filter(req => req.groupId === groupId && req.withdrawStatus =!= Withdrawn.value).size.result)

  /**
   * counts number of finished requests with mixGroupID
   *
   * @param groupId String
   */
  def countFinished(groupId: String): Future[Int] = db.run(
    mixingRequests
      .filter(req => req.groupId === groupId && req.mixStatus === MixStatus.fromString(Complete.value))
      .size
      .result
  )

  /**
   * counts number requests with mixGroupID
   *
   * @param groupId String
   */
  def countAll(groupId: String): Future[Int] = db.run(mixingRequests.filter(req => req.groupId === groupId).size.result)

  /**
   * deletes request by withdrawAddress
   *
   * @param address String
   */
  def deleteByWithdrawAddress(address: String): Future[Unit] =
    db.run(mixingRequests.filter(req => req.withdrawAddress === address).delete).map(_ => ())

  /**
   * deletes request by depositAddress
   *
   * @param address String
   */
  def deleteByDepositAddress(address: String): Future[Unit] =
    db.run(mixingRequests.filter(req => req.depositAddress === address).delete).map(_ => ())

  /**
   * deletes request by mixId
   *
   * @param mixId String
   */
  def delete(mixId: String): Future[Unit] =
    db.run(mixingRequests.filter(req => req.mixId === mixId).delete).map(_ => ())

  /**
   * deletes request by mixId
   *
   * @param groupId String
   */
  def deleteByGroupId(groupId: String): Future[Unit] =
    db.run(mixingRequests.filter(req => req.groupId === groupId).delete).map(_ => ())

  /**
   * returns non-completed deposits
   */
  def nonCompletedDeposits: Future[Seq[MixingRequest]] =
    db.run(mixingRequests.filter(req => req.depositCompleted === false).result)

  /**
   * checks if the address exists in table or not
   *
   * @param address String
   */
  def existsByDepositAddress(address: String): Future[Boolean] =
    db.run(mixingRequests.filter(req => req.depositAddress === address).exists.result)

  /**
   * sets depositCompleted to true by address
   *
   * @param address String
   */
  def updateDepositCompleted(address: String): Future[Unit] = {
    val query = for {
      req <- mixingRequests if req.depositAddress === address
    } yield req.depositCompleted
    db.run(query.update(true)).map(_ => ())
  }

  /**
   * checks if the mix request mixes erg or token
   *
   * @param mixId String
   */
  def isMixingErg(mixId: String): Future[Boolean] =
    db.run(mixingRequests.filter(req => req.mixId === mixId && req.tokenId === "").exists.result)

}
