package helpers

trait ErrorHandler {
  final case class NotFoundException(private val message: String = "object not found") extends Exception(message)

  case class NotEnoughErgException(minErg: Long, value: Long)
    extends Throwable(
      s"Not enough ERG for transaction fee. Selected boxes have ${new java.math.BigDecimal(value)
          .divide(new java.math.BigDecimal(1e9))
          .toPlainString} ERG (Required: ${new java.math.BigDecimal(minErg)
          .divide(new java.math.BigDecimal(1e9))
          .toPlainString} ERG).\nSelect and withdraw more boxes with enough ERG to the same withdraw address."
    )

  /**
   * exception handling for get any object from db
   *
   * @param inp
   * @tparam T
   * @return
   */
  def notFoundHandle[T](inp: Option[T]): T =
    if (inp.isDefined) inp.get else throw NotFoundException()
}
