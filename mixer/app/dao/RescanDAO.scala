package dao

import javax.inject.{Inject, Singleton}
import models.Models.PendingRescan
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait RescanComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class RescanTable(tag: Tag) extends Table[PendingRescan](tag, "RESCAN") {
        def id = column[String]("MIX_ID", O.PrimaryKey)

        def createdTime = column[Long]("CREATED_TIME")

        def round = column[Int]("ROUND")

        def goBackward = column[Boolean]("GO_BACKWARD")

        def isHalfMixTx = column[Boolean]("IS_HALF_MIX_TX")

        def mixBoxId = column[String]("MIX_BOX_ID")

        def * = (id, createdTime, round, goBackward, isHalfMixTx, mixBoxId) <> (PendingRescan.tupled, PendingRescan.unapply)
    }

    class RescanArchivedTable(tag: Tag) extends Table[(String, Long, Int, Boolean, Boolean, String, String)](tag, "RESCAN_ARCHIVE") {
        def id = column[String]("MIX_ID", O.PrimaryKey)

        def createdTime = column[Long]("CREATED_TIME")

        def round = column[Int]("ROUND")

        def goBackward = column[Boolean]("GO_BACKWARD")

        def isHalfMixTx = column[Boolean]("IS_HALF_MIX_TX")

        def mixBoxId = column[String]("MIX_BOX_ID")

        def reason = column[String]("REASON")

        def * = (id, createdTime, round, goBackward, isHalfMixTx, mixBoxId, reason)
    }

}

@Singleton()
class RescanDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends RescanComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val rescans = TableQuery[RescanTable]

    val rescansArchive = TableQuery[RescanArchivedTable]

    /**
     * selects all rescans
     *
     */
    def all: Future[Seq[PendingRescan]] = db.run(rescans.result)

    /**
     * deletes rescan by mixID
     *
     * @param mixID String
     */
    def delete(mixID: String): Future[Unit] = db.run(rescans.filter(rescan => rescan.id === mixID).delete).map(_ => ())

    /**
     * deletes rescan by mixID
     *
     * @param mixId String
     */
    def deleteWithArchive(mixId: String): Future[Unit] = db.run(DBIO.seq(
        rescans.filter(rescan => rescan.id === mixId).delete,
        rescansArchive.filter(rescan => rescan.id === mixId).delete
    ))

    /**
     * updates pending rescan by mixId
     *
     * @param new_rescan PendingRescan
     */
    def updateById(new_rescan: PendingRescan)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        rescans.filter(rescan => rescan.id === new_rescan.mixId).delete,
        rescans += new_rescan,
        rescansArchive += (new_rescan.mixId, new_rescan.time, new_rescan.round, new_rescan.goBackward, new_rescan.isHalfMixTx, new_rescan.mixBoxId, insertReason)
    ))
}