package testHandlers

import java.util

import scala.collection.JavaConverters._

import models.Box.{InBox, OutBox}
import org.ergoplatform.appkit.{ErgoId, InputBox, JavaHelpers, OutBox => AOUTBOX, SignedInput, SignedTransaction}
import org.ergoplatform.appkit.impl.OutBoxImpl
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import scorex.util.bytesToId

case class TestSignedTransaction(id: String, inBoxes: Seq[InBox], outBoxes: Seq[OutBox])
  extends SignedTransaction
  with MockitoSugar {

  val seqMockedInputBox: Seq[InputBox] = outBoxes.map { outbox =>
    val mockedObj = mock[InputBox]
    when(mockedObj.getId).thenReturn(ErgoId.create(outbox.id))

    mockedObj
  }

  var inputs: util.List[SignedInput] = seqAsJavaList(Seq.empty[SignedInput])
  var outputs: util.List[InputBox]   = seqAsJavaList(seqMockedInputBox)

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

  /**
   * @return list of input boxes ids for this transaction
   */
  def getInputBoxesIds: util.List[String] = seqAsJavaList(inputs.asScala.map(_.getId.toString))

  /**
   * Gets output boxes that will be created by this transaction
   */
  def getOutputs: util.List[AOUTBOX] = {
    var ind = 0
    val outs = outputs.asScala.map { out =>
      val bodx = new OutBoxImpl(
        JavaHelpers
          .createBoxCandidate(
            out.getValue,
            out.getErgoTree,
            out.getTokens.asScala,
            out.getRegisters.asScala,
            out.getCreationHeight
          )
          .toBox(bytesToId(this.id.getBytes), ind.toShort)
      )
      ind += 1
      bodx.asInstanceOf[AOUTBOX]
    }.asJava
    outs
  }
}
