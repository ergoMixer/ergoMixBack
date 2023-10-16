package dao.stealth

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import helpers.StealthUtils
import models.StealthModels.{ExtractedOutput, StealthAssets}
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import wallet.WalletHelper

trait OutputComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class OutputTable(tag: Tag) extends Table[ExtractedOutput](tag, "OUTPUTS") {
    def boxId = column[String]("BOX_ID")

    def txId = column[String]("TX_ID")

    def headerId = column[String]("HEADER_ID")

    def value = column[Long]("VALUE")

    def creationHeight = column[Int]("CREATION_HEIGHT")

    def index = column[Short]("INDEX")

    def ergoTree = column[String]("ERGO_TREE")

    def timestamp = column[Long]("TIMESTAMP")

    def bytes = column[Array[Byte]]("BYTES")

    def withdrawAddress = column[String]("WITHDRAW_ADDRESS")

    def stealthId = column[String]("STEALTH_ID")

    def withdrawTxId = column[String]("WITHDRAW_TX_ID")

    def withdrawTxCreatedTime = column[Long]("WITHDRAW_TX_CREATED_TIME")

    def withdrawFailedReason = column[String]("WITHDRAW_FAILED_REASON")

    def spendBlockId = column[String]("SPEND_BLOCK_ID")

    def spendBlockHeight = column[Long]("SPEND_BLOCK_HEIGHT")

    def spendTxId = column[String]("SPEND_TX_ID")

    def * = (
      boxId,
      txId,
      headerId,
      value,
      creationHeight,
      index,
      ergoTree,
      timestamp,
      bytes,
      withdrawAddress.?,
      stealthId.?,
      withdrawTxId.?,
      withdrawTxCreatedTime.?,
      withdrawFailedReason.?,
      spendBlockId.?,
      spendBlockHeight.?,
      spendTxId.?
    ) <> (ExtractedOutput.tupled, ExtractedOutput.unapply)

    def pk = primaryKey("PK_OUTPUTS", (boxId, headerId))
  }

  class OutputForkTable(tag: Tag) extends Table[ExtractedOutput](tag, "OUTPUTS_FORK") {
    def boxId = column[String]("BOX_ID")

    def txId = column[String]("TX_ID")

    def headerId = column[String]("HEADER_ID")

    def value = column[Long]("VALUE")

    def creationHeight = column[Int]("CREATION_HEIGHT")

    def index = column[Short]("INDEX")

    def ergoTree = column[String]("ERGO_TREE")

    def timestamp = column[Long]("TIMESTAMP")

    def bytes = column[Array[Byte]]("BYTES")

    def withdrawAddress = column[String]("withdraw_ADDRESS")

    def stealthId = column[String]("STEALTH_ID")

    def withdrawTxId = column[String]("WITHDRAW_TX_ID")

    def withdrawTxCreatedTime = column[Long]("WITHDRAW_TX_CREATED_TIME")

    def withdrawFailedReason = column[String]("WITHDRAW_FAILED_REASON")

    def spendBlockId = column[String]("SPEND_BLOCK_ID")

    def spendBlockHeight = column[Long]("SPEND_BLOCK_HEIGHT")

    def spendTxId = column[String]("SPEND_TX_ID")

    def * = (
      boxId,
      txId,
      headerId,
      value,
      creationHeight,
      index,
      ergoTree,
      timestamp,
      bytes,
      withdrawAddress.?,
      stealthId.?,
      withdrawTxId.?,
      withdrawTxCreatedTime.?,
      withdrawFailedReason.?,
      spendBlockId.?,
      spendBlockHeight.?,
      spendTxId.?
    ) <> (ExtractedOutput.tupled, ExtractedOutput.unapply)

    def pk = primaryKey("PK_OUTPUTS_FORK", (boxId, headerId))
  }
}

@Singleton()
class OutputDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends OutputComponent
  with StealthComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val outputs     = TableQuery[OutputTable]
  val stealths    = TableQuery[StealthTable]
  val outputsFork = TableQuery[OutputForkTable]

  /**
   * inserts a output into db
   *
   * @param output output
   */
  def insert(output: ExtractedOutput): Future[Unit] = db.run(outputs += output).map(_ => ())

  /**
   * create query for insert data
   *
   * @param outputs Seq of output
   */
  def insert(outputs: Seq[ExtractedOutput]): Future[Unit] = db.run(this.outputs ++= outputs).map(_ => ())

  /**
   * @return all outputs in db
   */
  def all: Future[Seq[ExtractedOutput]] = db.run(outputs.result)

  /**
   * set the withdrawAddress for boxId if exist and have not been spent yet
   *
   * @param boxId           - String
   * @param withdrawAddress - String
   * @return
   */
  def updateWithdrawAddressIfBoxExist(boxId: String, withdrawAddress: String): Future[Unit] =
    db.run(
      outputs
        .filter(box => box.boxId === boxId && box.withdrawTxId.?.isEmpty)
        .map(_.withdrawAddress)
        .update(withdrawAddress)
    ).map(_ => ())

  /**
   * filter by given boxId
   *
   * @param boxId - String
   * @return ExtractedOutput if exist
   */
  def getById(boxId: String): Future[Option[ExtractedOutput]] =
    db.run(outputs.filter(_.boxId === boxId).result.headOption)

  /**
   * set stealthId by given boxId
   *
   * @param boxIds     - Seq[String]
   * @param stealthId - String
   * @return
   */
  def updateStealthId(boxIds: Seq[String], stealthId: String): Future[Unit] =
    db.run(outputs.filter(_.boxId.inSet(boxIds)).map(_.stealthId).update(stealthId)).map(_ => ())

  /**
   * set withdrawTxId and withdrawTxCreatedTime by given boxIds and withdrawTx
   *
   * @param boxIds     - Seq[String]
   * @param withdrawTx - withdrawTx
   * @return
   */
  def updateWithdrawTxInformation(boxIds: Seq[String], withdrawTx: String): Future[Unit] =
    db.run(
      outputs
        .filter(_.boxId.inSet(boxIds))
        .map(box =>
          (
            box.withdrawTxId,
            box.withdrawTxCreatedTime,
            box.withdrawFailedReason
          )
        )
        .update(
          (withdrawTx, WalletHelper.now, null)
        )
    ).map(_ => ())

  /**
   * set withdrawFailedReason by given boxIds
   *
   * @param boxIds     - Seq[String]
   * @param withdrawFailedReason - reason of failed withdraw
   * @return
   */
  def setWithdrawFailedReason(boxIds: Seq[String], withdrawFailedReason: String): Future[Unit] =
    db.run(outputs.filter(_.boxId.inSet(boxIds)).map(box => box.withdrawFailedReason).update(withdrawFailedReason))
      .map(_ => ())

  /**
   * @param careWithdrawAddress - Boolean check withdrawAddress set or no
   * @return list of unspent ExtractedOutput
   */
  def selectUnspentBoxes(careWithdrawAddress: Boolean = false): Future[Seq[ExtractedOutput]] = {
    val query = for {
      unspentBoxes <-
        if (careWithdrawAddress)
          outputs
            .filter(box => box.withdrawTxId.?.isEmpty && box.spendTxId.?.isEmpty && box.withdrawAddress.?.isDefined)
        else
          outputs
            .filter(box => box.withdrawTxId.?.isEmpty && box.spendTxId.?.isEmpty)
    } yield unspentBoxes
    db.run(query.result)
  }

  /**
   * select all stealth with aggregate of all assets
   *
   * @return list of unspent assets of stealth
   */
  def selectAllStealthWithTotalUnspentAssets(): Future[Seq[StealthAssets]] = {
    val query = for {
      outputQ <-
        DBIO.successful(
          outputs.filter(box => box.stealthId.?.isDefined && box.withdrawTxId.?.isEmpty && box.spendTxId.?.isEmpty)
        )
      joinQ <-
        stealths.joinLeft(outputQ).on((stealth, output) => stealth.stealthId === output.stealthId.?.get).result
      outputGroupQ <- DBIO.successful(
                        joinQ
                          .map(data => (data._1.stealthId, data._1.stealthName, data._1.pk, data._2))
                          .groupBy(_._1)
                          .mapValues { data =>
                            var extractOutputs: Seq[InputBox] = Seq.empty
                            data.foreach(box =>
                              if (box._4.isDefined)
                                extractOutputs :+= new InputBoxImpl(ErgoBoxSerializer.parseBytes(box._4.get.bytes))
                            )
                            val totalAssets = StealthUtils.getTotalAssets(extractOutputs)
                            StealthAssets(data.head._1, data.head._2, data.head._3, totalAssets._1, totalAssets._2.size)
                          }
                          .values
                          .toList
                      )
    } yield outputGroupQ
    db.run(query)
  }

  /**
   * filter unspent extracted outputs by given stealthId
   *
   * @param stealthId - String
   * @return list of ExtractedOutputs
   */
  def selectUnspentExtractedOutputsByStealthId(stealthId: String): Future[Seq[ExtractedOutput]] = {
    val query = for {
      outs <-
        outputs.filter(box => box.withdrawTxId.?.isEmpty && box.spendTxId.?.isEmpty && box.stealthId === stealthId)
    } yield outs
    db.run(query.result)
  }

  /**
   * filter extracted outputs by given stealthId
   *
   * @param stealthId - String
   * @return list of ExtractedOutputs
   */
  def selectExtractedOutputsByStealthId(stealthId: String): Future[Seq[ExtractedOutput]] = {
    val query = for {
      outs <- outputs.filter(box => box.stealthId === stealthId)
    } yield outs
    db.run(query.result)
  }

  /**
   * delete all output objects in table
   */
  def clear(): Future[Unit] =
    db.run(outputs.delete).map(_ => ())

  /**
   * delete all output objects in table after pruneHeight
   *
   * @param pruneHeight - Long
   */
  def pruneSpendOutputsAfterCreationHeight(pruneHeight: Long): Future[Int] = {
    val query = for {
      affectedNumber <-
        outputs
          .filter(box =>
            box.spendTxId.?.isDefined && box.spendBlockHeight.?.isDefined && box.spendBlockHeight.?.get < pruneHeight
          )
          .delete
      _ <- outputsFork
             .filter(box =>
               box.spendTxId.?.isDefined && box.spendBlockHeight.?.isDefined && box.spendBlockHeight.?.get < pruneHeight
             )
             .delete
    } yield affectedNumber
    db.run(query.transactionally)
  }

}
