package org.ergoplatform.appkit

import java.io.File
import com.google.common.io.Files
import org.scalatest.{PropSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class SecretStorageSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
    with AppkitTesting {
  val mnemonic = Mnemonic.create("phrase".toCharArray, "mnemonic pass".toCharArray)
  val encryptionPass = "encryption pass"

  property("create from mnemonic") {
    withNewStorageFor(mnemonic, encryptionPass) { storage =>
      storage.isLocked shouldBe true
      storage.getFile().exists() shouldBe true
    }
  }

  property("unlocked by password with ") {
    withNewStorageFor(mnemonic, encryptionPass) { storage =>
      storage.unlock(encryptionPass)
      storage.isLocked shouldBe false
      val addr = Address.fromMnemonic(NetworkType.TESTNET, mnemonic)
      val secret = storage.getSecret()
      secret should not be(null)
      val expSecret = JavaHelpers.seedToMasterKey(mnemonic.getPhrase, mnemonic.getPassword)
      expSecret shouldBe secret
      storage.getAddressFor(NetworkType.TESTNET) shouldBe addr
    }
  }

  property("not unlock by wrong password") {
    a[RuntimeException] shouldBe thrownBy {
      withNewStorageFor(mnemonic, encryptionPass) { storage =>
        storage.unlock("wrong password")
      }
    }
  }

  property("load from file") {
    withNewStorageFor(mnemonic, encryptionPass) { storage =>
      val fileName = storage.getFile.getPath
      val loaded = SecretStorage.loadFrom(fileName)
      loaded.isLocked shouldBe true
      loaded.unlock(encryptionPass)
      loaded.isLocked shouldBe false

      // compare created and loaded storages
      storage.unlock(encryptionPass)
      storage.getSecret shouldBe loaded.getSecret
      storage.getAddressFor(NetworkType.TESTNET) shouldBe loaded.getAddressFor(NetworkType.TESTNET)
    }
  }

  def withNewStorageFor(mnemonic: Mnemonic, encryptionPass: String)(block: SecretStorage => Unit): Unit = {
    withTempDir { dir =>
      val dirPath = dir.getPath
      val storage = SecretStorage.createFromMnemonicIn(dirPath, mnemonic, encryptionPass)
      try {
        block(storage)
      }
      finally {
        storage.getFile.delete() shouldBe true
      }
    }
  }

  def withTempDir(block: File => Unit): Unit = {
    val dir = Files.createTempDir()
    try {
      block(dir)
    }
    finally {
      dir.delete() shouldBe true
    }
  }
}
