package app

import java.math.BigInteger

import ErgoMix._
import org.ergoplatform.appkit.{BlockchainContext, ErgoProver, ErgoValue, InputBox}
import ergomix.Carol
import ergomix.{DHT, FeeEmissionTx}
import sigmastate.eval._
import special.sigma.GroupElement

import scala.jdk.CollectionConverters._

class CarolImpl(z:BigInteger) (implicit ctx: BlockchainContext) extends Carol {
  override def createFeeEmissionBox(amount: Long, inputBoxes: Array[InputBox], feeAmount: Long, changeAddress: String, additionalDlogSecrets: Array[BigInteger], additionalDHTuples: Array[DHT]): FeeEmissionTx = {
    implicit  val ergoMix = new ErgoMix(ctx)

    val util = new Util()
    val gZ:GroupElement = g.exp(z)
    val txB = ctx.newTxBuilder()
    val newBox = txB.outBoxBuilder().value(amount).registers(ErgoValue.of(gZ)).contract(ergoMix.feeEmissionContract).build()
    val inputs = new java.util.ArrayList[InputBox]()

    inputs.addAll(inputBoxes.toList.asJava)

    val txToSign = txB.boxesToSpend(inputs).outputs(newBox).fee(feeAmount).sendChangeTo(util.getAddress(changeAddress)).build()

    val prover: ErgoProver = additionalDHTuples.foldLeft(
      additionalDlogSecrets.foldLeft(
        ctx.newProverBuilder()
      )(
        (ergoProverBuilder, bigInteger) => ergoProverBuilder.withDLogSecret(bigInteger)
      )
    )(
      (ergoProverBuilder, dh) => ergoProverBuilder.withDHTData(dh.gv, dh.hv, dh.uv, dh.vv, dh.x)
    ).build()

    val tx = prover.sign(txToSign)

    val address = util.addressEncoder.fromProposition(ergoMix.feeEmissionContract.getErgoTree).get

    FeeEmissionTx(tx, gZ, address)
  }
}
