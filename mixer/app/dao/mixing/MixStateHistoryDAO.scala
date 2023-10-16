package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.Models.MixHistory
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait MixStateHistoryComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class MixStateHistoryTable(tag: Tag) extends Table[MixHistory](tag, "MIX_STATE_HISTORY") {
    def mixId = column[String]("MIX_ID", O.PrimaryKey)

    def round = column[Int]("ROUND")

    def isAlice = column[Boolean]("IS_ALICE")

    def createdTime = column[Long]("CREATED_TIME")

    def * = (mixId, round, isAlice, createdTime) <> (MixHistory.tupled, MixHistory.unapply)
  }

  class MixStateHistoryArchivedTable(tag: Tag)
    extends Table[(String, Int, Boolean, Long, String)](tag, "MIX_STATE_HISTORY_ARCHIVED") {
    def mixId = column[String]("MIX_ID", O.PrimaryKey)

    def round = column[Int]("ROUND")

    def isAlice = column[Boolean]("IS_ALICE")

    def createdTime = column[Long]("CREATED_TIME")

    def reason = column[String]("REASON")

    def * = (mixId, round, isAlice, createdTime, reason)
  }

}

@Singleton()
class MixStateHistoryDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends MixStateHistoryComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val mixHistories = TableQuery[MixStateHistoryTable]

  val mixStatesArchive = TableQuery[MixStateHistoryArchivedTable]

  /**
   * returns all mixes
   */
  def all: Future[Seq[MixHistory]] = db.run(mixHistories.result)

  /**
   * deletes all of state histories
   */
  def clear: Future[Unit] = db.run(mixHistories.delete).map(_ => ())

  /**
   * deletes future mixHistory by mixID
   *
   * @param mixID String
   * @param round Int
   */
  def deleteFutureRounds(mixID: String, round: Int): Future[Unit] =
    db.run(mixHistories.filter(mix => mix.mixId === mixID && mix.round > round).delete).map(_ => ())

  /**
   * updates mix state history by mixID
   *
   * @param mixHistory MixHistory
   */
  def updateById(mixHistory: MixHistory)(implicit insertReason: String): Future[Unit] = db.run(
    DBIO.seq(
      mixHistories.filter(mix => mix.mixId === mixHistory.id && mix.round === mixHistory.round).delete,
      mixHistories += mixHistory,
      mixStatesArchive += (mixHistory.id, mixHistory.round, mixHistory.isAlice, mixHistory.time, insertReason)
    )
  )

  /**
   * inserts mix state
   *
   * @param mixHistory MixHistory
   */
  def insertMixHistory(mixHistory: MixHistory)(implicit insertReason: String): Future[Unit] = db.run(
    DBIO.seq(
      mixHistories += mixHistory,
      mixStatesArchive += (mixHistory.id, mixHistory.round, mixHistory.isAlice, mixHistory.time, insertReason)
    )
  )

  /**
   * delete mix state by mixId
   *
   * @param mixId String
   */
  def deleteWithArchive(mixId: String): Future[Unit] = db.run(
    DBIO.seq(
      mixHistories.filter(mix => mix.mixId === mixId).delete,
      mixStatesArchive.filter(mix => mix.mixId === mixId).delete
    )
  )
}
