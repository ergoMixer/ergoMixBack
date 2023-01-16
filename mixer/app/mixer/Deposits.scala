package mixer

import config.MainConfigs
import wallet.WalletHelper.now

import javax.inject.Inject
import models.Models.Deposit
import network.BlockExplorer
import play.api.Logger
import dao.{AllDepositsDAO, DAOUtils, MixingCovertRequestDAO, MixingRequestsDAO, SpentDepositsDAO, UnspentDepositsDAO}

class Deposits @Inject()(
                          daoUtils: DAOUtils,
                          explorer: BlockExplorer,
                          mixingRequestsDAO: MixingRequestsDAO,
                          covertMixingDAO: MixingCovertRequestDAO,
                          allDepositsDAO: AllDepositsDAO,
                          unspentDepositsDAO: UnspentDepositsDAO,
                          spentDepositsDAO: SpentDepositsDAO) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * process deposits, i.e. checks pending addresses to see if they've reached predefined threshold for entering mixing
   * Does both for ergs and tokens
   */
  def processDeposits(): Unit = {
    daoUtils.awaitResult(mixingRequestsDAO.nonCompletedDeposits).foreach { req =>
      logger.debug(s"Trying to read deposits for depositAddress: ${req.depositAddress}")
      val allBoxes = explorer.getUnspentBoxes(req.depositAddress)
      val knownIds = daoUtils.awaitResult(allDepositsDAO.knownIds(req.depositAddress))
      val timeValidate = now - req.createdTime >= MainConfigs.pruneUnusedCovertDepositsAfterMilliseconds
      if (allBoxes.isEmpty && knownIds.isEmpty && timeValidate) {
        val covertExist = daoUtils.awaitResult(covertMixingDAO.existsById(req.groupId))
        if (covertExist) {
          mixingRequestsDAO.delete(req.id)
          logger.info(s" [Deposit:${req.id}, Covert:${req.groupId}] was unused and removed")
          
        }
      }
      else {
        allBoxes.filterNot(box => knownIds.contains(box.id)).foreach { box =>
          insertDeposit(req.depositAddress, box.amount, box.id, req.neededAmount, box.getToken(req.tokenId), req.neededTokenAmount)
          logger.info(s"Successfully processed deposit of ${box.amount} with boxId ${box.id} for depositAddress ${req.depositAddress}")
        }
      }
    }
  }

  private implicit val insertReason: String = "Deposits.insertDeposit"

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
