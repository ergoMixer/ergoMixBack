package dao

import app.Configs
import helpers.ErgoMixerUtils
import models.Models.MixWithdrawStatus.Withdrawn
import models.Models.MixRequest
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.BlockchainContext
import play.api.Logger
import wallet.WalletHelper

import javax.inject.Inject

class Prune @Inject()(ergoMixerUtils: ErgoMixerUtils, networkUtils: NetworkUtils, explorer: BlockExplorer,
                      daoUtils: DAOUtils,
                      mixingGroupRequestDAO: MixingGroupRequestDAO,
                      mixingCovertRequestDAO: MixingCovertRequestDAO,
                      mixingRequestsDAO: MixingRequestsDAO,
                      withdrawDAO: WithdrawDAO,
                      distributeTransactionsDAO: DistributeTransactionsDAO,
                      unspentDepositsDAO: UnspentDepositsDAO,
                      spentDepositsDAO: SpentDepositsDAO,
                      mixStateDAO: MixStateDAO,
                      mixStateHistoryDAO: MixStateHistoryDAO,
                      halfMixDAO: HalfMixDAO,
                      fullMixDAO: FullMixDAO,
                      emissionDAO: EmissionDAO,
                      tokenEmissionDAO: TokenEmissionDAO,
                      mixTransactionsDAO: MixTransactionsDAO,
                      rescanDAO: RescanDAO) {
  private val logger: Logger = Logger(this.getClass)

  import networkUtils._

  /**
   * prunes group mixes and covert mixes
   */
  def processPrune(): Unit = {
    try {
      if (Configs.dbPrune) {
        usingClient { implicit ctx =>
          pruneGroupMixes()
          pruneCovertMixes()
        }
      }
    } catch {
      case a: Throwable =>
        logger.error(s"  prune DB: An error occurred. Stacktrace below")
        logger.error(ergoMixerUtils.getStackTraceStr(a))
    }
  }

  /**
   * Will prune group mixes if possible one by one
   */
  def pruneGroupMixes()(implicit ctx: BlockchainContext): Unit = {
    val currentTime = WalletHelper.now
    val requests = daoUtils.awaitResult(mixingGroupRequestDAO.completed)
    requests.foreach(req => {
      val mixes = daoUtils.awaitResult(mixingRequestsDAO.selectByMixGroupId(req.id))
      var shouldPrune = mixes.forall(mix => mix.withdrawStatus == Withdrawn.value)
      mixes.foreach(mix => {
        val withdrawTime: Long = daoUtils.awaitResult(withdrawDAO.selectCreatedTimeByMixId(mix.id)).getOrElse({
          logger.warn(s"mixId ${mix.id} not found in Withdraw")
          currentTime
        })
          shouldPrune &= (currentTime - withdrawTime) >= Configs.dbPruneAfter * 120L
      })
      if (shouldPrune) {
        mixes.foreach(mix => {
          val txId: String = daoUtils.awaitResult(withdrawDAO.selectByMixId(mix.id)).getOrElse(throw new Exception(s"mixId ${mix.id} not found in Withdraw")).txId
          val numConf = explorer.getTxNumConfirmations(txId)
          shouldPrune &= numConf >= Configs.dbPruneAfter
        })
      }
      if (shouldPrune) {
        logger.info(s"  will prune group mix ${req.id}")
        deleteGroupMix(req.id)
      }
    })
  }

  /**
   * will prune covert mix boxes one by one if possible
   */
  def pruneCovertMixes()(implicit ctx: BlockchainContext): Unit = {
    val currentTime = WalletHelper.now
    val requests = daoUtils.awaitResult(mixingCovertRequestDAO.all)
    requests.foreach(req => {
      val mixes = daoUtils.awaitResult(mixingRequestsDAO.selectAllByWithdrawStatus(req.id, Withdrawn.value))
      mixes.foreach(mix => {
        val withdrawTime: Long = daoUtils.awaitResult(withdrawDAO.selectCreatedTimeByMixId(mix.id)).getOrElse({
          logger.warn(s"mixId ${mix.id} not found in Withdraw")
          currentTime
        })
        if ((currentTime - withdrawTime) >= Configs.dbPruneAfter * 120L) {
          val txId: String = daoUtils.awaitResult(withdrawDAO.selectByMixId(mix.id)).getOrElse(throw new Exception(s"mixId ${mix.id} not found in Withdraw")).txId
          val numConf = explorer.getTxNumConfirmations(txId)
          if (numConf >= Configs.dbPruneAfter) {
            logger.info(s"  will prune covert mix box ${mix.id}")
            deleteMixBox(mix)
          }
        }
      })
      // pruning distributed transactions that are mined and have been confirmed at least dbPruneAfter
      val distributedTxs = daoUtils.awaitResult(distributeTransactionsDAO.zeroChainByMixGroupIdAndTime(req.id, currentTime - Configs.dbPruneAfter * 120L))
      distributedTxs.foreach(tx => {
        val numConf = explorer.getTxNumConfirmations(tx.txId)
        if (numConf >= Configs.dbPruneAfter) {
          logger.info(s"  will prune distributed tx of covert ${req.id}, txId: ${tx.txId}, confNum: $numConf")
          distributeTransactionsDAO.delete(tx.txId)
        }
      })
    })
  }

  def deleteMixBox(box: MixRequest): Unit = {
    mixingRequestsDAO.delete(box.id)

    unspentDepositsDAO.deleteByAddress(box.depositAddress)
    spentDepositsDAO.deleteByAddress(box.depositAddress)

    mixStateDAO.delete(box.id)
    mixStateHistoryDAO.deleteWithArchive(box.id)

    val halfMixBoxId = halfMixDAO.boxIdByMixId(box.id)
    val fullMixBodId = fullMixDAO.boxIdByMixId(box.id)
    mixTransactionsDAO.delete(daoUtils.awaitResult(halfMixBoxId).getOrElse({
      logger.warn(s"halfMixBoxId $halfMixBoxId not found in mixTransaction")
      ""
    }))
    mixTransactionsDAO.delete(daoUtils.awaitResult(fullMixBodId).getOrElse({
      logger.warn(s"fullMixBoxId $fullMixBodId not found in mixTransaction")
      ""
    }))

    halfMixDAO.deleteWithArchive(box.id)
    fullMixDAO.deleteWithArchive(box.id)

    withdrawDAO.deleteWithArchive(box.id)
    emissionDAO.delete(box.id)
    tokenEmissionDAO.delete(box.id)
    rescanDAO.deleteWithArchive(box.id)
  }

  /**
   * deletes a group mix request and everything associated with that request including mix boxes and secrets
   * only call this if group mix is done and every box is withdrawn
   * @param groupId group mix request
   */
  def deleteGroupMix(groupId: String): Unit = {
    val boxes = daoUtils.awaitResult(mixingRequestsDAO.selectByMixGroupId(groupId))
    mixingGroupRequestDAO.delete(groupId)
    mixingRequestsDAO.deleteByGroupId(groupId)
    distributeTransactionsDAO.deleteByGroupId(groupId)
    boxes.foreach(box => {
      deleteMixBox(box)
    })
  }
}
