package dao.admin

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.Admin.CommissionIncome
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import wallet.WalletHelper

trait CommissionIncomeComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class CommissionIncomeTable(tag: Tag) extends Table[CommissionIncome](tag, "COMMISSION_INCOME") {
    def tokenId = column[String]("TOKEN_ID")

    def ring = column[Long]("RING")

    def numEntered = column[Int]("NUM_ENTERED")

    def commission = column[Long]("COMMISSION")

    def donation = column[Long]("DONATION")

    def timestamp = column[Long]("TIMESTAMP")

    def * = (
      timestamp,
      tokenId,
      ring,
      numEntered,
      commission,
      donation
    ) <> (CommissionIncome.tupled, CommissionIncome.unapply)

    def pk = primaryKey("pk_COMMISSION", (timestamp, tokenId, ring))
  }
}

@Singleton()
class CommissionIncomeDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends CommissionIncomeComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val commissions = TableQuery[CommissionIncomeTable]

  /**
   * get commission incomes between start and end time
   * @param start
   * @param end
   * @return
   */
  def getCommissionIncome(start: Long, end: Long): Future[Map[String, (Map[Long, Int], Long, Long, Long)]] = {
    val realEnd = if (end == 0) WalletHelper.now else end
    val query = for {
      commissionIncomeQ <- commissions.filter(obj => obj.timestamp >= start && obj.timestamp <= realEnd).result
      ringEntry <- DBIO.successful(commissionIncomeQ.groupBy(_.tokenId).mapValues { ins =>
                     val ringStat        = ins.groupBy(_.ring).mapValues(_.map(_.numEntered).sum)
                     val totalCommission = ins.map(_.commission).sum
                     val totalDonation   = ins.map(_.donation).sum
                     val totalEntered    = ins.map(in => in.ring * in.numEntered).sum
                     (ringStat, totalCommission, totalDonation, totalEntered)
                   })
    } yield ringEntry
    db.run(query)
  }
}
