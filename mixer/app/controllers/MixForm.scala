package controllers

object MixForm {
  import play.api.data.Forms._
  import play.api.data.Form

  /**
   * A form processing DTO that maps to the form below.
   *
   * Using a class specifically for form binding reduces the chances
   * of a parameter tampering attack and makes code clearer.
   */
  case class NewMixData(withdrawalAddress: String)

  /**
   * The form definition for the "new mix" form.
   * It specifies the form fields and their types,
   * as well as how to convert from a Data to form data and vice versa.
   */
  val form = Form(
    mapping(
      "withdrawalAddress" -> nonEmptyText
    )(NewMixData.apply)(NewMixData.unapply)
  )
}
