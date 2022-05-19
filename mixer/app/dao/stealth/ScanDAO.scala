package dao.stealth

import dao.DAOUtils

import javax.inject.{Inject, Singleton}
import models.StealthModels.Types.ScanId
import models.StealthModels._
import org.ergoplatform.nodeView.wallet.scanning.{ScanningPredicate, ScanningPredicateSerializer}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait ScanComponent extends OutputComponent{ self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  implicit val ScanningPredicateColumnType = MappedColumnType.base[ScanningPredicate, Array[Byte]](
     s => ScanningPredicateSerializer.toBytes(s),
     i => ScanningPredicateSerializer.parseBytes(i)
  )

  class ScanTable(tag: Tag) extends Table[ScanModel](tag, "SCANS") {
    def scanId = column[Int]("SCAN_ID", O.PrimaryKey, O.AutoInc)
    def scanName = column[String]("SCAN_NAME")
    def scanningPredicate = column[ScanningPredicate]("SCANNING_PREDICATE")
    def * = (scanId, scanName, scanningPredicate) <> (ScanModel.tupled, ScanModel.unapply)
  }

  private val scansTable = TableQuery[ScanTable]
  private val outputsTable = TableQuery[OutputTable]

  class ScanOutputsPivotTable(tag: Tag) extends Table[(Int, String, String)](tag, "SCAN_OUTPUTS_PIVOT") {
    def scanId = column[Int]("SCAN_ID", O.PrimaryKey)
    def boxId = column[String]("BOX_ID")
    def headerId = column[String]("HEADER_ID")
    def * = (scanId, boxId, headerId)
    def pk = primaryKey("PK_OUTPUTS", (boxId, headerId))
    def outputs = foreignKey("OUTPUT_FK", (boxId, headerId), outputsTable)(ot => (ot.boxId, ot.headerId), onDelete=ForeignKeyAction.SetNull)
    def scans = foreignKey("SCAN_FK", scanId, scansTable)(_.scanId, onDelete=ForeignKeyAction.SetNull)
  }

}

@Singleton()
class ScanDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider, daoUtils: DAOUtils)(implicit executionContext: ExecutionContext)
  extends ScanComponent
    with HasDatabaseConfigProvider[JdbcProfile]{

  import profile.api._

  val scans = TableQuery[ScanTable]
  val scanOutputsPivot = TableQuery[ScanOutputsPivotTable]

  /**
   * inserts a scan into db
   * @param scan Scan
   */
  def insert(scan: ScanModel): Unit = daoUtils.execAwait(DBIO.seq(scans += scan).map(_ => ()))
  val insertQuery = scans returning scans.map(_.scanId) into ((item, id) => item.copy(scanId = id))

  def create(scan: ScanModel) : ScanModel = {
    daoUtils.execAwait(insertQuery += scan)
  }

  /**
  *
   * @param scan_FKs: Seq[(ScanId, box_id, header_id)]
   * @return
   */
  def insertFKs(scan_FKs: Seq[(ScanId, String, String)]): DBIO[Option[Int]] = scanOutputsPivot ++= scan_FKs

  /**
   * @param scanId Types.ScanId
   * @return whether this scan exists for a specific scanId or not
   */
  def exists(scanId: Types.ScanId): DBIO[Boolean] = {
    scans.filter(_.scanId === scanId).exists.result
  }


  /**
   * @param scanId Types.ScanId
   * @return Number of rows deleted
   */
  def deleteById(scanId: Types.ScanId): Future[ScanId] = {
    db.run(scans.filter(_.scanId === scanId).delete)
  }

  /**
   * @return Int number of scanning rules
   */
  def count(): Future[ScanId] = {
    db.run(scans.length.result)
  }

  /**
   * @return All scan record(s)
   */
  def selectAll: ExtractionRulesModel = {
    ExtractionRulesModel(daoUtils.execAwait(scans.result))
  }

}
