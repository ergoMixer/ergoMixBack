package services

import app.Configs
import cli.Client
import db.Tables
import mixer.{ErgoMixer, ErgoMixerJobs}

object ErgoMixingSystem {
  Client.setClient(isMainnet = Configs.networkType.toLowerCase().equals("mainnet"), Configs.explorerUrl)
  var tables: Tables = _
  var ergoMixer: ErgoMixer = _
  var ergoMixerJobs: ErgoMixerJobs = _
}
