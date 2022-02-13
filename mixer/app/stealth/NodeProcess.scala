package stealth

import app.Configs
import io.circe.Decoder
import io.circe.parser.parse
import models.StealthModel.{ExtractionInputResult, ExtractionInputResultModel, ExtractionOutputResult, ExtractionOutputResultModel, ExtractionResultModel, ExtractionRulesModel, ScanModel}
import models.StealthModel.Types.ScanId
import org.ergoplatform.ErgoBox
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.Header
import scalaj.http.{Http, HttpOptions}
import scorex.util.encode.Base16
import wallet.WalletHelper

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object RegexUtils {
  implicit class RichRegex(val underlying: Regex) extends AnyVal {
    def matches(s: String): Boolean = underlying.pattern.matcher(s).matches
  }
}

object NodeProcess {

  val node: String = Configs.nodes.head
  val serverUrl: String = s"${node}/"

  private def getJsonAsString(url: String): String = {
    Http(s"$url")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.readTimeout(10000))
      .asString
      .body
  }

  def lastHeight: Int = {
    val infoUrl = serverUrl + s"info"
    parse(getJsonAsString(infoUrl)).toTry match {
      case Success(infoJs) =>
        infoJs.hcursor.downField("fullHeight").as[Int].getOrElse(throw new Exception("can't parse fullHeight"))
      case Failure(exception) => throw exception
    }
  }

  def mainChainHeaderIdAtHeight(height: Int): Option[String] = {
    val blockUrl = serverUrl + s"blocks/at/$height"

    val parseResult = parse(getJsonAsString(blockUrl)).toOption.get

    val mainChainId = parseResult.as[Seq[String]].toOption.get.headOption
    mainChainId
  }

  def mainChainHeaderWithHeaderId(headerId: String): Option[Header] = {
    implicit val headerDecoder: Decoder[Header] = Header.jsonDecoder
    val blockHeaderUrl = serverUrl + s"blocks/$headerId/header"
    val parseResultBlockHeader = parse(getJsonAsString(blockHeaderUrl)).toOption.get
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

    val txsAsString = getJsonAsString(serverUrl + s"blocks/$headerId")
    val txsAsJson = parse(txsAsString).toOption.get

    val ergoFullBlock = txsAsJson.as[ErgoFullBlock].toOption
    ergoFullBlock
  }

  /**
   * check box.ErgoTree to be stealth
   * */
  def checkStealth(box: ErgoBox): Boolean = {
    import RegexUtils._
    val pattern = """(1004)((0e21)[a-fA-F0-9]{66}){4}(ceee7300ee7301ee7302ee7303)""".r
    if (pattern matches WalletHelper.toHexString(box.ergoTree.bytes)) {
      var gr = WalletHelper.toHexString(box.ergoTree.bytes).slice(8, 74)
      var gy = WalletHelper.toHexString(box.ergoTree.bytes).slice(78, 144)
      var ur = WalletHelper.toHexString(box.ergoTree.bytes).slice(148, 214)
      var uy = WalletHelper.toHexString(box.ergoTree.bytes).slice(218, 284)
      return true
    }
    false
  }

  /**
   *
   * @param box   ErgoBox
   * @param rules scanRules
   * @return Seq[ScanId], Sequence of match rules (scanID)
   */
  def checkBox(box: ErgoBox, rules: Seq[ScanModel]): Seq[ScanId] = {
    var validScanIds: Seq[ScanId] = Seq.empty
    rules.foreach(scanRule => {
      if (checkStealth(box)) validScanIds = validScanIds :+ scanRule.scanId
    })
    validScanIds
  }

  def processTransactions(
                           headerId: String,
                           extractionRules: ExtractionRulesModel
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
        val scanIds = checkBox(out, extractionRules.scans)
        if (scanIds.nonEmpty)
          createdOutputs += ExtractionOutputResult(
            out,
            ergoFullBlock.header,
            tx,
            scanIds
          )
      }
    }
    ExtractionResultModel(extractedInputs, createdOutputs)
  }
}
