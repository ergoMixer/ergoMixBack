package cli

import java.math.BigInteger

import cli.ErgoMixCLIUtil.usingClient
import app.AliceImpl
import app.ergomix.DHT

object Alice {
  /*
Play Alice's role in creating a half-mix box with secret x.
inputBoxIds are boxIds of input boxes funding the transaction.
Signing may require several secrets for proveDLog which are supplied in the array proveDlogSecrets.
Signing may also require several tuples of type (g, h, u, v, x) for proveDHTuple.
The arrays proverDHT_g, proverDHT_h, proverDHT_u, proverDHT_v, proverDHT_x must have equal number of elements, one for each such tuple.

The method attempts to create a transaction outputting a half-mix box at index 0.
If broadCast is false it just outputs the transaction but does not broadcast it.

feeAmount is the amount in fee in nanoErgs
   */
  def createHalfMixBox(x: BigInt, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String,
                       proverDlogSecrets: Array[String], broadCast: Boolean, poolAmount: Long, numToken: Int = 0) = {
    usingClient{implicit ctx =>
      val alice = new AliceImpl(x.bigInteger)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val tx = alice.createHalfMixBox(inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT](), poolAmount, numToken)
      if (broadCast) ctx.sendTransaction(tx.tx)
      tx
    }
  }
}
