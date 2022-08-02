package dao.stealth

import dao.DAOUtils

import javax.inject.{Inject, Singleton}
import models.StealthModels._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait StealthComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  implicit def BigInt2StringMapper =
    MappedColumnType.base[BigInt, String](
      bi => bi.toString,
      s => BigInt(s)
    )

  class StealthTable(tag: Tag) extends Table[StealthModel](tag, "STEALTH") {
    def stealthId = column[String]("STEALTH_ID", O.PrimaryKey)
    def secret = column[BigInt]("SECRET")
    def stealthName = column[String]("STEALTH_NAME")
    def * = (stealthId, secret, stealthName) <> (StealthModel.tupled, StealthModel.unapply)
  }
}

@Singleton()
class StealthDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider, daoUtils: DAOUtils)(implicit executionContext: ExecutionContext)
  extends StealthComponent
    with HasDatabaseConfigProvider[JdbcProfile]{

  import profile.api._

  val stealthQuery = TableQuery[StealthTable]

  /**
   * inserts a scan into db
   * @param stealth StealthModel
   */
  def insert(stealth: StealthModel): Future[Unit] = db.run(stealthQuery += stealth).map(_ => ())

  /**
   * returns all StealthModel
   *
   */
  def all: Future[Seq[StealthModel]] = db.run(stealthQuery.result)

  def deleteAll(): Unit = {
    db.run(stealthQuery.delete)
  }

  /**
   * @param secret String
   * @return Output record(s) associated with the header
   */
  def getBySecretKey(secret: BigInt): DBIO[Seq[StealthTable#TableElementType]] = {
    stealthQuery.filter(_.secret === secret).result
  }


  /**
   * @param stealthName String
   * @return Number of rows deleted
   */
  def deleteByStealthName(stealthName: String): Future[Int] = {
    db.run(stealthQuery.filter(_.stealthName === stealthName).delete)
  }

  /**
   * @param stealthId Long
   * @return Number of rows deleted
   */
  def deleteById(stealthId: String): Future[Int] = {
    db.run(stealthQuery.filter(_.stealthId === stealthId).delete)
  }

  /**
   * @param secret String
   * @return Number of rows deleted
   */
  def deleteBySecretKey(secret: BigInt): Future[Int] = {
    db.run(stealthQuery.filter(_.secret === secret).delete)
  }


  /**
   * @return Int number of scanning rules
   */
  def count(): Int = {
    daoUtils.execAwait(stealthQuery.length.result)
  }

  /**
   * @param stealthName String
   * @return whether this stealth query exists for a specific stealthName or not
   */
  def existsByStealthName(stealthName: String): Future[Boolean] = {
    db.run(stealthQuery.filter(req => req.stealthName === stealthName).exists.result)
  }

  /**
   * selects request stealthName
   *
   * @param stealthName String
   *
   * @return stealth record with stealthName == stealthName
   */
  def selectByStealthName(stealthName: String): Future[Seq[StealthModel]] = {
    db.run(stealthQuery.filter(req => req.stealthName === stealthName).result)
  }

  def selectByStealthId(stealthId: String): Future[Option[StealthModel]] = {
    db.run(stealthQuery.filter(req => req.stealthId === stealthId).result.headOption)
  }
}
