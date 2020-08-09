package org.ergoplatform.appkit

import org.ergoplatform.appkit.Mnemonic._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{PropSpec, Matchers}

class MnemonicSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  property("generate") {
    val entropy = Array[Byte](-10, -55, 58, 24, 3, 117, -34, -15, -7, 81, 116, 5, 50, -84, 3, -94, -70, 73, -93, 45)
    val mnemonic = Mnemonic.generate("english", DEFAULT_STRENGTH, entropy)
    mnemonic shouldBe "walnut endorse maid alone fuel jump torch company ahead nice abstract earth pig spice reduce"
  }

  property("entropy") {
    val entropy = getEntropy(DEFAULT_STRENGTH)
    entropy.length shouldBe 20
  }
}

