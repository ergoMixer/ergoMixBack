package stealth

import dataset.TestDataset
import mocked.MockedNetworkUtils
import scorex.util.encode.Base16

import scala.language.postfixOps
import testHandlers.TestSuite

class NodeProcessSpec
  extends TestSuite {

  val dataset: TestDataset.type = TestDataset

  /**
   * testing mainChainHeaderIdAtHeight function with given height should return header Id
   * Dependencies:
   * network
   * Procedure:
   * checking function output (HeaderId) be equal to mocked header1 id
   * Expected Output:
   * they should be equal
   */
  property("testing mainChainHeaderIdAtHeight function with given height should return header Id") {
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)
    val result = nodeProcess.mainChainHeaderIdAtHeight(dataset.newHeader._1.height).get

    result shouldEqual dataset.newHeader._1.id
  }

  /**
   * testing mainChainHeaderWithHeaderId function with given header Id should return the header
   * Dependencies:
   * network
   * Procedure:
   * checking function output (header1) be equal to mocked header1
   * Expected Output:
   * they should be equal (checking parentId and id is enough)
   */
  property("testing mainChainHeaderWithHeaderId function with given header Id should return the header") {
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)
    val result = nodeProcess.mainChainHeaderWithHeaderId(dataset.newHeader._1.id).get

    result.id shouldEqual dataset.newHeader._1.id
    result.parentId shouldEqual dataset.newHeader._1.parentId
  }

  /**
   * testing mainChainHeaderAtHeight function with given height should return the header
   * Dependencies:
   * network
   * Procedure:
   * checking function output (header1) be equal to mocked header1
   * Expected Output:
   * they should be equal (checking id and parentId is enough)
   */
  property("testing mainChainHeaderAtHeight function with given height should return the header") {
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)

    val result = nodeProcess.mainChainHeaderAtHeight(dataset.newHeader._1.height).get

    result.parentId shouldEqual dataset.newHeader._1.parentId
    result.id shouldEqual dataset.newHeader._1.id
  }

  /**
   * testing mainChainFullBlockWithHeaderId function with given header id should return ergo full block
   * Dependencies:
   * network
   * Procedure:
   * checking function output (ergo full block) be equal to mocked ergo full block
   * Expected Output:
   * they should be equal
   */
  property("testing mainChainFullBlockWithHeaderId function with given header id should return ergo full block") {
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)

    val result = nodeProcess.mainChainFullBlockWithHeaderId(dataset.newHeader._1.id).get
    result.adProofs.get.proofBytes shouldEqual dataset.newErgoBlock._1.adProofs.get.proofBytes
  }

  /**
   * testing processTransactions function with this given data the output should not be an empty list
   * Dependencies:
   * network
   * Procedure:
   * checking function output (list of trigger events) with this test data must not be empty
   * Expected Output:
   * not empty list
   */
  property("testing processTransactions function with this given data the output should not be an empty list") {
    val networkUtils = new MockedNetworkUtils
    val nodeProcess = new NodeProcess(networkUtils.getMocked)

    val result = nodeProcess.processTransactions(dataset.newHeader._1.id)
    result.createdOutputs.length should not be 0
  }

}
