package wallet

import WalletHelper.hash

class Wallet(seed: BigInt) {
  private val seedBytes = seed.toByteArray

  def getSecret(index: Int = 0, isManual:Boolean = false): BigInt = {
    if (!isManual){
      val indexBytes = BigInt(index).toByteArray
      BigInt(hash(seedBytes ++ indexBytes))
    } else {
      BigInt(seedBytes)
    }
  }
}