/* root package */

import cli.{Alice, AliceOrBob, Bob, Carol, ErgoMixCLIUtil}
import cli.ErgoMixCLIUtil._
import mixer.BlockExplorer

/*
Usage
First compile jar using sbt assembly then use as follows:

java -cp <jarfile> GetFeeEmissionBoxes
java -cp <jarfile> GetHalfMixBoxes
java -cp <jarfile> GetSpendingTx                             --boxId xyz

java -cp <jarfile> ProveDlogAddress                          --secret 123 --url http://88.198.13.202:9053/ --mainnet true
java -cp <jarfile> NewFeeEmissionBox                         --secret 123 --amount 1234      --inputBoxId abc --inputBoxId def --changeAddress uvw --dLogSecret 123 --url http://88.198.13.202:9053/ --mainNet true

java -cp <jarfile> AliceEntry                                --secret 123                    --inputBoxId abc --inputBoxId def --changeAddress uvw --dLogSecret 123 --url http://88.198.13.202:9053/ --mainnet true
java -cp <jarfile> BobEntry                                  --secret 123 --halfMixBoxId abc --inputBoxId abc --inputBoxId def --changeAddress uvw --dLogSecret 123 --url http://88.198.13.202:9053/ --mainNet true

java -cp <jarfile> FullMixBoxWithdraw       --mode alice|bob --secret 123 --fullMixBoxId xyz --withdrawAddress uvw --url http://88.198.13.202:9053/ --mainNet true

java -cp <jarfile> FullMixBoxRemixNextAlice --mode alice|bob --secret 123 --fullMixBoxId xyz --nextSecret 456 --feeEmissionBoxId abc --url http://88.198.13.202:9053/ --mainNet true
java -cp <jarfile> FullMixBoxRemixNextBob   --mode alice|bob --secret 123 --fullMixBoxId xyz --nextSecret 456 --nextHalfMixBoxId uvw --feeEmissionBoxId abc --url http://88.198.13.202:9053/ --mainNet true
*/

object GetBoxById {
  def main(args:Array[String]):Unit = {
    implicit val parsedArgs = parseArgsNoSecret(args)
    val boxId = getArg("boxId")
    usingClient { implicit ctx =>
      ErgoMixCLIUtil.getUnspentBoxById(boxId)
    }
  }
}

object GetFeeEmissionBoxes {
  def main(args:Array[String]):Unit = {
    parseArgsNoSecret(args)
    usingClient { implicit ctx =>
      ErgoMixCLIUtil.getFeeEmissionBoxes() foreach println
    }
  }
}

object GetHalfMixBoxes {
  def main(args:Array[String]):Unit = {
    parseArgsNoSecret(args)
    usingClient { implicit ctx =>
      val blockExplorer = new BlockExplorer
      ErgoMixCLIUtil.getHalfMixBoxes() foreach println
    }
  }
}

object GetSpendingTx {
  def main(args:Array[String]):Unit = {
    implicit val l = parseArgsNoSecret(args)
    usingClient { implicit ctx =>
      val blockExplorer = new BlockExplorer
      val boxId = getArg("boxId")
      println(blockExplorer.getSpendingTxId(boxId).getOrElse("Not spent"))
    }
  }
}

object ProveDlogAddress {
  def main(args:Array[String]):Unit = {
    val (a, secret) = parseArgs(args)
    println(Carol.getProveDlogAddress(secret))
  }
}

object NewFeeEmissionBox {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val amount: Long = getArg("amount").toLong
    val inputBoxIds: Seq[String] = getArgs("inputBoxId")
    val changeAddress = getArg("changeAddress")
    val dLogSecret = getArg("dLogSecret")
    val tx = Carol.createFeeEmissionBox(secret, amount, inputBoxIds.toArray, changeAddress, dLogSecret)
    println(tx(0))
    println
    println("Emission box address "+tx(1))
  }
}

object AliceEntry {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val inputBoxIds: Seq[String] = getArgs("inputBoxId")
    val changeAddress = getArg("changeAddress")
    val dLogSecret = getArg("dLogSecret")
//    val tx = Alice.createHalfMixBox(secret, inputBoxIds.toArray, changeAddress, dLogSecret)
//    println(tx)
  }
}

object BobEntry {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val halfMixBoxId = getArg("halfMixBoxId")
    val inputBoxIds: Seq[String] = getArgs("inputBoxId")
    val changeAddress = getArg("changeAddress")
    val dLogSecret = getArg("dLogSecret")
    val tx = Bob.spendHalfMixBox(secret, halfMixBoxId, inputBoxIds.toArray, changeAddress, dLogSecret)
    println(tx)
  }
}

object FullMixBoxWithdraw {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val fullMixBoxId = getArg("fullMixBoxId")
    val withdrawAddress = getArg("withdrawAddress")
    val tx = AliceOrBob.spendFullMixBox(isAlice, secret, fullMixBoxId, withdrawAddress)
    println(tx)
  }
}

object FullMixBoxRemixAsAlice {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val fullMixBoxId = getArg("fullMixBoxId")
    val feeEmissionBoxId = getArg("feeEmissionBoxId")
    val nextSecret = BigInt(getArg("nextSecret"), 10)
    val halfMixTx = AliceOrBob.spendFullMixBox_RemixAsAlice(isAlice, secret, fullMixBoxId, nextSecret, feeEmissionBoxId)
    val tx = halfMixTx.tx.toJson(false)
    println(tx)
  }
}

object FullMixBoxRemixAsBob {
  def main(args:Array[String]):Unit = {
    implicit val (a, secret) = parseArgs(args)
    val fullMixBoxId = getArg("fullMixBoxId")
    val halfMixBoxId = getArg("halfMixBoxId")
    val feeEmissionBoxId = getArg("feeEmissionBoxId")
    val nextSecret = BigInt(getArg("nextSecret"), 10)
    val (fullMixTx, bit) = AliceOrBob.spendFullMixBox_RemixAsBob(isAlice, secret, fullMixBoxId, nextSecret, halfMixBoxId, feeEmissionBoxId)
    println(fullMixTx.tx.toJson(false))
    println
    println("secret bit = "+bit)
  }
}
