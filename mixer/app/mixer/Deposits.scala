package mixer

import cli.ErgoMixCLIUtil
import db.ScalaDB._
import mixer.Columns._
import mixer.Models.MixRequest
import mixer.Util.now
import play.api.Logger

class Deposits(tables: Tables) {
  private val logger: Logger = Logger(this.getClass)
  import tables._

  def processDeposits():Unit = {
    mixRequestsTable.select(mixReqCols:_*).where(depositCompletedCol === false).as(MixRequest(_)).map{req =>
      // logger.info(s"Trying to read deposits for depositAddress: $depositAddress")
      ErgoMixCLIUtil.usingClient{implicit ctx =>
        val explorer = new BlockExplorer
        val allBoxes = explorer.getUnspentBoxes(req.depositAddress)

        val knownIds = unspentDepositsTable.select(boxIdCol).where(addressCol === req.depositAddress).firstAsT[String] ++
          spentDepositsTable.select(boxIdCol).where(addressCol === req.depositAddress).firstAsT[String]


        allBoxes.filterNot(box => knownIds.contains(box.id)).map{box =>
          insertDeposit(req.depositAddress, box.amount, box.id, req.neededAmount, box.getToken(req.tokenId), req.neededTokenAmount)
          logger.info(s"Successfully processed deposit of ${box.amount} with boxId ${box.id} for depositAddress ${req.depositAddress}")
        }
      }
    }
  }

  private implicit val insertReason = "Deposits.insertDeposit"

  def insertDeposit(address: String, amount:Long, boxId:String, needed: Long, tokenAmount: Long, neededTokenAmount: Long): String = {
    // does not check from block explorer. Should be used by experts only. Otherwise, the job will automatically insert deposits
    if (mixRequestsTable.exists(depositAddressCol === address)) {
      if (unspentDepositsTable.exists(boxIdCol === boxId)) throw new Exception(s"Deposit already exists")
      if (spentDepositsTable.exists(boxIdCol === boxId)) throw new Exception(s"Deposit already spent")
      tables.insertUnspentDeposit(address, boxId, amount, tokenAmount, now)
      val currentSum = unspentDepositsTable.select(amountCol).where(addressCol === address).firstAsT[Long].sum
      val tokenSum = unspentDepositsTable.select(mixingTokenAmount).where(addressCol === address).firstAsT[Long].sum
      if (currentSum >= needed && tokenSum >= neededTokenAmount) {
        mixRequestsTable.update(depositCompletedCol <-- true).where(depositAddressCol === address)
        "deposit completed"
      } else s"${needed - currentSum} nanoErgs pending"
    } else throw new Exception(s"Address $address does not belong to this wallet")
  }

}