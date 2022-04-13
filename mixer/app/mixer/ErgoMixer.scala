package mixer

import java.util.UUID
import app.Configs
import wallet.WalletHelper._

import javax.inject.Inject
import models.Models.MixStatus.{Complete, Queued, Running}
import models.Models.MixWithdrawStatus.{HopRequested, NoWithdrawYet, WithdrawRequested, Withdrawn}
import models.Models._
import network.NetworkUtils
import play.api.Logger
import wallet.{Wallet, WalletHelper}

import scala.collection.mutable
import dao._


class ErgoMixer @Inject()(
                             networkUtils: NetworkUtils,
                             daoUtils: DAOUtils,
                             allMixDAO: AllMixDAO,
                             mixingCovertRequestDAO: MixingCovertRequestDAO,
                             covertDefaultsDAO: CovertDefaultsDAO,
                             covertAddressesDAO: CovertAddressesDAO,
                             mixingRequestsDAO: MixingRequestsDAO,
                             mixingGroupRequestDAO: MixingGroupRequestDAO,
                             mixStateDAO: MixStateDAO,
                             fullMixDAO: FullMixDAO,
                             withdrawDAO: WithdrawDAO,
                             hopMixDAO: HopMixDAO,
                             covertsDAO: CovertsDAO
                         ) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * creates a covert address
   *
   * @param numRounds level of mixing
   * @param addresses selected addresses for withdrawal
   * @return covert address
   */
  def newCovertRequest(nameCovert: String, numRounds: Int, addresses: Seq[String] = Nil, privateKey: String): String = {
    okAddresses(addresses)
    networkUtils.usingClient { implicit ctx =>
      var masterSecret: BigInt = BigInt(0)
      if (privateKey.isEmpty) {
        masterSecret = randBigInt
      }
      else {
        masterSecret = BigInt(privateKey, 16)
        if (daoUtils.awaitResult(mixingCovertRequestDAO.existsByMasterKey(masterSecret))) {
          throw new Exception("this private key exist")
        }
      }
      val wallet = new Wallet(masterSecret)
      val depositSecret = wallet.getSecret(-1, privateKey.nonEmpty)
      val depositAddress = WalletHelper.getAddressOfSecret(depositSecret)
      val mixId = UUID.randomUUID().toString
      val lastErgRing = Configs.params.filter(_._1.isEmpty).head._2.rings.last
      val req: MixCovertRequest = MixCovertRequest(nameCovert, mixId, now, depositAddress, numRounds, privateKey.nonEmpty, masterSecret)
      val covertAddresses = addresses.map({(mixId, _)})
      val asset = CovertAsset(mixId, "", lastErgRing, 0L, now)
      covertsDAO.createCovert(req, covertAddresses, asset)
      logger.info(s"covert address $mixId is created, addr: $depositAddress. you can add supported asset for it.")
      depositAddress
    }
  }

  /**
   * Adds or updates name for a covert
   *
   * @param covertId   covert id
   * @param nameCovert name for covert
   */
  def handleNameCovert(covertId: String, nameCovert: String): Unit = {
    if (!daoUtils.awaitResult(mixingCovertRequestDAO.existsById(covertId))) {
      logger.info("no such covert id!")
      throw new Exception("no such covert id!")
    } else {
      mixingCovertRequestDAO.updateNameCovert(covertId, nameCovert)
    }
  }

  /**
   * Adds or updates assets for a covert
   *
   * @param covertId covert id
   * @param tokenId  token id of asset, empty in case of erg
   * @param ring     ring of the asset
   */
  def handleCovertSupport(covertId: String, tokenId: String, ring: Long): Unit = {
    if (!daoUtils.awaitResult(mixingCovertRequestDAO.existsById(covertId))) {
      logger.info("no such covert id!")
      throw new Exception("no such covert id!")
    }
    if (daoUtils.awaitResult(covertDefaultsDAO.exists(covertId, tokenId))) {
      daoUtils.awaitResult(covertDefaultsDAO.updateRing(covertId, tokenId, ring))
      logger.info(s"asset $tokenId updated, new ring: $ring")
    } else {
      val asset = CovertAsset(covertId, tokenId, ring, 0L, now)
      daoUtils.awaitResult(covertDefaultsDAO.insert(asset))
      logger.info(s"asset created for covert address, id: $tokenId, ring: $ring")
    }
  }

  /**
   * adds addresses as withdrawal addresses of a covert after parsing them
   *
   * @param covertId  covert id
   * @param addresses addresses to be added
   */
  def addCovertWithdrawAddress(covertId: String, addresses: Seq[String]): Unit = {
    okAddresses(addresses)
    if (!daoUtils.awaitResult(mixingCovertRequestDAO.existsById(covertId))) {
      throw new Exception("Invalid covert id")
    }
    daoUtils.awaitResult(covertAddressesDAO.delete(covertId))
    addresses.foreach(address => {
      daoUtils.awaitResult(covertAddressesDAO.insert(covertId, address))
    })
  }

  /**
   * updates withdraw address of a mix if address is valid
   *
   * @param mixId mix id
   */
  def updateMixWithdrawAddress(mixId: String, address: String): Unit = {
    okAddresses(Seq(address))
    daoUtils.awaitResult(mixingRequestsDAO.updateAddress(mixId, address))
  }

  /**
   * gets withdraw address of a mix
   *
   * @param mixId mix id
   */
  def getWithdrawAddress(mixId: String): String = {
    daoUtils.awaitResult(mixingRequestsDAO.selectByMixId(mixId)).getOrElse(throw new Exception("mixId not found")).withdrawAddress
  }

  /**
   * gets master secret of a mix
   *
   * @param mixId mix id
   */
  def getMasterSecret(mixId: String): BigInt = {
    daoUtils.awaitResult(mixingRequestsDAO.selectMasterKey(mixId)).getOrElse(throw new Exception("mixId not found"))
  }

  /**
   * gets round number of a mix
   *
   * @param mixId mix id
   */
  def getRoundNum(mixId: String): Int = {
    daoUtils.awaitResult(mixStateDAO.selectByMixId(mixId)).getOrElse(throw new Exception("mixId not found")).round
  }

  /**
   * gets isAlice of a mix
   *
   * @param mixId mix id
   */
  def getIsAlice(mixId: String): Boolean = {
    daoUtils.awaitResult(mixStateDAO.selectByMixId(mixId)).getOrElse(throw new Exception("mixId not found")).isAlice
  }

  /**
   * gets full box id of a mix
   *
   * @param mixId mix id
   */
  def getFullBoxId(mixId: String): String = {
    daoUtils.awaitResult(fullMixDAO.selectFullBoxIdByMixId(mixId)).getOrElse({
      logger.warn(s"mixId $mixId not found in FullMix")
      ""
    })
  }

  /**
   * sets mix for withdrawal
   *
   * @param mixId mix id
   */
  def withdrawMixNow(mixId: String): Unit = {
    val address = getWithdrawAddress(mixId)
    if (address.nonEmpty) {
      if (Configs.hopRounds > 0 && daoUtils.awaitResult(mixingRequestsDAO.isMixingErg(mixId))) daoUtils.awaitResult(mixingRequestsDAO.updateWithdrawStatus(mixId, HopRequested.value))
      else daoUtils.awaitResult(mixingRequestsDAO.updateWithdrawStatus(mixId, WithdrawRequested.value))
    } else throw new Exception("Set a valid withdraw address first!")
  }

  /**
   * @param covertId covert id
   * @return assets of the covert id
   */
  def getCovertAssets(covertId: String): Seq[CovertAsset] = {
    daoUtils.awaitResult(covertDefaultsDAO.selectAllAssetsByMixGroupId(covertId))
  }

  /**
   * calculates amount of current mixing (not withdrawn yet) for each asset of the covert request
   *
   * @param covertId covert id
   * @return map of string to long, specifying token id to amount of current mixing
   */
  def getCovertCurrentMixing(covertId: String): Map[String, Long] = {
    val mp = mutable.Map.empty[String, Long]
    val mixBoxes = daoUtils.awaitResult(mixingRequestsDAO.selectAllByWithdrawStatus(covertId, NoWithdrawYet.value))
    mixBoxes.foreach(box => mp(box.tokenId) = mp.getOrElse(box.tokenId, 0L) + box.getAmount)
    mp.toMap
  }

  /**
   * calculates amount of current running mixing for each asset of the covert request
   *
   * @param covertId covert id
   * @return map of string to long, specifying token id to amount of current running mixing
   */
  def getCovertRunningMixing(covertId: String): Map[String, Long] = {
    val mp = mutable.Map.empty[String, Long]
    val mixBoxes = daoUtils.awaitResult(mixingRequestsDAO.selectAllByMixAndWithdrawStatus(covertId, Running, NoWithdrawYet.value))
    mixBoxes.foreach(box => mp(box.tokenId) = mp.getOrElse(box.tokenId, 0L) + box.getAmount)
    mp.toMap
  }

  /**
   * @param covertId covert id
   * @return list of covert addresses
   */
  def getCovertAddresses(covertId: String): Seq[String] = {
    daoUtils.awaitResult(covertAddressesDAO.selectAllAddressesByCovertId(covertId))
  }

  /**
   * @return list of covert addresses
   */
  def getCovertList: Seq[MixCovertRequest] = {
    daoUtils.awaitResult(mixingCovertRequestDAO.all)
  }

  /**
   * @return covert request
   */
  def getCovertById(covertId: String): MixCovertRequest = {
    daoUtils.awaitResult(mixingCovertRequestDAO.selectCovertRequestByMixGroupId(covertId)).getOrElse(throw new Exception(s"covertId $covertId not found in MixingCovertRequest"))
  }

  /**
   * creates a new mix box, can be used both for erg mixing and token mixing
   *
   * @param withdrawAddress withdraw address, empty in case of manual withdrawal
   * @param numRounds       mixing level
   * @param ergRing         erg ring
   * @param ergNeeded       erg needed to start mixing
   * @param tokenRing       token ring, 0 in case of erg mixing
   * @param tokenNeeded     token needed to start mixing, 0 in case of erg mixing
   * @param mixingTokenId   mixing token id, empty in case of erg mixing
   * @param topId           group or covert mix id of this box
   * @return deposit address of the box
   */
  def newMixRequest(withdrawAddress: String, numRounds: Int, ergRing: Long, ergNeeded: Long, tokenRing: Long, tokenNeeded: Long, mixingTokenId: String, topId: String): String = {
    networkUtils.usingClient { implicit ctx =>
      val masterSecret = randBigInt
      val wallet = new Wallet(masterSecret)
      val depositSecret = wallet.getSecret(-1)
      val depositAddress = WalletHelper.getProveDlogAddress(depositSecret, ctx)
      val mixId = UUID.randomUUID().toString
      val req = MixingRequest(mixId, topId, ergRing, numRounds, MixStatus.fromString(Queued.value), now, withdrawAddress, depositAddress, false, ergNeeded, numRounds, NoWithdrawYet.value, tokenRing, tokenNeeded, mixingTokenId, masterSecret)
      mixingRequestsDAO.insert(req)
      depositAddress
    }
  }

  /**
   * creates a group mix and its respective boxes
   *
   * @param mixRequests requests of this group mix, each will become a mix box
   * @return group mix id
   */
  def newMixGroupRequest(mixRequests: Iterable[MixingBox]): String = {
    networkUtils.usingClient { implicit ctx =>
      val addresses = mixRequests.map(_.withdraw).filter(_.nonEmpty).toSeq
      okAddresses(addresses)
      // if here then addresses are valid
      val masterSecret = randBigInt
      val wallet = new Wallet(masterSecret)
      val numOut = Configs.maxOuts
      val numTxToDistribute = (mixRequests.size + numOut - 1) / numOut
      var totalNeededErg: Long = numTxToDistribute * Configs.distributeFee
      var totalNeededToken: Long = 0
      var mixingAmount: Long = 0
      var mixingTokenAmount: Long = 0
      var mixingTokenId: String = ""
      val depositSecret = wallet.getSecret(-1)
      val depositAddress = WalletHelper.getProveDlogAddress(depositSecret, ctx)
      val mixId = UUID.randomUUID().toString
      mixRequests.foreach(mixBox => {
        val price = mixBox.price
        totalNeededErg += price._1
        totalNeededToken += price._2
        mixingAmount += mixBox.amount
        mixingTokenAmount += mixBox.mixingTokenAmount
        mixingTokenId = mixBox.mixingTokenId
        this.newMixRequest(mixBox.withdraw, mixBox.token, mixBox.amount, price._1, mixBox.mixingTokenAmount, price._2, mixBox.mixingTokenId, mixId)
      })
      val req = MixGroupRequest(mixId, totalNeededErg, GroupMixStatus.Queued.value, now, depositAddress, 0L, 0L, mixingAmount, mixingTokenAmount, totalNeededToken, mixingTokenId, masterSecret)
      mixingGroupRequestDAO.insert(req)
      if (mixingTokenId.isEmpty) {
        logger.info(s"Please deposit $totalNeededErg nanoErgs to $depositAddress")
      } else {
        logger.info(s"Please deposit $totalNeededErg nanoErgs and $totalNeededToken of $mixingTokenId to $depositAddress")
      }
      mixId
    }
  }

  /**
   * @return returns group mixes
   */
  def getMixRequestGroups = {
    daoUtils.awaitResult(mixingGroupRequestDAO.all)
  }

  /**
   * @return returns not completed group mixes
   */
  def getMixRequestGroupsActive = {
    daoUtils.awaitResult(mixingGroupRequestDAO.active)
  }

  /**
   * calculates stat about finished and withdrawn boxes of a specific group mix
   *
   * @param groupId group id
   * @return (total, completed, withdrawn) numbers of this group
   */
  def getFinishedForGroup(groupId: String): (Long, Long, Long) = {
    val withdrawn = mixingRequestsDAO.countWithdrawn(groupId)
    val finished = mixingRequestsDAO.countFinished(groupId)
    val all = mixingRequestsDAO.countAll(groupId)
    (daoUtils.awaitResult(all).toLong, daoUtils.awaitResult(finished).toLong, daoUtils.awaitResult(withdrawn).toLong)
  }

  /**
   * calculates progress of a specific group mix
   *
   * @param groupId group id
   * @return (expected, done) number of mixes, so the progress is done / expected
   */
  def getProgressForGroup(groupId: String): (Long, Long) = {
    var mixDesired = 0
    val done = daoUtils.awaitResult(mixingRequestsDAO.groupRequestsProgress(groupId, MixStatus.Running)).map( tuple => {
        mixDesired += tuple._1
        Math min(tuple._1, tuple._2)
    }).sum
    (mixDesired, done)
  }

  /**
   * @return all group mixes
   */
  def getMixRequestGroupsComplete = {
    daoUtils.awaitResult(mixingGroupRequestDAO.completed)
  }

  /**
   * information about mix boxes of a specific group
   *
   * @param id id of the group or covert request
   * @return mix box info list of a specific group, whether it is half or full box, and whether it is withdrawn or not including tx id
   */
  def getMixes(id: String, status: String) = {
    val boxes: Seq[MixRequest] = daoUtils.awaitResult({
      if (status == "all") mixingRequestsDAO.selectByMixGroupId(id)
      else if (status == "active") mixingRequestsDAO.selectActiveRequests(id, Withdrawn.value)
      else mixingRequestsDAO.selectAllByWithdrawStatus(id, Withdrawn.value)
    })
    boxes.map { req =>
      val mixStateFuture = mixStateDAO.selectByMixId(req.id)
      val withdrawFuture = withdrawDAO.selectByMixId(req.id)

      val mixState = daoUtils.awaitResult(mixStateFuture)
      val halfAndFullMix = daoUtils.awaitResult(allMixDAO.selectMixes(req.id, mixState))
      val halfMix = halfAndFullMix._1
      val fullMix = halfAndFullMix._2

      val withdraw: Option[Withdraw] = {
        val result = daoUtils.awaitResult(withdrawFuture)
        if (result.isDefined)
            Option(CreateWithdraw(Array(result.get.mixId, result.get.txId, result.get.time, result.get.boxId, result.get.txBytes)))
        else
            Option.empty[Withdraw]
      }
      Mix(req, mixState, halfMix, fullMix, withdraw)
    }
  }

  /**
   * returns list of all covert addresses with their private keys
   *
   */
  def covertKeys: Seq[String] = {
    val header = "name, address, private key"
    val keys = daoUtils.awaitResult(mixingCovertRequestDAO.all).map(req => {
      val wallet = new Wallet(req.masterKey)
      val nameCovert = if (req.nameCovert.nonEmpty) req.nameCovert else "No Name"
      s"$nameCovert, ${req.depositAddress}, ${wallet.getSecret(-1, req.isManualCovert).toString(16)}"
    })
    Seq(header) ++ keys
  }

  /**
   * returns the private key and address of a covert
   *
   */
  def covertInfoById(covertId: String): (String, BigInt) = {
    val req: MixCovertRequest = daoUtils.awaitResult(mixingCovertRequestDAO.selectCovertRequestByMixGroupId(covertId)).getOrElse(throw new Exception(s"covertId $covertId not found in MIXING_COVERT_REQUEST"))
    val wallet = new Wallet(req.masterKey)
    (req.depositAddress, wallet.getSecret(-1, req.isManualCovert))
  }

  /**
   * TODO: remove this after next release
   * checks and updates state of group mixes which are already complete
   *
   */
  def updateGroupMixesStates(): Unit = {
    val groupIds: Seq[String] = daoUtils.awaitResult(mixingGroupRequestDAO.allIds)
    groupIds.foreach(groupId => {
      val numRunning = daoUtils.awaitResult(mixingRequestsDAO.countNotWithdrawn(groupId))
      if (numRunning == 0) { // group mix is done because all mix boxes are withdrawn
        mixingGroupRequestDAO.updateStatusById(groupId, GroupMixStatus.Complete.value)
      }
    })
  }

  /**
   * returns last round number of mixId
   *
   * @param mixId String
   */
  def getHopRound(mixId: String): Int = {
    val hopRounds = daoUtils.awaitResult(hopMixDAO.getHopRound(mixId))
    if (hopRounds.nonEmpty) hopRounds.get else -1
  }

}
