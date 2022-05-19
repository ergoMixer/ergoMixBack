package services

import dataset.TestDataset
import mocked.MockedNodeProcess
import testHandlers.TestSuite

import scala.concurrent.ExecutionContext.Implicits.global

class InitBestBlockTaskSpec extends TestSuite {
  val dataset = TestDataset

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
    val MockedNode = new MockedNodeProcess
    stealthDaoContext.extractedBlockDAO.deleteAll()

    MockedNode.storeData()
    val initBestBlockTask = new InitBestBlockTask(stealthDaoContext.extractedBlockDAO, MockedNode.getMockedNodeProcess)
    initBestBlockTask.store_block()

    stealthDaoContext.extractedBlockDAO.exists(dataset.newHeader._2.id) should be(true)
  }


}
