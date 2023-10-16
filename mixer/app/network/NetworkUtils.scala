package network

import javax.inject.{Inject, Singleton}

import scala.collection.mutable
import scala.collection.JavaConverters._

import config.MainConfigs
import io.circe.parser.parse
import io.circe.Json
import mixinterface.TokenErgoMix
import models.Box.OutBox
import models.StealthModels.{CreateExtractedBlock, CreateExtractedFullBlock, ExtractedBlock, ExtractedFullBlock}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.BoxOperations.ExplorerApiUnspentLoader
import play.api.Logger
import wallet.WalletHelper

@Singleton
class NetworkUtils @Inject() (explorer: BlockExplorer) {
  private val logger: Logger = Logger(this.getClass)

  var allClients: mutable.Map[String, ErgoClient]    = mutable.Map.empty[String, ErgoClient]
  var prunedClients: mutable.Map[String, ErgoClient] = mutable.Map.empty[String, ErgoClient]
  var tokenErgoMix: Option[TokenErgoMix]             = None

  def pruneClients(): Unit = {
    val explorerHeight = explorer.getHeight
    allClients.foreach { client =>
      try {
        val validClient = client._2.execute { ctx =>
          val nodeHeight = ctx.getHeight
          explorerHeight - nodeHeight <= 2
        }
        if (validClient) prunedClients(client._1) = client._2
      } catch {
        case e: Throwable =>
          logger.error(s"will ignore this node. ${e.getMessage}")
      }
    }
  }

  def clientsAreOk: Boolean =
    prunedClients.nonEmpty

  /**
   * select a random client
   *
   * @return tuple of client's url and ErgoClient
   */
  def getRandomClient: (String, ErgoClient) = {
    if (prunedClients.isEmpty) throw new Exception("There are no available nodes to connect to")
    prunedClients.toSeq(WalletHelper.randInt(prunedClients.size))
  }

  def usingClient[T](f: BlockchainContext => T): T = {
    val rndClient = getRandomClient._2
    rndClient.execute(ctx => f(ctx))
  }

  /**
   * @param considerPool whether to eliminate boxes already in mempool
   * @return list of half-boxes
   */
  def getHalfMixBoxes(considerPool: Boolean = false): List[OutBox] = {
    var txPool = ""
    if (considerPool) txPool = explorer.getPoolTransactionsStr
    val unspentBoxes = explorer.getUnspentBoxes(tokenErgoMix.get.halfMixAddress.toString)
    unspentBoxes.filter { box =>
      (!considerPool || !txPool.contains(s""""${box.id}","transactionId"""")) && // not already in mempool
      box.registers.contains("R4") &&
      !WalletHelper.poisonousHalfs.contains(box.ge("R4")) // not poisonous
    }.toList
  }

  /**
   * @return all unspent half-boxes
   */
  def getAllHalfBoxes: List[OutBox] =
    explorer.getUnspentBoxes(tokenErgoMix.get.halfMixAddress.toString).toList

  /**
   * @param numToken     minimum number of token you wish the boxes to have
   * @param considerPool whether to eliminate boxes already in mempool
   * @return list of unspent token emission boxes containing at least numToken mixing tokens
   */
  def getTokenEmissionBoxes(numToken: Int = 0, considerPool: Boolean = false): List[OutBox] = {
    var txPool = ""
    if (considerPool) txPool = explorer.getPoolTransactionsStr
    explorer
      .getUnspentBoxes(tokenErgoMix.get.tokenEmissionAddress.toString)
      .filter { box =>
        box.getToken(TokenErgoMix.tokenId) >= numToken &&
        (!considerPool || !txPool.contains(s""""${box.id}","txId""""))
      }
      .toList
  }

  /**
   * get all unspent boxes for address
   *
   * @param address Address
   * @return list of unspent boxes
   */
  def getAllUnspentBoxesForAddress(address: Address): List[InputBox] =
    usingClient { implicit ctx =>
      val inputBoxesLoader = new ExplorerApiUnspentLoader()
      inputBoxesLoader.prepare(ctx, List(address).asJava, MainConfigs.maxErg, Seq.empty.asJava)
      val coverBoxes = BoxOperations.getCoveringBoxesFor(
        MainConfigs.maxErg,
        Seq.empty.asJava,
        false,
        (page: Integer) => inputBoxesLoader.loadBoxesPage(ctx, address, page)
      )
      coverBoxes.getBoxes.asScala.toList
    }

  /**
   * @param numToken minimum number of token you wish the boxes to have
   * @return list of unspent token emission boxes containing at least numToken mixing tokens
   */
  def getTokenEmissionBoxes(numToken: Int): List[InputBox] = {
    val address = Address.create(tokenErgoMix.get.tokenEmissionAddress.toString)
    val boxes   = getAllUnspentBoxesForAddress(address)
    boxes.filter { token =>
      val cur = token.getTokens
      cur.size() > 0 && cur.get(0).getId.toString == TokenErgoMix.tokenId && cur.get(0).getValue >= numToken
    }
  }

  /**
   * @param poolAmount mix ring
   * @return list of unspent full-boxes in a specific ring
   */
  def getFullMixBoxes(poolAmount: Long = -1): Seq[OutBox] = {
    val unspent = explorer.getUnspentBoxes(tokenErgoMix.get.fullMixAddress.toString)
    if (poolAmount != -1)
      unspent.filter(_.amount == poolAmount)
    else
      unspent
  }

  /**
   * @param boxId       box id of the box we wish to get
   * @param findInPool  whether to find boxes that are currently in mempool
   * @param findInSpent whether to find boxes that are spent
   * @return box as InputBox
   */
  def getUnspentBoxById(boxId: String, findInPool: Boolean = false, findInSpent: Boolean = false): InputBox =
    usingClient { implicit ctx =>
      try
        ctx.getDataSource.getBoxById(boxId, findInPool, findInSpent)
      catch {
        case _: Exception => throw new Exception("No box found")
      }
    }

  /**
   * @param considerPool whether to eliminate boxes already in mempool
   * @return list of fee-boxes
   */
  def getFeeEmissionBoxes(considerPool: Boolean = false): Seq[OutBox] = {
    var txPool = ""
    if (considerPool) txPool = explorer.getPoolTransactionsStr
    explorer
      .getUnspentBoxes(tokenErgoMix.get.feeEmissionAddress.toString)
      .filter(box =>
        box.spendingTxId.isEmpty
          && box.amount >= MainConfigs.defaultFullTokenFee && (!considerPool || !txPool
            .contains(s""""${box.id}","txId""""))
      ) // not an input
  }

  /**
   * @return list of fee-boxes
   */
  def getFeeEmissionBoxes: List[InputBox] = {
    val address = Address.create(tokenErgoMix.get.feeEmissionAddress.toString)
    val boxes   = getAllUnspentBoxesForAddress(address)
    boxes
  }

  /**
   * @return list of param-boxes
   */
  def getParamBoxes: List[InputBox] = {
    val boxes = getAllUnspentBoxesForAddress(TokenErgoMix.paramAddress)
    boxes.filter(box => box.getTokens.size() > 0 && box.getTokens.get(0).getId.toString.equals(TokenErgoMix.tokenId))
  }

  /**
   * @return option of owner's box
   */
  def getOwnerBox: Option[InputBox] = {
    val boxes = getAllUnspentBoxesForAddress(TokenErgoMix.mixerOwner)
    boxes.find(in => in.getTokens.size() == 1 && in.getTokens.get(0).getId.toString == TokenErgoMix.tokenId)
  }

  /**
   * @return list of income-boxes
   */
  def getIncomeBoxes: List[InputBox] = {
    val boxes = getAllUnspentBoxesForAddress(TokenErgoMix.mixerIncome)
    boxes
  }

  /**
   * @param nodeUrl node url
   * @param apiKey  node's api key
   * @return a fresh address derived from node
   */
  def deriveNextAddress(nodeUrl: String, apiKey: String): String =
    GetURL.getStr(
      s"$nodeUrl/wallet/deriveNextKey",
      Map("api_key" -> apiKey, "Content-Type" -> "application/json"),
      useProxyIfSet = false
    )

  /**
   * whether boxId is double spent
   *
   * @param boxId    box id
   * @param wrtBoxId other box in the tx. if the boxId is spent and this box is also present int the tx then there is no double spending
   * @return
   */
  def isDoubleSpent(boxId: String, wrtBoxId: String): Boolean = usingClient { implicit ctx =>
    explorer.getSpendingTxId(boxId).flatMap { txId =>
      // boxId has been spent, while the fullMixBox generated has zero confirmations. Looks like box has been double-spent elsewhere
      explorer.getTransaction(txId).map { tx =>
        // to be sure, get the complete tx and check that none if its outputs are our fullMixBox
        !tx.outboxes.map(_.id).contains(wrtBoxId)
      }
    }
  }.getOrElse(false)

  /**
   * fetch main chain (not forked) header from node by given range height
   *
   * @param from  - Int (not included)
   * @param until - Int
   * @return Header of blocks if exist in range of heights
   */
  def chainSliceHeaderAtHeight(from: Int, until: Int): Seq[ExtractedBlock] = {
    val url = s"${getRandomClient._1}/blocks/chainSlice?fromHeight=$from&toHeight=$until"
    parse(GetURL.getStr(url)) match {
      case Right(data) =>
        data.hcursor.as[Seq[Json]].toOption.get.map(headerJson => CreateExtractedBlock(headerJson.toString()).get)
      case Left(ex) => throw ex
    }
  }

  /**
   * fetch ExtractedBlock from node by given headerId
   *
   * @param headerId - String
   * @return fetch header of block if exist by given headerId
   */
  def mainChainHeaderWithHeaderId(headerId: String): Option[ExtractedBlock] = {
    val url = s"${getRandomClient._1}/blocks/$headerId/header"
    CreateExtractedBlock(GetURL.getStr(url))
  }

  /**
   * fetch ExtractedBlock from node by given height
   *
   * @param height - Int
   * @return fetch header of block if exist by given height
   */
  def mainChainHeaderAtHeight(height: Int): Option[ExtractedBlock] = {
    val headers = chainSliceHeaderAtHeight(height - 1, height)
    if (headers.nonEmpty && headers.head.height == height) headers.headOption
    else None
  }

  /**
   * fetch ExtractedFullBlock from node by given headerId
   *
   * @param headerId - String
   * @return fetch full header of block if exist by given headerId
   */
  def mainChainFullBlockWithHeaderId(headerId: String): Option[ExtractedFullBlock] = {
    val url = s"${getRandomClient._1}/blocks/$headerId"
    CreateExtractedFullBlock(GetURL.getStr(url))
  }

}
