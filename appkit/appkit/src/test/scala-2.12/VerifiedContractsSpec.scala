import org.ergoplatform.Height
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.{BoolToSigmaProp, LT, SInt}
import sigmastate.Values.{ConstantPlaceholder, IntConstant}
import sigmastate.verification.contract.DummyContractCompilation

class VerifiedContractsSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks {

  property("get ErgoTree from verified contract") {
    val verifiedContract = DummyContractCompilation.contractInstance(1000)
    println(verifiedContract.ergoTree)
    verifiedContract.ergoTree.constants.length shouldBe 1
    verifiedContract.ergoTree.constants.head shouldEqual IntConstant(1000)
    verifiedContract.ergoTree.root.right.get shouldEqual
      BoolToSigmaProp(LT(Height, ConstantPlaceholder(0, SInt)))
  }

}
