package models

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import models.StealthModels.{ExtractedOutputController, Stealth, TokenInformation}
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import org.ergoplatform.JsonCodecs
import scorex.util.ModifierId

trait StealthTrait extends JsonCodecs {

  implicit val encodeTokenInformation: Encoder[(Seq[TokenInformation], Map[ModifierId, Long])] =
    (a: (Seq[TokenInformation], Map[ModifierId, Long])) =>
      a._1.map { tokenInformation =>
        val amount = a._2(ModifierId(tokenInformation.id))
        tokenInformation.asJson.deepMerge(
          Json.obj(
            "amount" -> Json.fromLong(amount)
          )
        )
      }.asJson

  implicit val encodeExtractedOutputController: Encoder[ExtractedOutputController] = (a: ExtractedOutputController) => {
    val extractedOutput = a.extractedOutput
    val ergoBox         = ErgoBoxSerializer.parseBytes(a.extractedOutput.bytes)
    ergoBox.asJson.deepMerge(
      Json.obj(
        "withdrawAddress" -> Json.fromString(extractedOutput.withdrawAddress.getOrElse("")),
        "withdrawTxId" -> Json.fromString(
          extractedOutput.spendTxId.getOrElse(extractedOutput.withdrawTxId.getOrElse(""))
        ),
        "withdrawFailedReason" -> Json.fromString(extractedOutput.withdrawFailedReason.getOrElse("")),
        "assets"               -> (a.tokensInformation, ergoBox.tokens).asJson
      )
    )
  }

  implicit val encodeStealth: Encoder[Stealth] = (a: Stealth) =>
    Json.obj(
      "stealthId"   -> Json.fromString(a.stealthId),
      "stealthName" -> Json.fromString(a.stealthName),
      "pk"          -> Json.fromString(a.pk),
      "secret"      -> Json.fromString(a.secret.toString(16))
    )
}
