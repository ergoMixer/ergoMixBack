package dao

import models.Models.CovertAsset
import models.Request.MixCovertRequest
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class CovertsDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends CovertAddressesComponent with CovertDefaultsComponent with MixingCovertRequestComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val covertAddresses = TableQuery[CovertAddressesTable]
    val covertAssets = TableQuery[CovertDefaultsTable]
    val covertRequests = TableQuery[MixCovertRequestTable]

    /**
     * selects unspent and spent deposits by address
     *
     * @param addresses String
     */
    def createCovert(req: MixCovertRequest, addresses: Seq[(String, String)], asset: CovertAsset): Future[Unit] = {
        val query = for {
            _ <- DBIO.seq(covertRequests += req, covertAddresses ++= addresses, covertAssets += asset).transactionally
        } yield { }
        db.run(query)
    }
}
