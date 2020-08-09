package mixer

import cli.ErgoMixCLIUtil
import special.collection.Coll
import app.Configs

case class MixBox(withdraw: String, amount: Long, token: Int) {
  def price: Long = {
    val rate: Int = Stats.entranceFee.getOrElse(1000)
    val tokenPrice: Long = Stats.tokenPrices.get.getOrElse(token, -1)
    assert(tokenPrice != -1)
    val rate_value = if (rate > 0 && rate < 1000) this.amount / rate else  0
    this.amount + Configs.feeAmount + tokenPrice + rate_value
  }
}
