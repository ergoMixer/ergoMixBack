package wallet

import config.MainConfigs

import java.math.BigInteger
import org.ergoplatform.{ErgoAddress, ErgoAddressEncoder}
import org.ergoplatform.appkit.{Address, JavaHelpers}
import scorex.crypto.hash.Digest32
import sigmastate.Values.ErgoTree
import sigmastate.basics.DLogProtocol.DLogProverInput
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement

object WalletHelper {
  val secureRandom = new java.security.SecureRandom

  def randBigInt: BigInt = new BigInteger(256, secureRandom)

  def randBit: Boolean = secureRandom.nextBoolean()

  def randInt(mod: Int): Int = secureRandom.nextInt(mod)

  def now: Long = System.currentTimeMillis()

  def hash(bytes: Array[Byte]): Array[Byte] = {
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
  }

  def getHash(bytes: Array[Byte]): Digest32 = scorex.crypto.hash.Blake2b256(bytes)

  val g: GroupElement = CryptoConstants.dlogGroup.generator

  val poisonousHalfs: Seq[GroupElement] = Seq(g.exp(BigInt(1).bigInteger), g.exp(BigInt(-1).bigInteger))

  def hexToGroupElement(hex: String): GroupElement = {
    JavaHelpers.decodeStringToGE(hex)
  }

  def getAddressOfSecret(secret: BigInt): String = new Address(JavaHelpers.createP2PKAddress(
    DLogProverInput(secret.bigInteger).publicImage, addressEncoder.networkPrefix)).toString

  /**
   * @param masterSecret mix request master secret key
   * @return first hop address of the mix request
   */
  def getHopAddress(masterSecret: BigInt, round: Int): String = {
    val wallet = new Wallet(masterSecret)
    val secret = wallet.getSecret(round, toFirst = true)
    getAddressOfSecret(secret)
  }

  val addressEncoder = new ErgoAddressEncoder(MainConfigs.networkType.networkPrefix)

  def getAddress(address: String): ErgoAddress = addressEncoder.fromString(address).get

  def getAddress(address: ErgoTree): ErgoAddress = addressEncoder.fromProposition(address).get

  def okAddresses(addresses: Seq[String]): Unit = {
    addresses.foreach(address => {
      try getAddress(address).script catch {
        case _: Throwable => throw new Exception("Invalid withdraw address")
      }
    })
  }
}
