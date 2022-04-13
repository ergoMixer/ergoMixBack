package dao

import javax.inject.{Inject, Singleton}
import models.Models.HopMix
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait HopMixComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class HopMixTable(tag: Tag) extends Table[HopMix](tag, "HOP_MIX") {
        def id = column[String]("MIX_ID")

        def round = column[Int]("ROUND")

        def createdTime = column[Long]("CREATED_TIME")

        def boxId = column[String]("BOX_ID")

        def * = (id, round, createdTime, boxId) <> (HopMix.tupled, HopMix.unapply)

        def pk = primaryKey("pk_HOP_MIX", (id, round))
    }

}

@Singleton()
class HopMixDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends HopMixComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val hopMixes = TableQuery[HopMixTable]

    /**
     * inserts a hop into table
     *
     * @param hop HopMix
     */
    def insert(hop: HopMix): Future[Unit] = db.run(hopMixes += hop).map(_ => ())

    /**
     * returns all hops
     *
     */
    def all: Future[Seq[HopMix]] = db.run(hopMixes.result)

    /**
     * deletes all of hops
     *
     */
    def clear: Future[Unit] = db.run(hopMixes.delete).map(_ => ())

    /**
     * deletes future hops by mixId
     *
     * @param mixId String
     * @param round Int
     */
    def deleteFutureRounds(mixId: String, round: Int): Future[Unit] = db.run(hopMixes
      .filter(hop => hop.id === mixId && hop.round > round).delete).map(_ => ())

    /**
     * updates hop by id
     *
     * @param hopMix HopMix
     */
    def updateById(hopMix: HopMix): Future[Unit] = db.run(DBIO.seq(
        hopMixes.filter(hop => hop.id === hopMix.mixId && hop.round === hopMix.round).delete,
        hopMixes += hopMix
    ))

    /**
     * returns last hop round of mixId
     *
     * @param mixId String
     */
    def getHopRound(mixId: String): Future[Option[Int]] = db.run(hopMixes
      .filter(hop => hop.id === mixId)
      .groupBy(hop => hop.id)
      .map{ case (id, group) => group.map(_.round).max.get }
      .result.headOption
    )

}
