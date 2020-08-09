package mixer

import java.security.SecureRandom

import mixer.Models.{FBox, HBox}
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import cli.ErgoMixCLIUtil.usingClient
import cli.{Carol, ErgoMixCLIUtil}
import app.{ErgoMix, Util => EUtil}
import app.Configs

object ErgoMixerUtil {

  // The minimum number of confirmations for a current mix transaction before proceeding to next step
  val minConfirmations: Int = Configs.numConfirmation

  def isDoubleSpent(boxId:String, wrtBoxId:String): Boolean = ErgoMixCLIUtil.usingClient{ implicit ctx =>
    val explorer: BlockExplorer = new BlockExplorer()
    explorer.getSpendingTxId(boxId).flatMap { txId =>
      // boxId has been spent, while the fullMixBox generated has zero confirmations. Looks like box has been double-spent elsewhere
      explorer.getTransaction(txId).map{tx =>
        // to be sure, get the complete tx and check that none if its outputs are our fullMixBox
        !tx.outboxes.map(_.id).contains(wrtBoxId)
      }
    }
  }.getOrElse(false)

  def getRandomValidBoxId(origBoxIds:Seq[String]) = ErgoMixCLIUtil.usingClient{ implicit ctx =>
    val random = new SecureRandom()
    val boxIds = new scala.util.Random(random).shuffle(origBoxIds)
    boxIds.find{boxId =>
      try {
        ctx.getBoxesById(boxId)
        true
      } catch{
        case a:Throwable =>
          println(s"      Error reading boxId ${boxId}: "+a.getMessage)
          false
      }
    }
  }

  def generateWithdrawAddress = {
    val secret = Util.randBigInt
    val address = Carol.getProveDlogAddress(secret)
    Array(
      "The following rows contain the secret and the withdraw address. Please save the secret as it is not stored",
      secret.toString,
      address
    )

  }

  def createFeeEmissionBox(inputBoxIds:Array[String], dlogSecret:BigInt, amount:Long, feeEmissionBoxSecret:BigInt, changeAddress:String) = {
    ErgoMixCLIUtil.usingClient { implicit ctx =>
      val arr = Carol.createFeeEmissionBox(feeEmissionBoxSecret, amount, inputBoxIds, changeAddress, dlogSecret.toString())
      arr(1)
    }
  }

  def send(inputBoxIds:Array[String], dlogSecret:BigInt, outputAddress:String, amount:Long) = {
    ErgoMixCLIUtil.usingClient{implicit ctx =>
      val inputBoxes = ctx.getBoxesById(inputBoxIds: _*)
      val txB = ctx.newTxBuilder()
      val util = new EUtil()
      val outBox = txB.outBoxBuilder().value(amount).contract(new ErgoTreeContract(util.getAddress(outputAddress).script)).build()

      val inputs = new java.util.ArrayList[InputBox]()
      inputBoxes.foreach(inputs.add)
      val txToSign = txB.boxesToSpend(inputs)
        .outputs(outBox)
        .fee(ErgoMix.feeAmount)
        .sendChangeTo(util.getAddress(Carol.getProveDlogAddress(dlogSecret)))
        .build()

      val sender = ctx.newProverBuilder().withDLogSecret(dlogSecret.bigInteger).build()
      val tx = sender.sign(txToSign)
      ctx.sendTransaction(tx)
      tx.toJson(false)
    }
  }

  def getBoxById(boxId:String) = usingClient{implicit ctx =>
    val explorer = new BlockExplorer()
    explorer.getBoxById(boxId)
  }

  def getMixBox(boxId:String): Option[Either[HBox, FBox]] = getBoxById(boxId).mixBox

  /* General util methods, for testing or debugging

  def getUnspentBoxWithId(boxId:String) = {
    ErgoMixCLIUtil.usingClient{implicit ctx =>
      ctx.getBoxesById(boxId).headOption.map(box => new EUtil().getAddressFromProposition(box.getErgoTree).toString)
    }
  }

  def getConfirmationsForBoxId(boxId:String) = {
    ErgoMixCLIUtil.getConfirmationsForBoxId(boxId)
  }

  def getSpendingTxId(boxId:String) = {
    ErgoMixCLIUtil.getSpendingTxId(boxId)
  }

  def getTransactionOutputs(txId:String) = {
    ErgoMixCLIUtil.getTransactionOutputs(txId).getOrElse(Nil)
  }

  def getBoxById(boxId:String) = {
    ErgoMixCLIUtil.getBoxById(boxId).toJson(false)
  }
   */

}

