package models

import app.Configs
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, parser}
import mixinterface.TokenErgoMix
import org.ergoplatform.appkit.{Address, ErgoToken, ErgoValue, InputBox}
import play.api.libs.json.{JsArray, JsResult, JsSuccess, JsValue, Reads}
import sigmastate.Values.ErgoTree
import special.sigma.GroupElement
import wallet.WalletHelper

import scala.collection.JavaConverters._
import scala.collection.mutable

object Box {

  case class InBox(id: String, address: String, createdTxId: String, value: Long)

  case class OutBox(id: String, amount: Long, registers: Map[String, String], ergoTree: String, tokens: Seq[ErgoToken], creationHeight: Int, address: String, spendingTxId: Option[String]) {
    def ge(regId: String): GroupElement = WalletHelper.hexToGroupElement(registers(regId).drop(2))

    def getToken(tokenId: String): Long = {
      tokens.filter(_.getId.toString.equals(tokenId)).map(_.getValue.longValue()).sum
    }

    def mixBox(tokenErgoMix: TokenErgoMix): Option[Either[HBox, FBox]] = {
      try {
        val fullMixBoxErgoTree = tokenErgoMix.fullMixScriptErgoTree.bytesHex
        val halfMixBoxErgoTree = tokenErgoMix.halfMixContract.getErgoTree.bytesHex
        ergoTree match {
          case `fullMixBoxErgoTree` =>
            Some(Right(FBox(id, ge("R4"), ge("R5"), ge("R6"))))
          case `halfMixBoxErgoTree` =>
            Some(Left(HBox(id, ge("R4"))))
          case _ =>
            None
        }
      } catch {
        case _: Throwable =>
          None
      }
    }

    def getFBox(tokenErgoMix: TokenErgoMix): Option[FBox] = mixBox(tokenErgoMix).flatMap {
      case Right(fBox) => Some(fBox)
      case _ => None
    }

    def isAddressEqualTo(address: String): Boolean = {
      val addressErgoTree = Address.create(address).getErgoAddress.script.bytesHex
      addressErgoTree == ergoTree
    }
  }

  // for scanning blockchain
  case class FBox(id: String, r4: GroupElement, r5: GroupElement, r6: GroupElement)

  case class HBox(id: String, r4: GroupElement)

  case class MixingBox(withdraw: String, amount: Long, token: Int, mixingTokenAmount: Long, mixingTokenId: String) {
    def price: (Long, Long) = {
      MixingBox.getPrice(amount, mixingTokenAmount, token)
    }
  }

  object MixingBox {

    /**
     * calculates needed token for a given ring
     *
     * @return token needed to enter mixing, i.e. ring + tokenFee
     */
    def getTokenPrice(ring: Long): Long = {
      val rate: Int = Configs.entranceFee.getOrElse(1000000)
      ring + (if (rate > 0 && rate < 1000000) ring / rate else 0)
    }

    /**
     * calculates needed amount with current fees for a specific mix box
     *
     * @param ergRing   erg ring of mix
     * @param tokenRing token ring of mix
     * @param mixRounds number of mixing rounds i.e. token num
     * @return (erg needed, token needed)
     */
    def getPrice(ergRing: Long, tokenRing: Long, mixRounds: Int): (Long, Long) = {
      val rate: Int = Configs.entranceFee.getOrElse(1000000)
      val tokenPrice: Long = Configs.tokenPrices.get.getOrElse(mixRounds, -1)
      assert(tokenPrice != -1)
      val ergVal = if (rate > 0 && rate < 1000000) ergRing / rate else 0
      (ergRing + Configs.startFee + tokenPrice + ergVal, getTokenPrice(tokenRing))
    }

    implicit val mixingBoxDecoder: Decoder[MixingBox] = deriveDecoder[MixingBox]

    def apply(jsonString: String): MixingBox = {
      parser.decode[MixingBox](jsonString) match {
        case Left(e) => throw new Exception(s"Error while parsing MixingBox from Json: $e")
        case Right(asset) => asset
      }
    }
  }

  case class MixBoxList(items: Iterable[MixingBox])

  object MixBoxList {
    implicit val ReadsMixBoxList: Reads[MixBoxList] = new Reads[MixBoxList] {
      override def reads(json: JsValue): JsResult[MixBoxList] = {
        JsSuccess(MixBoxList(json.as[JsArray].value.map(item => {
          val withdraw = (item \ "withdraw").as[String]
          val amount = (item \ "amount").as[Long]
          val token = (item \ "token").as[Int]
          val mixingTokenId = (item \ "mixingTokenId").as[String]
          val mixingTokenAmount = (item \ "mixingTokenAmount").as[Long]
          MixingBox(withdraw, amount, token, mixingTokenAmount, mixingTokenId)
        })))
      }
    }
  }

  case class EndBox(receiverBoxScript: ErgoTree, receiverBoxRegs: Seq[ErgoValue[_]] = Nil, value: Long, tokens: Seq[ErgoToken] = Nil) // box spending full mix box

  abstract class MixBox(inputBox: InputBox) {
    def getRegs: mutable.Seq[ErgoValue[_]] = inputBox.getRegisters.asScala

    def getR4: ErgoValue[_] = getRegs.head

    def getR5: ErgoValue[_] = getRegs(1)

    def getR6: ErgoValue[_] = getRegs(2)
  }

  case class HalfMixBox(inputBox: InputBox) extends MixBox(inputBox) {
    def id: String = inputBox.getId.toString

    val gX: GroupElement = getR4.getValue match {
      case g: GroupElement => g
      case any => throw new Exception(s"Invalid value in R4: $any of type ${any.getClass}")
    }
  }

  case class FullMixBox(inputBox: InputBox) extends MixBox(inputBox) {
    def id: String = inputBox.getId.toString

    val (r4, r5, r6) = (getR4.getValue, getR5.getValue, getR6.getValue) match {
      case (c1: GroupElement, c2: GroupElement, gX: GroupElement) => (c1, c2, gX) //Values.GroupElementConstant(c1), Values.GroupElementConstant(c2)) => (c1, c2)
      case (r4, r5, r6) => throw new Exception(s"Invalid registers R4:$r4, R5:$r5, R6:$r6")
    }
  }

}
