package mixer

import java.security.SecureRandom
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date

import app.Configs
import cli.ErgoMixCLIUtil
import mixer.Models.EntityInfo
import play.api.Logger

object ErgoMixerUtils {
  private val logger: Logger = Logger(this.getClass)

  // The minimum number of confirmations for a current mix transaction before proceeding to next step
  val minConfirmations: Int = Configs.numConfirmation

  def getFee(tokenId: String, tokenAmount: Long, ergAmount: Long, isFull: Boolean): Long = {
    if (tokenId.nonEmpty) {
      if (isFull) Configs.defaultFullTokenFee
      else Configs.defaultHalfTokenFee

    } else {
      if (isFull) Configs.defaultFullFee
      else Configs.defaultHalfFee
    }
  }

  def getStackTraceStr(e: Throwable): String = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    sw.toString
  }

  def isDoubleSpent(boxId:String, wrtBoxId:String): Boolean = ErgoMixCLIUtil.usingClient{ implicit ctx =>
    val explorer: BlockExplorer = new BlockExplorer()
    explorer.getSpendingTxId(boxId).flatMap { txId =>
      // boxId has been spent, while the fullMixBox generated has zero confirmations. Looks like box has been double-spent elsewhere
      explorer.getTransaction(txId).map{tx =>
        // to be sure, get the complete tx and check that none if its outputs are our fullMixBox
        !tx.outboxes.map(_.id).contains(wrtBoxId)
      }
    }
  }.getOrElse(false)

  def getRandomValidBoxId(origBoxIds:Seq[String]) = ErgoMixCLIUtil.usingClient{ implicit ctx =>
    val random = new SecureRandom()
    val boxIds = new scala.util.Random(random).shuffle(origBoxIds)
    boxIds.find{boxId =>
      try {
        ctx.getBoxesById(boxId)
        true
      } catch{
        case a:Throwable =>
          logger.error(s"      Error reading boxId ${boxId}: "+a.getMessage)
          false
      }
    }
  }

  def prettyDate(timestamp: Long): String = {
    val date = new Date(timestamp)
    val formatter = new SimpleDateFormat("HH:mm:ss")
    formatter.format(date)
  }
}

