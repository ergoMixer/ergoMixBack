package network

import java.net.{Authenticator, InetSocketAddress, PasswordAuthentication, Proxy}

import app.Configs.readKey
import app.Configs
import helpers.{ErgoMixerUtils, TrayUtils}
import javax.inject.Inject
import mixinterface.TokenErgoMix
import org.ergoplatform.appkit.{NetworkType, RestApiErgoClient}
import play.api.Logger

class Client @Inject()(ergoMixerUtils: ErgoMixerUtils, networkUtils: NetworkUtils) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * Sets client for the entire app when the app starts, will use proxy if set in config
   *
   * @param isMainnet   is working in mainnet or testnet
   * @param explorerUrl explorer url
   * @return current height of blockchain
   */
  def setClient(isMainnet: Boolean, explorerUrl: String): Unit = {

    handleProxy(Configs.proxyUrl, Configs.proxyPort, Configs.proxyProtocol)


    val netWorkType = if (isMainnet) NetworkType.MAINNET else NetworkType.TESTNET

     Configs.nodes.foreach(node => {
      if (Configs.proxy != null) networkUtils.allClients(node) = RestApiErgoClient.createWithProxy(node, netWorkType, "", explorerUrl, Configs.proxy)
      else networkUtils.allClients(node) = RestApiErgoClient.create(node, netWorkType, "", explorerUrl)
    })
    networkUtils.pruneClients()

    try {
      networkUtils.usingClient(ctx => networkUtils.tokenErgoMix = Some(new TokenErgoMix(ctx)))

    } catch {
      case e: Throwable =>
        logger.error("Problem connecting to the node! Please check the accessibility of your configured node and proxy (if you have set one) and try again.")
        logger.error(ergoMixerUtils.getStackTraceStr(e))
        TrayUtils.showNotification("Problem connecting to the node, exiting!", "Please check the accessibility of your configured node and proxy (if you have set one) and try again. ErgoMixer will be closed in 15 seconds automatically.")
        Thread.sleep(15e3.toLong)
        sys.exit(0)
    }
  }

  def handleProxy(url: String, port: Int, protocol: String): Unit = {
    if (url.nonEmpty && port != -1 && protocol.nonEmpty) {
      val prot: Proxy.Type = {
        if (protocol.toLowerCase().contains("socks")) Proxy.Type.SOCKS
        else if (protocol.toLowerCase().contains("http")) Proxy.Type.HTTP
        else null
      }
      if (prot == null) {
        logger.error("protocol type for proxy is not valid.")
        return
      }
      Configs.proxy = new Proxy(prot, new InetSocketAddress(url, port))
    }
  }

}
