package mixer

import java.nio.charset.StandardCharsets

import app.{Configs, TokenErgoMix}
import cli.ErgoMixCLIUtil
import mixer.Models.EntityInfo
import special.collection.Coll

import scala.collection.mutable
import scala.compat.Platform
import scala.collection.JavaConverters._

class ChainScanner(tables: Tables) {
  def ringStats(): mutable.Map[String, mutable.Map[Long, mutable.Map[String, Long]]] = {
    val result = mutable.Map.empty[String, mutable.Map[Long, mutable.Map[String, Long]]]
    // Limit for get transaction from explorer
    val limitGetTransaction = Configs.limitGetTransaction
    val periodTime: Long = Platform.currentTime - Configs.periodTimeRings
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      // Get full Box address
      val ergoMix = ErgoMixCLIUtil.tokenErgoMix.get
      val fullBoxAddress = ergoMix.fullMixAddress.toString
      val halfBoxAddress = ergoMix.halfMixAddress.toString
      // Get unspent halfMixBox boxes
      val halfMixBoxes = ErgoMixCLIUtil.getAllHalfBoxes
      // Add value of halfMixBoxes to result
      for (halfMixBox <- halfMixBoxes) {
        var coin = "erg"
        var ring = halfMixBox.amount
        if (halfMixBox.tokens.length > 1) {
          coin = halfMixBox.tokens.last.getId.toString
          ring = halfMixBox.getToken(coin)
        }
        if (!result.contains(coin)) result(coin) = mutable.Map(ring -> mutable.Map("unspentHalf" -> 1, "spentHalf" -> 0))
        else {
          if (!result(coin).contains(ring)) result(coin)(ring) = mutable.Map("unspentHalf" -> 0, "spentHalf" -> 0)
          val cur = result(coin)(ring)
          result(coin).update(ring, mutable.Map(
            "unspentHalf" -> (cur("unspentHalf") + 1),
            "spentHalf" -> cur("spentHalf")
          ))
        }
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
          val inputs = transaction.inboxes
          val outs = transaction.outboxes
          if (periodTime < transaction.timestamp) {
            val output = outs.find(_.address == fullBoxAddress)
            if (output.nonEmpty && inputs.exists(_.address == halfBoxAddress)) {
              var coin = "erg"
              var ring = output.get.amount
              if (output.get.tokens.length > 1) {
                coin = output.get.tokens.last.getId.toString
                ring = output.get.getToken(coin)
              }
              if (!result.contains(coin)) result(coin) = mutable.Map(ring -> mutable.Map("unspentHalf" -> 0, "spentHalf" -> 1))
              else {
                if (!result(coin).contains(ring)) result(coin)(ring) = mutable.Map("unspentHalf" -> 0, "spentHalf" -> 0)
                val cur = result(coin)(ring)
                result(coin).update(ring, mutable.Map(
                  "unspentHalf" -> cur("unspentHalf"),
                  "spentHalf" -> (cur("spentHalf") + 1)
                ))
              }
            }

          } else timeFlag = false
        }
        i = i + 1
      }
    }
    result
  }

  def scanTokens: (Map[Int, Long], Int) = {
    val tokenBoxes = ErgoMixCLIUtil.getTokenEmissionBoxes(0)
    if (tokenBoxes.nonEmpty) {
      val token = tokenBoxes.head
      val tokenBox = ErgoMixCLIUtil.getUnspentBoxById(token.id)
      var rate = 1000000
      if (tokenBox.getRegisters.size == 2) {
        rate = tokenBox.getRegisters.get(1).getValue.asInstanceOf[Int]
      }
      return (tokenBox.getRegisters.get(0).getValue.asInstanceOf[Coll[(Int, Long)]].toMap, rate)
    }
    (Map.empty, 1000000)
  }

  def scanParams: Seq[EntityInfo] = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      return ctx.getUnspentBoxesFor(TokenErgoMix.paramAddress).asScala
        .filter(box => box.getTokens.size() > 0 && box.getTokens.get(0).getId.toString.equals(TokenErgoMix.tokenId))
        .map(box => EntityInfo(box))
    }
  }
}
