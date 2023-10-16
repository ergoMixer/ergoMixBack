package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.Rescan.PendingRescan
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait RescanComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class RescanTable(tag: Tag) extends Table[PendingRescan](tag, "RESCAN") {
    def mixId = column[String]("MIX_ID", O.PrimaryKey)

    def createdTime = column[Long]("CREATED_TIME")

    def round = column[Int]("ROUND")

    def goBackward = column[Boolean]("GO_BACKWARD")

    def boxType = column[String]("BOX_TYPE")

    def mixBoxId = column[String]("MIX_BOX_ID")

    def * = (mixId, createdTime, round, goBackward, boxType, mixBoxId) <> (PendingRescan.tupled, PendingRescan.unapply)
  }

  class RescanArchivedTable(tag: Tag)
    extends Table[(String, Long, Int, Boolean, String, String, String)](tag, "RESCAN_ARCHIVE") {
    def mixId = column[String]("MIX_ID", O.PrimaryKey)

    def createdTime = column[Long]("CREATED_TIME")

    def round = column[Int]("ROUND")

    def goBackward = column[Boolean]("GO_BACKWARD")

    def boxType = column[String]("BOX_TYPE")

    def mixBoxId = column[String]("MIX_BOX_ID")

    def reason = column[String]("REASON")

    def * = (mixId, createdTime, round, goBackward, boxType, mixBoxId, reason)
  }

}

@Singleton()
class RescanDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends RescanComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val rescans = TableQuery[RescanTable]

  val rescansArchive = TableQuery[RescanArchivedTable]

  /**
   * selects all rescans
   */
  def all: Future[Seq[PendingRescan]] = db.run(rescans.result)

  /**
   * deletes rescan by mixID
   *
   * @param mixID String
   */
  def delete(mixID: String): Future[Unit] = db.run(rescans.filter(rescan => rescan.mixId === mixID).delete).map(_ => ())

  /**
   * deletes rescan by mixID
   *
   * @param mixId String
   */
  def deleteWithArchive(mixId: String): Future[Unit] = db.run(
    DBIO.seq(
      rescans.filter(rescan => rescan.mixId === mixId).delete,
      rescansArchive.filter(rescan => rescan.mixId === mixId).delete
    )
  )

  /**
   * updates pending rescan by mixId
   *
   * @param new_rescan PendingRescan
   */
  def updateById(new_rescan: PendingRescan)(implicit insertReason: String): Future[Unit] = db.run(
    DBIO.seq(
      rescans.filter(rescan => rescan.mixId === new_rescan.mixId).delete,
      rescans += new_rescan,
      rescansArchive += (new_rescan.mixId, new_rescan.time, new_rescan.round, new_rescan.goBackward, new_rescan.boxType, new_rescan.mixBoxId, insertReason)
    )
  )
}
