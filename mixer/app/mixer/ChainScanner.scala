package mixer

import app.Configs
import javax.inject.Inject
import mixinterface.TokenErgoMix
import models.Models.EntityInfo
import network.{BlockExplorer, NetworkUtils}
import special.collection.Coll

import scala.collection.mutable
import scala.compat.Platform
import scala.collection.JavaConverters._

class ChainScanner @Inject()(networkUtils: NetworkUtils, explorer: BlockExplorer) {
  /**
   * scans blockchain to extract ring statistics, # of half-boxes, # of mixes in the last 24 h
   *
   * @return a map which maps asset id to its spent half-boxes (# of mixes) and current available half-boxes
   */
  def ringStats(): mutable.Map[String, mutable.Map[Long, mutable.Map[String, Long]]] = {
    val result = mutable.Map.empty[String, mutable.Map[Long, mutable.Map[String, Long]]]
    // Limit for get transaction from explorer
    val limitGetTransaction = Configs.limitGetTransaction
    val periodTime: Long = Platform.currentTime - Configs.periodTimeRings
    networkUtils.usingClient { implicit ctx =>
      // Get full Box address
      val ergoMix = networkUtils.tokenErgoMix.get
      val fullBoxAddress = ergoMix.fullMixAddress.toString
      val halfBoxAddress = ergoMix.halfMixAddress.toString
      // Get unspent halfMixBox boxes
      val halfMixBoxes = networkUtils.getAllHalfBoxes
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
      var i = 0
      var timeFlag: Boolean = true
      while (timeFlag) {
        // get transactions for calculate number of spent halfBox in `periodTime`
        val transactions = explorer.getTransactionsByAddress(fullBoxAddress, limitGetTransaction, i * limitGetTransaction)
        if (transactions.isEmpty || transactions.length < limitGetTransaction) timeFlag = false
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

  /**
   * scans blockchain to extract token info, i.e. token price, fee
   *
   * @return tuple containing a map (level -> price) and entering fee
   */
  def scanTokens: (Map[Int, Long], Int) = {
    val tokenBoxes = networkUtils.getTokenEmissionBoxes(0)
    if (tokenBoxes.nonEmpty) {
      val token = tokenBoxes.head
      val tokenBox = networkUtils.getUnspentBoxById(token.id)
      var rate = 1000000
      if (tokenBox.getRegisters.size == 2) {
        rate = tokenBox.getRegisters.get(1).getValue.asInstanceOf[Int]
      }
      return (tokenBox.getRegisters.get(0).getValue.asInstanceOf[Coll[(Int, Long)]].toMap, rate)
    }
    (Map.empty, 1000000)
  }

  /**
   * scans blockchain to extract parameters like supported assets (erg, usdt, ...)
   *
   * @return list of supported entities
   */
  def scanParams: Seq[EntityInfo] = {
    networkUtils.usingClient { implicit ctx =>
      val maxErg = (1e9*1e8).toLong
      return ctx.getCoveringBoxesFor(TokenErgoMix.paramAddress, maxErg, Seq.empty.asJava).getBoxes.asScala
        .filter(box => box.getTokens.size() > 0 && box.getTokens.get(0).getId.toString.equals(TokenErgoMix.tokenId))
        .map(EntityInfo(_))
    }
  }
}
