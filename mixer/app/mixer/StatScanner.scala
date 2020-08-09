package mixer

import app.{Configs, TokenErgoMix}
import cli.ErgoMixCLIUtil
import special.collection.Coll

import scala.compat.Platform

class StatScanner(tables: Tables) {
  def ringStats(): collection.mutable.Map[Long, collection.mutable.Map[String, Long]] = {
    val result = collection.mutable.Map.empty[Long, collection.mutable.Map[String, Long]]
    // Limit for get transaction from explorer
    val limitGetTransaction = Configs.limitGetTransaction
    val periodTime: Long = Platform.currentTime - Configs.periodTimeRings
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      // Get full Box address
      val ergoMix = new TokenErgoMix(ctx)
      val fullBoxAddress = ergoMix.fullMixAddress.toString
      val halfBoxAddress = ergoMix.halfMixAddress.toString
      // Get unspent halfMixBox boxes
      val halfMixBoxes = ErgoMixCLIUtil.getHalfMixBoxes()
      // Add value of halfMixBoxes to result
      for (halfMixBox <- halfMixBoxes) {
        val valueOfBox = halfMixBox.getValue
        if (!result.contains(valueOfBox)) result(valueOfBox) = collection.mutable.Map("unspentHalf" -> 1, "spentHalf" -> 0)
        else result.update(valueOfBox, collection.mutable.Map(
          "unspentHalf" -> (result(valueOfBox)("unspentHalf") + 1),
          "spentHalf" -> result(valueOfBox)("spentHalf")))
      }
      // Create connection to explorer
      val explorer: BlockExplorer = new BlockExplorer()
      var i = 0
      var timeFlag: Boolean = true
      while (timeFlag) {
        // get transactions for calculate number of spent halfBox in `periodTime`
        val transactions = explorer.getTransactionsByAddress(fullBoxAddress, limitGetTransaction, i * limitGetTransaction)
        if (transactions.isEmpty) timeFlag = false
        // Check time stamp of transactions that there is in periodTime also check address of boxes that equals to halfBoxAddress so add value of boxes to result
        for (transaction <- transactions.reverse) {
          if (periodTime < transaction.timestamp) {
            for(input <- transaction.inboxes)
            {
              if(input.address == halfBoxAddress){
                val inputValue = input.value
                if (!result.contains(inputValue)) result(inputValue) = collection.mutable.Map("unspentHalf" -> 0, "spentHalf" -> 1)
                else result.update(inputValue, collection.mutable.Map(
                  "spentHalf" -> (result(inputValue)("spentHalf") + 1),
                  "unspentHalf" -> result(inputValue)("unspentHalf")))
              }
            }
          }
          else timeFlag = false
        }
        i = i + 1
      }
    }
    result
  }

  def scanTokens: (Map[Int, Long], Int) = {
    val tokenBoxes = ErgoMixCLIUtil.getTokenEmissionBoxes(0)
    if (tokenBoxes.nonEmpty) {
      val tokenBox = tokenBoxes.head
      var rate = 1000000
      if (tokenBox.getRegisters.size() == 6) {
        rate = tokenBox.getRegisters.get(5).getValue.asInstanceOf[Int]
      }
      return (tokenBox.getRegisters.get(4).getValue.asInstanceOf[Coll[(Int, Long)]].toMap, rate)
    }
    (Map.empty, 1000000)
  }

}
