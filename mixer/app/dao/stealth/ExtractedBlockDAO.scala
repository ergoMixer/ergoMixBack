package dao.stealth

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import dao.DAOUtils
import models.StealthModels.ExtractedBlock
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait ExtractedBlockComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class ExtractedBlockTable(tag: Tag) extends Table[ExtractedBlock](tag, "HEADERS") {
    def id        = column[String]("ID")
    def parentId  = column[String]("PARENT_ID")
    def height    = column[Int]("HEIGHT")
    def timestamp = column[Long]("TIMESTAMP")
    def *         = (id, parentId, height, timestamp) <> (ExtractedBlock.tupled, ExtractedBlock.unapply)
  }

  class ExtractedBlockForkTable(tag: Tag) extends Table[ExtractedBlock](tag, "HEADERS_FORK") {
    def id        = column[String]("ID")
    def parentId  = column[String]("PARENT_ID")
    def height    = column[Int]("HEIGHT")
    def timestamp = column[Long]("TIMESTAMP")
    def *         = (id, parentId, height, timestamp) <> (ExtractedBlock.tupled, ExtractedBlock.unapply)
  }
}

@Singleton()
class ExtractedBlockDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider, daoUtils: DAOUtils)(
  implicit executionContext: ExecutionContext
) extends ExtractedBlockComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val extractedBlocks     = TableQuery[ExtractedBlockTable]
  val extractedBlocksFork = TableQuery[ExtractedBlockForkTable]

  /**
   * insert extractedBlock into db
   * @param extractedBlock - ExtractedBlock
   */
  def insert(extractedBlock: ExtractedBlock): Future[Unit] =
    db.run(this.extractedBlocks += extractedBlock).map(_ => ())

  /**
   * number of extractedBlocks in db
   * @return
   */
  def count(): Future[Int] =
    db.run(extractedBlocks.length.result)

  /**
   * @param height block Height
   * @return Header Id associated with the height
   */
  def getHeaderIdByHeight(height: Int): Future[Option[String]] = {
    val query = extractedBlocks.filter(_.height === height).map(_.id).result.headOption
    db.run(query)
  }

  /**
   * @return Last Height
   */
  def getLastHeight: Future[Option[Int]] = {
    val query = extractedBlocks.map(_.height).sortBy(_.desc.nullsLast).result.headOption
    db.run(query)
  }

  /**
   * deletes all extractedBlocks from db
   */
  def clear(): Unit =
    db.run(extractedBlocks.delete)
}
