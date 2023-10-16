package dao.stealth

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import models.StealthModels.{ExtractedBlock, ExtractedInput, ExtractedOutput, TokenInformation}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

class ExtractionResultDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends ExtractedBlockComponent
  with OutputComponent
  with TokenInformationComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val extractedBlocks     = TableQuery[ExtractedBlockTable]
  val outputs             = TableQuery[OutputTable]
  val extractedBlocksFork = TableQuery[ExtractedBlockForkTable]
  val outputsFork         = TableQuery[OutputForkTable]
  val tokenQuery          = TableQuery[TokenInformationTable]

  /**
   * remove forked block with extractedBlock.id, store extractedBlock and store outputs into db transactional.
   *
   * @param createdOutputs : ExtractedOutputModel extracted outputs
   * @param extractedBlock : ExtractedBlockModel extracted block
   */
  def storeOutputsAndRelatedData(
    createdOutputs: Seq[ExtractedOutput],
    extractedBlock: ExtractedBlock,
    tokens: Seq[TokenInformation],
    extractedInputs: Seq[ExtractedInput]
  ): Future[Unit] = {
    val query = for {
      _ <- this.extractedBlocksFork.filter(_.id === extractedBlock.id).delete
      _ <- this.extractedBlocks += extractedBlock
      _ <- this.outputsFork.filter(_.boxId.inSet(createdOutputs.map(_.boxId))).delete
      _ <- this.outputs ++= createdOutputs
      _ <- this.tokenQuery ++= tokens
      _ <- DBIO.seq(extractedInputs.map { input =>
             for {
               outputQ      <- DBIO.successful(this.outputs.filter(obj => obj.boxId === input.boxId))
               outputOption <- outputQ.result.headOption
               _ <- if (outputOption.isDefined)
                      outputQ
                        .map(box => (box.spendBlockId, box.spendBlockHeight, box.spendTxId))
                        .update((input.headerId, input.spendBlockHeight, input.txId))
                    else DBIO.seq()
             } yield {}
           }: _*)
    } yield {}
    db.run(query.transactionally)
  }

  /**
   * Migrate blocks from a detected fork to alternate tables.
   *
   * @param height height of block to be migrated
   */
  def migrateBlockByHeight(height: Int): Future[Unit] = {
    val action = for {
      headerId <- this.extractedBlocks.filter(_.height === height).map(_.id).result.headOption
      blocksQ  <- DBIO.successful(this.extractedBlocks.filter(_.id === headerId.getOrElse("")))
      blocks   <- blocksQ.result
      _        <- (this.extractedBlocksFork ++= blocks).andThen(blocksQ.delete)
      outputsQ <- DBIO.successful(this.outputs.filter(_.headerId === headerId.getOrElse("")))
      outputs  <- outputsQ.result
      _        <- (this.outputsFork ++= outputs).andThen(outputsQ.delete)
      _ <- this.outputs
             .filter(_.spendBlockId === headerId.getOrElse(""))
             .map(output => (output.spendBlockId.?, output.spendBlockHeight.?, output.spendTxId.?))
             .update((null, null, null))
    } yield {}
    db.run(action.transactionally)
  }

}
