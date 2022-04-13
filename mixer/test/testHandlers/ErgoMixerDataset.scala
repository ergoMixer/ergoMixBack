package testHandlers

import io.circe.{Json, parser}
import models.Models._
import testHandlers.MixScannerDataset.{jsonToObjectList, readJsonFile}

import scala.collection.mutable

/**
 * Dataset of test values for classes:
 * ErgoMixer
 */
object ErgoMixerDataset extends DatasetSuite {

  def jsonToMixingBoxPrices(jsonString: String): Seq[(Long, Long)] = {
    parser.parse(jsonString) match {
      case Left(e) => throw new Exception(s"Error while parsing MixingBox from Json: $e")
      case Right(js) => js.hcursor.downField("items").as[Seq[Json]] match {
        case Left(e) => throw new Exception(s"Error while parsing MixingBox from Json: $e")
        case Right(arr) => arr.map(js => {
          val cursor = js.hcursor
          val neededAmount = cursor.downField("neededAmount").as[Long] match {
            case Right(needed) => needed
          }
          val tokenNeededAmount = cursor.downField("tokenNeededAmount").as[Long] match {
            case Right(needed) => needed
          }
          (neededAmount, tokenNeededAmount)
        })
      }
    }
  }

  private val sample0_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample0_MixRequest.json"))
  private val sample0_MixRequest: MixRequest = sample0_MixingRequest.toMixRequest
  private val sample0_MixCovertRequest: MixCovertRequest = CreateMixCovertRequest(readJsonFile("./test/dataset/Sample0_MixCovertRequest.json"))
  private val sample0_CovertAsset: CovertAsset = CreateCovertAsset(readJsonFile("./test/dataset/Sample0_CovertAsset.json"))
  private val sample0_WithdrawAddress: String = sample0_MixRequest.withdrawAddress
  private val sample0_MixId: String = sample0_MixRequest.id
  private val sample0_GroupId: String = sample0_MixRequest.groupId
  private val sample0_TokenId: String = sample0_MixRequest.tokenId
  private val sample0_MixState: MixState = CreateMixState(readJsonFile("./test/dataset/Sample0_MixState.json"))
  private val sample0_HalfMix: Option[HalfMix] = Option(CreateHalfMix(readJsonFile("./test/dataset/Sample0_HalfMix.json")))
  private val sample0_FullMix = Option.empty[FullMix]
  private val sample0_Withdraw = Option.empty[Withdraw]

  private val sample1_MixState: MixState = CreateMixState(readJsonFile("./test/dataset/Sample1_MixState.json"))
  private val sample1_MixId: String = sample1_MixState.id
  private val sample1_RoundNum: Int = sample1_MixState.round
  private val sample1_IsAlice: Boolean = sample1_MixState.isAlice

  private val sample2_CovertName: String = "Test covert name"
  private val sample2_roundNum: Int = 30
  private val sample2_Addresses: Seq[String] = Seq("9g3mFZbS6C8ju9whTkp1kbj5Ucti1yBCjrZEwnVdaZLAhU2w855")
  private val sample2_masterKey_1: BigInt = sample0_MixCovertRequest.masterKey
  private val sample2_masterKey_2: BigInt = BigInt("66760146388183827226642846566692984587383552135637379597027429969154056748902")

  private val sample3_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample3_MixRequest.json"))
  private val sample3_MixRequest: MixRequest = sample3_MixingRequest.toMixRequest
  private val sample3_MixState = CreateMixState(readJsonFile("./test/dataset/Sample3_MixState.json"))
  private val sample3_HalfMix = Option.empty[HalfMix]
  private val sample3_FullMix = Option(CreateFullMix(readJsonFile("./test/dataset/Sample3_FullMix.json")))
  private val sample3_Withdraw = Option.empty[Withdraw]

  private val sample4_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample4_MixRequest.json"))
  private val sample4_MixRequest: MixRequest = sample4_MixingRequest.toMixRequest
  private val sample4_MixState = CreateMixState(readJsonFile("./test/dataset/Sample4_MixState.json"))
  private val sample4_HalfMix = Option(CreateHalfMix(readJsonFile("./test/dataset/Sample4_HalfMix.json")))
  private val sample4_FullMix = Option(CreateFullMix(readJsonFile("./test/dataset/Sample4_FullMix.json")))
  private val sample4_Withdraw = Option.empty[Withdraw]

  private val sampleMap_All: Map[String, Long] = Map(sample0_TokenId -> 60000)
  private val sampleMap_Running: Map[String, Long] = Map(sample0_TokenId -> 40000)
  private val sample_SeqMixingRequests = Seq(sample0_MixingRequest, sample3_MixingRequest, sample4_MixingRequest)
  private val sample_SeqRunningMixingRequests = Seq(sample0_MixingRequest, sample3_MixingRequest)

  private val sample5_WithdrawAddress: String = sample0_WithdrawAddress
  private val sample5_numRounds: Int = 30
  private val sample5_ergRing: Long = 1000000L
  private val sample5_ergNeeded: Long = 123503333L
  private val sample5_tokenRing: Long = 20000L
  private val sample5_tokenNeeded: Long = 20066L
  private val sample5_mixingTokenId: String = sample0_TokenId
  private val sample5_topId: String = "818841b2-8393-499a-8a72-e4a4c54a6ebe"

  private val sample_MixingBox0 = MixingBox(readJsonFile("./test/dataset/SampleNewGroup_MixingBox0.json"))
  private val sample_MixingBox1 = MixingBox(readJsonFile("./test/dataset/SampleNewGroup_MixingBox1.json"))
  private val sampleArray_MixGroupRequest = Array(sample_MixingBox0, sample_MixingBox1)
  private val sampleArray_MixGroupRequestPrices = jsonToMixingBoxPrices(readJsonFile("./test/dataset/SampleNewGroup_MixingBoxPrices.json"))
  private val sampleArray_MixGroupRequestAll = CreateMixGroupRequest(readJsonFile("./test/dataset/Sample0_MixGroupRequest.json"))

  private val sample6_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample6_MixRequest.json"))
  private val sample6_MixRequest: MixRequest = sample6_MixingRequest.toMixRequest
  private val sample6_MixState = CreateMixState(readJsonFile("./test/dataset/Sample6_MixState.json"))
  private val sample6_HalfMix = Option.empty[HalfMix]
  private val sample6_FullMix = Option(CreateFullMix(readJsonFile("./test/dataset/Sample6_FullMix.json")))
  private val sample6_WithdrawTx = Option(CreateWithdrawTx(readJsonFile("./test/dataset/Sample6_WithdrawTx.json")))
  private val sample6_Withdraw = Option(CreateWithdraw(Array(sample6_WithdrawTx.get.mixId, sample6_WithdrawTx.get.txId, sample6_WithdrawTx.get.time, sample6_WithdrawTx.get.boxId, sample6_WithdrawTx.get.txBytes)))

  private val sample7_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample7_MixRequest.json"))
  private val sample7_MixRequest: MixRequest = sample7_MixingRequest.toMixRequest
  private val sample7_MixState = CreateMixState(readJsonFile("./test/dataset/Sample7_MixState.json"))
  private val sample7_HalfMix = Option(CreateHalfMix(readJsonFile("./test/dataset/Sample7_HalfMix.json")))
  private val sample7_FullMix = Option(CreateFullMix(readJsonFile("./test/dataset/Sample7_FullMix.json")))
  private val sample7_WithdrawTx = Option(CreateWithdrawTx(readJsonFile("./test/dataset/Sample7_WithdrawTx.json")))
  private val sample7_Withdraw = Option(CreateWithdraw(Array(sample7_WithdrawTx.get.mixId, sample7_WithdrawTx.get.txId, sample7_WithdrawTx.get.time, sample7_WithdrawTx.get.boxId, sample7_WithdrawTx.get.txBytes)))

  private val sample_AllMixingRequests = Seq(sample0_MixingRequest, sample3_MixingRequest, sample4_MixingRequest, sample6_MixingRequest, sample7_MixingRequest)

  private val sample_AllMixStates = Seq(sample0_MixState, sample3_MixState, sample4_MixState, sample6_MixState, sample7_MixState)
  private val sample_AllMixWithdraws: Seq[WithdrawTx] = Seq(sample6_WithdrawTx.get, sample7_WithdrawTx.get)
  private val sample_HalfMixes = Seq(sample0_HalfMix.get, sample4_HalfMix.get, sample7_HalfMix.get)
  private val sample_FullMixes = Seq(sample3_FullMix.get, sample4_FullMix.get, sample6_FullMix.get, sample7_FullMix.get)

  private val sample0_Mix = Mix(sample0_MixRequest, Option(sample0_MixState), sample0_HalfMix, sample0_FullMix, sample0_Withdraw)
  private val sample3_Mix = Mix(sample3_MixRequest, Option(sample3_MixState), sample3_HalfMix, sample3_FullMix, sample3_Withdraw)
  private val sample4_Mix = Mix(sample4_MixRequest, Option(sample4_MixState), sample4_HalfMix, sample4_FullMix, sample4_Withdraw)
  private val sample6_Mix = Mix(sample6_MixRequest, Option(sample6_MixState), sample6_HalfMix, sample6_FullMix, sample6_Withdraw)
  private val sample7_Mix = Mix(sample7_MixRequest, Option(sample7_MixState), sample7_HalfMix, sample7_FullMix, sample7_Withdraw)

  private val sampleSeq_AllMixes = Seq(sample0_Mix, sample3_Mix, sample4_Mix, sample6_Mix, sample7_Mix)
  private val sampleSeq_ActiveMixes = Seq(sample0_Mix, sample3_Mix, sample4_Mix)
  private val sampleSeq_WithdrawnMixes = Seq(sample6_Mix, sample7_Mix)

  private val emptyAddress_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/SampleEmptyAddress_MixRequest.json"))
  private val emptyAddress_MixId = emptyAddress_MixingRequest.id

  private val sample12_MixingRequest: MixingRequest = CreateMixingRequest(readJsonFile("./test/dataset/Sample12_MixRequest.json"))
  private val sample12_MixId: String = sample12_MixingRequest.id

  private val sample16_hopMixList = jsonToObjectList[HopMix](readJsonFile("./test/dataset/Sample16_HopMixList.json"), CreateHopMix.apply)
  private val sample16_lastRound = sample16_hopMixList.last.round
  private val sample16_mixId = sample16_hopMixList.last.mixId

  /**
   * apis for testing ErgoMixer.newCovertRequest
   * spec data: BigInt, the masterKey and it exists or not
   * and (String, Int, Seq[String]) which is newCovertData
   * db data: MixCovertRequest, the covert object in database
   *
   * @return BigInt masterKey
   * @return Boolean exists
   */
  def newCovertData: (String, Int, Seq[String]) = (sample2_CovertName, sample2_roundNum, sample2_Addresses)

  def existsMasterSecretKey: (BigInt, MixCovertRequest) = (sample2_masterKey_1, sample0_MixCovertRequest)

  def notExistsMasterSecretKey: BigInt = sample2_masterKey_2

  /**
   * api for testing ErgoMixer.handleCovertSupport
   * spec data: (String, String, Long), covertId, tokenId, ring
   * db data: (MixCovertRequest, CovertAsset), the covert object and corresponding asset in database
   */
  def existsCovertAsset: (String, String, Long, MixCovertRequest, CovertAsset) = (sample0_CovertAsset.covertId, sample0_CovertAsset.tokenId, 200000000000L, sample0_MixCovertRequest, sample0_CovertAsset)

  def notExistsCovertAsset: (String, String, Long, MixCovertRequest) = (sample0_CovertAsset.covertId, sample0_CovertAsset.tokenId, 200000000000L, sample0_MixCovertRequest)

  /**
   * api for testing ErgoMixer.getWithdrawAddress
   * spec data: (String, String), mixId and withdrawAddress
   * db data: MixingRequest, the mix request object in database
   *
   * @return String mixId
   * @return String withdrawAddress
   * @return MixRequest mixRequest
   */
  def withdrawAddressOfMixRequest: (String, String, MixingRequest) = (sample0_MixId, sample0_WithdrawAddress, sample0_MixingRequest)

  /**
   * api for testing ErgoMixer.getRoundNum
   * spec data: (String, Int), mixId and roundNum
   * db data: MixState, the mix state object in database
   *
   * @return String mixId
   * @return Int roundNum
   * @return MixState mixState
   */
  def roundNumOfMixState: (String, Int, MixState) = (sample1_MixId, sample1_RoundNum, sample1_MixState)

  /**
   * api for testing ErgoMixer.getIsAlice
   * spec data: (String, Boolean), mixId and isAlice
   * db data: MixState, the mix state object in database
   *
   * @return String mixId
   * @return Boolean isAlice
   * @return MixState mixState
   */
  def isAliceOfMixState: (String, Boolean, MixState) = (sample1_MixId, sample1_IsAlice, sample1_MixState)

  /**
   * api for testing ErgoMixer.withdrawMixNow
   * spec data: String, mixId
   * db data: MixingRequest, the mix request object in database
   *
   * @return String mixId
   * @return MixRequest req
   */
  def withAddressMixId_token: (String, MixingRequest) = (sample0_MixId, sample0_MixingRequest)
  def withAddressMixId_erg: (String, MixingRequest) = (sample12_MixId, sample12_MixingRequest)

  def emptyAddressMixId: (String, MixingRequest) = (emptyAddress_MixId, emptyAddress_MixingRequest)

  /**
   * api for testing ErgoMixer.getCovertCurrentMixing
   * spec data: (String, Map[String, Long]), groupId and Map of tokenId to amount of current running mixing
   * db data: Seq[MixingRequest], sequence of MixingRequest objects
   *
   * @return String mixId
   * @return Boolean isAlice
   * @return MixState mixState
   */
  def notWithdrawnMixingRequests: (String, Map[String, Long], Seq[MixingRequest]) = (sample0_GroupId, sampleMap_All, sample_SeqMixingRequests)

  /**
   * api for testing ErgoMixer.getCovertRunningMixing
   * spec data: (String, Map[String, Long]), groupId and Map of tokenId to amount of current running mixing
   * db data: Seq[MixingRequest], sequence of MixingRequest objects
   *
   * @return String mixId
   * @return Boolean isAlice
   * @return MixState mixState
   */
  def notWithdrawnRunningMixRequests: (String, Map[String, Long], Seq[MixingRequest]) = (sample0_GroupId, sampleMap_Running, sample_SeqRunningMixingRequests)

  /**
   * apis for testing ErgoMixer.newMixRequest
   * all spec data
   */
  def newMixData: (String, Int, Long, Long, Long, Long, String, String) = (
    sample5_WithdrawAddress,
    sample5_numRounds,
    sample5_ergRing,
    sample5_ergNeeded,
    sample5_tokenRing,
    sample5_tokenNeeded,
    sample5_mixingTokenId,
    sample5_topId)

  /**
   * apis for testing ErgoMixer.newMixGroupRequest
   * all spec data
   */
  def newGroupData: (mutable.Iterable[MixingBox], Seq[(Long, Long)], MixGroupRequest) = (sampleArray_MixGroupRequest, sampleArray_MixGroupRequestPrices, sampleArray_MixGroupRequestAll)

  /**
   * apis for testing ErgoMixer.getFinishedForGroup
   * spec data: (Int, Int, Int), number of withdrawn, finished and all mix requests
   * db data: Seq[MixingRequest], sequence of MixingRequest objects
   *
   * @return String groupId
   * @return Int countWithdrawn
   * @return Int countFinished
   * @return Int countAll
   */
  def roundFinishedData: (String, Int, Int, Int, Seq[MixingRequest]) = (sample0_GroupId, 2, 3, 5, sample_AllMixingRequests)

  /**
   * apis for testing ErgoMixer.getProgressForGroup
   * spec data: (String, (Int, Int)), groupId and progress output
   * db data: (Seq[MixingRequest], Seq[MixState]), sequence of MixingRequest and corresponding mix state objects
   */
  def roundProgressData: (String, (Int, Int), Seq[MixingRequest], Seq[MixState]) = (sample0_GroupId, (60, 10), sample_AllMixingRequests, sample_AllMixStates)

  /**
   * apis for testing ErgoMixer.getMixes
   * spec data:
   * Mixes: (String, Seq[Mix]), groupId and progress output for three type of mixes(all, active and withdrawn)
   *
   * db data: (Seq[MixingRequest], Seq[MixState], Seq[WithdrawTx], Seq[HalfMix], Seq[FullMix])
   * which are sequence of data for each table of MixingRequest, MixState, Withdraw, HalfMix and FullMix
   */
  def dbMixes: (Seq[MixingRequest], Seq[MixState], Seq[WithdrawTx], Seq[HalfMix], Seq[FullMix]) = (sample_AllMixingRequests, sample_AllMixStates, sample_AllMixWithdraws, sample_HalfMixes, sample_FullMixes)

  def allMixes: (String, Seq[Mix]) = (sample0_GroupId, sampleSeq_AllMixes)

  def activeMixes: (String, Seq[Mix]) = (sample0_GroupId, sampleSeq_ActiveMixes)

  def withdrawnMixes: (String, Seq[Mix]) = (sample0_GroupId, sampleSeq_WithdrawnMixes)

  /**
   * apis for testing ErgoMixer.getMixes
   * spec data: (String, Int), the mix request ID and last hop round
   * db data: (Seq[HopMix],
   */
  def lastHopRound: (Seq[HopMix], String, Int) = (sample16_hopMixList, sample16_mixId, sample16_lastRound)
}
