package mixer
import scala.collection.mutable

object Stats {
  var tokenPrices: Option[Map[Int, Long]] = None
  var entranceFee: Option[Int] = None
  var ringStats = mutable.Map.empty[String, mutable.Map[Long, mutable.Map[String, Long]]]
}
