package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait TokenEmissionComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class TokenEmissionTable(tag: Tag) extends Table[(String, String)](tag, "TOKEN_EMISSION_BOX") {
    def id = column[String]("MIX_ID", O.PrimaryKey)

    def boxId = column[String]("BOX_ID")

    def * = (id, boxId)
  }

}

@Singleton()
class TokenEmissionDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends TokenEmissionComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val tokenEmissions = TableQuery[TokenEmissionTable]

  /**
   * inserts a box into TOKEN_EMISSION_BOX table
   *
   * @param mixId String
   * @param boxId String
   */
  def insert(mixId: String, boxId: String): Future[Unit] = db.run(tokenEmissions += (mixId, boxId)).map(_ => ())

  /**
   * selects emission boxId by mixId
   *
   * @param mixID String
   */
  def selectBoxId(mixID: String): Future[Option[String]] = {
    val query = for {
      box <- tokenEmissions if box.id === mixID
    } yield box.boxId
    db.run(query.result.headOption)
  }

  /**
   * checks if the boxId exists in table or not
   *
   * @param boxId String
   */
  def existsByBoxId(boxId: String): Future[Boolean] =
    db.run(tokenEmissions.filter(box => box.boxId === boxId).exists.result)

  /**
   * delete box by mixId
   *
   * @param mixId String
   */
  def delete(mixId: String): Unit = db.run(tokenEmissions.filter(box => box.id === mixId).delete).map(_ => ())
}
