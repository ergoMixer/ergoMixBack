package cli

import java.math.BigInteger

import org.ergoplatform.appkit.ConstantsBuilder
import app.ErgoMix.{feeAmount, g}
import cli.ErgoMixCLIUtil.usingClient
import app.{CarolImpl, Util}
import app.ergomix.DHT
import special.sigma.GroupElement
import sigmastate.eval._

object Carol {
  def getProveDlogAddress(z:BigInt):String = {
    val $INFO$ = "Utility method to compute proveDlog address for some secret (mostly for use in createFeeEmissionBox)"
    val gZ:GroupElement = g.exp(z.bigInteger)
    usingClient { implicit ctx =>
      val contract = ctx.compileContract(
        ConstantsBuilder.create().item(
          "gZ", gZ
        ).build(),"{proveDlog(gZ)}"
      )
      new Util().addressEncoder.fromProposition(contract.getErgoTree).get.toString
    }
  }

  /*
Fee Emission box is box that can be used to pay fee if the following conditions are satisfied:
(1) It is used for spending an ErgoMix Box that has the potential to break privacy via fee
(2) A fixed amount of fee is deducted
(3) Balance is put in an identical fee emission box

A fee emission box requires an additional secret z that can be used to withdraw the entire amount anytime
   */
  def createFeeEmissionBox(z:BigInt, amount:Long, inputBoxIds:Array[String], changeAddress:String,
                           proverDlogSecret:String):Array[String] = {
    createFeeEmissionBox(z, amount, inputBoxIds, feeAmount, changeAddress, Array(proverDlogSecret), true)
  }

  /*
Fee Emission box is box that can be used to pay fee if the following conditions are satisfied:
(1) It is used for spending an ErgoMix Box that has the potential to break privacy via fee
(2) A fixed amount of fee is deducted
(3) Balance is put in an identical fee emission box

A fee emission box requires an additional secret z that can be used to withdraw the entire amount anytime
   */
  private def createFeeEmissionBox(z: BigInt, amount: Long, inputBoxIds: Array[String], feeAmount: Long, changeAddress: String, proverDlogSecrets: Array[String], broadCast: Boolean) = {
    usingClient{implicit ctx =>
      val carol = new CarolImpl(z.bigInteger)
      val dlogs: Array[BigInteger] = proverDlogSecrets.map(BigInt(_).bigInteger)
      val tx = carol.createFeeEmissionBox(amount, inputBoxIds, feeAmount, changeAddress, dlogs, Array[DHT]())
      if (broadCast) ctx.sendTransaction(tx.tx)
      Array(tx.tx.toJson(false), tx.address.toString)
    }
  }
}
