package cli

import java.math.BigInteger

import app.BobImpl
import app.ergomix.{DHT, HalfMixBox}
import cli.MixUtils.usingClient
import org.ergoplatform.appkit.InputBox

object Bob {
  /**
   * enters mixing as bob, i.e. spends a half-box and generates two full-boxes
   *
   * @param y                 secret for the first round
   * @param halfMixBoxId      half-box the will be spend
   * @param inputBoxIds       other inputs of the tx (token emission box, deposit box)
   * @param feeAmount         fee amount
   * @param changeAddress     change address
   * @param proverDlogSecrets secrets for spending inputs
   * @param broadCast         whether to broadcast or just return
   * @param numToken          number of mixing tokens, i.e. mixing level
   * @return tx spending half-box and inputs and enter mixing as bob
   */
  def spendHalfMixBox(y: BigInt, halfMixBoxId: String, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean, numToken: Int = 0) = {
    usingClient { implicit ctx =>
      val bob = new BobImpl(y.bigInteger)
      val halfMixBox: InputBox = ctx.getBoxesById(halfMixBoxId)(0)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val (fullMixTx, bit) = bob.spendHalfMixBox(HalfMixBox(halfMixBox), inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT](), numToken)
      if (broadCast) ctx.sendTransaction(fullMixTx.tx)
      (fullMixTx, bit)
    }
  }
}
