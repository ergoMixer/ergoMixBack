package mixer

import cli.ErgoMixCLIUtil
import special.collection.Coll
import app.Configs
import play.api.Logger

object MixBox {
  /**
   * calculates needed amount with current fees for a specific mix box
   * @param ergRing erg ring of mix
   * @param tokenRing token ring of mix
   * @param mixRounds number of mixing rounds i.e. token num
   * @return (erg needed, token needed)
   */
  def getPrice(ergRing: Long, tokenRing: Long, mixRounds: Int): (Long, Long) = {
    val rate: Int = Stats.entranceFee.getOrElse(1000000)
    val tokenPrice: Long = Stats.tokenPrices.get.getOrElse(mixRounds, -1)
    assert(tokenPrice != -1)
    val ergVal = if (rate > 0 && rate < 1000000) ergRing / rate else  0
    val tokenVal = if (rate > 0 && rate < 1000000) tokenRing / rate else  0
    (ergRing + Configs.startFee + tokenPrice + ergVal, tokenVal + tokenRing)
  }
}

case class MixBox(withdraw: String, amount: Long, token: Int, mixingTokenAmount: Long, mixingTokenId: String) {
  def price: (Long, Long) = {
    MixBox.getPrice(amount, mixingTokenAmount, token)
  }
}
