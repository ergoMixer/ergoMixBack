package helpers

import app.Configs
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{ErgoToken, InputBox}
import play.api.Logger
import special.sigma.GroupElement
import wallet.WalletHelper
import network.NetworkUtils
import scala.collection.mutable

class StealthUtils(networkUtils: NetworkUtils){
  private val logger: Logger = Logger(this.getClass)

  def convertToInputBox(box: ErgoBox): InputBox = {
    val tokens = mutable.Buffer[ErgoToken]()
    box.additionalTokens.toArray.foreach(token =>
      tokens += new ErgoToken(token._1, token._2)
    )
    networkUtils.usingClient { implicit ctx =>
      val txB = ctx.newTxBuilder()
      val input = txB.outBoxBuilder()
        .value(box.value)
        .tokens(tokens: _*)
        .contract(new ErgoTreeContract(box.ergoTree))
        .build().convertToInputWith(box.id.toString, 1)
      input
    }
  }

  def getTokens(inputList: List[InputBox]): Seq[ErgoToken] = {
    val inputsTokens = mutable.Buffer[ErgoToken]()
    inputList.foreach( input =>{
      input.getTokens.forEach(token => inputsTokens += token)
    })
    inputsTokens
  }

  def feeCalculator(inputList: List[InputBox]): Long = {
    val values = inputList.map(_.getValue.toLong).sum
    (values * Configs.stealthTransactionFeePercent).toLong + Configs.stealthImplementorFee
  }

  def getDHTDataFromErgoTree(ergoTree: String, start: Int, until: Int): GroupElement = {
    WalletHelper.hexToGroupElement(ergoTree.slice(start, until))
  }
}
