package dao

import javax.inject.{Inject, Singleton}
import models.Models.MixState
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait MixStateComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class MixStateTable(tag: Tag) extends Table[MixState](tag, "MIX_STATE") {
        def mixId = column[String]("MIX_ID", O.PrimaryKey)

        def round = column[Int]("ROUND")

        def isAlice = column[Boolean]("IS_ALICE")

        def * = (mixId, round, isAlice) <> (MixState.tupled, MixState.unapply)
    }

}

@Singleton()
class MixStateDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends MixStateComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val mixes = TableQuery[MixStateTable]

    /**
     * returns all mixes
     *
     */
    def all: Future[Seq[MixState]] = db.run(mixes.result)

    /**
     * deletes all of states
     *
     */
    def clear: Future[Unit] = db.run(mixes.delete).map(_ => ())

    /**
     * inserts a mix state into MIX_STATE table
     *
     * @param state MixState
     */
    def insert(state: MixState): Future[Unit] = db.run(mixes += state).map(_ => ())

    /**
     * selects state by mixID
     *
     * @param mixID String
     */
    def selectByMixId(mixID: String): Future[Option[MixState]] = db.run(mixes.filter(state => state.mixId === mixID).result.headOption)

    /**
     * returns min of number of rounds in mixingRequest and mixState by mixID
     *
     * @param mixID String
     * @param numRounds Int
     */
    def minRoundsByMixId(mixID: String, numRounds: Int): Future[Int] = {
        val query = for {
            state <- mixes.filter(state => state.mixId === mixID).result.headOption
            minRounds <- DBIO.successful(Math min(state.getOrElse(throw new Exception("corresponding mixId not found in MixState table")).round, numRounds))
        } yield minRounds
        db.run(query)
    }

    /**
     * updates mix state by mixID
     *
     * @param mixState MixState
     */
    def updateById(mixState: MixState): Future[Unit] = db.run(mixes.filter(mix => mix.mixId === mixState.id).update(mixState)).map(_ => ())

    /**
     * deletes mix state by mixID
     *
     * @param mixId String
     */
    def delete(mixId: String): Future[Unit] = db.run(mixes.filter(mix => mix.mixId === mixId).delete).map(_ => ())

    /**
     * updates mix state by mixID, insert new mixStates if not exists (this happen in rescan process)
     *
     * @param mixState MixState
     */
    def updateInRescan(mixState: MixState): Future[Unit] = db.run(DBIO.seq(
        mixes.filter(mix => mix.mixId === mixState.id).delete,
        mixes += mixState
    ))
}
