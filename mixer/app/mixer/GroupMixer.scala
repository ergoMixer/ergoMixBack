package mixer

import org.ergoplatform.appkit.BlockchainContext
import app.Configs
import cli.{AliceOrBob, ErgoMixCLIUtil}
import db.ScalaDB._
import db.core.DataStructures.anyToAny
import mixer.Columns._
import mixer.ErgoMixerUtil._
import mixer.Models.GroupMixStatus._
import mixer.Models.{DistributeTx, MixGroupRequest}

class GroupMixer(tables: Tables) {

  import tables._

  def processGroupMixes(): Unit = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      val explorer = new BlockExplorer
      mixRequestsGroupTable.selectStar.where(
        mixStatusCol === Queued.value
      ).as(arr =>
        MixGroupRequest(arr)
      ).foreach(req => {
        println(s"[MixGroup: ${req.id}] processing deposits...")
        val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
        val confirmedDepositSum = allBoxes.map(box => {
          val conf = ErgoMixCLIUtil.getConfirmationsForBoxId(box.id)
          if (conf >= minConfirmations) box.amount
          else 0
        }).sum
        if (confirmedDepositSum > 0) {
          mixRequestsGroupTable.update(depositCol <-- confirmedDepositSum).where(mixGroupIdCol === req.id)
          println(s"  processed confirmed deposits $confirmedDepositSum")
        }

        if (confirmedDepositSum >= req.neededAmount) {
          println(s"  sufficient deposit, starting...")
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
            println(s" [MixGroup: ${req.id}] An error occurred. Stacktrace below")
            a.printStackTrace()
        }
      })
    }
  }

  def processStartingGroup(req: MixGroupRequest) = ErgoMixCLIUtil.usingClient { implicit ctx =>
    val explorer = new BlockExplorer()
    println(s"[MixGroup: ${req.id}] processing...")
    val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
    if (distributeTxsTable.exists(mixGroupIdCol === req.id)) { // txs already created, just w8 for enough confirmation
      var allTxsConfirmed = true
      distributeTxsTable.selectStar.where(mixGroupIdCol === req.id).as(DistributeTx(_))
        .sortBy(_.order)
        .foreach(tx => {
          val confNum = explorer.getTxNumConfirmations(tx.txId)
          allTxsConfirmed &= confNum >= Configs.numConfirmation
          if (confNum == -1) { // not mined yet, broadcast tx again!
            println(s"  broadcasting tx ${tx.txId}...")
            ctx.sendTransaction(ctx.signedTxFromJson(tx.toString))
          }
        })

      if (allTxsConfirmed) {
        println("  all distribute transactions are confirmed...")
        mixRequestsGroupTable.update(mixStatusCol <-- Running.value).where(mixGroupIdCol === req.id)
      }

    } else { // create and send chain of transactions
      println("  distributing deposits to start mixing...")
      val wallet = new Wallet(req.masterKey)
      val secret = wallet.getSecret(-1).bigInteger
      val requests = mixRequestsTable.select(depositAddressCol, neededCol)
        .where(mixGroupIdCol === req.id)
        .as { arr =>
          val i = arr.toIterator
          (
            i.next.as[String],
            i.next.as[Long]
          )
        }.toArray

      val excess = req.doneDeposit - req.neededAmount
      requests(0) = (requests(0)._1, requests(0)._2 + excess)
      if (excess > 0) {
        println(s"  excess deposit: $excess...")
      }

      val transactions = AliceOrBob.distribute(allBoxes.map(_.id).toArray, requests, Array(secret), Configs.feeInStartTransaction, req.depositAddress, Configs.numOutputsInStartTransaction)
      for (i <- transactions.indices) {
        val tx = transactions(i)
        distributeTxsTable.insert(req.id, tx.getId, i, Util.now, tx.toJson(false).getBytes("utf-16"))
      }
    }

  }
}
