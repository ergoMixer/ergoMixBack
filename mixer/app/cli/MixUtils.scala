package cli

import app._
import helpers.Util
import mixer.Models.OutBox
import mixer.{BlockExplorer, GetURL, Models}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, InputBox}
import play.api.Logger

object MixUtils {
  private val logger: Logger = Logger(this.getClass)

  var allClients: Seq[ErgoClient] = Seq()
  var prunedClients: Seq[ErgoClient] = Seq()
  var tokenErgoMix: Option[TokenErgoMix] = None
  val explorer = new BlockExplorer

  def pruneClients(): Unit = {
    val explorerHeight = explorer.getHeight
    prunedClients = MixUtils.allClients.filter(client => {
      try {
        client.execute(ctx => {
          val nodeHeight = ctx.getHeight
          explorerHeight - nodeHeight <= 2
        })
      } catch {
        case e: Throwable =>
          logger.error(s"will ignore this node. ${e.getMessage}")
          false
      }
    })

  }

  def usingClient[T](f: BlockchainContext => T): T = {
    if (prunedClients.isEmpty) throw new Exception("There are no available nodes to connect to")
    val rndClient = prunedClients(Util.randInt(prunedClients.size))
    rndClient.execute { ctx =>
      f(ctx)
    }
  }

  /**
   * @param address address to get unspent boxes of
   * @return unspent boxes of the provided address
   */
  def getUnspentBoxes(address: String): Seq[OutBox] = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      explorer.getUnspentBoxes(address)
    }
  }

  /**
   * @param boxId box id of desired box
   * @return number of confirmations of this box based on explorer
   */
  def getConfirmationsForBoxId(boxId: String): Int = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      explorer.getConfirmationsForBoxId(boxId)
    }
  }

  /**
   * @param considerPool whether to eliminate boxes already in mempool
   * @return list of half-boxes
   */
  def getHalfMixBoxes(considerPool: Boolean = false): List[OutBox] = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      var txPool = ""
      if (considerPool) txPool = explorer.getPoolTransactionsStr
      val unspentBoxes = getUnspentBoxes(tokenErgoMix.get.halfMixAddress.toString)
      unspentBoxes.filter(box => {
        (!considerPool || !txPool.contains(s""""${box.id.toString}","txId"""")) && // not already in mempool
          !TokenErgoMix.poisonousHalfs.contains(box.ge("R4")) // not poisonous
      }).toList
    }
  }

  /**
   * @return all unspent half-boxes
   */
  def getAllHalfBoxes: List[OutBox] = {
    usingClient { implicit ctx =>
      getUnspentBoxes(tokenErgoMix.get.halfMixAddress.toString).toList
    }
  }

  /**
   * @param numToken     minimum number of token you wish the boxes to have
   * @param considerPool whether to eliminate boxes already in mempool
   * @return list of unspent token emission boxes containg at least numToken mixing tokens
   */
  def getTokenEmissionBoxes(numToken: Int, considerPool: Boolean = false): List[OutBox] = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      var txPool = ""
      if (considerPool) txPool = explorer.getPoolTransactionsStr
      getUnspentBoxes(tokenErgoMix.get.tokenEmissionAddress.toString).filter(box => {
        box.getToken(TokenErgoMix.tokenId) >= numToken &&
          (!considerPool || !txPool.contains(s""""${box.id}","txId""""))
      }).toList
    }
  }

  /**
   *
   * @param poolAmount mix ring
   * @return list of unspent full-boxes in a specific ring
   */
  def getFullMixBoxes(poolAmount: Long = -1): Seq[Models.OutBox] = {
    usingClient { implicit ctx =>
      val unspent = getUnspentBoxes(tokenErgoMix.get.fullMixAddress.toString)
      if (poolAmount != -1)
        unspent.filter(_.amount == poolAmount)
      else
        unspent
    }
  }

  /**
   * @param boxId box id of the box we wish to get
   * @return box as InputBox
   */
  def getUnspentBoxById(boxId: String): InputBox = {
    usingClient { implicit ctx =>
      ctx.getBoxesById(boxId).headOption.getOrElse(throw new Exception("No box found"))
    }
  }

  /**
   * @param boxId box id of the box we wish to get
   * @return box as OutBox
   */
  def getOutBoxById(boxId: String): OutBox = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      explorer.getBoxById(boxId)
    }
  }

  /**
   * @param boxId box id
   * @return tx spending the box with id boxId
   */
  def getSpendingTxId(boxId: String) = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      explorer.getSpendingTxId(boxId)
    }
  }

  /**
   * @param txId transaction id
   * @return whether the txId is in mempool waiting to be mined
   */
  def isTxInPool(txId: String): Boolean = {
    usingClient { implicit ctx =>
      try {
        val explorer = new BlockExplorer()
        explorer.doesTxExistInPool(txId)
      } catch {
        case _: Throwable => false
      }
    }
  }

  /**
   * @param considerPool whether to eliminate boxes already in mempool
   * @return list of fee-boxes
   */
  def getFeeEmissionBoxes(considerPool: Boolean = false): Seq[OutBox] = {
    usingClient { implicit ctx =>
      val explorer = new BlockExplorer()
      var txPool = ""
      if (considerPool) txPool = explorer.getPoolTransactionsStr
      getUnspentBoxes(tokenErgoMix.get.feeEmissionAddress.toString).filter(box => box.spendingTxId.isEmpty
        && box.amount >= Configs.defaultFullTokenFee && (!considerPool || !txPool.contains(s""""${box.id}","txId""""))) // not an input
    }
  }

  /**
   * @param nodeUrl node url
   * @param apiKey  node's api key
   * @return a fresh address derived from node
   */
  def deriveNextAddress(nodeUrl: String, apiKey: String): String = {
    GetURL.getStr(s"$nodeUrl/wallet/deriveNextKey", Map("api_key" -> apiKey, "Content-Type" -> "application/json"), useProxyIfSet = false)
  }

  /**
   * @param secret  secret associated with mix round
   * @param isAlice was box created as alice or bob
   * @param ctx     blockchain context
   * @return Alic or Bob implementation based on isAlice
   */
  def getProver(secret: BigInt, isAlice: Boolean)(implicit ctx: BlockchainContext): ergomix.FullMixBoxSpender = {
    if (isAlice) new AliceImpl(secret.bigInteger)
    else new BobImpl(secret.bigInteger)
  }

}
