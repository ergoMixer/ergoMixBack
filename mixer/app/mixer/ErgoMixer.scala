package mixer

import java.util.UUID

import mixer.Columns._
import mixer.Models.MixStatus.{Complete, Queued, Running}
import mixer.Models.MixWithdrawStatus.NoWithdrawYet
import mixer.Models.{Deposit, FullMix, GroupMixStatus, HalfMix, Mix, MixCovertRequest, MixGroupRequest, MixRequest, MixState, MixStatus, MixWithdrawStatus, Withdraw}
import cli._
import db.ScalaDB._
import mixer.Util._
import app.{Configs, TokenErgoMix, Util => EUtil}
import play.api.Logger

/* Utility methods used in other jobs or for debugging */

class ErgoMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)

  import tables._

  def getAllUnspentDeposits: List[Deposit] = unspentDepositsTable.selectStar.as(Deposit(_))

  def getMasterSecret(mixId: String) = mixRequestsTable.select(masterSecretCol).where(mixIdCol === mixId).firstAsT[BigDecimal].map(_.toBigInt)

  def getSpentDeposits(address: String) = spentDepositsTable.selectStar.where(addressCol === address).as(Deposit(_))

  def markAsQueued(mixId: String) = {
    if (mixStateTable.exists(mixIdCol === mixId)) throw new Exception("Mix already started")
    mixRequestsTable.update(mixStatusCol <-- Queued.value).where(mixIdCol === mixId)
  }

  def newCovertRequest(ergRing: Long, tokenRing: Long, tokenId: String, numRounds: Int): String = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      val masterSecret = randBigInt
      val wallet = new Wallet(masterSecret)
      val depositSecret = wallet.getSecret(-1)
      val depositAddress = TokenErgoMix.getProveDlogAddress(depositSecret)
      val mixId = UUID.randomUUID().toString
      mixCovertTable.insert(mixId, now, depositAddress, numRounds, 0L, 0L, ergRing, tokenRing, tokenId, masterSecret)

      val need = mixCovertTable.selectStar.where(mixGroupIdCol === mixId).as(MixCovertRequest(_)).head.getMinNeeded
      if (tokenId.isEmpty) {
        logger.info(s"Needs at least ${need._1} nanoErgs, address: $depositAddress")
      } else {
        logger.info(s"Needs at least ${need._1} nanoErgs and ${need._2} of $tokenId, address: $depositAddress")
      }
      depositAddress
    }
  }

  def newMixRequest(numRounds: Int, withdrawAddress: String, numToken: Int, poolAmount: Long, needed: Long, mixingTokenAmount: Long, mixingTokenNeeded: Long, mixingTokenId: String, mixGroupId: String): String = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      val masterSecret = randBigInt
      val wallet = new Wallet(masterSecret)
      val depositSecret = wallet.getSecret(-1)
      val depositAddress = TokenErgoMix.getProveDlogAddress(depositSecret)
      val mixId = UUID.randomUUID().toString
      mixRequestsTable.insert(mixId, mixGroupId, poolAmount, numRounds, Queued.value, now, withdrawAddress, depositAddress, false, needed, numToken, NoWithdrawYet.value, mixingTokenAmount, mixingTokenNeeded, mixingTokenId, masterSecret)
      depositAddress
    }
  }

  def newMixGroupRequest(mixRequests: Iterable[MixBox]): String = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
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
        this.newMixRequest(mixBox.token, mixBox.withdraw, mixBox.token, mixBox.amount, price._1, mixBox.mixingTokenAmount, price._2, mixBox.mixingTokenId, mixId)
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

  def getMixes = {
    mixRequestsTable.select(mixReqCols: _*).as(MixRequest(_)).map { req =>
      val mixState = mixStateTable.selectStar.where(mixIdCol === req.id).as(MixState(_)).headOption
      val halfMix = mixState.flatMap(state => halfMixTable.selectStar.where(mixIdCol === req.id, roundCol === state.round).as(HalfMix(_)).headOption)
      val fullMix = mixState.flatMap(state => fullMixTable.selectStar.where(mixIdCol === req.id, roundCol === state.round).as(FullMix(_)).headOption)
      val withdraw = withdrawTable.selectStar.where(mixIdCol === req.id).as(Withdraw(_)).headOption
      Mix(req, mixState, halfMix, fullMix, withdraw)
    }
  }

  def getMixRequestGroups = {
    mixRequestsGroupTable.select(mixGroupReqCols :+ masterSecretGroupCol: _*).as(MixGroupRequest(_))
  }

  def getMixRequestGroupsActive = {
    mixRequestsGroupTable.select(mixGroupReqCols :+ masterSecretGroupCol: _*)
      .where(mixStatusCol <> GroupMixStatus.Complete.value).as(MixGroupRequest(_))
  }

  def getFinishedForGroup(mix: MixGroupRequest): (Long, Long, Long) = {
    val withdrawn = mixRequestsTable.countWhere(mixWithdrawStatusCol === MixWithdrawStatus.Withdrawn.value, mixGroupIdCol === mix.id)
    val finished = mixRequestsTable.countWhere(mixStatusCol === Complete.value, mixGroupIdCol === mix.id)
    val all = mixRequestsTable.countWhere(mixGroupIdCol === mix.id)
    (all, finished, withdrawn)
  }

  def getProgressForGroup(mix: MixGroupRequest): (Long, Long) = {
    var mixDesired = 0
    val done = mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === mix.id, mixStatusCol === MixStatus.Running.value).as(MixRequest(_)).map { req =>
      val mixState = mixStateTable.selectStar.where(mixIdCol === req.id).as(MixState(_)).head
      mixDesired += req.numRounds
      Math.min(mixState.round, req.numRounds)
    }.sum
    (mixDesired, done)
  }

  def getMixRequestGroupsComplete = {
    mixRequestsGroupTable.select(mixGroupReqCols :+ masterSecretGroupCol: _*)
      .where(mixStatusCol === GroupMixStatus.Complete.value).as(MixGroupRequest(_))
  }

  def getMixes(groupId: String) = {
    mixRequestsTable.select(mixReqCols: _*).where(mixGroupIdCol === groupId).as(MixRequest(_)).map { req =>
      val mixState = mixStateTable.selectStar.where(mixIdCol === req.id).as(MixState(_)).headOption
      val halfMix = mixState.flatMap(state => halfMixTable.selectStar.where(mixIdCol === req.id, roundCol === state.round).as(HalfMix(_)).headOption)
      val fullMix = mixState.flatMap(state => fullMixTable.selectStar.where(mixIdCol === req.id, roundCol === state.round).as(FullMix(_)).headOption)
      val withdraw = withdrawTable.selectStar.where(mixIdCol === req.id).as(Withdraw(_)).headOption
      Mix(req, mixState, halfMix, fullMix, withdraw)
    }
  }

  def getActiveMixes = {
    getMixes.filter(_.mixRequest.mixStatus == Running)
  }

  def getPotentialBadAlice = {
    halfMixTable.selectStar.where(
      mixIdCol === mixIdCol.of(mixStateTable),
      roundCol === roundCol.of(mixStateTable),
      isSpentCol === false
    ).as(HalfMix(_)).filter(halfMix => ErgoMixCLIUtil.getSpendingTxId(halfMix.halfMixBoxId).isEmpty)
  }

  def getPotentialBadBob = {
    fullMixTable.selectStar.where(
      mixIdCol === mixIdCol.of(mixStateTable),
      roundCol === roundCol.of(mixStateTable),
      isAliceCol.of(mixStateTable) === false,
      mixIdCol.of(mixRequestsTable) === mixIdCol,
      mixStatusCol.of(mixRequestsTable) === Running.value
    ).as(FullMix(_)).filter { fullMix =>
      ErgoMixCLIUtil.getSpendingTxId(fullMix.fullMixBoxId).isEmpty
    }
  }

  def getFullMixes(mixId: String) = {
    fullMixTable.selectStar.where(mixIdCol === mixId).as(FullMix(_))
  }

  def getHalfMixes(mixId: String) = {
    halfMixTable.selectStar.where(mixIdCol === mixId).as(HalfMix(_))
  }

  // def getMixHistory(mixId:String) = {mixStateHistoryArchiveTable.selectStar.where(mixIdCol === mixId).as(MixHistory(_))}

  def insertMixHistory(mixId: String, round: Int, isAlice: Boolean) = {
    mixStateHistoryTable.insert(mixId, round, isAlice, now)
  }

  def decrementMixId(mixId: String, prevIsAlice: Boolean) = {
    val round = mixStateHistoryTable.select(roundCol).where(mixIdCol === mixId).firstAsT[Int].max
    val roundInMixState = mixStateTable.select(roundCol).where(mixIdCol === mixId).firstAsT[Int].headOption.getOrElse(throw new Exception(s"No entry found for mixId $mixId"))
    if (round != roundInMixState) throw new Exception(s"History mismatch. max round in history = $round. Current round $roundInMixState")
    if (round == 0) throw new Exception("Cannot decrement round 0")
    if (fullMixTable.exists(roundCol === round, mixIdCol === mixId)) throw new Exception(s"Round exists in full mix table")
    if (halfMixTable.exists(roundCol === round, mixIdCol === mixId)) throw new Exception(s"Round exists in half mix table")
    mixStateTable.update(roundCol <-- (round - 1), isAliceCol <-- prevIsAlice).where(mixIdCol === mixId)
    mixStateHistoryTable.deleteWhere(mixIdCol === mixId, roundCol === round)
  }

  def undoWithdraw(txId: String) = ErgoMixCLIUtil.usingClient { implicit ctx =>
    // should be used only in case of fork. Leaving it to be called manually for now.
    val explorer = new BlockExplorer()
    if (explorer.getTransaction(txId).isDefined) throw new Exception("Transaction already confirmed")

    mixRequestsTable.select(mixIdCol of withdrawTable).where(
      (txIdCol of withdrawTable) === txId,
      (mixIdCol of mixRequestsTable) === (mixIdCol of withdrawTable),
      (mixStatusCol of mixRequestsTable) === Complete.value
    ).firstAsT[String].headOption.fold(
      throw new Exception("No withdraw found")
    ) { mixId =>
      withdrawTable.deleteWhere(txIdCol === txId)
      mixRequestsTable.update(mixStatusCol <-- Running.value).where(mixIdCol === mixId)
    }
  }

  /* More utility methods (for debugging)

  def getUnspentDeposits(address:String): List[Deposit] = unspentDepositsTable.selectStar.where(addressCol === address).as(Deposit(_))

  def getAllMixHistory = mixStateHistoryTable.selectStar.as(MixHistory(_))

  def getBalance = unspentDepositsTable.select(amountCol).firstAsT[Long].sum

  def getEmissionBoxLog = spentFeeEmissionBoxTable.selectStar.as { a =>
    val mixId = a(0).asInstanceOf[String]
    val round = a(1).asInstanceOf[Int]
    val boxId = a(2).asInstanceOf[String]
    val txId = a(3).asInstanceOf[String]
    s"""{"mixId":"$mixId","round":$round,"boxId","$boxId","txId":"$txId"}"""
  }

  def clearEmissionBoxLog = spentFeeEmissionBoxTable.deleteAll

  def markAsIncomplete(mixId:String) = mixRequestsTable.update(mixStatusCol <-- Running.value).where(mixIdCol === mixId)

  def getAllSpentDeposits = spentDepositsTable.selectStar.as(Deposit(_))

  def getAllWithdraws = withdrawTable.selectStar.as(Withdraw(_))

  def getAllMixRequests = mixRequestsTable.select(mixReqCols:_*).as(MixRequest(_))

  def getAllMixHistory = mixStateHistoryTable.selectStar.as(MixHistory(_))

  def getMixStates = mixStateTable.selectStar.as(MixState(_))

  def getAllHalfMixes = halfMixTable.selectStar.as(HalfMix(_))

  def getAllFullMixes = fullMixTable.selectStar.as(FullMix(_))

  */
}
