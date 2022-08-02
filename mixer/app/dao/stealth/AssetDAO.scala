package dao.stealth

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import models.StealthModels.ExtractedAssetModel

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AssetComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class AssetTable(tag: Tag) extends Table[ExtractedAssetModel](tag, "ASSETS") {
    def tokenId = column[String]("TOKEN_ID")
    def boxId = column[String]("BOX_ID")
    def headerId = column[String]("HEADER_ID")
    def index = column[Short]("INDEX")
    def value = column[Long]("VALUE")
    def * = (tokenId, boxId, headerId, index, value) <> (ExtractedAssetModel.tupled, ExtractedAssetModel.unapply)
  }

  class AssetForkTable(tag: Tag) extends Table[ExtractedAssetModel](tag, "ASSETS_FORK") {
    def tokenId = column[String]("TOKEN_ID")
    def boxId = column[String]("BOX_ID")
    def headerId = column[String]("HEADER_ID")
    def index = column[Short]("INDEX")
    def value = column[Long]("VALUE")
    def * = (tokenId, boxId, headerId, index, value) <> (ExtractedAssetModel.tupled, ExtractedAssetModel.unapply)
  }
}

@Singleton()
class AssetDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends AssetComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val assets = TableQuery[AssetTable]
  val assetsFork = TableQuery[AssetForkTable]

  /**
   * inserts a Asset of box into db
   * @param asset asset
   */
  def insert(asset: ExtractedAssetModel ): Future[Unit] = db.run(assets += asset).map(_ => ())

  /**
   * inserts assets into db
   * @param assets Seq of ExtractedAssetModel
   */
  def insert(assets: Seq[ExtractedAssetModel]): DBIO[Option[Int]] = this.assets ++= assets

  def deleteAll(): Unit = {
    db.run(assets.delete)
  }

  def save(assets: Seq[ExtractedAssetModel]): Future[Unit] = {
    db.run(insert(assets)).map(_ => ())
  }

  /**
   * @param tokenId token id
   * @return whether this asset exists for a specific TokenId or not
   */
  def exists(tokenId: String): Future[Boolean] = {
    db.run(assets.filter(_.tokenId === tokenId).exists.result)
  }

  /**
   * @param headerId header id
   */
  def migrateForkByHeaderId(headerId: String): DBIO[Int] = {
    getByHeaderId(headerId)
      .map(assetsFork ++= _)
      .andThen(deleteByHeaderId(headerId))
  }

  /**
   * @param headerId header id
   * @return Asset record(s) associated with the header
   */
  def getByHeaderId(headerId: String): DBIO[Seq[AssetTable#TableElementType]] = {
    assets.filter(_.headerId === headerId).result
  }

  /**
   * @param headerId header id
   * @return Number of rows deleted
   */
  def deleteByHeaderId(headerId: String): DBIO[Int] = {
    assets.filter(_.headerId === headerId).delete
  }
}
