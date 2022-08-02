package services

import dao.DAOUtils
import dataset.TestDataset
import mocked.MockedNodeProcess
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.db.slick.DatabaseConfigProvider
import testHandlers.TestSuite

import scala.concurrent.ExecutionContext.Implicits.global

class InitBestBlockTaskSpec extends TestSuite {
  val dataset: TestDataset.type = TestDataset
  val mockedDBConfigProvider: DatabaseConfigProvider = mock[DatabaseConfigProvider]

  val daoUtils = new DAOUtils(mockedDBConfigProvider)


  def clearDatabase(): Unit = {
    stealthDaoContext.inputDAO.deleteAll()
    stealthDaoContext.registerDAO.deleteAll()
    stealthDaoContext.assetDAO.deleteAll()
    stealthDaoContext.dataInputDAO.deleteAll()
    stealthDaoContext.outputDAO.deleteAll()
    stealthDaoContext.transactionDAO.deleteAll()
    stealthDaoContext.extractedBlockDAO.deleteAll()
    stealthDaoContext.stealthDAO.deleteAll()
  }

  /**
   * testing initBestBlock task
   * Dependencies:
   * database, node processor
   * Procedure:
   * storing best block in the database
   * Expected Output:
   * data should be successfully stored in the database.
   */
  property("store_block, storing best block in the database") {
    val mockedNode = new MockedNodeProcess
    clearDatabase()

    mockedNode.storeData()
    val initBestBlockTask = new InitBestBlockTask(stealthDaoContext.extractedBlockDAO, mockedNode.getMockedNodeProcess)
    initBestBlockTask.store_block()

    stealthDaoContext.extractedBlockDAO.exists(dataset.newHeader._2.id) should be(true)
  }
}
