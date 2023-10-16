package config

import java.net.Proxy

import scala.collection.mutable

import helpers.ConfigHelper
import models.Models.EntityInfo
import org.ergoplatform.appkit.NetworkType

object MainConfigs extends ConfigHelper {
  lazy val isAdmin: Boolean         = readKey("isAdmin", "false").toBoolean
  lazy val nodes: Seq[String]       = readNodes()
  lazy val explorerUrl: String      = readKey("explorerBackend")
  lazy val explorerFrontend: String = readKey("explorerFrontend")
  lazy val networkType: NetworkType =
    if (readKey("networkType").toLowerCase().equals("mainnet")) NetworkType.MAINNET else NetworkType.TESTNET
  lazy val jobInterval: Int           = readKey("jobInterval").toInt
  lazy val statisticJobsInterval: Int = readKey("statisticJobsInterval").toInt
  lazy val numConfirmation: Int       = readKey("numConfirmation").toInt
  lazy val dbPrune: Boolean           = readKey("database.prune", "false").toBoolean
  lazy val dbPruneAfter: Int          = readKey("database.pruneAfter", "720").toInt
  lazy val pruneUnusedCovertDepositsAfterMilliseconds: Long =
    readKey("pruneUnusedCovertDepositsAfter", "1296000").toLong * 1000 // in milliseconds
  lazy val maxOuts: Int           = readKey("maxOutputs").toInt
  lazy val maxIns: Int            = readKey("maxInputs").toInt
  lazy val connectionTimeout: Int = readKey("connectionTimeout", "20").toInt

  lazy val stealthSpendInterval: Int          = readKey("stealth.spendInterval").toInt
  lazy val stealthScanInterval: Int           = readKey("stealth.scanInterval").toInt
  lazy val stealthBestBlockId: String         = readKey("stealth.bestBlockId")
  lazy val stealthImplementorFeePercent: Long = 20
  lazy val stealthFee: Long                   = readKey("fees.stealth", "5000000").toLong
  lazy val distributeFee: Long                = readKey("fees.distributeTx").toLong
  lazy val ageusdFee: Long                    = readKey("fees.ageusd", "5000000").toLong
  lazy val startFee: Long                     = readKey("fees.startTx").toLong
  lazy val defaultHalfFee: Long               = readKey("fees.halfTx").toLong
  lazy val defaultFullFee: Long               = readKey("fees.fullTx").toLong
  lazy val defaultHalfTokenFee: Long          = readKey("fees.halfTokenTx").toLong
  lazy val defaultFullTokenFee: Long          = readKey("fees.fullTokenTx").toLong
  // this may go to stat depending on whether it is on blockchain or config file
  // id to info, one info is rings --> contains fees
  var params               = mutable.Map.empty[String, EntityInfo] // id (empty for Erg) -> info
  var dynamicFeeRate: Long = 1000L

  val ergRing: Long             = 1e6.toLong // erg ring used in mixing tokens
  val minPossibleErgInBox: Long = 1e6.toLong

  // proxy config
  val proxyUrl: String      = readKey("proxy.url", "")
  val proxyPort: Int        = readKey("proxy.port", "-1").toInt
  val proxyProtocol: String = readKey("proxy.protocol", "")
  var proxy: Proxy          = _

  lazy val periodTimeRings: Long = 24L * 3600L * 1000L // Period time for calculate number of spent halfBox
  lazy val limitGetTransaction   = 100 // Limit for get transaction from explorer

  // stats
  var tokenPrices: Option[Map[Int, Long]] = None
  var entranceFee: Option[Int]            = None
  var ringStats                           = mutable.Map.empty[String, mutable.Map[Long, mutable.Map[String, Long]]]

  lazy val mixOnlyAsBob: Boolean                   = readKey("mixOnlyAsBob", "false").toBoolean
  lazy val stopMixingWhenReachedThreshold: Boolean = readKey("stopMixingWhenReachedThreshold", "true").toBoolean

  lazy val hopRounds: Int = readKey("hopRounds", "0").toInt

  lazy val logPath: String = readKey("logPath")

  lazy val maxErg: Long = (1e9 * 1e8).toLong
}
