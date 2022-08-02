package models

import org.ergoplatform.{DataInput, ErgoBox, Input}
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.nodeView.wallet.scanning.ScanningPredicate
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigmastate.serialization.ValueSerializer

object StealthModels {
  object Types {
    type Identifier = String
  }

  case class ExtractedBlockModel(headerId: String, parentId: String, height: Int, timestamp: Long)

  object ExtractedBlock {
    def apply(header: Header): ExtractedBlockModel = {
      ExtractedBlockModel(header.id, header.parentId, header.height, header.timestamp)
    }
  }

  case class ExtractedTransactionModel(id: String, headerId: String, inclusionHeight: Int, timestamp: Long)

  object ExtractedTransaction {
    def apply(tx: ErgoTransaction, header: Header): ExtractedTransactionModel = {
      ExtractedTransactionModel(tx.id, header.id, header.height, header.timestamp)
    }
  }

  case class ExtractedRegisterModel(id: String, boxId: String, value: Array[Byte])

  object ExtractedRegister {
    def apply(register: (org.ergoplatform.ErgoBox.NonMandatoryRegisterId, _ <: sigmastate.Values.EvaluatedValue[_ <: sigmastate.SType]), ergoBox: ErgoBox): ExtractedRegisterModel = {
      ExtractedRegisterModel(register._1.toString(), Base16.encode(ergoBox.id), ValueSerializer.serialize(register._2))
    }
  }

  case class ExtractedAssetModel(tokenId: String, boxId: String, headerId: String, index: Short, value: Long)

  object ExtractedAsset {
    def apply(token: (ModifierId, Long), ergoBox: ErgoBox, headerId: String, index: Short): ExtractedAssetModel = {
      ExtractedAssetModel(token._1, Base16.encode(ergoBox.id), headerId, index, token._2)
    }
  }

  case class ExtractedOutputModel(boxId: String, txId: String, headerId: String, value: Long, creationHeight: Int, index: Short, ergoTree: String, timestamp: Long, bytes: Array[Byte], spent: Boolean = false, spendAddress: String = "", stealthId: String = "")

  object ExtractedOutput {
    def apply(ergoBox: ErgoBox, header: Header): ExtractedOutputModel = {
      ExtractedOutputModel(
        Base16.encode(ergoBox.id), ergoBox.transactionId, header.id, ergoBox.value, ergoBox.creationHeight,
        ergoBox.index, Base16.encode(ergoBox.ergoTree.bytes), header.timestamp, ergoBox.bytes
      )
    }
  }

  case class ExtractedInputModel(boxId: String, txId: String, headerId: String, proofBytes: Array[Byte], index: Short)

  object ExtractedInput {
    def apply(inputBox: Input, index: Short, txId: String, header: Header): ExtractedInputModel = {
      ExtractedInputModel(Base16.encode(inputBox.boxId), txId, header.id, inputBox.spendingProof.proof, index)
    }
  }

  case class ExtractedDataInputModel(boxId: String, txId: String, headerId: String, index: Short)

  object ExtractedDataInput {
    def apply(dataInput: DataInput, index: Short, txId: String, header: Header): ExtractedDataInputModel = {
      ExtractedDataInputModel(Base16.encode(dataInput.boxId), txId, header.id, index)
    }
  }

  case class ExtractionOutputResultModel(extractedOutput: ExtractedOutputModel, extractedRegisters: Seq[ExtractedRegisterModel], extractedAssets: Seq[ExtractedAssetModel], extractedTransaction: ExtractedTransactionModel, extractedDataInput: Seq[ExtractedDataInputModel])

  object ExtractionOutputResult {
    def apply(ergoBox: ErgoBox, header: Header, tx: ErgoTransaction): ExtractionOutputResultModel = {
      val extractedOutput = ExtractedOutput(ergoBox, header)
      val extractedRegisters = ergoBox.additionalRegisters.map(
        register => ExtractedRegister(register, ergoBox)
      )
      val extractedAssets = ergoBox.tokens.zipWithIndex.map {
        case (token, index) =>
          ExtractedAsset(token, ergoBox, header.id, index.toShort)
      }
      val extractedTransaction = ExtractedTransaction(tx, header)
      val extractedDataInputs = tx.dataInputs.zipWithIndex.map {
        case (dataInput, index) => ExtractedDataInput(dataInput, index.toShort, tx.id, header)
      }
      ExtractionOutputResultModel(extractedOutput, extractedRegisters.toSeq, extractedAssets.toSeq, extractedTransaction, extractedDataInputs)
    }
  }

  case class ExtractionInputResultModel(extractedInput: ExtractedInputModel, extractedTransaction: ExtractedTransactionModel, extractedDataInput: Seq[ExtractedDataInputModel])

  object ExtractionInputResult {
    def apply(input: Input, index: Short, header: Header, tx: ErgoTransaction): ExtractionInputResultModel = {
      val extractedInput = ExtractedInput(input, index, tx.id, header)
      val extractedTransaction = ExtractedTransaction(tx, header)
      val extractedDataInputs = tx.dataInputs.zipWithIndex.map {
        case (dataInput, index) => ExtractedDataInput(dataInput, index.toShort, tx.id, header)
      }
      ExtractionInputResultModel(extractedInput, extractedTransaction, extractedDataInputs)
    }
  }

  case class ExtractionResultModel(extractedInputs: Seq[ExtractionInputResultModel], createdOutputs: Seq[ExtractionOutputResultModel])

  case class StealthModel(stealthId: String, secret: BigInt, stealthName: String)

  object Stealth {
    def apply(stealthId: String, secret: BigInt, stealthName: String): StealthModel = {
      StealthModel(stealthId,secret, stealthName)
    }
  }

  case class StealthAddressModel(stealthId: String, stealthName: String, address: String, value: Long)

  object StealthAddress {
    def apply(stealthId: String, stealthName: String, address: String, value: Long): StealthAddressModel = {
      StealthAddressModel(stealthId, stealthName, address, value)
    }
  }

  case class StealthSpendAddress(boxId: String, address: String)
  case class SpendBoxModel(box: ErgoBox, spendAddress: String, stealthId: String)
  object SpendBox {
    def apply(box: ExtractedOutputModel): SpendBoxModel = {
      SpendBoxModel(ErgoBoxSerializer.parseBytes(box.bytes), box.spendAddress, box.stealthId)
    }
  }
}
