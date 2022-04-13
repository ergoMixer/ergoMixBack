package testHandlers

import mocked.MockedNetworkUtils
import models.Models.{CreateHopMix, CreateMixGroupRequest, CreateMixingRequest, CreateWithdrawTx, HopMix, MixGroupRequest, MixingRequest, WithdrawTx}
import org.ergoplatform.appkit.SignedTransaction

/**
 * Dataset of test values for classes:
 * WithdrawMixer
 */
object WithdrawMixerDataset extends DatasetSuite {

  val networkUtils = new MockedNetworkUtils

  private val sample8_MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample8_MixRequest.json"))
  private val sample8_WithdrawTx = CreateWithdrawTx(readJsonFile("./test/dataset/Sample8_WithdrawTx.json"))

  private val sample9_MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample9_MixRequest.json"))
  private val sample9_MixGroupRequest = CreateMixGroupRequest(readJsonFile("./test/dataset/Sample9_MixGroupRequest.json"))
  private val sample9_WithdrawTx = CreateWithdrawTx(readJsonFile("./test/dataset/Sample9_WithdrawTx.json"))

  private val sample10_MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample10_MixRequest.json"))
  private val sample10_WithdrawTx = CreateWithdrawTx(readJsonFile("./test/dataset/Sample10_WithdrawTx.json"))
  private val sample10_HopMix = CreateHopMix(readJsonFile("./test/dataset/Sample10_HopMix.json"))

  private val sample11_WithdrawTx = CreateWithdrawTx(readJsonFile("./test/dataset/Sample11_WithdrawTx.json"))
  private val sample11_spentTxId = "68c90f5730e218d898eba27c82bec7138820cc32d01a9860c5513ac112e3ab09"

  /**
   * apis for testing WithdrawMixer.processWithdraw
   * spec data: ???
   *
   * db data: ???
   */
  def confirmedTx_mockedData: (String, Int) = (sample9_WithdrawTx.txId, 3)

  def confirmedTx_dbData: (MixingRequest, MixGroupRequest, WithdrawTx) = (sample9_MixingRequest, sample9_MixGroupRequest, sample9_WithdrawTx)

  def notMinedTx_mockedData: (String, Int) = (sample8_WithdrawTx.txId, -1)

  def notMinedTx_dbData: (MixingRequest, WithdrawTx) = (sample8_MixingRequest, sample8_WithdrawTx)

  /**
   * apis for testing WithdrawMixer.processInitiateHops
   * spec data: HopMix, the inserted hopMixBox
   * db data: (MixingRequest, WithdrawTx), the mixingRequest and withdrawTx objects in database
   * explorer data: (String, Int), the txId and it's numConfirmation
   */
  def confirmedTxInitiateHop_mockedData: (String, Int) = (sample10_WithdrawTx.txId, 3)

  def confirmedTxInitiateHop_dbData: (MixingRequest, WithdrawTx, HopMix) = (sample10_MixingRequest, sample10_WithdrawTx, sample10_HopMix)

  def notMinedTxInitiateHop_mockedData: (String, Int) = (sample11_WithdrawTx.txId, -1)

  def spentBoxIdInitiateHop_mockedData: (String, String) = (sample11_WithdrawTx.getFeeBox.get, sample11_spentTxId)

  def notMinedWithdrawTx: (WithdrawTx, SignedTransaction) = (sample11_WithdrawTx, networkUtils.jsonToSignedTx(sample11_WithdrawTx.toString))

}
