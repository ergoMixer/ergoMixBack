package dao.stealth

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.StealthModels.Stealth
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait StealthComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  implicit def BigInt2StringMapper =
    MappedColumnType.base[BigInt, String](
      bi => bi.toString,
      s => BigInt(s)
    )

  class StealthTable(tag: Tag) extends Table[Stealth](tag, "STEALTH") {
    def stealthId = column[String]("STEALTH_ID", O.PrimaryKey)

    def stealthName = column[String]("STEALTH_NAME")

    def pk = column[String]("PK")

    def secret = column[BigInt]("SECRET")

    def * = (stealthId, stealthName, pk, secret) <> (Stealth.tupled, Stealth.unapply)
  }
}

@Singleton()
class StealthDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends StealthComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val stealthQuery = TableQuery[StealthTable]

  /**
   * inserts a scan into db
   *
   * @param stealth StealthModel
   */
  def insert(stealth: Stealth): Future[Unit] = db.run(stealthQuery += stealth).map(_ => ())

  /**
   * returns all StealthModel
   */
  def all: Future[Seq[Stealth]] = db.run(stealthQuery.result)

  /**
   * @param stealthId String
   * @return whether this stealth query exists for a specific stealthId or not
   */
  def existsByStealthId(stealthId: String): Future[Boolean] =
    db.run(stealthQuery.filter(req => req.stealthId === stealthId).exists.result)

  /**
   * @param stealthId String
   * @return Option of StealthModel if exist
   */
  def selectByStealthId(stealthId: String): Future[Option[Stealth]] =
    db.run(stealthQuery.filter(req => req.stealthId === stealthId).result.headOption)

  /**
   * delete all stealth objects in table
   */
  def clear(): Future[Unit] =
    db.run(stealthQuery.delete).map(_ => ())

  /**
   * update stealth's name
   *
   * @param stealthId - String
   * @param stealthName - String
   */
  def updateNameStealth(stealthId: String, stealthName: String): Future[Unit] = {
    val query = stealthQuery.filter(req => req.stealthId === stealthId).map(_.stealthName).update(stealthName)
    db.run(query).map(_ => ())
  }
}
