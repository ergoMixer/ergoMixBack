package network

import java.net.{InetSocketAddress, Proxy}
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject

import config.MainConfigs
import dao.DAOUtils
import helpers.{ErgoMixerUtils, TrayUtils}
import mixinterface.TokenErgoMix
import okhttp3.OkHttpClient
import org.ergoplatform.appkit.{NetworkType, RestApiErgoClient}
import play.api.Logger

class Client @Inject() (
  ergoMixerUtils: ErgoMixerUtils,
  networkUtils: NetworkUtils,
  daoUtils: DAOUtils,
  trayUtils: TrayUtils
) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * Sets client for the entire app when the app starts, will use proxy if set in config
   *
   * @param networkType  type of network -> mainnet or testnet
   * @param explorerUrl explorer url
   * @return current height of blockchain
   */
  def setClient(networkType: NetworkType, explorerUrl: String): Unit = {

    handleProxy(MainConfigs.proxyUrl, MainConfigs.proxyPort, MainConfigs.proxyProtocol)

    MainConfigs.nodes.foreach { node =>
      val httpClientBuilder = new OkHttpClient.Builder()
        .callTimeout(MainConfigs.connectionTimeout, SECONDS)
        .connectTimeout(MainConfigs.connectionTimeout, SECONDS)
        .writeTimeout(MainConfigs.connectionTimeout * 2, SECONDS)
        .readTimeout(MainConfigs.connectionTimeout * 3, SECONDS)

      if (MainConfigs.proxy != null)
        networkUtils.allClients(node) = RestApiErgoClient.createWithHttpClientBuilder(
          node,
          networkType,
          "",
          explorerUrl,
          httpClientBuilder.proxy(MainConfigs.proxy)
        )
      else
        networkUtils.allClients(node) = RestApiErgoClient.createWithHttpClientBuilder(
          node,
          networkType,
          "",
          explorerUrl,
          httpClientBuilder
        )
    }
    networkUtils.pruneClients()

    try
      networkUtils.usingClient(ctx => networkUtils.tokenErgoMix = Some(new TokenErgoMix(ctx)))

    catch {
      case e: Throwable =>
        logger.error(
          "Problem connecting to the node! Please check the accessibility of your configured node and proxy (if you have set one) and try again."
        )
        logger.error(ergoMixerUtils.getStackTraceStr(e))
        trayUtils.showNotification(
          "Problem connecting to the node, exiting!",
          "Please check the accessibility of your configured node and proxy (if you have set one) and try again. ErgoMixer will be closed in 15 seconds automatically."
        )
        Thread.sleep(15e3.toLong)
        daoUtils.shutdown(true)
    }
  }

  def handleProxy(url: String, port: Int, protocol: String): Unit =
    if (url.nonEmpty && port != -1 && protocol.nonEmpty) {
      val prot: Proxy.Type =
        if (protocol.toLowerCase().contains("socks")) Proxy.Type.SOCKS
        else if (protocol.toLowerCase().contains("http")) Proxy.Type.HTTP
        else null
      if (prot == null) {
        logger.error("protocol type for proxy is not valid.")
        return
      }
      MainConfigs.proxy = new Proxy(prot, new InetSocketAddress(url, port))
    }

}
