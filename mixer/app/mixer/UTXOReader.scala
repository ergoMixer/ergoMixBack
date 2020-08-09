package mixer

import Models.OutBox

trait UTXOReader {
  def getUnspentBoxes(address:String):Seq[OutBox]
}
