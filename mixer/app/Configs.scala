package app

import com.typesafe.config.{Config, ConfigFactory}
import helpers.ConfigHelper
import mixer.Models.EntityInfo

import scala.collection.mutable

object Configs extends ConfigHelper {
  lazy val nodeUrl: String = readKey("node") // TODO make it independent of node
  lazy val explorerUrl: String = readKey("explorerBackend")
  lazy val explorerFrontend: String = readKey("explorerFrontend")
  lazy val networkType: String = readKey("networkType")
  lazy val jobInterval: Int = readKey("jobInterval").toInt
  lazy val statisticJobsInterval: Int = readKey("statisticJobsInterval").toInt
  lazy val numConfirmation: Int = readKey("numConfirmation").toInt
  lazy val dbName: String = readKey("db.name", "ergo_mixer")
  lazy val dbUser: String = readKey("db.user", "ergo_mixer")
  lazy val dbPass: String = readKey("db.pass", "2l93sQWzd1f4esk5w")
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

  lazy val periodTimeRings: Long = (24 * 60 * 60 * 1000).toLong // Period time for calculate number of spent halfBox
  lazy val limitGetTransaction = 100 // Limit for get transaction from explorer
}
