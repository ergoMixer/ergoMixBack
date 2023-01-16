package config

import helpers.ConfigHelper
import models.Models.EntityInfo
import org.ergoplatform.appkit.{Address, InputBox, SignedTransaction}

object AdminConfigs extends ConfigHelper {
    // intervals
    lazy val paramInterval: Long = readKey("admin.paramInterval", 600L.toString).toLong
    lazy val chargeInterval: Long = readKey("admin.chargeInterval", 100L.toString).toLong
    lazy val incomeInterval: Long = readKey("admin.incomeInterval", (3600 * 5).toString).toLong

    // admin panel params
    var fees: Seq[InputBox] = Seq()
    var tokens: Seq[InputBox] = Seq()
    var supports: Seq[InputBox] = Seq()

    var desiredFee: (Long, Long) = (0L, 0L) // (max fee, dynamic fee)

    var commissionFee: Int = 0 // for example 200 for 0.5% (100/200)
    var tokenLevels: Seq[(Int, Long)] = Seq() // (level, price)

    var toAddSupport: Seq[(EntityInfo, SignedTransaction)] = Seq() // (entity info to add support for, transaction responsible for adding the support)
    var toRemoveSupport: Seq[EntityInfo] = Seq() // box id of support box

    // params
    lazy val fee: Long = readKey("admin.fee", "10000000").toLong // fee to pay for admin related txs
    lazy val feeThreshold: Long = readKey("admin.feeThreshold", 2e7.toLong.toString).toLong // fee boxes must be bigger than this or they'll be charged
    lazy val tokenThreshold: Long = readKey("admin.tokenThreshold", 500.toString).toLong // token boxes' token amount must be bigger than this or they'll be charged
    lazy val feeCharge: Long = readKey("admin.feeCharge", 7e8.toLong.toString).toLong // fee charge amount
    lazy val tokenCharge: Long = readKey("admin.tokenCharge", 1e4.toLong.toString).toLong // token charge amount
    lazy val supportBoxValue: Long = readKey("admin.supportBoxValue", 1e8.toLong.toString).toLong // minimum needed Erg in each support Box
    lazy val maxTokenBoxInInput: Int = readKey("admin.maxTokenBoxesInInput", 3.toString).toInt // will charge at most this number of token emission boxes in one tx
    lazy val maxFeeBoxInInput: Int = readKey("admin.maxFeeBoxesInInput", 5.toString).toInt // will charge or update registers at most this number of boxes boxes in one tx
    lazy val minBoxToMerge: Int = readKey("admin.minBoxToMerge", 2.toString).toInt // min number of boxes to merge for sending to income covert address
    lazy val maxBoxToMerge: Int = readKey("admin.maxBoxToMerge", 15.toString).toInt // max number of boxes to merge for sending to income covert address
    lazy val maxTxToStat: Int = readKey("admin.maxTxToStat", 150.toString).toInt // max number of txs to fetch from explorer for calculate Mixer's income
    lazy val maxRetryToCalStat: Int = readKey("admin.maxRetryToCalStat", 5.toString).toInt // max number of retry to calculate token and commission income stats
    lazy val keepErg: Long = readKey("admin.keepErg", 20e9.toLong.toString).toLong // will keep this amount of ergs in income address always

    lazy val incomeCovert: Address = Address.create(readKey("admin.incomeCovert")) // convert address to send incomes to

    // secrets
    lazy val incomeSecret: BigInt = BigInt(readKey("admin.incomeSecret"), 16)
    lazy val ownerSecret: BigInt = BigInt(readKey("admin.ownerSecret"), 16)
    lazy val paramSecret: BigInt = BigInt(readKey("admin.paramSecret"), 16)
}
