package cli

import java.math.BigInteger

import app.AliceImpl
import app.ergomix.DHT
import cli.MixUtils.usingClient

object Alice {
  /**
   * Creates a half-box for entering mix as alice
   *
   * @param x                 the secret
   * @param inputBoxIds       input boxes
   * @param feeAmount         fee
   * @param changeAddress     change address
   * @param proverDlogSecrets DLog secrets
   * @param broadCast         broadcast the tx or just return
   * @param poolAmount        mixing ring
   * @param numToken          number of mixing token (mix level)
   * @param mixingTokenId     if of mixing token (if it is a token mixing mix)
   * @param mixingTokenAmount number mixing token
   * @return the transaction in which half-box is created
   */
  def createHalfMixBox(x: BigInt, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String,
                       proverDlogSecrets: Array[String], broadCast: Boolean, poolAmount: Long, numToken: Int = 0,
                       mixingTokenId: String, mixingTokenAmount: Long) = {
    usingClient { implicit ctx =>
      val alice = new AliceImpl(x.bigInteger)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val tx = alice.createHalfMixBox(inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT](), poolAmount, numToken, mixingTokenId, mixingTokenAmount)
      if (broadCast) ctx.sendTransaction(tx.tx)
      tx
    }
  }
}
