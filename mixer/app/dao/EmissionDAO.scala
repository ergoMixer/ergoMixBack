package dao

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait EmissionComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class EmissionTable(tag: Tag) extends Table[(String, Int, String)](tag, "EMISSION_BOX") {
        def id = column[String]("MIX_ID")

        def round = column[Int]("ROUND")

        def boxId = column[String]("BOX_ID", O.PrimaryKey)

        def * = (id, round, boxId)
    }

}

@Singleton()
class EmissionDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends EmissionComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val emissions = TableQuery[EmissionTable]

    /**
     * selects emission boxId by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def selectBoxId(mixId: String, round: Int): Future[Option[String]] = {
        val query = for {
            box <- emissions if box.id === mixId && box.round === round
        } yield box.boxId
        db.run(query.result.headOption)
    }

    /**
     * checks if the boxId exists in table or not
     *
     * @param boxId String
     */
    def existsByBoxId(boxId: String): Future[Boolean] = db.run(emissions.filter(box => box.boxId === boxId).exists.result)

    /**
     * inserts a box into EMISSION_BOX table
     *
     * @param mixId String
     * @param round Int
     * @param boxId String
     */
    def insert(mixId: String, round: Int, boxId: String): Future[Unit] = db.run(emissions += (mixId, round, boxId)).map(_ => ())

    /**
     * delete box by mixId
     *
     * @param mixId String
     */
    def delete(mixId: String): Unit = db.run(emissions.filter(box => box.id === mixId).delete).map(_ => ())
}
