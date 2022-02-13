package mixer

import wallet.WalletHelper.now

import javax.inject.Inject
import models.Models.Deposit
import network.{BlockExplorer, NetworkUtils}
import play.api.Logger
import dao.{AllDepositsDAO, DAOUtils, MixingRequestsDAO, SpentDepositsDAO, UnspentDepositsDAO}

class Deposits @Inject()(
                            networkUtils: NetworkUtils,
                            daoUtils: DAOUtils,
                            explorer: BlockExplorer,
                            mixingRequestsDAO: MixingRequestsDAO,
                            allDepositsDAO: AllDepositsDAO,
                            unspentDepositsDAO: UnspentDepositsDAO,
                            spentDepositsDAO: SpentDepositsDAO) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * process deposits, i.e. checks pending addresses to see if they've reached predefined threshold for entering mixing
   * Does both for ergs and tokens
   */
  def processDeposits(): Unit = {
    daoUtils.awaitResult(mixingRequestsDAO.nonCompletedDeposits).map { req =>
      // logger.info(s"Trying to read deposits for depositAddress: $depositAddress")
      networkUtils.usingClient { implicit ctx =>
        val allBoxes = explorer.getUnspentBoxes(req.depositAddress)

        val knownIds = daoUtils.awaitResult(allDepositsDAO.knownIds(req.depositAddress))

        allBoxes.filterNot(box => knownIds.contains(box.id)).map { box =>
          insertDeposit(req.depositAddress, box.amount, box.id, req.neededAmount, box.getToken(req.tokenId), req.neededTokenAmount)
          logger.info(s"Successfully processed deposit of ${box.amount} with boxId ${box.id} for depositAddress ${req.depositAddress}")
        }
      }
    }
  }

  private implicit val insertReason = "Deposits.insertDeposit"

  /**
   * inserts deposits into db
   *
   * @param address           deposit address
   * @param amount            amount of deposit
   * @param boxId             box id containing amount of deposit
   * @param needed            needed erg amount to enter mixing
   * @param tokenAmount       mixing level
   * @param neededTokenAmount needed token amount to enter mixing
   */
  def insertDeposit(address: String, amount: Long, boxId: String, needed: Long, tokenAmount: Long, neededTokenAmount: Long): String = {
    // does not check from block explorer. Should be used by experts only. Otherwise, the job will automatically insert deposits
    if (daoUtils.awaitResult(mixingRequestsDAO.existsByDepositAddress(address))) {
      val deposit_exists = unspentDepositsDAO.existsByBoxId(boxId)
      val deposit_spent = spentDepositsDAO.existsByBoxId(boxId)
      if (daoUtils.awaitResult(deposit_exists)) throw new Exception(s"Deposit already exists")
      if (daoUtils.awaitResult(deposit_spent)) throw new Exception(s"Deposit already spent")

      val deposit = Deposit(address, boxId, amount, tokenAmount, now)
      daoUtils.awaitResult(unspentDepositsDAO.insertDeposit(deposit))

      val depositsAmount = daoUtils.awaitResult(unspentDepositsDAO.amountByAddress(address))
      val currentSum = depositsAmount._1.get
      val tokenSum = depositsAmount._2.get

      if (currentSum >= needed && tokenSum >= neededTokenAmount) {
        mixingRequestsDAO.updateDepositCompleted(address)
        "deposit completed"
      } else s"${needed - currentSum} nanoErgs pending"
    } else throw new Exception(s"Address $address does not belong to this wallet")
  }

}
