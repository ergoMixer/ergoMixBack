package services

import cli.Client
import db.config.DBConfig
import mixer.{ErgoMixer, ErgoMixerJobs, Tables}
import app.Configs

object ErgoMixingSystem {

  object Config extends DBConfig {
    override val dbname: String = Configs.dbName
    override val dbuser: String = Configs.dbUser
    override val dbpass: String = Configs.dbPass
  }

  Client.setClient(Configs.nodeUrl, isMainnet = Configs.networkType.toLowerCase().equals("mainnet"), None, Configs.explorerUrl)
  val tables = new Tables(Config)
  val ergoMixer = new ErgoMixer(tables)
  val ergoMixerJobs = new ErgoMixerJobs(tables)
}
