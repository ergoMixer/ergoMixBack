package org.ergoplatform.appkit

import org.ergoplatform.validation.ValidationRules
import org.ergoplatform.wallet.interpreter.ErgoInterpreter
import sigmastate.basics.DLogProtocol.{ProveDlog, DLogProverInput}
import java.util
import java.util.{List => JList}

import org.ergoplatform.wallet.secrets.ExtendedSecretKey
import sigmastate.basics.{SigmaProtocol, SigmaProtocolPrivateInput, SigmaProtocolCommonInput, DiffieHellmanTupleProverInput}
import org.ergoplatform._
import org.ergoplatform.wallet.protocol.context.{ErgoLikeParameters, ErgoLikeStateContext, TransactionContext}

import scala.util.Try
import sigmastate.eval.CompiletimeIRContext
import sigmastate.interpreter.{ContextExtension, ProverInterpreter}

import scala.collection.mutable

object Helpers {
  implicit class AppkitTryOps[A](val source: Try[A]) extends AnyVal {
    import sigmastate.utils.Helpers._  // don't remove, required for Scala 2.11
    def mapOrThrow[B](f: A => B): B = source.fold(t => throw t, f)
  }
}

/**
 * A class which holds secrets and can sign transactions (aka generate proofs).
 *
 * @param secretKeys secrets in extended form to be used by prover
 * @param dhtInputs  prover inputs containing secrets for generating proofs for ProveDHTuple nodes.
 * @param params     ergo blockchain parameters
 */
class AppkitProvingInterpreter(
      val secretKeys: JList[ExtendedSecretKey],
      val dLogInputs: JList[DLogProverInput],
      val dhtInputs: JList[DiffieHellmanTupleProverInput],
      params: ErgoLikeParameters)
  extends ErgoLikeInterpreter()(new CompiletimeIRContext) with ProverInterpreter {

  override type CTX = ErgoLikeContext
  import Iso._
  import Helpers._

  val secrets: Seq[SigmaProtocolPrivateInput[_ <: SigmaProtocol[_], _ <: SigmaProtocolCommonInput[_]]] = {
    val dlogs: IndexedSeq[DLogProverInput] = try JListToIndexedSeq(identityIso[ExtendedSecretKey]).to(secretKeys).map(_.key) catch {case _:Exception =>  IndexedSeq()}
    val dlogsAdditional: IndexedSeq[DLogProverInput] = try JListToIndexedSeq(identityIso[DLogProverInput]).to(dLogInputs) catch {case _:Exception =>  IndexedSeq()}
    val dhts: IndexedSeq[DiffieHellmanTupleProverInput] = try JListToIndexedSeq(identityIso[DiffieHellmanTupleProverInput]).to(dhtInputs) catch {case _:Exception =>  IndexedSeq()}
    dlogs ++ dlogsAdditional ++ dhts
  }

  val pubKeys: Seq[ProveDlog] = secrets
    .filter { case _: DLogProverInput => true case _ => false}
    .map(_.asInstanceOf[DLogProverInput].publicImage)

  /**
   * @note requires `unsignedTx` and `boxesToSpend` have the same boxIds in the same order.
   */
  def sign(unsignedTx: UnsignedErgoLikeTransaction,
           boxesToSpend: IndexedSeq[ErgoBox],
           dataBoxes: IndexedSeq[ErgoBox],
           stateContext: ErgoLikeStateContext): Try[ErgoLikeTransaction] = Try {
    if (unsignedTx.inputs.length != boxesToSpend.length) throw new Exception("Not enough boxes to spend")
    if (unsignedTx.dataInputs.length != dataBoxes.length) throw new Exception("Not enough data boxes")

    // Cost of transaction initialization: we should read and parse all inputs and data inputs,
    // and also iterate through all outputs to check rules
    val inputsCost = boxesToSpend.size * params.inputCost
    val dataInputsCost = dataBoxes.size * params.dataInputCost
    val outputsCost = unsignedTx.outputCandidates.size * params.outputCost
    val initialCost: Long = inputsCost + dataInputsCost + outputsCost

    val provedInputs = mutable.ArrayBuilder.make[Input]()
    var currentCost = initialCost
    for ((inputBox, boxIdx) <- boxesToSpend.zipWithIndex) {
      val unsignedInput = unsignedTx.inputs(boxIdx)
      require(util.Arrays.equals(unsignedInput.boxId, inputBox.id))

      val transactionContext = TransactionContext(boxesToSpend, dataBoxes, unsignedTx, boxIdx.toShort)

      val context = new ErgoLikeContext(ErgoInterpreter.avlTreeFromDigest(stateContext.previousStateDigest),
        stateContext.sigmaLastHeaders,
        stateContext.sigmaPreHeader,
        transactionContext.dataBoxes,
        transactionContext.boxesToSpend,
        transactionContext.spendingTransaction,
        transactionContext.selfIndex,
        ContextExtension.empty,
        ValidationRules.currentSettings,
        params.maxBlockCost,
        currentCost
      )

      prove(inputBox.ergoTree, context, unsignedTx.messageToSign).mapOrThrow { proverResult =>
        currentCost += proverResult.cost
//        if (currentCost > context.costLimit)
//          throw new Exception(s"Cost of transaction $unsignedTx exceeds limit ${context.costLimit}")
        provedInputs += Input(unsignedInput.boxId, proverResult)
      }
    }

    new ErgoLikeTransaction(provedInputs.result(), unsignedTx.dataInputs, unsignedTx.outputCandidates)
  }

}
