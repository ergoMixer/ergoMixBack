package dao.stealth

import dao.DAOUtils
import helpers.ErrorHandler.notFoundHandle

import javax.inject.{Inject, Singleton}
import models.StealthModels.ExtractedBlockModel
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait ExtractedBlockComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class ExtractedBlockTable(tag: Tag) extends Table[ExtractedBlockModel](tag, "HEADERS") {
    def id = column[String]("ID")
    def parentId = column[String]("PARENT_ID")
    def height = column[Int]("HEIGHT")
    def timestamp = column[Long]("TIMESTAMP")
    def * = (id, parentId, height, timestamp) <> (ExtractedBlockModel.tupled, ExtractedBlockModel.unapply)
  }

  class ExtractedBlockForkTable(tag: Tag) extends Table[ExtractedBlockModel](tag, "HEADERS_FORK") {
    def id = column[String]("ID")
    def parentId = column[String]("PARENT_ID")
    def height = column[Int]("HEIGHT")
    def timestamp = column[Long]("TIMESTAMP")
    def * = (id, parentId, height, timestamp) <> (ExtractedBlockModel.tupled, ExtractedBlockModel.unapply)
  }
}

@Singleton()
class ExtractedBlockDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider, daoUtils: DAOUtils)(implicit executionContext: ExecutionContext)
    extends ExtractedBlockComponent
      with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val timeout: FiniteDuration = 5.second
  val extractedBlocks = TableQuery[ExtractedBlockTable]
  val extractedBlocksFork = TableQuery[ExtractedBlockForkTable]

  /**
   * insert Seq[extractedBlock] into db
   * @param extractedBlocks blocks
   */
  def insert(extractedBlocks: Seq[ExtractedBlockModel]): DBIO[Option[Int]]= {
    this.extractedBlocks ++= extractedBlocks
  }

  def save(extractedBlocks: Seq[ExtractedBlockModel]): Future[Unit] = {
    db.run(insert(extractedBlocks)).map(_ => ())
  }

  def count(): Future[Int] = {
    db.run(extractedBlocks.length.result)
  }

  /**
   * whether headerId exists in extractedBlock
   * @param headerId block id
   * @return boolean result
   */
  def exists(headerId: String): Boolean = {
    val res = db.run(extractedBlocks.filter(_.id === headerId).exists.result)
    Await.result(res, timeout)
  }

    /**
   * @param height block Height
   * @return DBIO Action Header Id associated with the height
   */
  def getHeaderIdByHeightQuery(height: Int): DBIO[Try[Option[String]]] = {
    extractedBlocks.filter(_.height === height).map(_.id).result.headOption.asTry
  }

  /**
   * @param height block Height
   * @return Header Id associated with the height
   */
  def getHeaderIdByHeight(height: Int): String = {
    notFoundHandle(daoUtils.execAwait(getHeaderIdByHeightQuery(height)))
  }

  /**
   * @return Last Height
   */
  def getLastHeight: Int = {
    val res = db.run(extractedBlocks.sortBy(_.height.desc.nullsLast).map(_.height).result.headOption.asTry)
    val out = Await.result(res, timeout)
    notFoundHandle(out)
  }

  /**
   * deletes all extractedBlocks from db
   */
  def deleteAll(): Unit = {
    db.run(extractedBlocks.delete)
  }

  /**
   * @param headerId header id
   */
  def migrateForkByHeaderId(headerId: String): DBIO[Int] = {
      getByHeaderId(headerId)
        .map(extractedBlocksFork ++= _)
        .andThen(deleteByHeaderId(headerId))
  }

  /**
   * @param headerId header id
   * @return Header record(s) associated with the id
   */
  def getByHeaderId(headerId: String): DBIO[Seq[ExtractedBlockTable#TableElementType]] = {
    extractedBlocks.filter(_.id === headerId).result
  }

  /**
   * @param headerId header id
   * @return Number of rows deleted
   */
  def deleteByHeaderId(headerId: String): DBIO[Int] = {
    extractedBlocks.filter(_.id === headerId).delete
  }
}
