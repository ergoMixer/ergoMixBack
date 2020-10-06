package app

import java.net.Proxy

import helpers.{ConfigHelper, ErgoMixerUtils}
import mixer.Models.EntityInfo

import scala.collection.mutable

object Configs extends ConfigHelper {
  lazy val nodes: Seq[String] = readNodes()
  lazy val explorerUrl: String = readKey("explorerBackend")
  lazy val explorerFrontend: String = readKey("explorerFrontend")
  lazy val networkType: String = readKey("networkType")
  lazy val jobInterval: Int = readKey("jobInterval").toInt
  lazy val statisticJobsInterval: Int = readKey("statisticJobsInterval").toInt
  lazy val numConfirmation: Int = readKey("numConfirmation").toInt
  lazy val dbPrune: Boolean = readKey("database.prune", "false").toBoolean
  lazy val dbPruneAfter: Int = readKey("database.pruneAfter", "720").toInt
  lazy val maxOuts: Int = readKey("maxOutputs").toInt
  lazy val maxIns: Int = readKey("maxInputs").toInt

  lazy val distributeFee: Long = readKey("fees.distributeTx").toLong
  lazy val startFee: Long = readKey("fees.startTx").toLong
  lazy val defaultHalfFee: Long = readKey("fees.halfTx").toLong
  lazy val defaultFullFee: Long = readKey("fees.fullTx").toLong
  lazy val defaultHalfTokenFee: Long = readKey("fees.halfTokenTx").toLong
  lazy val defaultFullTokenFee: Long = readKey("fees.fullTokenTx").toLong
  // this may go to stat depending on whether it is on blockchain or config file
  // id to info, one info is rings --> contains fees
  var params = mutable.Map.empty[String, EntityInfo] // id (empty for Erg) -> info
  var dynamicFeeRate: Long = 1000L

  val ergRing: Long = 1e6.toLong // erg ring used in mixing tokens
  val minPossibleErgInBox: Long = 1e4.toLong

  var proxy: Proxy = _
  ErgoMixerUtils.handleProxy(readKey("proxy.url", ""), readKey("proxy.port", "-1").toInt,
    readKey("proxy.protocol", ""))

  lazy val periodTimeRings: Long = 24L * 3600L * 1000L // Period time for calculate number of spent halfBox
  lazy val limitGetTransaction = 1000 // Limit for get transaction from explorer
}

