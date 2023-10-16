package testHandlers

import mocked.MockedStealthContract
import models.StealthModels.{CreateExtractedOutput, ExtractedOutput, Stealth, TokenInformation}
import org.ergoplatform.appkit.{Address, JavaHelpers}
import sigmastate.Values.ErgoTree
import stealth.StealthContract
import wallet.WalletHelper

object StealthDataset extends DatasetSuite {

  private val stealth1 = (
    Stealth(
      "3262fe47-e172-48fd-9739-7c5aae2af4d3",
      "stealthName1",
      "stealth6bus7wSp1yXPDDTwGjFjQxjhvfeeMScyR7VGhWchDVNp8SfSLU",
      BigInt("113430735202492609318901290987979663709125611447056267020845245465308921545278")
    ),
    WalletHelper.getAddress(
      "6QBPS6hEZW45RaTip4LQAZTt9PAXmEFsji4gJ44WrpwWoZxjRncumYocJGNH2hYrLzEzzpc2jTUDNEFcFv5MokksBvH4EPooNuMwv3DPZ3fvm1RuQNxAT1fvtCp35rJoxf7Wu2PoPQHDH11xDdEKxwxFpxTH9tBDgWrWVGd1QHvvaAuiVGnNCCJSCzemFZZiRXfnn49q8WqeLhX7AuboWMrSCf"
    ),
    "02e1c04e5f52fd1831eb0febab6149886da8f52a35860e98c14cee5b06847efc8e"
  )

  private val stealth2 = (
    Stealth(
      "265feb86-3f45-4031-bff7-58eb316adc3c",
      "stealthName2",
      "stealth746DSSuVvTUmkivURdkDn3P3dMpVkJy5qyukEk2kcRBHHFoeZn",
      BigInt("17892650072285699907798394529163405137002300467899637219303423790115050487391")
    ),
    WalletHelper.getAddress(
      "6QBPS6hGeitm2qeD8RHCZ8kMUL6xZVef56FWS4QgJUtxq5pqY7CBNELTUztf1FQua1ojyZ8j2kyZrQPLrQRiV3DWPR82i1MSGbeHeYk2scvnixfKiXkrih59TKKmyELjUumkt91Ebw1a7QW43CegJ4gR9o9W9xBjvn3Wj51tg8xc9yUZZjA62a25DjvnW2YKpMHjK1SMpgpGmGmYvqYeWNGRSL"
    )
  )

  private val wrongStealthAddress = "resYtT5NvNhx3Mb6SWwaf72K8UY5NPiHekTpszr8xBFK"

  private val wrongChecksumAddress = "stealthveuY71ukTsFgvDrmDHChCg8BoKVNRM1YfCWQZxhaZMwJ"

  private val fakeStealthId = "111aaa86-3f45-1020-bff7-58eb316adc1c"

  private val fakeStealthErgoTree = JavaHelpers.decodeStringToErgoTree(
    "1008040808cd022939465f6dc8c012f03c516ae46139ae94e10f07b756b4cc4ca187dd4adf1c6d08cd02322b48fb352c43640e7cf6cd6593016d626561c17ce7043029f79f896c2cffbe08cd023bb846ce6ebb823f4ad8ea18f1341c80d865e5fd48776c2288bcba9966e7d9c208cd028e89ab3c5bc3f9d34d69be8f0cc26f7e7b9bb3013b79b4aa4f1e90fc60b5330408cd0315da9f64e482476e8ada2f2df1e8c96379b0cf2435137bb18c13cc977d65f8da08cd0323d6a239c2de21b21d21cd7c6d7761a29dae755564e75d4972cf8d06807c55df08cd035af1af52f0e11408674ff3e6013ef2aa382c80909c88902e7b1ff8e04c7514339873008307087301730273037304730573067307"
  )

  private val specTokenId       = "03faf2cb329f2e90d6d23b58d91bbb6c046aa143261cc21f52fbe2824bfcbf04"
  private val specTokenName     = "testToken"
  private val specTokenDecimals = 3

  private val spendAddress1 = "9f4bRuh6yjhz4wWuz75ihSJwXHrtGXsZiQWUaHSDRf3Da16dMuf"

  private val spendAddress2 = "9hUjrNWLTXBU4qkGSA6ssCG8Fe7WpPKT5HW4E5zUr3YJ1HSo1rB"

  private val wrongWithdrawAddress = "resYtT5NvNhx3Mb6SWwaf72K8UY5NPiHekTpszr8xBFK"

  def stealthSpecData: ((Stealth, Address, String), (Stealth, Address)) = (stealth1, stealth2)

  def fakeStealthIdSpecData: String = fakeStealthId

  def fakeStealthErgoTreeSpecData: ErgoTree = fakeStealthErgoTree

  def wrongAddressSpecData: String = wrongStealthAddress

  def wrongChecksumAddressSpecData: String = wrongChecksumAddress

  def extractedOutputs(stealthObj: StealthContract, mockedErgValueInBoxes: Long): (
    ExtractedOutput,
    ExtractedOutput,
    ExtractedOutput,
    ExtractedOutput,
    ExtractedOutput
  ) = {
    val mockedStealthContract = new MockedStealthContract

    var extractedOutput = CreateExtractedOutput(
      mockedStealthContract
        .createFakeStealthBox(stealthObj.generatePaymentAddressByStealthAddress(stealth1._1.pk), mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )
    val extractedOutput1 = extractedOutput.copy(stealthId = Option(stealth1._1.stealthId))

    extractedOutput = CreateExtractedOutput(
      mockedStealthContract
        .createFakeStealthBox(stealthObj.generatePaymentAddressByStealthAddress(stealth1._1.pk), mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )
    val extractedOutput2 = extractedOutput.copy(
      stealthId       = Option(stealth1._1.stealthId),
      withdrawAddress = Option(spendAddress1),
      withdrawTxId    = Option(mockedStealthContract.randomId())
    )

    extractedOutput = CreateExtractedOutput(
      mockedStealthContract
        .createFakeStealthBox(stealthObj.generatePaymentAddressByStealthAddress(stealth2._1.pk), mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )
    val extractedOutput3 = extractedOutput.copy(stealthId = Option(stealth2._1.stealthId))

    extractedOutput = CreateExtractedOutput(
      mockedStealthContract
        .createFakeStealthBox(stealthObj.generatePaymentAddressByStealthAddress(stealth1._1.pk), mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )
    val extractedOutput4 = extractedOutput.copy(stealthId = Option(stealth1._1.stealthId))

    extractedOutput = CreateExtractedOutput(
      mockedStealthContract.createFakeStealthBox(
        stealthObj.generatePaymentAddressByStealthAddress(stealth1._1.pk),
        mockedErgValueInBoxes,
        fakeTokenId = specTokenId
      ),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )
    val extractedOutput5 = extractedOutput.copy(stealthId = Option(stealth1._1.stealthId))

    (extractedOutput1, extractedOutput2, extractedOutput3, extractedOutput4, extractedOutput5)
  }

  def extractedOutputsWithoutStealthId(stealthObj: StealthContract, mockedErgValueInBoxes: Long): (
    ExtractedOutput,
    ExtractedOutput,
    ExtractedOutput,
    ExtractedOutput
  ) = {
    val mockedStealthContract = new MockedStealthContract

    val commonStealthAddress = stealthObj.generatePaymentAddressByStealthAddress(stealth2._1.pk)
    val extractedOutput1 = CreateExtractedOutput(
      mockedStealthContract.createFakeStealthBox(commonStealthAddress, mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )

    val extractedOutput2 = CreateExtractedOutput(
      mockedStealthContract.createFakeStealthBox(commonStealthAddress, mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )

    val extractedOutput3 = CreateExtractedOutput(
      mockedStealthContract
        .createFakeStealthBox(stealthObj.generatePaymentAddressByStealthAddress(stealth2._1.pk), mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )

    val fakeStealthAddress =
      "6QBPS6hESaBgRZqWwMPtBXrzTaBn6gFCraKTuH7ABpj2UdBZaKp51LWGxiwLrnyKrY7G5kakpH7Phh9J6oqnJnbPzJtiA8jUXJxfHAMt5BA15XpPmap7AcKz4PgkdgAEkppDX3DCC1ohvSTqxuMRHqMUVZeunMzJCxfWg8XHSas3jzdENZ1h5RQnxJ9iKcKR1AicgRwcNxsvuCz64dNWMasySd"
    val extractedOutput4 = CreateExtractedOutput(
      mockedStealthContract.createFakeStealthBox(fakeStealthAddress, mockedErgValueInBoxes),
      mockedStealthContract.randomId(),
      WalletHelper.now
    )

    (extractedOutput1, extractedOutput2, extractedOutput3, extractedOutput4)
  }

  def spendAddressesSpecData: (String, String) = (spendAddress1, spendAddress2)

  def wrongWithdrawAddressSpecData: String = wrongWithdrawAddress

  def tokenIdSpecData: String = specTokenId

  def tokenInformationSpecData: TokenInformation =
    TokenInformation(specTokenId, Some(specTokenName), Some(specTokenDecimals))
}
