package models

import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import org.ergoplatform.{ErgoBox, ErgoLikeTransaction, Input, JsonCodecs}
import org.ergoplatform.appkit.{ErgoToken, InputBox}
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import play.api.Logger
import scorex.util.encode.Base16
import special.sigma.GroupElement

object StealthModels extends JsonCodecs {
  private val logger: Logger = Logger(this.getClass)

  case class ExtractedTransaction(id: String, inputs: Seq[Input], outputs: Seq[ErgoBox])

  object CreateExtractedTransaction {
    implicit val extractedTransactionDecoder: Decoder[ExtractedTransaction] = { cursor =>
      for {
        ergoLikeTx <- cursor.as[ErgoLikeTransaction]
      } yield ExtractedTransaction(ergoLikeTx.id, ergoLikeTx.inputs, ergoLikeTx.outputs)
    }
  }

  case class ExtractedBlock(id: String, parentId: String, height: Int, timestamp: Long)

  object CreateExtractedBlock {
    implicit val extractedBlockDecoder: Decoder[ExtractedBlock] = deriveDecoder[ExtractedBlock]

    def apply(jsonString: String): Option[ExtractedBlock] =
      parser.decode[ExtractedBlock](jsonString) match {
        case Left(e) =>
          logger.warn(s"couldn't parse ExtractedBlock from Json: $e")
          Option.empty
        case Right(asset) => Option(asset)
      }
  }

  case class ExtractedFullBlock(header: ExtractedBlock, transactions: Seq[ExtractedTransaction])

  object CreateExtractedFullBlock {
    implicit private val extractedTransactionDecoder: Decoder[ExtractedTransaction] =
      CreateExtractedTransaction.extractedTransactionDecoder
    implicit private val extractedBlockDecoder: Decoder[ExtractedBlock] = CreateExtractedBlock.extractedBlockDecoder
    implicit private val extractedFullBlockDecoder: Decoder[ExtractedFullBlock] = { c: HCursor =>
      for {
        header <- c.downField("header").as[ExtractedBlock]
        transactions <- c.downField("blockTransactions")
                          .as[Json]
                          .toOption
                          .get
                          .hcursor
                          .downField("transactions")
                          .as[Seq[ExtractedTransaction]]
      } yield ExtractedFullBlock(header, transactions)
    }

    def apply(jsonString: String): Option[ExtractedFullBlock] =
      parser.decode[ExtractedFullBlock](jsonString) match {
        case Left(e) =>
          logger.warn(s"couldn't parse ExtractedFullBlock from Json: $e")
          Option.empty
        case Right(asset) => Option(asset)
      }
  }

  case class ExtractedInput(
    boxId: String,
    headerId: String,
    spendBlockHeight: Long,
    txId: String,
  )

  object CreateExtractedInput {
    def apply(input: Input, headerId: String, spendBlockHeight: Long, txId: String): ExtractedInput =
      ExtractedInput(
        Base16.encode(input.boxId),
        headerId,
        spendBlockHeight,
        txId
      )
  }

  case class ExtractedOutput(
    boxId: String,
    txId: String,
    headerId: String,
    value: Long,
    creationHeight: Int,
    index: Short,
    ergoTree: String,
    timestamp: Long,
    bytes: Array[Byte],
    withdrawAddress: Option[String]      = null,
    stealthId: Option[String]            = null,
    withdrawTxId: Option[String]         = null,
    withdrawTxCreatedTime: Option[Long]  = null,
    withdrawFailedReason: Option[String] = null,
    spendBlockId: Option[String]         = null,
    spendBlockHeight: Option[Long]       = null,
    spendTxId: Option[String]            = null
  )

  object CreateExtractedOutput {
    def apply(ergoBox: ErgoBox, headerId: String, timestamp: Long): ExtractedOutput =
      ExtractedOutput(
        Base16.encode(ergoBox.id),
        ergoBox.transactionId,
        headerId,
        ergoBox.value,
        ergoBox.creationHeight,
        ergoBox.index,
        ergoBox.ergoTree.bytesHex,
        timestamp,
        ergoBox.bytes
      )
  }

  case class ExtractedOutputController(extractedOutput: ExtractedOutput, tokensInformation: Seq[TokenInformation])

  case class Stealth(stealthId: String, stealthName: String, pk: String, secret: BigInt) {
    def toStealthInfo: StealthInfo =
      StealthInfo(stealthId, stealthName, pk)
  }

  case class StealthInfo(stealthId: String, stealthName: String, pk: String)

  case class StealthAssets(stealthId: String, stealthName: String, pk: String, value: Long, assetsSize: Int)

  case class TokenInformation(id: String, name: Option[String] = null, decimals: Option[Int] = null)

  object CreateTokenInformation {
    implicit val tokenInformationDecoder: Decoder[TokenInformation] = deriveDecoder[TokenInformation]

    def apply(jsonString: String): Option[TokenInformation] =
      parser.decode[TokenInformation](jsonString) match {
        case Left(e) =>
          logger.warn(s"couldn't parse TokenInformation from Json: $e")
          Option.empty
        case Right(asset) => Option(asset)
      }
  }

  case class SpendBox(box: InputBox, withdrawAddress: String, stealthId: String)

  object CreateSpendBox {
    def apply(box: ExtractedOutput): SpendBox =
      SpendBox(new InputBoxImpl(ErgoBoxSerializer.parseBytes(box.bytes)), box.withdrawAddress.get, box.stealthId.get)
  }

  case class AssetFunds(user: (Long, Seq[ErgoToken]), service: (Long, Seq[ErgoToken]))

  case class StealthDHTData(gr: GroupElement, gy: GroupElement, ur: GroupElement, uy: GroupElement)

}
