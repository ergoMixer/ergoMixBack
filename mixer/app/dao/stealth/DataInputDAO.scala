package dao.stealth

import javax.inject.{Inject, Singleton}
import models.StealthModels._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}

trait DataInputComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class DataInputTable(tag: Tag) extends Table[ExtractedDataInputModel](tag, "DATA_INPUTS") {
    def boxId = column[String]("BOX_ID")
    def txId = column[String]("TX_ID")
    def headerId = column[String]("HEADER_ID")
    def index = column[Short]("INDEX")
    def * = (boxId, txId, headerId, index) <> (ExtractedDataInputModel.tupled, ExtractedDataInputModel.unapply)
  }

  class DataInputForkTable(tag: Tag) extends Table[ExtractedDataInputModel](tag, "DATA_INPUTS_FORK") {
    def boxId = column[String]("BOX_ID")
    def txId = column[String]("TX_ID")
    def headerId = column[String]("HEADER_ID")
    def index = column[Short]("INDEX")
    def * = (boxId, txId, headerId, index) <> (ExtractedDataInputModel.tupled, ExtractedDataInputModel.unapply)
  }
}

@Singleton()
class DataInputDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends DataInputComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val dataInputs = TableQuery[DataInputTable]
  val dataInputsFork = TableQuery[DataInputForkTable]

  /**
   * inserts a dataInput into db
   * @param dataInput dataInput
   */
  def insert(dataInput: ExtractedDataInputModel): Future[Unit] = db.run(this.dataInputs += dataInput).map(_ => ())

  /**
   * create query for insert data
   * @param dataInputs Seq of dataInput
   */
  def insert(dataInputs: Seq[ExtractedDataInputModel]): DBIO[Option[Int]] = this.dataInputs ++= dataInputs

  def insertIfDoesNotExist(dataInputs: Seq[ExtractedDataInputModel]): DBIO[Option[Int]] = {
       val ids = dataInputs.map(_.boxId)
       (for {
         existing <- this.dataInputs.filter(_.boxId inSet ids).result
         filtered = dataInputs.filterNot(dataInput => existing.contains(dataInput))
         count <- this.dataInputs ++= filtered
       } yield count).transactionally
  }

  /**
   * exec insert query
   * @param inputs Seq of input
   */
  def save(inputs: Seq[ExtractedDataInputModel]): Future[Unit] = {
    db.run(insert(inputs)).map(_ => ())
  }

  /**
   * @param boxId box id
   * @return whether this box exists for a specific boxId or not
   */
  def exists(boxId: String): Future[Boolean] = {
    db.run(dataInputs.filter(_.boxId === boxId).exists.result)
  }

  /**
   * @param headerId header id
   */
  def migrateForkByHeaderId(headerId: String): DBIO[Int] = {
    getByHeaderId(headerId)
      .map(dataInputsFork ++= _)
      .andThen(deleteByHeaderId(headerId))
  }

  /**
   * @param headerId header id
   * @return dataInput record(s) associated with the header
   */
  def getByHeaderId(headerId: String): DBIO[Seq[DataInputTable#TableElementType]] = {
    dataInputs.filter(_.headerId === headerId).result
  }

  /**
   * @param headerId header id
   * @return Number of rows deleted
   */
  def deleteByHeaderId(headerId: String): DBIO[Int] = {
    dataInputs.filter(_.headerId === headerId).delete
  }
}
