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
        def mixId = column[String]("MIX_ID")

        def round = column[Int]("ROUND")

        def createdTime = column[Long]("CREATED_TIME")

        def boxId = column[String]("BOX_ID")

        def * = (mixId, round, createdTime, boxId) <> (HopMix.tupled, HopMix.unapply)

        def pk = primaryKey("pk_HOP_MIX", (mixId, round))
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
     * deletes hops by mixId
     *
     * @param mixId String
     */
    def delete(mixId: String): Future[Unit] = db.run(hopMixes.filter(hop => hop.mixId === mixId).delete).map(_ => ())

    /**
     * deletes future hops by mixId
     *
     * @param mixId String
     * @param round Int
     */
    def deleteFutureRounds(mixId: String, round: Int): Future[Unit] = db.run(hopMixes
      .filter(hop => hop.mixId === mixId && hop.round > round).delete).map(_ => ())

    /**
     * updates hop by id
     *
     * @param hopMix HopMix
     */
    def updateById(hopMix: HopMix): Future[Unit] = db.run(DBIO.seq(
        hopMixes.filter(hop => hop.mixId === hopMix.mixId && hop.round === hopMix.round).delete,
        hopMixes += hopMix
    ))

    /**
     * returns last hop round of mixId
     *
     * @param mixId String
     */
    def getHopRound(mixId: String): Future[Option[Int]] = db.run(hopMixes
      .filter(hop => hop.mixId === mixId)
      .groupBy(hop => hop.mixId)
      .map{ case (_, group) => group.map(_.round).max.get }
      .result.headOption
    )

    /**
     * selects hopMixBoxId by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def getMixBoxIdByRound(mixId: String, round: Int): Future[Option[String]] = db.run(hopMixes.filter(mix =>
        mix.mixId === mixId && mix.round === round).map(_.boxId).result.headOption)

}
