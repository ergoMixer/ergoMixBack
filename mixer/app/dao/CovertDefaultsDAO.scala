package dao

import javax.inject.{Inject, Singleton}
import models.Models.CovertAsset
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait CovertDefaultsComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class CovertDefaultsTable(tag: Tag) extends Table[CovertAsset](tag, "COVERT_DEFAULTS") {
        def covertId = column[String]("MIX_GROUP_ID")

        def tokenId = column[String]("TOKEN_ID")

        def ring = column[Long]("MIXING_TOKEN_AMOUNT")

        def confirmedDeposit = column[Long]("DEPOSIT_DONE")

        def lastActivity = column[Long]("LAST_ACTIVITY")

        def * = (covertId, tokenId, ring, confirmedDeposit, lastActivity) <> (CovertAsset.tupled, CovertAsset.unapply)

        def pk = primaryKey("pk_COVERT_DEFAULTS", (covertId, tokenId))
    }

}

@Singleton()
class CovertDefaultsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends CovertDefaultsComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val covertAssets = TableQuery[CovertDefaultsTable]

    /**
     * returns all assets
     *
     */
    def all: Future[Seq[CovertAsset]] = db.run(covertAssets.result)

    /**
     * returns number of assets in table
     *
     */
    def size: Future[Int] = db.run(covertAssets.size.result)

    /**
     * deletes all of assets
     *
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
     * @param covertId String
     * @param tokenId String
     */
    def exists(covertId: String, tokenId: String): Future[Boolean] = db.run(covertAssets.filter(asset => asset.covertId === covertId && asset.tokenId === tokenId).exists.result)

    /**
     * selects assets by mixGroupId
     *
     * @param covertId String
     */
    def selectAllAssetsByMixGroupId(covertId: String): Future[Seq[CovertAsset]] = db.run(covertAssets.filter(asset => asset.covertId === covertId).result)

    /**
     * selects assets by mixGroupId and tokenId
     *
     * @param covertId String
     * @param tokenId String
     */
    def selectByGroupAndTokenId(covertId: String, tokenId: String): Future[Option[CovertAsset]] = db.run(covertAssets.filter(asset => asset.covertId === covertId && asset.tokenId === tokenId).result.headOption)

    /**
     * updates ring by pair of covertId and tokenId
     *
     * @param covertId String
     * @param tokenId String
     * @param ring Long
     */
    def updateRing(covertId: String, tokenId: String, ring: Long): Future[Unit] = {
        val query = for {
            asset <- covertAssets if asset.covertId === covertId && asset.tokenId === tokenId
        } yield asset.ring
        db.run(query.update(ring)).map(_ => ())
    }

    /**
     * updates confirmedDeposit and lastActivity by pair of covertId and tokenId
     *
     * @param covertId String
     * @param tokenId String
     * @param confirmedDeposit Long
     * @param lastActivity Long
     */
    def updateConfirmedDeposit(covertId: String, tokenId: String, confirmedDeposit: Long, lastActivity: Long): Future[Unit] = {
        val query = for {
            asset <- covertAssets if asset.covertId === covertId && asset.tokenId === tokenId
        } yield (asset.confirmedDeposit, asset.lastActivity)
        db.run(query.update(confirmedDeposit, lastActivity)).map(_ => ())
    }

    /**
     * deletes an asset if no ring has been set for it by the pair of mixGroupId and tokenId
     *
     * @param covertId String
     * @param tokenId String
     */
    def deleteIfRingIsEmpty(covertId: String, tokenId: String): Future[Unit] = db.run(covertAssets.filter(asset => asset.covertId === covertId && asset.tokenId === tokenId && asset.ring === 0L).delete).map(_ => ())

}
