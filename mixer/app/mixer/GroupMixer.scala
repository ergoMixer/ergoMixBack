package mixer

import app.Configs
import app.ergomix.EndBox
import cli.{AliceOrBob, MixUtils}
import db.Columns._
import db.ScalaDB._
import db.Tables
import db.core.DataStructures.anyToAny
import helpers.ErgoMixerUtils._
import helpers.Util
import mixer.Models.GroupMixStatus._
import mixer.Models.{DistributeTx, MixGroupRequest}
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.appkit.{Address, ErgoToken}
import play.api.Logger

class GroupMixer(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)

  import tables._

  /**
   * processes group mixes one by one
   */
  def processGroupMixes(): Unit = {
    MixUtils.usingClient { implicit ctx =>
      val explorer = new BlockExplorer
      mixRequestsGroupTable.selectStar.where(
        mixStatusCol === Queued.value
      ).as(arr =>
        MixGroupRequest(arr)
      ).foreach(req => {
        logger.info(s"[MixGroup: ${req.id}] processing deposits...")
        val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
        var confirmedErgDeposits = 0L
        var confirmedTokenDeposits = 0L
        allBoxes.foreach(box => {
          val conf = MixUtils.getConfirmationsForBoxId(box.id)
          if (conf >= minConfirmations) {
            confirmedErgDeposits += box.amount
            confirmedTokenDeposits += box.getToken(req.tokenId)
          }
          else 0
        })
        if (confirmedErgDeposits > 0 || confirmedTokenDeposits > 0) {
          mixRequestsGroupTable.update(depositCol <-- confirmedErgDeposits, tokenDepositCol <-- confirmedTokenDeposits).where(mixGroupIdCol === req.id)
          if (req.tokenId.isEmpty) logger.info(s"  processed confirmed deposits $confirmedErgDeposits")
          else logger.info(s"  processed confirmed deposits, erg: $confirmedErgDeposits, ${req.tokenId}: $confirmedTokenDeposits")
        }

        if (confirmedErgDeposits >= req.neededAmount && confirmedTokenDeposits >= req.neededTokenAmount) {
          logger.info(s"  sufficient deposit, starting...")
          mixRequestsGroupTable.update(mixStatusCol <-- Starting.value).where(mixGroupIdCol === req.id)
        }
      })

      mixRequestsGroupTable.selectStar.where(
        mixStatusCol === Starting.value
      ).as(arr =>
        MixGroupRequest(arr)
      ).foreach(req => {
        try {
          processStartingGroup(req)
        } catch {
          case a: Throwable =>
            logger.error(s" [MixGroup: ${req.id}] An error occurred. Stacktrace below")
            logger.error(getStackTraceStr(a))
        }
      })
    }
  }

  /**
   * processes a specific group mix (enters mixing if all ok)
   * @param req group request
   */
  def processStartingGroup(req: MixGroupRequest) = MixUtils.usingClient { implicit ctx =>
    val explorer = new BlockExplorer()
    logger.info(s"[MixGroup: ${req.id}] processing...")
    val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
    if (distributeTxsTable.exists(mixGroupIdCol === req.id)) { // txs already created, just w8 for enough confirmation
      var allTxsConfirmed = true
      distributeTxsTable.selectStar.where(mixGroupIdCol === req.id).as(DistributeTx(_))
        .sortBy(_.order)
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
        mixRequestsGroupTable.update(mixStatusCol <-- Running.value).where(mixGroupIdCol === req.id)
      }

    } else { // create and send chain of transactions
      logger.info("  distributing deposits to start mixing...")
      val wallet = new Wallet(req.masterKey)
      val secret = wallet.getSecret(-1).bigInteger
      val requests = mixRequestsTable.select(depositAddressCol, neededCol, mixingTokenNeeded)
        .where(mixGroupIdCol === req.id)
        .as { arr =>
          val i = arr.toIterator
          (
            i.next.as[String],
            i.next.as[Long], // erg
            i.next.as[Long] // token
          )
        }.toArray

      val excessErg = req.doneDeposit - req.neededAmount
      val excessToken = req.tokenDoneDeposit - req.neededTokenAmount
      requests(0) = (requests(0)._1, requests(0)._2 + excessErg, requests(0)._3 + excessToken)
      val reqEndBoxes = requests.map(cur => {
        var token: Seq[ErgoToken] = Seq()
        if (!req.tokenId.isEmpty) token = Seq(new ErgoToken(req.tokenId, cur._3))
        EndBox(Address.create(cur._1).getErgoAddress.script, Seq(), cur._2, token)
      })
      if (excessErg > 0) logger.info(s"  excess deposit: $excessErg...")
      if (excessToken > 0) logger.info(s"  excess token deposit: $excessToken...")

      val transactions = AliceOrBob.distribute(allBoxes.map(_.id).toArray, reqEndBoxes, Array(secret), Configs.distributeFee, req.depositAddress, Configs.maxOuts)
      for (i <- transactions.indices) {
        val tx = transactions(i)
        val inputs = tx.getInputBoxes.map(ScalaBridge.isoErgoTransactionInput.from(_).getBoxId).mkString(",")
        distributeTxsTable.insert(req.id, tx.getId, i + 1, Util.now, tx.toJson(false).getBytes("utf-16"), inputs)
        val sendRes = ctx.sendTransaction(tx)
        if (sendRes == null) logger.error(s"  transaction got refused by the node! maybe it doesn't support chained transactions, waiting... consider updating your node for a faster mixing experience.")
      }
    }

  }
}
