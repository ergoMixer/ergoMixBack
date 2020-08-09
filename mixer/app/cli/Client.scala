package cli

import org.ergoplatform.appkit.{NetworkType, RestApiErgoClient}

object Client {
  def setClient(url:String, isMainnet:Boolean, optApiKey:Option[String]):Int = {
    val $INFO$ = "Returns the current height of the blockchain"
    val $url$ = "nodeUrl from config"
    val $isMainnet$ = "networkType from config"
    val netWorkType = if (isMainnet) NetworkType.MAINNET else NetworkType.TESTNET
    val apiKey = optApiKey.fold("")(x => x)
    val client = RestApiErgoClient.create(url, netWorkType, apiKey)
    ErgoMixCLIUtil.optClient = Some(client)
    client.execute(ctx => ctx.getHeight)
  }
}
