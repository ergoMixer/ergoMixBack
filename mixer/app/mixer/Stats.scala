package mixer

object Stats {
  var tokenPrices: Option[Map[Int, Long]] = None
  var entranceFee: Option[Int] = None
  var ringStats = collection.mutable.Map.empty[Long, collection.mutable.Map[String, Long]]
}
