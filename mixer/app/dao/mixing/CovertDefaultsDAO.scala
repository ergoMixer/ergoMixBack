package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.Models.CovertAsset
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait CovertDefaultsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class CovertDefaultsTable(tag: Tag) extends Table[CovertAsset](tag, "COVERT_DEFAULTS") {
    def groupId = column[String]("MIX_GROUP_ID")

    def tokenId = column[String]("TOKEN_ID")

    def ring = column[Long]("MIXING_TOKEN_AMOUNT")

    def confirmedDeposit = column[Long]("DEPOSIT_DONE")

    def lastActivity = column[Long]("LAST_ACTIVITY")

    def * = (groupId, tokenId, ring, confirmedDeposit, lastActivity) <> (CovertAsset.tupled, CovertAsset.unapply)

    def pk = primaryKey("pk_COVERT_DEFAULTS", (groupId, tokenId))
  }

}

@Singleton()
class CovertDefaultsDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends CovertDefaultsComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val covertAssets = TableQuery[CovertDefaultsTable]

  /**
   * returns all assets
   */
  def all: Future[Seq[CovertAsset]] = db.run(covertAssets.result)

  /**
   * returns number of assets in table
   */
  def size: Future[Int] = db.run(covertAssets.size.result)

  /**
   * deletes all of assets
   */
  def clear: Future[Unit] = db.run(covertAssets.delete).map(_ => ())

  /**
   * inserts an asset into COVERT_DEFAULTS table
   *
   * @param asset CovertAsset
   */
  def insert(asset: CovertAsset): Future[Unit] = db.run(covertAssets += asset).map(_ => ())

  /**
   * checks if the pair of mixGroupId and tokenId exists in table or not
   *
   * @param groupId String
   * @param tokenId String
   */
  def exists(groupId: String, tokenId: String): Future[Boolean] =
    db.run(covertAssets.filter(asset => asset.groupId === groupId && asset.tokenId === tokenId).exists.result)

  /**
   * selects assets by mixGroupId
   *
   * @param groupId String
   */
  def selectAllAssetsByMixGroupId(groupId: String): Future[Seq[CovertAsset]] =
    db.run(covertAssets.filter(asset => asset.groupId === groupId).result)

  /**
   * selects assets by mixGroupId and tokenId
   *
   * @param groupId String
   * @param tokenId String
   */
  def selectByGroupAndTokenId(groupId: String, tokenId: String): Future[Option[CovertAsset]] =
    db.run(covertAssets.filter(asset => asset.groupId === groupId && asset.tokenId === tokenId).result.headOption)

  /**
   * updates ring by pair of groupId and tokenId
   *
   * @param groupId String
   * @param tokenId String
   * @param ring Long
   */
  def updateRing(groupId: String, tokenId: String, ring: Long): Future[Unit] = {
    val query = for {
      asset <- covertAssets if asset.groupId === groupId && asset.tokenId === tokenId
    } yield asset.ring
    db.run(query.update(ring)).map(_ => ())
  }

  /**
   * updates confirmedDeposit and lastActivity by pair of groupId and tokenId
   *
   * @param groupId String
   * @param tokenId String
   * @param confirmedDeposit Long
   * @param lastActivity Long
   */
  def updateConfirmedDeposit(
    groupId: String,
    tokenId: String,
    confirmedDeposit: Long,
    lastActivity: Long
  ): Future[Unit] = {
    val query = for {
      asset <- covertAssets if asset.groupId === groupId && asset.tokenId === tokenId
    } yield (asset.confirmedDeposit, asset.lastActivity)
    db.run(query.update(confirmedDeposit, lastActivity)).map(_ => ())
  }

  /**
   * deletes an asset if no ring has been set for it by the pair of mixGroupId and tokenId
   *
   * @param groupId String
   * @param tokenId String
   */
  def deleteIfRingIsEmpty(groupId: String, tokenId: String): Future[Unit] = db
    .run(
      covertAssets.filter(asset => asset.groupId === groupId && asset.tokenId === tokenId && asset.ring === 0L).delete
    )
    .map(_ => ())

}
