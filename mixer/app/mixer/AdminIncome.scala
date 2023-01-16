package mixer

import config._
import helpers.ErgoMixerUtils
import mixinterface.TokenErgoMix
import network.NetworkUtils
import org.ergoplatform.appkit._
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.collection.mutable

// TODO: need write unit test (#81)
@Singleton
class AdminIncome @Inject()(networkUtils: NetworkUtils, ergoMixerUtils: ErgoMixerUtils) {
  private val logger: Logger = Logger(this.getClass)
  private var prover: ErgoProver = _

  /**
   * handle income boxes
   */
  def handleIncome(): Unit = {
      networkUtils.usingClient(implicit ctx => {
      if (prover == null) prover = ctx.newProverBuilder()
        .withDLogSecret(AdminConfigs.incomeSecret.bigInteger)
        .build()

      AdminConfigs.tokens = networkUtils.getTokenEmissionBoxes(0)
      AdminConfigs.fees = networkUtils.getFeeEmissionBoxes
      var incomes = networkUtils.getIncomeBoxes
      var sm = 0L
      while (sm < AdminConfigs.keepErg && incomes.nonEmpty) {
        sm += incomes.head.getValue
        incomes = incomes.drop(1)
      }

      try {
          if (incomes.length >= AdminConfigs.minBoxToMerge) handleMerge(incomes)
      } catch {
        case a: Throwable =>
          logger.error(s" [Admin Income: An error occurred. Stacktrace below")
          logger.error(ergoMixerUtils.getStackTraceStr(a))
      }
    })
  }

  /**
   * merge income boxes to incomeCovert Address
   * @param incomes income boxes
   * @param ctx
   */
  def handleMerge(incomes: Seq[InputBox])(implicit ctx: BlockchainContext): Unit = {
    logger.info(s"Going to send ${incomes.length} income boxes to income covert address")
    var rem = incomes
    while (rem.nonEmpty) {
      val ins = rem.slice(0, AdminConfigs.maxBoxToMerge)
      rem = rem.drop(AdminConfigs.maxBoxToMerge)
      var sm: Long = 0
      val tokenChange = mutable.Map.empty[String, Long]
      ins.foreach(in => {
          sm += in.getValue
          in.getTokens.forEach(token => {
              tokenChange(token.getId.toString) = tokenChange.getOrElse(token.getId.toString, 0L) + token.getValue
          })
      })
      if (sm >= MainConfigs.minPossibleErgInBox + AdminConfigs.fee) {
        val tokens = tokenChange.map(token => new ErgoToken(token._1, token._2)).toSeq

        val txB = ctx.newTxBuilder()
        val changeB = txB.outBoxBuilder()
          .contract(ctx.newContract(AdminConfigs.incomeCovert.getErgoAddress.script))
          .value(sm - AdminConfigs.fee)
        val change = {
          if (tokens.nonEmpty) changeB.tokens(tokens: _*).build()
          else changeB.build()
        }

        val signed = prover.sign(txB.addInputs(ins: _*)
          .addOutputs(change)
          .fee(AdminConfigs.fee)
          .sendChangeTo(TokenErgoMix.mixerIncome)
          .build())
        val signedTx = ctx.sendTransaction(signed)
        logger.info(s"send income to covert address transaction sent. response: $signedTx")
      }
    }
  }
}
