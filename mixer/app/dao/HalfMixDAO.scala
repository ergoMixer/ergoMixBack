package dao

import javax.inject.{Inject, Singleton}
import models.Models.HalfMix
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait HalfMixComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class HalfMixTable(tag: Tag) extends Table[HalfMix](tag, "HALF_MIX") {
        def mixId = column[String]("MIX_ID", O.PrimaryKey)

        def round = column[Int]("ROUND")

        def createdTime = column[Long]("CREATED_TIME")

        def halfMixBoxId = column[String]("HALF_MIX_BOX_ID")

        def isSpent = column[Boolean]("IS_SPENT")

        def * = (mixId, round, createdTime, halfMixBoxId, isSpent) <> (HalfMix.tupled, HalfMix.unapply)
    }

    class HalfMixArchivedTable(tag: Tag) extends Table[(String, Int, Long, String, Boolean, String)](tag, "HALF_MIX_ARCHIVED") {
        def mixId = column[String]("MIX_ID", O.PrimaryKey)

        def round = column[Int]("ROUND")

        def createdTime = column[Long]("CREATED_TIME")

        def halfMixBoxId = column[String]("HALF_MIX_BOX_ID")

        def isSpent = column[Boolean]("IS_SPENT")

        def reason = column[String]("REASON")

        def * = (mixId, round, createdTime, halfMixBoxId, isSpent, reason)
    }

}

@Singleton()
class HalfMixDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends HalfMixComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val halfMix = TableQuery[HalfMixTable]

    val halfMixArchive = TableQuery[HalfMixArchivedTable]

    /**
     * returns all mixes
     *
     */
    def all: Future[Seq[HalfMix]] = db.run(halfMix.result)

    /**
     * inserts a mix into table
     *
     * @param mix HalfMix
     */
    def insert(mix: HalfMix): Future[Unit] = db.run(halfMix += mix).map(_ => ())

    /**
     * deletes all of mixes
     *
     */
    def clear: Future[Unit] = db.run(halfMix.delete).map(_ => ())

    /**
     * selects halfMix by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def selectOption(mixId: String, round: Int): Future[Option[HalfMix]] = db.run(halfMix.filter(mix => mix.mixId === mixId && mix.round === round).result.headOption)

    /**
     * selects halfMix by mixId
     *
     * @param mixId String
     */
    def boxIdByMixId(mixId: String): Future[Option[String]] = db.run(halfMix.filter(mix => mix.mixId === mixId).map(_.halfMixBoxId).result.headOption)

    /**
     * delete halfMix by mixId
     *
     * @param mixId String
     */
    def deleteWithArchive(mixId: String): Future[Unit] = db.run(DBIO.seq(
        halfMix.filter(mix => mix.mixId === mixId).delete,
        halfMixArchive.filter(mix => mix.mixId === mixId).delete
    ))

    /**
     * deletes future halfMix by mixId
     *
     * @param mixId String
     * @param round Int
     */
    def deleteFutureRounds(mixId: String, round: Int): Future[Unit] = db.run(halfMix.filter(mix => mix.mixId === mixId && mix.round > round).delete).map(_ => ())

    /**
     * updates half mix by id
     *
     * @param new_mix HalfMix
     */
    def updateById(new_mix: HalfMix)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        halfMix.filter(mix => mix.mixId === new_mix.mixId && mix.round === new_mix.round).delete,
        halfMix += new_mix,
        halfMixArchive += (new_mix.mixId, new_mix.round, new_mix.createdTime, new_mix.halfMixBoxId, new_mix.isSpent, insertReason)
    ))

    /**
     * updates isSpent by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def setAsSpent(mixId: String, round: Int): Future[Unit] = {
        val query = for {
            mix <- halfMix if mix.mixId === mixId && mix.round === round
        } yield mix.isSpent
        db.run(query.update(true)).map(_ => ())
    }

    /**
     * inserts halfMix
     *
     * @param new_mix HalfMix
     */
    def insertHalfMix(new_mix: HalfMix)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        halfMix += new_mix,
        halfMixArchive += (new_mix.mixId, new_mix.round, new_mix.createdTime, new_mix.halfMixBoxId, new_mix.isSpent, insertReason)
    ))

    /**
     * selects halfMixBox by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def getMixBoxIdByRound(mixId: String, round: Int): Future[Option[String]] = db.run(halfMix.filter(mix =>
        mix.mixId === mixId && mix.round === round).map(_.halfMixBoxId).result.headOption)

}
