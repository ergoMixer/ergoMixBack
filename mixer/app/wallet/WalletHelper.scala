package wallet

import java.math.BigInteger
import app.Configs
import org.ergoplatform.{ErgoAddress, ErgoAddressEncoder}
import org.ergoplatform.appkit.{Address, BlockchainContext, ConstantsBuilder, JavaHelpers, NetworkType}
import sigmastate.basics.DLogProtocol.DLogProverInput
import sigmastate.eval._
import sigmastate.interpreter.CryptoConstants
import special.sigma.GroupElement

object WalletHelper {
  val secureRandom = new java.security.SecureRandom

  def randBigInt: BigInt = new BigInteger(256, secureRandom)

  def randBit = secureRandom.nextBoolean()

  def randInt(mod: Int) = secureRandom.nextInt(mod)

  def now = System.currentTimeMillis()

  def hash(bytes: Array[Byte]) = {
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
  }

  def getHash(bytes: Array[Byte]) = scorex.crypto.hash.Blake2b256(bytes)

  val g: GroupElement = CryptoConstants.dlogGroup.generator

  val poisonousHalfs: Seq[GroupElement] = Seq(g.exp(BigInt(1).bigInteger), g.exp(BigInt(-1).bigInteger))

  def hexToGroupElement(hex: String): GroupElement = {
    JavaHelpers.decodeStringToGE(hex)
  }

  @deprecated("This needs blockchain context and compiles contract. Use getAddressOfSecret instead", "1.0")
  def getProveDlogAddress(z: BigInt, ctx: BlockchainContext): String = {
    val gZ: GroupElement = g.exp(z.bigInteger)
      val contract = ctx.compileContract(
        ConstantsBuilder.create().item(
          "gZ", gZ
        ).build(), "{proveDlog(gZ)}"
      )
      addressEncoder.fromProposition(contract.getErgoTree).get.toString
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

  val networkType: NetworkType = if (Configs.isMainnet) NetworkType.MAINNET else NetworkType.TESTNET
  val addressEncoder = new ErgoAddressEncoder(networkType.networkPrefix)

  def getAddress(address: String): ErgoAddress = addressEncoder.fromString(address).get

  def okAddresses(addresses: Seq[String]): Unit = {
    addresses.foreach(address => {
      try getAddress(address).script catch {
        case _: Throwable => throw new Exception("Invalid withdraw address")
      }
    })
  }
}
