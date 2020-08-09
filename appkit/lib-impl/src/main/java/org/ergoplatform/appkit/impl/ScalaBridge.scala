package org.ergoplatform.appkit.impl

import _root_.org.ergoplatform.restapi.client._
import org.ergoplatform.explorer.client.model.{Asset => EAsset, Registers => ERegisters}

import java.util
import java.util.stream.Collectors
import java.util.{List, ArrayList}

import org.ergoplatform.ErgoBox.{NonMandatoryRegisterId, TokenId}
import org.ergoplatform.{DataInput, ErgoLikeTransaction, ErgoBox, Input}
import org.ergoplatform.appkit.{Iso, ErgoToken, JavaHelpers}
import org.ergoplatform.explorer.client.model.TransactionOutput
import org.ergoplatform.settings.ErgoAlgos
import special.sigma.Header
import scorex.crypto.authds.{ADDigest, ADKey}
import org.ergoplatform.wallet.interpreter.ErgoInterpreter
import scorex.crypto.hash.Digest32
import scorex.util.ModifierId
import sigmastate.SType
import sigmastate.Values.{ErgoTree, EvaluatedValue}
import sigmastate.eval.{CAvlTree, CHeader, Colls}
import sigmastate.interpreter.{ProverResult, ContextExtension}
import sigmastate.serialization.ErgoTreeSerializer.{DefaultSerializer => TreeSerializer}
import sigmastate.serialization.ValueSerializer
import special.collection.Coll

import scala.collection.JavaConversions

object ScalaBridge {
  import org.ergoplatform.appkit.JavaHelpers._

  implicit val isoSpendingProof: Iso[SpendingProof, ProverResult] = new Iso[SpendingProof, ProverResult] {
    override def to(spendingProof: SpendingProof): ProverResult = {
      val proof = ErgoAlgos.decodeUnsafe(spendingProof.getProofBytes)
      new ProverResult(proof, ContextExtension.empty)
    }

    override def from(proverResult: ProverResult): SpendingProof = {
      val proof = ErgoAlgos.encode(proverResult.proof)
      new SpendingProof().proofBytes(proof)
    }
  }

  implicit val isoErgoTransactionDataInput: Iso[ErgoTransactionDataInput, DataInput] = new Iso[ErgoTransactionDataInput, DataInput] {
    override def to(ergoTransactionDataInput: ErgoTransactionDataInput) =
      new DataInput(ADKey @@ ErgoAlgos.decodeUnsafe(ergoTransactionDataInput.getBoxId))

    override def from(dataInput: DataInput): ErgoTransactionDataInput =
      new ErgoTransactionDataInput().boxId(ErgoAlgos.encode(dataInput.boxId))
  }

  implicit val isoErgoTransactionInput: Iso[ErgoTransactionInput, Input] = new Iso[ErgoTransactionInput, Input] {
    override def to(ergoTransactionInput: ErgoTransactionInput) =
      new Input(
        ADKey @@ ErgoAlgos.decodeUnsafe(ergoTransactionInput.getBoxId),
        ScalaBridge.isoSpendingProof.to(ergoTransactionInput.getSpendingProof))

    override def from(input: Input): ErgoTransactionInput =
      new ErgoTransactionInput()
          .boxId(ErgoAlgos.encode(input.boxId))
          .spendingProof(ScalaBridge.isoSpendingProof.from(input.spendingProof))
  }

  implicit val isoAssetToErgoToken: Iso[Asset, ErgoToken] = new Iso[Asset, ErgoToken] {
    override def to(a: Asset): ErgoToken = new ErgoToken(a.getTokenId, a.getAmount)
    override def from(t: ErgoToken): Asset = new Asset().tokenId(t.getId.toString).amount(t.getValue)
  }

  implicit val isoAssetToPair: Iso[Asset, (TokenId, Long)] = new Iso[Asset, (TokenId, Long)] {
    override def to(a: Asset) = (Digest32 @@ a.getTokenId.toBytes, a.getAmount)
    override def from(t: (TokenId, Long)): Asset = new Asset().tokenId(ErgoAlgos.encode(t._1)).amount(t._2)
  }

  implicit val isoExplorerAssetToPair: Iso[EAsset, (TokenId, Long)] = new Iso[EAsset, (TokenId, Long)] {
    override def to(a: EAsset) = (Digest32 @@ a.getTokenId.toBytes, a.getAmount)
    override def from(t: (TokenId, Long)): EAsset = new EAsset().tokenId(ErgoAlgos.encode(t._1)).amount(t._2)
  }


  implicit val isoStringToErgoTree: Iso[String, ErgoTree] = new Iso[String, ErgoTree] {
    override def to(treeStr: String): ErgoTree = {
      val treeBytes = ErgoAlgos.decodeUnsafe(treeStr)
      TreeSerializer.deserializeErgoTree(treeBytes)
    }
    override def from(tree: ErgoTree): String = {
      ErgoAlgos.encode(TreeSerializer.serializeErgoTree(tree))
    }
  }

  type AdditionalRegisters = Map[NonMandatoryRegisterId, EvaluatedValue[_ <: SType]]

  implicit val isoRegistersToMap: Iso[Registers, AdditionalRegisters] = new Iso[Registers, AdditionalRegisters] {
    override def to(regs: Registers): AdditionalRegisters = {
      JavaConversions.mapAsScalaMap(regs).map { r =>
        val id = ErgoBox.registerByName(r._1).asInstanceOf[NonMandatoryRegisterId]
        val v = ValueSerializer.deserialize(ErgoAlgos.decodeUnsafe(r._2))
        (id, v.asInstanceOf[EvaluatedValue[_ <: SType]])
      }.toMap
    }
    override def from(ergoRegs: AdditionalRegisters): Registers = {
      val res = new Registers()
      ergoRegs.foreach { case (id, value) =>
        val name = id.toString()
        val v = ErgoAlgos.encode(ValueSerializer.serialize(value))
        res.put(name, v)
      }
      res
    }
  }

  implicit val isoExplRegistersToMap: Iso[ERegisters, AdditionalRegisters] = new Iso[ERegisters, AdditionalRegisters] {
    override def to(regs: ERegisters): AdditionalRegisters = {
      JavaConversions.mapAsScalaMap(regs).map { r =>
        val id = ErgoBox.registerByName(r._1).asInstanceOf[NonMandatoryRegisterId]
        val v = ValueSerializer.deserialize(ErgoAlgos.decodeUnsafe(r._2))
        (id, v.asInstanceOf[EvaluatedValue[_ <: SType]])
      }.toMap
    }
    override def from(ergoRegs: AdditionalRegisters): ERegisters = {
      val res = new ERegisters()
      ergoRegs.foreach { case (id, value) =>
        val name = id.toString()
        val v = ErgoAlgos.encode(ValueSerializer.serialize(value))
        res.put(name, v)
      }
      res
    }
  }

  implicit val isoErgoTransactionOutput: Iso[ErgoTransactionOutput, ErgoBox] = new Iso[ErgoTransactionOutput, ErgoBox] {
    override def to(boxData: ErgoTransactionOutput): ErgoBox = {
      val tree= boxData.getErgoTree.convertTo[ErgoTree]
      val tokens = boxData.getAssets.convertTo[Coll[(TokenId, Long)]]
      val regs = boxData.getAdditionalRegisters().convertTo[AdditionalRegisters]
      new ErgoBox(boxData.getValue, tree,
        tokens, regs,
        ModifierId @@ boxData.getTransactionId,
        boxData.getIndex.shortValue,
        boxData.getCreationHeight)
    }

    override def from(box: ErgoBox): ErgoTransactionOutput = {
      val assets = box.additionalTokens.convertTo[List[Asset]]
      val regs = isoRegistersToMap.from(box.additionalRegisters)
      val out = new ErgoTransactionOutput()
          .boxId(ErgoAlgos.encode(box.id))
          .value(box.value)
          .ergoTree(ErgoAlgos.encode(TreeSerializer.serializeErgoTree(box.ergoTree)))
          .assets(assets)
          .additionalRegisters(regs)
          .creationHeight(box.creationHeight)
          .transactionId(box.transactionId)
          .index(box.index)
      out
    }
  }

//  implicit val isoExplTransactionOutput: Iso[TransactionOutput, ErgoBox] = new Iso[TransactionOutput, ErgoBox] {
//    override def to(boxData: TransactionOutput): ErgoBox = {
//      val tree = boxData.getErgoTree.convertTo[ErgoTree]
//      val tokens = boxData.getAssets.convertTo[Coll[(TokenId, Long)]]
//      val regs = boxData.getAdditionalRegisters().convertTo[AdditionalRegisters]
//      new ErgoBox(boxData.getValue, tree,
//        tokens, regs,
//        ModifierId @@ boxData.getTransactionId,
//        boxData.getIndex.shortValue,
//        boxData.getCreationHeight)
//    }
//
//    override def from(box: ErgoBox): ErgoTransactionOutput = {
//      val assets = box.additionalTokens.convertTo[List[Asset]]
//      val regs = isoRegistersToMap.from(box.additionalRegisters)
//      val out = new ErgoTransactionOutput()
//          .boxId(ErgoAlgos.encode(box.id))
//          .value(box.value)
//          .ergoTree(ErgoAlgos.encode(TreeSerializer.serializeErgoTree(box.ergoTree)))
//          .assets(assets)
//          .additionalRegisters(regs)
//          .creationHeight(box.creationHeight)
//          .transactionId(box.transactionId)
//          .index(box.index)
//      out
//    }
//  }

  implicit val isoBlockHeader: Iso[BlockHeader, Header] = new Iso[BlockHeader, Header] {
    override def to(h: BlockHeader): Header =
      CHeader(
        id = h.getId.toColl,
        version = h.getVersion.toByte,
        parentId = h.getParentId.toColl,
        ADProofsRoot = h.getAdProofsRoot.toColl,
        stateRoot = CAvlTree(ErgoInterpreter.avlTreeFromDigest(ADDigest @@ h.getStateRoot.toBytes)),
        transactionsRoot = h.getTransactionsRoot.toColl,
        timestamp = h.getTimestamp(),
        nBits = h.getNBits(),
        height = h.getHeight,
        extensionRoot = h.getExtensionHash.toColl,
        minerPk = h.getPowSolutions.getPk.toGroupElement,
        powOnetimePk = h.getPowSolutions.getW.toGroupElement,
        powNonce = h.getPowSolutions.getN.toColl,
        powDistance = SigmaDsl.BigInt(h.getPowSolutions.getD.toBigIntegerExact),
        votes = h.getVotes.toColl
      )

    override def from(a: Header): BlockHeader = ???
  }

  implicit val isoErgoTransaction: Iso[ErgoTransaction, ErgoLikeTransaction] = new Iso[ErgoTransaction, ErgoLikeTransaction] {
    override def to(apiTx: ErgoTransaction): ErgoLikeTransaction =
      new ErgoLikeTransaction(
        apiTx.getInputs.convertTo[IndexedSeq[Input]],
        apiTx.getDataInputs.convertTo[IndexedSeq[DataInput]],
        apiTx.getOutputs.convertTo[IndexedSeq[ErgoBox]]
      )

    override def from(tx: ErgoLikeTransaction): ErgoTransaction =
      new ErgoTransaction()
        .id(tx.id)
        .inputs(tx.inputs.convertTo[List[ErgoTransactionInput]])
        .dataInputs(tx.dataInputs.convertTo[List[ErgoTransactionDataInput]])
        .outputs(tx.outputs.convertTo[List[ErgoTransactionOutput]])
  }
}
