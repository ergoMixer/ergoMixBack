package testHandlers

import mocked.MockedNetworkUtils
import models.Models.MixStatus.Complete
import models.Models.MixWithdrawStatus.UnderHop
import models.Models._

/**
 * Dataset of test values for classes:
 * WithdrawMixer
 */
object MixScannerDataset extends DatasetSuite {

  val networkUtils = new MockedNetworkUtils

  private val sample14_address = "9hGtssWQoasFDi5JYdU6MXrAwWb5XHwBrVnsxG1pjaavnVZNm9m"
  private val sample14_masterSecret = BigInt("22043820302096273663382944374577805587149983015121743307126172116680944577694")
  private val sample14_spendTxList = jsonToObjectList[SpendTx](readJsonFile("./test/dataset/Sample14_SpendTxs.json"), jsonToSpentTx).map(tx => (tx.inboxes.head.id, tx))
  private val sample14_firstHopBoxId = sample14_spendTxList.head._2.inboxes.head.id
  private val sample14_followedHopList = jsonToObjectList[FollowedHop](readJsonFile("./test/dataset/Sample14_FollowedHopList.json"), CreateFollowedHop.apply)
  private val sample14_followedWithdraw = CreateFollowedWithdraw(readJsonFile("./test/dataset/Sample14_FollowedWithdraw.json"))
  private val sample14_lastMixBoxId = sample14_spendTxList.head._1

  private val sample15_fullMixBoxId = "b38b408cbcb614ee2fdc380ecddc61aa9bf46d8f900210475f3bdc0f31c43f74"
  private val sample15_round = 1
  private val sample15_masterSecret = BigInt("13409451885754448647109802699845420163693098594592537239731787078490546220417")
  private val sample15_spendTxList = jsonToObjectList[SpendTx](readJsonFile("./test/dataset/Sample15_SpendTxs.json"), jsonToSpentTx).flatMap(tx => tx.inboxes.map(inBox => (inBox.id, tx)))
  private val sample15_followedMixList = jsonToObjectList[FollowedMix](readJsonFile("./test/dataset/Sample15_FollowedMixList.json"), CreateFollowedMix.apply) // TODO: add file
  private val sample15_lastMixBoxId = "887deb51d6487d023954f85c97473132efa1c6e6f22982377aba280b31472039"
  private val sample15_followedWithdraw = CreateFollowedWithdraw(readJsonFile("./test/dataset/Sample15_FollowedWithdraw.json"))

  private val sample16_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample16_MixRequest.json"))
  private val sample16_mixId = sample16_MixingRequest.id
  private val sample16_round = sample15_round
  private val sample16_boxId = sample15_fullMixBoxId
  private val sample16_boxType = "full"
  private val sample16_masterSecret = sample16_MixingRequest.masterKey
  private val sample16_followedMixList = sample15_followedMixList
  private val sample16_followedHopList = sample14_followedHopList
  private val sample16_followedWithdraw = sample14_followedWithdraw
  private val sample16_lastMixBoxId = sample15_lastMixBoxId

  private val sample16_fullMixList = jsonToObjectList[FullMix](readJsonFile("./test/dataset/Sample16_FullMixList.json"), CreateFullMix.apply).map(mix => (mix.mixId, mix.round, mix.halfMixBoxId, mix.fullMixBoxId))
  private val sample16_halfMixList = jsonToObjectList[HalfMix](readJsonFile("./test/dataset/Sample16_HalfMixList.json"), CreateHalfMix.apply).map(mix => (mix.mixId, mix.round, mix.halfMixBoxId))
  private val sample16_hopMixList = jsonToObjectList[HopMix](readJsonFile("./test/dataset/Sample16_HopMixList.json"), CreateHopMix.apply).map(mix => (mix.mixId, mix.round, mix.boxId))
  private val sample16_mixStateHistoryList = jsonToObjectList[MixHistory](readJsonFile("./test/dataset/Sample16_MixStateHistoryList.json"), CreateMixHistory.apply).map(mix => (mix.id, mix.round, mix.isAlice))
  private val sample16_MixState = CreateMixState(readJsonFile("./test/dataset/Sample16_MixState.json"))
  private val sample16_WithdrawTx = CreateWithdrawTx(readJsonFile("./test/dataset/Sample16_WithdrawTx.json"))

  // This is for rescan hopBox. So the mix status is Complete and withdraw status is under hop. Other values are the same.
  private val sample16_HopMixingRequest: MixingRequest = MixingRequest(
    sample16_MixingRequest.id,
    sample16_MixingRequest.groupId,
    sample16_MixingRequest.amount,
    sample16_MixingRequest.numRounds,
    Complete,
    sample16_MixingRequest.createdTime,
    sample16_MixingRequest.withdrawAddress,
    sample16_MixingRequest.depositAddress,
    sample16_MixingRequest.depositCompleted,
    sample16_MixingRequest.neededAmount,
    sample16_MixingRequest.numToken,
    UnderHop.value,
    sample16_MixingRequest.mixingTokenAmount,
    sample16_MixingRequest.neededTokenAmount,
    sample16_MixingRequest.tokenId,
    sample16_MixingRequest.masterKey
  )
  private val sample16_spentHopBoxId = "4ccb85f225155df9d5c0fb5b889c34e966e9b1478b66cf2794bc858b578402a9"
  private val sample16_hopRound = 0
  private val sample16_hopBoxType = "hop"

  /**
   * apis for testing MixScanner.scanHopMix and MixScanner.followHopMix
   * spec data: (BigInt, Seq[FollowedHop], Option[FollowedWithdraw]), the mix request master secret and function expected return values
   * explorer data: (String, Seq[OutBox], Seq[(String, SpendTx)]), first hop address, it's boxId and the list of mix boxIds and their spendTxs
   */
  def scanHopMix_explorerMockedData: (String, Seq[String], Seq[(String, SpendTx)]) = (sample14_address, Seq(sample14_firstHopBoxId), sample14_spendTxList)

  def scanHopMix_specData: (String, BigInt, Seq[FollowedHop], Option[FollowedWithdraw]) = (sample14_lastMixBoxId, sample14_masterSecret, sample14_followedHopList, Option(sample14_followedWithdraw))

  /**
   * apis for testing MixScanner.followFullMix, MixScanner.followHalfMix and MixScanner.getWithdrawal
   * spec data: (String, Int, BigInt, Seq[FollowedMix], Option[FollowedWithdraw]), spent boxId (the on rescan would called on it), box mix round, the mix request master secret and function expected return values
   * explorer data: Seq[(String, SpendTx)], the list of mix boxIds and their spendTxs
   */
  def followMix_spendTxList: Seq[(String, SpendTx)] = sample15_spendTxList

  def followMix_specData: (String, Int, BigInt, Seq[FollowedMix]) = (sample15_fullMixBoxId, sample15_round, sample15_masterSecret, sample15_followedMixList)

  def getWithdrawal_specData: (String, BigInt, Seq[FollowedHop], Option[FollowedWithdraw]) = (sample15_lastMixBoxId, sample15_masterSecret, Nil, Option(sample15_followedWithdraw))

  /**
   * apis for testing Rescan.processRescan
   * spec data: mixId, round, goBackward, isHalfMixTx and mixBoxId (the pending rescan object parameters)
   * db data: the mixing request in db
   * mocked data:
   */
  def rescan_specData: (String, Int, Boolean, String, String) = (sample16_mixId, sample16_round, false, sample16_boxType, sample16_boxId)

  def rescan_dbData: MixingRequest = sample16_MixingRequest

  def rescan_mockedData = (mockFollowFullMix, mockWithdrawWithHop)

  def mockFollowFullMix: (String, Int, BigInt, Seq[FollowedMix]) = (sample16_boxId, sample16_round, sample16_masterSecret, sample16_followedMixList)

  def mockWithdrawWithHop: (String, BigInt, Seq[FollowedHop], Option[FollowedWithdraw]) = (sample16_lastMixBoxId, sample16_masterSecret, sample16_followedHopList, Option(sample16_followedWithdraw))

  def rescan_resultData = (sample16_fullMixList, sample16_halfMixList, sample16_hopMixList, sample16_mixStateHistoryList, sample16_MixState, sample16_WithdrawTx)

  def rescanHop_specData: (String, Int, Boolean, String, String) = (sample16_mixId, sample16_hopRound, false, sample16_hopBoxType, sample16_spentHopBoxId)

  def rescanHop_dbData: MixingRequest = sample16_HopMixingRequest

  def mockFollowHopMix: (String, Int, BigInt, Seq[FollowedHop], Option[FollowedWithdraw]) = (sample16_spentHopBoxId, sample16_hopRound, sample16_masterSecret, sample16_followedHopList, Option(sample16_followedWithdraw))

  def rescanHop_resultData = (sample16_hopMixList, sample16_WithdrawTx)

}
