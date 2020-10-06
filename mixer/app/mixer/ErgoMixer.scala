package mixer

import java.util.UUID

import app.{Configs, TokenErgoMix, Util => EUtil}
import cli._
import db.Columns._
import db.ScalaDB._
import db.Tables
import helpers.Util._
import db.core.DataStructures.anyToAny
import mixer.Models.MixStatus.{Complete, Queued}
import mixer.Models.MixWithdrawStatus.{NoWithdrawYet, WithdrawRequested, Withdrawn}
import mixer.Models.{CovertAsset, FullMix, GroupMixStatus, HalfMix, Mix, MixBox, MixCovertRequest, MixGroupRequest, MixRequest, MixState, MixStatus, MixWithdrawStatus, Withdraw}
import play.api.Logger

import scala.collection.mutable


class ErgoMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)

  import tables._

  /**
   * creates a covert address
   *
   * @param numRounds level of mixing
   * @param addresses selected addresses for withdrawal
   * @return covert address
   */
  def newCovertRequest(numRounds: Int, addresses: Seq[String] = Nil): String = {
    MixUtils.usingClient { implicit ctx =>
      val masterSecret = randBigInt
      val wallet = new Wallet(masterSecret)
      val depositSecret = wallet.getSecret(-1)
      val depositAddress = TokenErgoMix.getProveDlogAddress(depositSecret)
      val mixId = UUID.randomUUID().toString
      val minErgRing = Configs.params.filter(_._1.isEmpty).head._2.rings.head
      mixCovertTable.insert(mixId, now, depositAddress, numRounds, masterSecret)
      addCovertWithdrawAddress(mixId, addresses)
      handleCovertSupport(mixId, "", minErgRing)
      logger.info(s"covert address $mixId is created, addr: $depositAddress. you can add supported asset for it.")
      depositAddress
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
    if (!mixCovertTable.exists(mixGroupIdCol === covertId)) {
      logger.info("no such covert id!")
      throw new Exception("no such covert id!")
    }
    if (covertDefaultsTable.exists(mixGroupIdCol === covertId, tokenIdCol === tokenId)) {
      covertDefaultsTable.update(mixingTokenAmount <-- ring).where(mixGroupIdCol === covertId, tokenIdCol === tokenId)
      logger.info(s"asset $tokenId updated, new ring: $ring")
    } else {
      covertDefaultsTable.insert(covertId, tokenId, ring, 0L, now)
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
    MixUtils.usingClient { implicit ctx =>
      val util = new EUtil()
      addresses.foreach(address => {
        try util.getAddress(address).script catch {
          case _: Throwable => throw new Exception("Invalid withdraw address")
        }
      })
    }
    if (!mixCovertTable.exists(mixGroupIdCol === covertId)) {
      throw new Exception("Invalid covert id")
    }
    covertAddressesTable.deleteWhere(mixGroupIdCol === covertId)
    addresses.foreach(address => {
      covertAddressesTable.insert(covertId, address)
    })
  }

  /**
   * updates withdraw address of a mix if address is valid
   * @param mixId mix id
   */
  def updateMixWithdrawAddress(mixId: String, address: String): Unit = {
    MixUtils.usingClient { implicit ctx =>
      val util = new EUtil()
      try util.getAddress(address).script catch {
        case _: Throwable => throw new Exception("Invalid withdraw address")
      }
      mixRequestsTable.update(withdrawAddressCol <-- address).where(mixIdCol === mixId)
    }
  }


  /**
   * sets mix for withdrawal
   * @param mixId mix id
   */
  def withdrawMixNow(mixId: String): Unit = {
    val address = mixRequestsTable.select(withdrawAddressCol).where(mixIdCol === mixId).firstAsT[String].head
    if (address.nonEmpty) {
      mixRequestsTable.update(mixWithdrawStatusCol <-- WithdrawRequested.value).where(mixIdCol === mixId)

    } else throw new Exception("Set a valid withdraw address first!")
  }

  /**
   * @param covertId covert id
   * @return assets of the covert id
   */
  def getCovertAssets(covertId: String): Seq[CovertAsset] = {
    covertDefaultsTable.selectStar.where(mixGroupIdCol === covertId).as(CovertAsset(_))
  }

  /**
   * calculates amount of current mixing (not withdrawn yet) for each asset of the covert request
   *
   * @param covertId covert id
   * @return map of string to long, specifying token id to amount of current mixing
   */
  def getCovertCurrentMixing(covertId: String): Map[String, Long] = {
    val mp = mutable.Map.empty[String, Long]
    val mixBoxes = mixRequestsTable.selectStar.where(mixGroupIdCol === covertId, mixWithdrawStatusCol === NoWithdrawYet.value).as(MixRequest(_))
    mixBoxes.foreach(box => mp(box.tokenId) = mp.getOrElse(box.tokenId, 0L) + box.getAmount)
    mp.toMap
  }

  /**
   * @param covertId covert id
   * @return list of covert addresses
   */
  def getCovertAddresses(covertId: String): Seq[String] = {
    covertAddressesTable.select(addressCol).where(mixGroupIdCol === covertId).as(it => it.toIterator.next().as[String])
  }

  /**
   * @return list of covert addresses
   */
  def getCovertList: Seq[MixCovertRequest] = {
    mixCovertTable.selectStar.as(MixCovertRequest(_))
  }

  /**
   * @return covert request
   */
  def getCovertById(covertId: String): MixCovertRequest = {
    mixCovertTable.selectStar.where(mixGroupIdCol === covertId).as(MixCovertRequest(_)).head
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
    MixUtils.usingClient { implicit ctx =>
      val masterSecret = randBigInt
      val wallet = new Wallet(masterSecret)
      val depositSecret = wallet.getSecret(-1)
      val depositAddress = TokenErgoMix.getProveDlogAddress(depositSecret)
      val mixId = UUID.randomUUID().toString
      mixRequestsTable.insert(mixId, topId, ergRing, numRounds, Queued.value, now, withdrawAddress, depositAddress, false, ergNeeded, numRounds, NoWithdrawYet.value, tokenRing, tokenNeeded, mixingTokenId, masterSecret)
      depositAddress
    }
  }

  /**
   * creates a group mix and its respective boxes
   *
   * @param mixRequests requests of this group mix, each will become a mix box
   * @return group mix id
   */
  def newMixGroupRequest(mixRequests: Iterable[MixBox]): String = {
    MixUtils.usingClient { implicit ctx =>
      val util = new EUtil()
      mixRequests.foreach(mixBox => {
        if (mixBox.withdraw.nonEmpty) { // empty withdraw address means manual withdrawal!
          try util.getAddress(mixBox.withdraw).script catch {
            case e: Throwable => throw new Exception("Invalid withdraw address")
          }
        }
      })
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
      val depositAddress = TokenErgoMix.getProveDlogAddress(depositSecret)
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
      mixRequestsGroupTable.insert(mixId, totalNeededErg, GroupMixStatus.Queued.value, now, depositAddress, 0L, 0L, mixingAmount, mixingTokenAmount, totalNeededToken, mixingTokenId, masterSecret)
      if (mixingTokenId.isEmpty) {
        logger.info(s"Please deposit $totalNeededErg nanoErgs to $depositAddress")
      } else {
        logger.info(s"Please deposit $totalNeededErg nanoErgs and $totalNeededToken of $mixingTokenId to $depositAddress")
      }
      return mixId
    }
  }

  /**
   * @return returns group mixes
   */
  def getMixRequestGroups = {
    mixRequestsGroupTable.select(mixGroupReqCols :+ masterSecretGroupCol: _*).as(MixGroupRequest(_))
  }

  /**
   * @return returns not completed group mixes
   */
  def getMixRequestGroupsActive = {
    mixRequestsGroupTable.select(mixGroupReqCols :+ masterSecretGroupCol: _*)
      .where(mixStatusCol <> GroupMixStatus.Complete.value).as(MixGroupRequest(_))
  }

  /**
   * calculates stat about finished and withdrawn boxes of a specific group mix
   *
   * @param groupId group id
   * @return (total, completed, withdrawn) numbers of this group
   */
  def getFinishedForGroup(groupId: String): (Long, Long, Long) = {
    val withdrawn = mixRequestsTable.countWhere(mixWithdrawStatusCol === MixWithdrawStatus.Withdrawn.value, mixGroupIdCol === groupId)
    val finished = mixRequestsTable.countWhere(mixStatusCol === Complete.value, mixGroupIdCol === groupId)
    val all = mixRequestsTable.countWhere(mixGroupIdCol === groupId)
    (all, finished, withdrawn)
  }

  /**
   * calculates progress of a specific group mix
   *
   * @param groupId group id
   * @return (expected, done) number of mixes, so the progress is done / expected
   */
  def getProgressForGroup(groupId: String): (Long, Long) = {
    var mixDesired = 0
    val done = mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === groupId, mixStatusCol === MixStatus.Running.value).as(MixRequest(_)).map { req =>
      val mixState = mixStateTable.selectStar.where(mixIdCol === req.id).as(MixState(_)).head
      mixDesired += req.numRounds
      Math.min(mixState.round, req.numRounds)
    }.sum
    (mixDesired, done)
  }

  /**
   * @return all group mixes
   */
  def getMixRequestGroupsComplete = {
    mixRequestsGroupTable.select(mixGroupReqCols :+ masterSecretGroupCol: _*)
      .where(mixStatusCol === GroupMixStatus.Complete.value).as(MixGroupRequest(_))
  }

  /**
   * information about mix boxes of a specific group
   *
   * @param id id of the group or covert request
   * @return mix box info list of a specific group, whether it is half or full box, and whether it is withdrawn or not including tx id
   */
  def getMixes(id: String, status: String) = {
    val boxes = {
      if (status == "all") mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === id).as(MixRequest(_))
      else if (status == "active") mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === id, mixWithdrawStatusCol <> Withdrawn.value).as(MixRequest(_))
      else mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === id, mixWithdrawStatusCol === Withdrawn.value).as(MixRequest(_))
    }
    boxes.map { req =>
      val mixState = mixStateTable.selectStar.where(mixIdCol === req.id).as(MixState(_)).headOption
      val halfMix = mixState.flatMap(state => halfMixTable.selectStar.where(mixIdCol === req.id, roundCol === state.round).as(HalfMix(_)).headOption)
      val fullMix = mixState.flatMap(state => fullMixTable.selectStar.where(mixIdCol === req.id, roundCol === state.round).as(FullMix(_)).headOption)
      val withdraw = withdrawTable.selectStar.where(mixIdCol === req.id).as(Withdraw(_)).headOption
      Mix(req, mixState, halfMix, fullMix, withdraw)
    }
  }
}
