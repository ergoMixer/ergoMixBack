package helpers

import java.math.BigInteger

object Util {
  val secureRandom = new java.security.SecureRandom

  def randBigInt: BigInt = new BigInteger(256, secureRandom)

  def randBit = secureRandom.nextBoolean()

  def randInt(mod: Int) = secureRandom.nextInt(mod)

  def now = System.currentTimeMillis()

  def hash(bytes: Array[Byte]) = {
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
  }
}
