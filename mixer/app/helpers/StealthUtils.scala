package helpers

import scala.collection.mutable
import scala.util.matching.Regex

import config.MainConfigs
import models.StealthModels.{AssetFunds, Stealth, StealthDHTData}
import org.ergoplatform.appkit.{ErgoToken, InputBox}
import special.sigma.GroupElement
import wallet.WalletHelper

object StealthUtils extends ErrorHandler {

  /**
   * calculate total assets of input boxes
   *
   * @param inputList Seq[InputBox]
   * @return (ERG value, Ergo Tokens)
   */
  def getTotalAssets(inputList: Seq[InputBox]): (Long, mutable.Map[String, Long]) = {
    val tokenChange      = mutable.Map.empty[String, Long]
    var totalValue: Long = 0
    inputList.foreach { in =>
      totalValue += in.getValue
      in.getTokens.forEach { token =>
        tokenChange(token.getId.toString) = tokenChange.getOrElse(token.getId.toString, 0L) + token.getValue
      }
    }
    (totalValue, tokenChange)
  }

  /**
   * calculate user and service funds
   *
   * @param totalInputAssets (Long, mutable.Map[String, Long])
   * @return AssetFunds
   */
  def fundCalculator(totalInputAssets: (Long, mutable.Map[String, Long])): AssetFunds = {
    var userTokenFunds: Seq[ErgoToken]   = Seq.empty
    var systemTokenFunds: Seq[ErgoToken] = Seq.empty
    val minErg                           = 2 * MainConfigs.minPossibleErgInBox + MainConfigs.stealthFee
    if (totalInputAssets._1 < minErg) throw NotEnoughErgException(minErg, totalInputAssets._1)
    val systemErgFund =
      Math.max(totalInputAssets._1 * MainConfigs.stealthImplementorFeePercent / 10000, MainConfigs.minPossibleErgInBox)
    val userErgFund = totalInputAssets._1 - systemErgFund - MainConfigs.stealthFee
    totalInputAssets._2.foreach { token =>
      val implFee: Long = MainConfigs.stealthImplementorFeePercent * token._2 / 10000
      if (implFee > 0) {
        systemTokenFunds :+= new ErgoToken(token._1, implFee)
        userTokenFunds :+= new ErgoToken(token._1, token._2 - implFee)
      } else
        userTokenFunds :+= new ErgoToken(token._1, token._2)
    }
    AssetFunds((userErgFund, userTokenFunds), (systemErgFund, systemTokenFunds))
  }

  /**
   * create DHT data from ergo tree
   *
   * @param ergoTree String
   * @param start    Int
   * @param until    Int
   * @return GroupElement
   */
  def getDHTDataFromErgoTree(ergoTree: String, start: Int, until: Int): GroupElement =
    WalletHelper.hexToGroupElement(ergoTree.slice(start, until))

  /**
   * create DHT data from ergo tree
   *
   * @return StealthDHTData
   */
  def getDHTDataFromErgoTree(ergoTree: String): StealthDHTData = {
    val gr = StealthUtils.getDHTDataFromErgoTree(ergoTree, 8, 74)
    val gy = StealthUtils.getDHTDataFromErgoTree(ergoTree, 78, 144)
    val ur = StealthUtils.getDHTDataFromErgoTree(ergoTree, 148, 214)
    val uy = StealthUtils.getDHTDataFromErgoTree(ergoTree, 218, 284)
    StealthDHTData(gr, gy, ur, uy)
  }

  /**
   * create export for all stealth in db
   *
   * @return list of all stealth addresses with their private keys in csv format
   */
  def exportAllStealth(stealthObjs: Seq[Stealth]): Seq[String] = {
    val header = "name, address, secret"
    val keys   = stealthObjs.map(req => s"${req.stealthName}, ${req.pk}, ${req.secret.toString(16)}")
    Seq(header) ++ keys
  }
}

object RegexUtils {
  implicit class RichRegex(val underlying: Regex) extends AnyVal {
    def matches(s: String): Boolean = underlying.pattern.matcher(s).matches
  }
}
