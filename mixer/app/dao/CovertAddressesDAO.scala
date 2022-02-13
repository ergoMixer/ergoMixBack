package dao

import javax.inject.{Inject, Singleton}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait CovertAddressesComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class CovertAddressesTable(tag: Tag) extends Table[(String, String)](tag, "COVERT_ADDRESSES") {
        def covertId = column[String]("MIX_GROUP_ID")

        def address = column[String]("ADDRESS")

        def * = (covertId, address)

        def pk = primaryKey("pk_COVERT_DEFAULTS", (covertId, address))
    }

}

@Singleton()
class CovertAddressesDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends CovertAddressesComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val covertAddresses = TableQuery[CovertAddressesTable]

    /**
     * returns all addresses
     *
     */
    def all: Future[Seq[(String, String)]] = db.run(covertAddresses.result)

    /**
     * deletes all of addresses
     *
     */
    def clear: Future[Unit] = db.run(covertAddresses.delete).map(_ => ())

    /**
     * inserts an address into COVERT_ADDRESSES table
     *
     * @param covertId String
     * @param address String
     */
    def insert(covertId: String, address: String): Future[Unit] = db.run(covertAddresses += (covertId, address)).map(_ => ())

    /**
     * delete the address from the table by covertId
     *
     * @param covertId String
     */
    def delete(covertId: String): Future[Unit] = db.run(covertAddresses.filter(address => address.covertId === covertId).delete).map(_ => ())

    /**
     * selects the addresses by covertId
     *
     * @param covertId String
     */
    def selectAllAddressesByCovertId(covertId: String): Future[Seq[String]] = {
        val query = for {
            address <- covertAddresses if address.covertId === covertId
        } yield address.address
        db.run(query.result)
    }
}
