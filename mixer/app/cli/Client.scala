package cli

import app.TokenErgoMix
import org.ergoplatform.appkit.{NetworkType, RestApiErgoClient}

object Client {
  def setClient(url:String, isMainnet:Boolean, optApiKey:Option[String], explorerUrl:String):Int = {
    val netWorkType = if (isMainnet) NetworkType.MAINNET else NetworkType.TESTNET
    val apiKey = optApiKey.fold("")(x => x)
    val client = RestApiErgoClient.create(url, netWorkType, apiKey, explorerUrl)
    ErgoMixCLIUtil.optClient = Some(client)
    client.execute(ctx => ErgoMixCLIUtil.tokenErgoMix = Some(new TokenErgoMix(ctx)))
    client.execute(ctx => ctx.getHeight)
  }
}
