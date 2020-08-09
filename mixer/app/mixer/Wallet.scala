package mixer

import mixer.Util.hash

class Wallet(seed:BigInt) {
  private val seedBytes = seed.toByteArray

  def getSecret(index: Int): BigInt = {
    // TODO Make it better
    val indexBytes = BigInt(index).toByteArray
    BigInt(hash(seedBytes ++ indexBytes))
  }
}