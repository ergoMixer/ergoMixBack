package mixer

class ErgoMixerJobs(tables:Tables) {
  val covertMixer = new CovertMixer(tables)
  val groupMixer = new GroupMixer(tables)
  val ergoMixer = new ErgoMixer(tables)
  val rescan = new Rescan(tables)
  val halfMixer = new HalfMixer(tables)
  val fullMixer = new FullMixer(tables)
  val newMixer = new NewMixer(tables)
  val withdrawMixer = new WithdrawMixer(tables)
  val deposits = new Deposits(tables)

  // stats
  val statScanner = new ChainScanner(tables)
}
