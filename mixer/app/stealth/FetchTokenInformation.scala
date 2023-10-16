package stealth

import javax.inject.Inject

import dao.stealth.TokenInformationDAO
import dao.DAOUtils
import network.BlockExplorer
import play.api.Logger

class FetchTokenInformation @Inject() (
  blockExplorer: BlockExplorer,
  daoUtils: DAOUtils,
  tokenInformationDAO: TokenInformationDAO,
) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Fetch information for tokens that do not have any information in the db.
   */
  def fetchTokenInformation(): Unit = {
    val withoutInformationTokens = daoUtils.awaitResult(tokenInformationDAO.selectWithoutInformationTokens())
    withoutInformationTokens.foreach { token =>
      try {
        val information = blockExplorer.getTokenInformation(token.id)
        logger.info(s"fetching information of token ${token.id}")
        if (information.isDefined) daoUtils.awaitResult(tokenInformationDAO.updateToken(information.get))
      } catch {
        case e: Throwable =>
          logger.warn(s"Problem in fetching information of token: ${token.id}")
          logger.debug(s"Exception: ${e.getMessage}")
      }
    }
  }
}
