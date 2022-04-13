package wallet

import WalletHelper.hash

class Wallet(seed: BigInt) {
  private val seedBytes = seed.toByteArray

  def getSecret(index: Int = 0, isManual: Boolean = false, toFirst: Boolean = false): BigInt = {
    if (!isManual){
      val indexBytes = BigInt(index).toByteArray
      if (toFirst) BigInt(hash(indexBytes ++ seedBytes))
      else BigInt(hash(seedBytes ++ indexBytes))
    } else {
      BigInt(seedBytes)
    }
  }
}