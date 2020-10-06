package cli

import app.{Configs, TokenErgoMix}
import helpers.ErgoMixerUtils.getStackTraceStr
import helpers.TrayUtils
import org.ergoplatform.appkit.{NetworkType, RestApiErgoClient}
import play.api.Logger

object Client {
  private val logger: Logger = Logger(this.getClass)

  /**
   * Sets client for the entire app when the app starts, will use proxy if set in config
   *
   * @param isMainnet   is working in mainnet or testnet
   * @param explorerUrl explorer url
   * @return current height of blockchain
   */
  def setClient(isMainnet: Boolean, explorerUrl: String): Unit = {
    val netWorkType = if (isMainnet) NetworkType.MAINNET else NetworkType.TESTNET

    MixUtils.allClients = Configs.nodes.map(node => {
      if (Configs.proxy != null) RestApiErgoClient.create(node, netWorkType, "", explorerUrl, Configs.proxy)
      else RestApiErgoClient.create(node, netWorkType, "", explorerUrl)
    })
    MixUtils.pruneClients()

    try {
      MixUtils.usingClient(ctx => MixUtils.tokenErgoMix = Some(new TokenErgoMix(ctx)))

    } catch {
      case e: Throwable =>
        logger.error("Problem connecting to the node! Please check the accessibility of your configured node and proxy (if you have set one) and try again.")
        logger.error(getStackTraceStr(e))
        TrayUtils.showNotification("Problem connecting to the node, exiting!", "Please check the accessibility of your configured node and proxy (if you have set one) and try again. ErgoMixer will be closed in 15 seconds automatically.")
        Thread.sleep(15e3.toLong)
        sys.exit(0)
    }
  }
}
