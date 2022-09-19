package mixer

import app.Configs
import mixinterface.AliceOrBob
import helpers.ErgoMixerUtils

import javax.inject.Inject
import scala.collection.JavaConverters._
import models.Status.GroupMixStatus._
import network.{BlockExplorer, NetworkUtils}
import org.ergoplatform.appkit.{Address, ErgoToken}
import play.api.Logger
import wallet.{Wallet, WalletHelper}
import dao.{DAOUtils, DistributeTransactionsDAO, MixingGroupRequestDAO, MixingRequestsDAO}
import models.Box.EndBox
import models.Request.MixGroupRequest
import models.Transaction.DistributeTx

class GroupMixer @Inject()(aliceOrBob: AliceOrBob, ergoMixerUtils: ErgoMixerUtils,
                           networkUtils: NetworkUtils, explorer: BlockExplorer,
                           daoUtils: DAOUtils,
                           mixingGroupRequestDAO: MixingGroupRequestDAO,
                           distributeTransactionsDAO: DistributeTransactionsDAO, mixingRequestsDAO: MixingRequestsDAO) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * processes group mixes one by one
   */
  def processGroupMixes(): Unit = {
    networkUtils.usingClient { implicit ctx =>
      daoUtils.awaitResult(mixingGroupRequestDAO.queued).foreach(req => {
        logger.info(s"[MixGroup: ${req.id}] processing deposits...")
        val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
        var confirmedErgDeposits = 0L
        var confirmedTokenDeposits = 0L
        allBoxes.foreach(box => {
          val conf = networkUtils.getConfirmationsForBoxId(box.id)
          if (conf >= Configs.numConfirmation) {
            confirmedErgDeposits += box.amount
            confirmedTokenDeposits += box.getToken(req.tokenId)
          }
          else 0
        })
        if (confirmedErgDeposits > 0 || confirmedTokenDeposits > 0) {
          mixingGroupRequestDAO.updateDepositById(req.id, confirmedErgDeposits, confirmedTokenDeposits)
          if (req.tokenId.isEmpty) logger.info(s"  processed confirmed deposits $confirmedErgDeposits")
          else logger.info(s"  processed confirmed deposits, erg: $confirmedErgDeposits, ${req.tokenId}: $confirmedTokenDeposits")
        }

        if (confirmedErgDeposits >= req.neededAmount && confirmedTokenDeposits >= req.neededTokenAmount) {
          logger.info(s"  sufficient deposit, starting...")
          mixingGroupRequestDAO.updateStatusById(req.id, Starting.value)
        }
      })

      daoUtils.awaitResult(mixingGroupRequestDAO.starting).foreach(req => {
        try {
          processStartingGroup(req)
        } catch {
          case a: Throwable =>
            logger.error(s" [MixGroup: ${req.id}] An error occurred. Stacktrace below")
            logger.error(ergoMixerUtils.getStackTraceStr(a))
        }
      })
    }
  }

  /**
   * processes a specific group mix (enters mixing if all ok)
   *
   * @param req group request
   */
  def processStartingGroup(req: MixGroupRequest): Unit = networkUtils.usingClient { implicit ctx =>
    logger.info(s"[MixGroup: ${req.id}] processing...")
    val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
    if (daoUtils.awaitResult(distributeTransactionsDAO.existsByMixGroupId(req.id))) {
      var allTxsConfirmed = true
      daoUtils.awaitResult(distributeTransactionsDAO.selectByMixGroupId(req.id))
        .foreach(tx => {
          val confNum = explorer.getTxNumConfirmations(tx.txId)
          allTxsConfirmed &= confNum >= Configs.numConfirmation
          if (confNum == -1) { // not mined yet, broadcast tx again!
            logger.info(s"  broadcasting tx ${tx.txId}...")
            ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
          }
        })

      if (allTxsConfirmed) {
        logger.info("  all distribute transactions are confirmed...")
        mixingGroupRequestDAO.updateStatusById(req.id, Running.value)
      }

    } else { // create and send chain of transactions
      logger.info("  distributing deposits to start mixing...")
      val wallet = new Wallet(req.masterKey)
      val secret = wallet.getSecret(-1).bigInteger
      /*val requests = mixRequestsTable.select(depositAddressCol, neededCol, mixingTokenNeeded)
        .where(mixGroupIdCol === req.id)
        .as { arr =>
          val i = arr.toIterator
          (
            i.next.as[String],
            i.next.as[Long], // erg
            i.next.as[Long] // token
          )
        }.toArray*/
      val requests = daoUtils.awaitResult(mixingRequestsDAO.selectPartsByMixGroupId(req.id)).toArray

      val excessErg = req.doneDeposit - req.neededAmount
      val excessToken = req.tokenDoneDeposit - req.neededTokenAmount
      requests(0) = (requests(0)._1, requests(0)._2 + excessErg, requests(0)._3 + excessToken)
      val reqEndBoxes = requests.map(cur => {
        var token: Seq[ErgoToken] = Seq()
        if (req.tokenId.nonEmpty) token = Seq(new ErgoToken(req.tokenId, cur._3))
        EndBox(Address.create(cur._1).getErgoAddress.script, Seq(), cur._2, token)
      })
      if (excessErg > 0) logger.info(s"  excess deposit: $excessErg...")
      if (excessToken > 0) logger.info(s"  excess token deposit: $excessToken...")

      val transactions = aliceOrBob.distribute(allBoxes.map(_.id).toArray, reqEndBoxes, Array(secret), Configs.distributeFee, req.depositAddress, Configs.maxOuts)
      for (i <- transactions.indices) {
        val tx = transactions(i)
        val inputs = tx.getSignedInputs.asScala.map(_.getId).mkString(",")
        val new_tx = DistributeTx(req.id, tx.getId, i + 1, WalletHelper.now, tx.toJson(false).getBytes("utf-16"), inputs)
        distributeTransactionsDAO.insert(new_tx)
        try {
          ctx.sendTransaction(tx)
        }
        catch {
          case e: Throwable =>
            logger.error(s"  transaction got refused by the node! maybe it doesn't support chained transactions, waiting... consider updating your node for a faster mixing experience.")
            logger.debug(s"  Exception: ${e.getMessage}")
        }
      }
    }

  }
}
