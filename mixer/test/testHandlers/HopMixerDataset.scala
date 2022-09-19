package testHandlers

import mocked.MockedNetworkUtils
import models.Models.{CreateHopMix, HopMix}
import models.Request.{CreateMixingRequest, MixingRequest}
import models.Transaction.{CreateWithdrawTx, WithdrawTx}
import org.ergoplatform.appkit.SignedTransaction
import wallet.Wallet

import java.math.BigInteger

/**
 * Dataset of test values for classes:
 * WithdrawMixer
 */
object HopMixerDataset extends DatasetSuite {

  val networkUtils = new MockedNetworkUtils

  private val sample12_MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample12_MixRequest.json"))
  private val sample12_WithdrawTx = CreateWithdrawTx(readJsonFile("./test/dataset/Sample12_WithdrawTx.json"))
  private val sample12_HopMix = CreateHopMix(readJsonFile("./test/dataset/Sample12_HopMix.json"))
  private val sample12_Secret = {
    val masterSecret = sample12_MixingRequest.masterKey
    val wallet = new Wallet(masterSecret)
    wallet.getSecret(sample12_HopMix.round, toFirst = true).bigInteger
  }
  private val sample12_SignedTransaction = networkUtils.jsonToSignedTx(sample12_WithdrawTx.toString)

  private val sample13_MixingRequest = sample12_MixingRequest
  private val sample13_HopMix = CreateHopMix(readJsonFile("./test/dataset/Sample13_HopMix.json"))
  private val sample13_Secret = {
    val masterSecret = sample13_MixingRequest.masterKey
    val wallet = new Wallet(masterSecret)
    wallet.getSecret(sample13_HopMix.round, toFirst = true).bigInteger
  }
  private val sample13_SignedTransaction = jsonToSignedTransaction(readJsonFile("./test/dataset/Sample13_SignedTransaction.json"))
  private val sample13_NextHopMix = CreateHopMix(readJsonFile("./test/dataset/Sample13_NextHopMix.json"))
  private val sample13_NextHopAddress = "9gQRoFgaYVmQGRHpNhsETRGjyEovMX3zHG2fHU53JQjBUPymtep"

  /**
   * apis for testing HopMixer.processHopBox
   * spec data: ???
   *
   * db data: ???
   */
  def withdrawFromHop_explorerMockedData: (String, Int) = (sample12_HopMix.boxId, 3)

  def withdrawFromHop_ergoMixerMockedData: (String, BigInt, String) = (sample12_HopMix.mixId, sample12_MixingRequest.masterKey, sample12_MixingRequest.withdrawAddress)

  def withdrawFromHop_aliceOrBobMockedData: (BigInteger, String, String, SignedTransaction) = (sample12_Secret, sample12_HopMix.boxId, sample12_MixingRequest.withdrawAddress, sample12_SignedTransaction)

  def withdrawFromHop_dbData: (WithdrawTx, MixingRequest) = (sample12_WithdrawTx, sample12_MixingRequest)

  def withdrawFromHop_specData: (HopMix, SignedTransaction) = (sample12_HopMix, sample12_SignedTransaction)

  def nextHop_explorerMockedData: (String, Int) = (sample13_HopMix.boxId, 3)

  def nextHop_aliceOrBobMockedData: (BigInteger, String, String, SignedTransaction) = (sample13_Secret, sample13_HopMix.boxId, sample13_NextHopAddress, sample13_SignedTransaction)

  def nextHop_dbData: (HopMix, MixingRequest) = (sample13_HopMix, sample13_MixingRequest)

  def nextHop_specData: HopMix = sample13_NextHopMix

}
