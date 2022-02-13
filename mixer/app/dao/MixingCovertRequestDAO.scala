package dao

import javax.inject.{Inject, Singleton}
import models.Models.MixCovertRequest
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait MixingCovertRequestComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    implicit def BigInt2StringMapper =
        MappedColumnType.base[BigInt, String](
            bi => bi.toString,
            s => BigInt(s)
        )

    class MixCovertRequestTable(tag: Tag) extends Table[MixCovertRequest](tag, "MIXING_COVERT_REQUEST") {
        def nameCovert = column[String]("NAME_COVERT")

        def id = column[String]("MIX_GROUP_ID", O.PrimaryKey)

        def createdTime = column[Long]("CREATED_TIME")

        def depositAddress = column[String]("DEPOSIT_ADDRESS")

        def numRounds = column[Int]("NUM_TOKEN")

        def isManualCovert = column[Boolean]("IS_MANUAL_COVERT")

        def masterKey = column[BigInt]("MASTER_SECRET_GROUP")

        def * = (nameCovert, id, createdTime, depositAddress, numRounds, isManualCovert, masterKey) <> (MixCovertRequest.tupled, MixCovertRequest.unapply)
    }

}

@Singleton()
class MixingCovertRequestDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends MixingCovertRequestComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val covertRequests = TableQuery[MixCovertRequestTable]

    /**
     * inserts a request into MIXING_COVERT_REQUEST table
     *
     * @param req MixCovertRequest
     */
    def insert(req: MixCovertRequest): Future[Unit] = db.run(covertRequests += req).map(_ => ())

    /**
     * deletes all of requests
     *
     */
    def clear: Future[Unit] = db.run(covertRequests.delete).map(_ => ())

    /**
     * checks if the privateKey exists in table or not
     *
     * @param masterSecret BigInt
     */
    def existsByMasterKey(masterSecret: BigInt): Future[Boolean] = db.run(covertRequests.filter(req => req.masterKey === masterSecret).exists.result)

    /**
     * checks if the covertId exists in table or not
     *
     * @param covertId String
     */
    def existsById(covertId: String): Future[Boolean] = db.run(covertRequests.filter(req => req.id === covertId).exists.result)

    /**
     * updates nameCovert by covertId
     *
     * @param covertId String
     * @param nameCovert String
     */
    def updateNameCovert(covertId: String, nameCovert: String): Future[Unit] = {
        val query = for {
            req <- covertRequests if req.id === covertId
        } yield req.nameCovert
        db.run(query.update(nameCovert)).map(_ => ())
    }

    /**
     * selects all CovertRequests (CovertList)
     *
     */
    def all: Future[Seq[MixCovertRequest]] = db.run(covertRequests.result)

    /**
     * selects request by covertId
     *
     * @param covertId String
     */
    def selectCovertRequestByMixGroupId(covertId: String): Future[Option[MixCovertRequest]] = db.run(covertRequests.filter(req => req.id === covertId).result.headOption)
}
