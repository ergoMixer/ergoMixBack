package stealth

import app.Configs
import io.circe.Decoder
import io.circe.parser.parse
import models.StealthModels.{ExtractionInputResult, ExtractionInputResultModel, ExtractionOutputResult, ExtractionOutputResultModel, ExtractionResultModel}
import network.NetworkUtils
import org.ergoplatform.ErgoBox
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.Header
import scorex.util.encode.Base16

import javax.inject.Inject
import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object RegexUtils {
  implicit class RichRegex(val underlying: Regex) extends AnyVal {
    def matches(s: String): Boolean = underlying.pattern.matcher(s).matches
  }
}

class NodeProcess @Inject()(networkUtils: NetworkUtils){

  val node: String = Configs.nodes.head
  val serverUrl: String = s"$node/"

  def lastHeight: Int = {
    val infoUrl = serverUrl + s"info"
    parse(networkUtils.getJsonAsString(infoUrl)).toTry match {
      case Success(infoJs) =>
        infoJs.hcursor.downField("fullHeight").as[Int].getOrElse(throw new Exception("can't parse fullHeight"))
      case Failure(exception) => throw exception
    }
  }

  def mainChainHeaderIdAtHeight(height: Int): Option[String] = {
    val blockUrl = serverUrl + s"blocks/at/$height"

    val parseResult = parse(networkUtils.getJsonAsString(blockUrl)).toOption.get

    val mainChainId = parseResult.as[Seq[String]].toOption.get.headOption
    mainChainId
  }

  def mainChainHeaderWithHeaderId(headerId: String): Option[Header] = {
    implicit val headerDecoder: Decoder[Header] = Header.jsonDecoder
    val blockHeaderUrl = serverUrl + s"blocks/$headerId/header"
    val parseResultBlockHeader = parse(networkUtils.getJsonAsString(blockHeaderUrl)).toOption.get
    val blockHeader = parseResultBlockHeader.as[Header].toOption
    blockHeader
  }

  def mainChainHeaderAtHeight(height: Int): Option[Header] = {
    val mainChainId = mainChainHeaderIdAtHeight(height)
    if (mainChainId.nonEmpty) mainChainHeaderWithHeaderId(mainChainId.get)
    else None
  }

  def mainChainFullBlockWithHeaderId(headerId: String): Option[ErgoFullBlock] = {
    implicit val txDecoder: Decoder[ErgoFullBlock] = ErgoFullBlock.jsonDecoder

    val txsAsString = networkUtils.getJsonAsString(serverUrl + s"blocks/$headerId")
    val txsAsJson = parse(txsAsString).toOption.get

    val ergoFullBlock = txsAsJson.as[ErgoFullBlock].toOption
    ergoFullBlock
  }

  /**
   * check box.ErgoTree to be stealth.
   * */
  def checkStealth(box: ErgoBox): Boolean = {
    import RegexUtils._
    val pattern = """(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)""".r
    if (pattern matches Base16.encode(box.ergoTree.bytes)) {
      return true
    }
    false
  }

  def processTransactions(
                           headerId: String,
                         ): ExtractionResultModel = {

    val ergoFullBlock = mainChainFullBlockWithHeaderId(headerId).get

    val createdOutputs = mutable.Buffer[ExtractionOutputResultModel]()
    val extractedInputs = mutable.Buffer[ExtractionInputResultModel]()
    ergoFullBlock.transactions.foreach { tx =>
      tx.inputs.zipWithIndex.map {
        case (input, index) =>
          extractedInputs += ExtractionInputResult(
            input,
            index.toShort,
            ergoFullBlock.header,
            tx
          )
      }
      tx.outputs.foreach { out =>
        if (checkStealth(out)) {
          createdOutputs += ExtractionOutputResult(
            out,
            ergoFullBlock.header,
            tx
          )
        }
      }
    }
    ExtractionResultModel(extractedInputs, createdOutputs)
  }
}
