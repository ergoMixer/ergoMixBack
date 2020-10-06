package mixer

import helpers.Util.hash

class Wallet(seed: BigInt) {
  private val seedBytes = seed.toByteArray

  def getSecret(index: Int): BigInt = {
    val indexBytes = BigInt(index).toByteArray
    BigInt(hash(seedBytes ++ indexBytes))
  }
}