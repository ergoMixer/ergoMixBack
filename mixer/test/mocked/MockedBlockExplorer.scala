package mocked

import models.Transaction.SpendTx
import org.scalatestplus.mockito.MockitoSugar
import network.BlockExplorer
import org.mockito.Mockito.when
import testHandlers.{HopMixerDataset, MixScannerDataset, WithdrawMixerDataset}

import javax.inject.Singleton

@Singleton
class MockedBlockExplorer extends MockitoSugar {

  private val blockExplorer = mock[BlockExplorer]
  private val dataset_withdrawMixer = WithdrawMixerDataset
  private val dataset_hopMixer = HopMixerDataset
  private val dataset_mixScanner = MixScannerDataset

  def getMocked = blockExplorer

  def setTestCases(): Unit = {
    val confirmedTx = dataset_withdrawMixer.confirmedTx_mockedData
    val notMinedTx = dataset_withdrawMixer.notMinedTx_mockedData
    val confirmedTx_initiateHop = dataset_withdrawMixer.confirmedTxInitiateHop_mockedData
    val notMinedTx_initiateHop = dataset_withdrawMixer.notMinedTxInitiateHop_mockedData
    val spentBoxId_initiateHop = dataset_withdrawMixer.spentBoxIdInitiateHop_mockedData
    val withdrawFromHop = dataset_hopMixer.withdrawFromHop_explorerMockedData
    val nextHop = dataset_hopMixer.nextHop_explorerMockedData
    val followHop = dataset_mixScanner.scanHopMix_explorerMockedData
    val followMix = dataset_mixScanner.followMix_spendTxList

    setReturnValue_getTxNumConfirmations(confirmedTx._1, confirmedTx._2)
    setReturnValue_getTxNumConfirmations(notMinedTx._1, notMinedTx._2)
    setReturnValue_getTxNumConfirmations(confirmedTx_initiateHop._1, confirmedTx_initiateHop._2)
    setReturnValue_getTxNumConfirmations(notMinedTx_initiateHop._1, notMinedTx_initiateHop._2)

    setReturnValue_getConfirmationsForBoxId(withdrawFromHop._1, withdrawFromHop._2)
    setReturnValue_getConfirmationsForBoxId(nextHop._1, nextHop._2)

    setReturnValue_getSpendingTxId(spentBoxId_initiateHop._1, spentBoxId_initiateHop._2)
    followHop._3.foreach(boxTxTuple => {
      setReturnValue_getSpendingTxId(boxTxTuple._1, boxTxTuple._2.id)
      setReturnValue_getTransaction(boxTxTuple._2.id, boxTxTuple._2)
    })
    followMix.foreach(boxTxTuple => {
      setReturnValue_getSpendingTxId(boxTxTuple._1, boxTxTuple._2.id)
      setReturnValue_getTransaction(boxTxTuple._2.id, boxTxTuple._2)
    })

    setReturnValue_getBoxIdsByAddress(followHop._1, followHop._2)
  }

  /**
   * specify what to return when getTxNumConfirmations of mock class called
   *
   * @param txId transaction ID
   * @param numConfirm confirmation of transaction in explorer
   */
  def setReturnValue_getTxNumConfirmations(txId: String, numConfirm: Int): Unit = when(blockExplorer.getTxNumConfirmations(txId)).thenReturn(numConfirm)

  /**
   * specify what to return when getConfirmationsForBoxId of mock class called
   *
   * @param boxId Box ID
   * @param numConfirm confirmation of the box in explorer
   */
  def setReturnValue_getConfirmationsForBoxId(boxId: String, numConfirm: Int): Unit = when(blockExplorer.getConfirmationsForBoxId(boxId)).thenReturn(numConfirm)

  /**
   * specify what to return when getSpendingTxId of mock class called
   *
   * @param boxId Box ID
   * @param txId spent transaction ID
   */
  def setReturnValue_getSpendingTxId(boxId: String, txId: String): Unit = when(blockExplorer.getSpendingTxId(boxId)).thenReturn(Option(txId))

  /**
   * specify what to return when getTransaction of mock class called
   *
   * @param txId transaction ID
   * @param tx transaction
   */
  def setReturnValue_getTransaction(txId: String, tx: SpendTx): Unit = when(blockExplorer.getTransaction(txId)).thenReturn(Option(tx))

  /**
   * specify what to return when getBoxIdsByAddress of mock class called
   *
   * @param address string representing ergo address
   * @param boxIds sequence of box IDs belongs to the address
   */
  def setReturnValue_getBoxIdsByAddress(address: String, boxIds: Seq[String]): Unit = when(blockExplorer.getBoxIdsByAddress(address)).thenReturn(boxIds)

  setTestCases()

}
