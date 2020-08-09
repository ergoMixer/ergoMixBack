package app

import com.typesafe.config.{Config, ConfigFactory}
import helpers.ConfigHelper

object Configs extends ConfigHelper {
  lazy val nodeUrl: String = readKey("node") // TODO make it independent of node
  lazy val explorerUrl: String = readKey("explorerBackend")
  lazy val explorerFrontend: String = readKey("explorerFrontend")
  lazy val networkType: String = readKey("networkType")
  lazy val jobInterval: Int = readKey("jobInterval").toInt
  lazy val statisticJobsInterval: Int = readKey("statisticJobsInterval").toInt
  lazy val numConfirmation: Int = readKey("numConfirmation").toInt
  lazy val feeAmount: Long = readKey("feeAmount").toLong
  lazy val dbName: String = readKey("db.name", "ergo_mixer")
  lazy val dbUser: String = readKey("db.user", "ergo_mixer")
  lazy val dbPass: String = readKey("db.pass", "2l93sQWzd1f4esk5w")
  lazy val numOutputsInStartTransaction: Int = readKey("startTx.maxOutNum").toInt
  lazy val feeInStartTransaction: Long = readKey("startTx.fee").toLong

  lazy val periodTimeRings: Long = (24 * 60 * 60 * 1000).toLong // Period time for calculate number of spent halfBox
  lazy val limitGetTransaction = 100 // Limit for get transaction from explorer
}
