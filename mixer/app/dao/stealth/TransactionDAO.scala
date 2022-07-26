package dao.stealth

import javax.inject.{Inject, Singleton}
import models.StealthModels.ExtractedTransactionModel
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait TransactionComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class TransactionTable(tag: Tag) extends Table[ExtractedTransactionModel](tag, "TRANSACTIONS") {
    def id = column[String]("ID")
    def headerId = column[String]("HEADER_ID")
    def inclusionHeight = column[Int]("INCLUSION_HEIGHT")
    def timestamp = column[Long]("TIMESTAMP")
    def * = (id, headerId, inclusionHeight, timestamp) <> (ExtractedTransactionModel.tupled, ExtractedTransactionModel.unapply)
  }

  class TransactionForkTable(tag: Tag) extends Table[ExtractedTransactionModel](tag, "TRANSACTIONS_FORK") {
    def id = column[String]("ID")
    def headerId = column[String]("HEADER_ID")
    def inclusionHeight = column[Int]("INCLUSION_HEIGHT")
    def timestamp = column[Long]("TIMESTAMP")
    def * = (id, headerId, inclusionHeight, timestamp) <> (ExtractedTransactionModel.tupled, ExtractedTransactionModel.unapply)
  }
}

@Singleton()
class TransactionDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends TransactionComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val transactions = TableQuery[TransactionTable]
  val transactionsFork = TableQuery[TransactionForkTable]

  /**
   * inserts a tx into db
   * @param transaction transaction
   */
  def insert(transaction: ExtractedTransactionModel): Future[Unit] = db.run(transactions += transaction).map(_ => ())

  /**
   * create query for insert data
   * @param transactions transaction
   */
  def insert(transactions: Seq[ExtractedTransactionModel]): DBIO[Option[Int]] = this.transactions ++= transactions

  def insertIfDoesNotExist(transactions: Seq[ExtractedTransactionModel]): DBIO[Option[Int]] = {
       val ids = transactions.map(_.id)
       (for {
         existing <- this.transactions.filter(_.id inSet ids).result
         filtered = transactions.filterNot(tx => existing.contains(tx))
         count <- insert(filtered)
       } yield count).transactionally
  }

  /**
   * exec insert query
   * @param transactions Seq of transaction
   */
  def save(transactions: Seq[ExtractedTransactionModel]): Future[Unit] = {
    db.run(insert(transactions)).map(_ => ())
  }

  /**
   * @param txId transaction id
   * @return whether tx exists or not
   */
  def exists(txId: String): Future[Boolean] = {
    db.run(transactions.filter(_.id === txId).exists.result)
  }

  /**
   * deletes all txs from db
   */
  def deleteAll(): Future[Int] = {
    db.run(transactions.delete)
  }

  /**
   * @param headerId header id
   */
  def migrateForkByHeaderId(headerId: String): DBIO[Int] = {
    getByHeaderId(headerId)
      .map(transactionsFork ++= _)
      .andThen(deleteByHeaderId(headerId))
  }

  /**
   * @param headerId header id
   * @return Transaction record(s) associated with the header
   */
  def getByHeaderId(headerId: String): DBIO[Seq[TransactionTable#TableElementType]] = {
    transactions.filter(_.headerId === headerId).result
  }

  /**
   * @param headerId header id
   * @return Number of rows deleted
   */
  def deleteByHeaderId(headerId: String): DBIO[Int] = {
    transactions.filter(_.headerId === headerId).delete
  }

}
