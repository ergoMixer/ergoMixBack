package dao.mixing

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait CovertAddressesComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class CovertAddressesTable(tag: Tag) extends Table[(String, String)](tag, "COVERT_ADDRESSES") {
    def groupId = column[String]("MIX_GROUP_ID")

    def address = column[String]("ADDRESS")

    def * = (groupId, address)

    def pk = primaryKey("pk_COVERT_DEFAULTS", (groupId, address))
  }

}

@Singleton()
class CovertAddressesDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends CovertAddressesComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val covertAddresses = TableQuery[CovertAddressesTable]

  /**
   * returns all addresses
   */
  def all: Future[Seq[(String, String)]] = db.run(covertAddresses.result)

  /**
   * deletes all of addresses
   */
  def clear: Future[Unit] = db.run(covertAddresses.delete).map(_ => ())

  /**
   * inserts an address into COVERT_ADDRESSES table
   *
   * @param groupId String
   * @param address String
   */
  def insert(groupId: String, address: String): Future[Unit] =
    db.run(covertAddresses += (groupId, address)).map(_ => ())

  /**
   * delete the address from the table by groupId
   *
   * @param groupId String
   */
  def delete(groupId: String): Future[Unit] =
    db.run(covertAddresses.filter(address => address.groupId === groupId).delete).map(_ => ())

  /**
   * selects the addresses by groupId
   *
   * @param groupId String
   */
  def selectAllAddressesByCovertId(groupId: String): Future[Seq[String]] = {
    val query = for {
      address <- covertAddresses if address.groupId === groupId
    } yield address.address
    db.run(query.result)
  }
}
