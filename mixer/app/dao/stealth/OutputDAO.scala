package dao.stealth

import javax.inject.{Inject, Singleton}
import models.StealthModel.ExtractedOutputModel
import models.StealthModel.Types.ScanId
import org.ergoplatform.ErgoBox
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import helpers.DbUtils

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait OutputComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class OutputTable(tag: Tag) extends Table[ExtractedOutputModel](tag, "OUTPUTS") {
    def boxId = column[String]("BOX_ID")
    def txId = column[String]("TX_ID")
    def headerId = column[String]("HEADER_ID")
    def value = column[Long]("VALUE")
    def creationHeight = column[Int]("CREATION_HEIGHT")
    def index = column[Short]("INDEX")
    def ergoTree = column[String]("ERGO_TREE")
    def timestamp = column[Long]("TIMESTAMP")
    def spent = column[Boolean]("SPENT", O.Default(false))
    def bytes = column[Array[Byte]]("BYTES")
    def spendAddress = column[String]("SPEND_ADDRESS")
    def stealthId = column[String]("STEALTH_ID")
    def * = (boxId, txId, headerId, value, creationHeight, index, ergoTree, timestamp, bytes, spent, spendAddress, stealthId) <> (ExtractedOutputModel.tupled, ExtractedOutputModel.unapply)
    def pk = primaryKey("PK_OUTPUTS", (boxId, headerId))
  }

  class OutputForkTable(tag: Tag) extends Table[ExtractedOutputModel](tag, "OUTPUTS_FORK") {
    def boxId = column[String]("BOX_ID")
    def txId = column[String]("TX_ID")
    def headerId = column[String]("HEADER_ID")
    def value = column[Long]("VALUE")
    def creationHeight = column[Int]("CREATION_HEIGHT")
    def index = column[Short]("INDEX")
    def ergoTree = column[String]("ERGO_TREE")
    def timestamp = column[Long]("TIMESTAMP")
    def spent = column[Boolean]("SPENT", O.Default(false))
    def bytes = column[Array[Byte]]("BYTES")
    def spendAddress = column[String]("SPEND_ADDRESS")
    def stealthId = column[String]("STEALTH_ID")
    def pk = primaryKey("PK_OUTPUTS_FORK", (boxId, headerId))
    def * = (boxId, txId, headerId, value, creationHeight, index, ergoTree, timestamp, bytes, spent, spendAddress, stealthId) <> (ExtractedOutputModel.tupled, ExtractedOutputModel.unapply)
  }
}

@Singleton()
class OutputDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends DbUtils with OutputComponent with ExtractedBlockComponent with ScanComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val outputs = TableQuery[OutputTable]
  val outputsFork = TableQuery[OutputForkTable]
  val scan_outputs = TableQuery[ScanOutputsPivotTable]
  val extractedBlocks = TableQuery[ExtractedBlockTable]

  /**
   * inserts a output into db
   * @param output output
   */
  def insert(output: ExtractedOutputModel ): Future[Unit] = db.run(outputs += output).map(_ => ())

  /**
   * create query for insert data
   * @param outputs Seq of output
   */
  def insert(outputs: Seq[ExtractedOutputModel]): DBIO[Option[Int]] = this.outputs ++= outputs

  /**
   * exec insert query
   * @param outputs Seq of input
   */
  def save(outputs: Seq[ExtractedOutputModel]): Future[Unit] = {
    db.run(insert(outputs)).map(_ => ())
  }

  def doSpentIfExist(boxId: String, txId: String): Future[Unit] = {
    db.run(outputs.filter(_.boxId === boxId).map(_.txId).update(txId)).map(_ => ())
  }

  /**
   * @param boxId box id
   * @return whether this box exists for a specific boxId or not
   */
  def exists(boxId: String): Boolean = {
    val res = db.run(outputs.filter(_.boxId === boxId).exists.result)
    Await.result(res, 5.second)
  }

  /**
   * @param headerId header id
   */
  def migrateForkByHeaderId(headerId: String): DBIO[Int] = {
    getByHeaderId(headerId)
      .map(outputsFork ++= _)
      .andThen(deleteByHeaderId(headerId))
  }

  /**
   * @param headerId header id
   * @return Output record(s) associated with the header
   */
  def getByHeaderId(headerId: String): DBIO[Seq[OutputTable#TableElementType]] = {
    outputs.filter(_.headerId === headerId).result
  }

  /**
   * @param headerId header id
   * @return Number of rows deleted
   */
  def deleteByHeaderId(headerId: String): DBIO[Int] = {
    outputs.filter(_.headerId === headerId).delete
  }

  /**
   * @param headerId header id
   * @return Box id(s) associated with the header
   */
  def getBoxIdsByHeaderIdQuery(headerId: String): DBIO[Seq[String]] = {
    outputs.filter(_.headerId === headerId).map(_.boxId).result
  }

  /**
  *
   * @param scanId Types.ScanId (Int)
   * @param maxHeight Int
   * @param minInclusionHeight Int
   * @return Sequence of Ergo boxes that are unspent and mined before "maxHeight" (for consider minConfirmation) and also mined after "minInclusionHeight"
   */
  def selectUnspentBoxesWithScanId(scanId: ScanId, maxHeight: Int, minInclusionHeight: Int): Seq[ErgoBox] = {
    val query = for {
      ((scans, outs), blocks) <- scan_outputs.filter(_.scanId === scanId) join
        outputs.filter(!_.spent) on ((scanFK, output) => {scanFK.boxId === output.boxId && scanFK.headerId === output.headerId}) join
        extractedBlocks.filter(block =>  {block.height <= maxHeight && block.height > minInclusionHeight}) on (_._2.headerId === _.id)
    } yield (outs.bytes)
    execAwait(query.result).map(ErgoBoxSerializer.parseBytes)
  }

  def all: Future[Seq[ExtractedOutputModel]] = db.run(outputs.result)

  def addSpendAddressIfExist(boxId: String, spendAddress: String): Future[Unit] = {
    db.run(outputs.filter(_.boxId === boxId).map(_.spendAddress).update(spendAddress)).map(_ => ())
  }

  def getById(boxId: String): Future[Option[ExtractedOutputModel]] = {
    db.run(outputs.filter(_.boxId === boxId).result.headOption)
  }

  def selectUnspentBoxesHaveSpendAddress(): Future[Seq[ExtractedOutputModel]] = {
    db.run(outputs.filter(req => !req.spent && req.spendAddress.toString().nonEmpty).result)
  }

  def updateStealthId(boxId: String, stealthId: String): Unit ={
    db.run(outputs.filter(_.boxId === boxId).map(_.stealthId).update(stealthId)).map(_ => ())
  }

  def selectUnspentBoxesByStealthId(stealthId: String): Seq[ErgoBox] = {
    val query = for {
      outs <-  outputs.filter(req => !req.spent && req.stealthId === stealthId)
    } yield (outs.bytes)
    execAwait(query.result).map(ErgoBoxSerializer.parseBytes)
  }

  def getUnspentBoxesValuesByStealthId(stealthId: String): Future[Option[Long]] = {
    db.run(outputs.filter(box => box.stealthId === stealthId).map(_.value).sum.result)
  }


}
