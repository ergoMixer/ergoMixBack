package wallet

import java.math.BigInteger

import config.MainConfigs
import org.ergoplatform.{ErgoAddress, ErgoAddressEncoder}
import org.ergoplatform.appkit.{Address, JavaHelpers}
import scorex.crypto.hash.Digest32
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.DLogProverInput
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import sigmastate.Values.ErgoTree
import special.sigma.GroupElement

object WalletHelper {
  val secureRandom = new java.security.SecureRandom

  def randBigInt: BigInt = new BigInteger(256, secureRandom)

  def randBit: Boolean = secureRandom.nextBoolean()

  def randInt(mod: Int): Int = secureRandom.nextInt(mod)

  def now: Long = System.currentTimeMillis()

  def hash(bytes: Array[Byte]): Array[Byte] =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

  def getHash(bytes: Array[Byte]): Digest32 = scorex.crypto.hash.Blake2b256(bytes)

  val g: GroupElement = CryptoConstants.dlogGroup.generator

  val poisonousHalfs: Seq[GroupElement] = Seq(g.exp(BigInt(1).bigInteger), g.exp(BigInt(-1).bigInteger))

  def hexToGroupElement(hex: String): GroupElement =
    JavaHelpers.decodeStringToGE(hex)

  def getDHTDataInBase16(base: GroupElement, power: BigInt): String =
    Base16.encode(base.exp(power.bigInteger).getEncoded.toArray)

  def getAddressOfSecret(secret: BigInt): String = new Address(
    JavaHelpers.createP2PKAddress(DLogProverInput(secret.bigInteger).publicImage, addressEncoder.networkPrefix)
  ).toString

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

  def getErgoAddress(address: String): ErgoAddress = addressEncoder.fromString(address).get

  def getErgoAddress(ergoTree: ErgoTree): ErgoAddress = addressEncoder.fromProposition(ergoTree).get

  def getAddress(address: String): Address = Address.create(address)

  def okAddresses(addresses: Seq[String]): Unit =
    addresses.foreach { address =>
      try getErgoAddress(address).script
      catch {
        case _: Throwable => throw new Exception("Invalid withdraw address")
      }
    }
}
