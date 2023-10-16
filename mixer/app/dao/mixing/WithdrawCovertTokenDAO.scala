package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.Status.CovertAssetWithdrawStatus
import models.Transaction.CovertAssetWithdrawTx
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait WithdrawCovertTokenComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class WithdrawCovertTokenTable(tag: Tag) extends Table[CovertAssetWithdrawTx](tag, "WITHDRAW_COVERT_TOKEN") {
    def groupId = column[String]("MIX_GROUP_ID")

    def tokenId = column[String]("TOKEN_ID")

    def withdrawAddress = column[String]("WITHDRAW_ADDRESS")

    def createdTime = column[Long]("CREATED_TIME")

    def withdrawStatus = column[String]("WITHDRAW_STATUS")

    def txId = column[String]("TX_ID")

    def tx = column[Array[Byte]]("TX")

    def * = (
      groupId,
      tokenId,
      withdrawAddress,
      createdTime,
      withdrawStatus,
      txId,
      tx
    ) <> (CovertAssetWithdrawTx.tupled, CovertAssetWithdrawTx.unapply)

    def pk = primaryKey("pk_WITHDRAW_COVERT_TOKEN", (groupId, tokenId, createdTime))
  }

}

@Singleton()
class WithdrawCovertTokenDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends WithdrawCovertTokenComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val covertTokenTx = TableQuery[WithdrawCovertTokenTable]

  /**
   * selects non-completed withdraws
   */
  def selectRequestedWithdraws: Future[Seq[CovertAssetWithdrawTx]] =
    db.run(covertTokenTx.filter(token => token.withdrawStatus === CovertAssetWithdrawStatus.Requested.value).result)

  /**
   * selects non-completed withdraws
   */
  def selectNotProcessedWithdraws: Future[Seq[CovertAssetWithdrawTx]] =
    db.run(covertTokenTx.filter(token => token.withdrawStatus === CovertAssetWithdrawStatus.NoWithdrawYet.value).result)

  /**
   * inserts a request for withdraw
   */
  def insert(assetWithdrawRequest: CovertAssetWithdrawTx): Future[Unit] =
    db.run(covertTokenTx += assetWithdrawRequest).map(_ => ())

  /**
   * checks if a non-complete request for withdraw of this asset is already exists or not
   */
  def isActiveRequest(covertId: String, tokenId: String): Future[Boolean] = db.run(
    covertTokenTx
      .filter(token => token.groupId === covertId && token.tokenId === tokenId && token.withdrawStatus =!= "complete")
      .exists
      .result
  )

  /**
   * updates txId and txBytes for tokens of tokenIds in covert
   *
   * @param covertId String
   * @param tokenIds Seq[String]
   * @param txId String
   * @param tx Array[Byte]
   */
  def updateTx(covertId: String, tokenIds: Seq[String], txId: String, tx: Array[Byte]): Future[Unit] = db
    .run(
      covertTokenTx
        .filter(token =>
          token.groupId === covertId && token.withdrawStatus =!= CovertAssetWithdrawStatus.Complete.value && token.tokenId
            .inSet(tokenIds)
        )
        .map(token => (token.txId, token.tx, token.withdrawStatus))
        .update(txId, tx, CovertAssetWithdrawStatus.Requested.value)
    )
    .map(_ => ())

  /**
   * sets a request as complete by pair of covertId and tokenId
   *
   * @param covertId String
   * @param tokenId String
   */
  def setRequestComplete(covertId: String, tokenId: String): Future[Unit] = {
    val query = for {
      token <- covertTokenTx
      if token.groupId === covertId && token.tokenId === tokenId && token.withdrawStatus === CovertAssetWithdrawStatus.Requested.value
    } yield token.withdrawStatus
    db.run(query.update("complete")).map(_ => ())
  }

  /**
   * updates a request (clear transaction byte and id) by pair of covertId and tokenId
   *
   * @param covertId String
   * @param tokenId String
   */
  def resetRequest(covertId: String, tokenId: String): Future[Unit] = {
    val query = for {
      token <- covertTokenTx
      if token.groupId === covertId && token.tokenId === tokenId && token.withdrawStatus === CovertAssetWithdrawStatus.Requested.value
    } yield (token.txId, token.tx, token.withdrawStatus)
    db.run(query.update("", Array.empty[Byte], CovertAssetWithdrawStatus.NoWithdrawYet.value)).map(_ => ())
  }
}
