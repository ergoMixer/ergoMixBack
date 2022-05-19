package mocked

import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.Header
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import dataset.TestDataset
import stealth.NodeProcess


import scala.language.postfixOps

/**
 * mocking environment for test
 * */

class MockedNodeProcess {
  private val dataset = TestDataset

  private val mockedNodeProcess = mock(classOf[NodeProcess])

  def getMockedNodeProcess: NodeProcess = mockedNodeProcess


  def storeData(): Unit = {
    val (header1, header2) = dataset.newHeader
    when(mockedNodeProcess.mainChainHeaderIdAtHeight(any())).thenReturn(
      Option[String] {
        header1.id
      }
    )
    when(mockedNodeProcess.mainChainHeaderAtHeight(any())).thenAnswer(item => {
      val args = item.getArguments
      if (args(0).asInstanceOf[Int] == header2.height) {
        Option[Header] {
          header2
        }
      } else {
        None
      }
    })
  }


  def fork(): Unit = {
    val (header1, header2) = dataset.newHeader

    when(mockedNodeProcess.mainChainHeaderIdAtHeight(any())).thenAnswer(item => {
      val args = item.getArguments
      if (args(0).asInstanceOf[Int] == header2.height) {
        Option[String] {
          header2.id
        }
      } else {
        Option[String] {
          header1.id
        }
      }
    })
    when(mockedNodeProcess.mainChainHeaderAtHeight(any())).thenReturn(None)

  }


  // node process mocked functions

  when(mockedNodeProcess.mainChainHeaderWithHeaderId(any())).thenReturn(
    Option[Header] {
      dataset.newHeader._2
    }
  )

  when(mockedNodeProcess.mainChainFullBlockWithHeaderId(any())).thenReturn(
    Option[ErgoFullBlock] {
      dataset.newErgoBlock._1
    }
  )
  when(mockedNodeProcess.processTransactions(any(), any())).thenReturn(
    null
  )

}
