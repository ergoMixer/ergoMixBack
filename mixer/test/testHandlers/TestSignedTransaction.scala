package testHandlers

import models.Box.{InBox, OutBox}
import org.ergoplatform.appkit.{ErgoId, InputBox, SignedInput, SignedTransaction}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar

import java.util
import scala.collection.JavaConverters


case class TestSignedTransaction(id: String, inBoxes: Seq[InBox], outBoxes: Seq[OutBox]) extends SignedTransaction with MockitoSugar {

  val seqMockedInputBox: Seq[InputBox] = outBoxes.map(outbox => {
    val mockedObj = mock[InputBox]
    when(mockedObj.getId).thenReturn(ErgoId.create(outbox.id))

    mockedObj
  })

  var inputs: util.List[SignedInput] = JavaConverters.seqAsJavaList(Seq.empty[SignedInput])
  var outputs: util.List[InputBox] = JavaConverters.seqAsJavaList(seqMockedInputBox)

  /**
   * returns transaction id
   */
  def getId: String = this.id

  /**
   * no implementation
   */
  def toJson(prettyPrint: Boolean): String = ""

  /**
   * no implementation
   */
  def toJson(prettyPrint: Boolean, formatJson: Boolean): String = ""

  /**
   * no implementation
   */
  def getSignedInputs: util.List[SignedInput] = inputs

  /**
   * returns mocked of InputBox for each outBoxes
   */
  def getOutputsToSpend: util.List[InputBox] = outputs

  /**
   * no implementation
   */
  def getCost: Int = -1

  /**
   * no implementation
   */
  def toBytes: Array[Byte] = Array.empty[Byte]

}
