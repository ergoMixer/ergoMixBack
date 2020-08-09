package mixer

import db.core.DataStructures.{BIGDEC, BLOB, BOOL, Col, UINT, ULONG, VARCHAR}

object Columns {

  val BIGSTR = VARCHAR(10000)
  val STR = VARCHAR(255)
  val BIGINT = BIGDEC(100, 0) // 100 digits which is approx 2^332, much higher than 2^256, which we need

  val insertReasonCol = Col("reason", STR) // for archive table, explaining why this row was inserted
  val addressCol = Col("address", BIGSTR) // max 10000 characters in an address allowed here
  val depositAddressCol = Col("deposit_address", STR) // deposit address will always be of type profeDlog, so 255 chars are enough
  val depositCompletedCol = Col("deposit_completed", BOOL) // indicates if the deposit is pending or completed
  val createdTimeCol = Col("created_time", ULONG) // unsigned long, epoch time millis

  val boxIdCol = Col("box_id", STR)
  val inputsCol = Col("inputs", BIGSTR)
  val tokenId = Col("token_id", STR)
  val amountCol = Col("amount", ULONG)
  val depositCol = Col("deposit_done", ULONG)
  val tokenDepositCol = Col("deposit_done_token", ULONG)
  val mixingAmount = Col("mixing_amount", ULONG)
  val mixingTokenAmount = Col("mixing_token_amount", ULONG)
  val mixingTokenNeeded = Col("mixing_token_needed", ULONG)
  val neededCol = Col("needed_amount", ULONG)
  val numTokenCol = Col("num_token", UINT)

  val txIdCol = Col("tx_id", STR)
  val purposeCol = Col("purpose", STR)
  val spentTimeCol = Col("spent_time", ULONG) // unsigned long, epoch time millis

  val numRoundsCol = Col("num_rounds", UINT)
  val chainOrderCol = Col("order_num", UINT)
  val mixGroupIdCol = Col("mix_group_id", STR)
  val mixIdCol = Col("mix_id", STR)
  val mixStatusCol = Col("status", STR)
  val masterSecretGroupCol = Col("master_secret_group", BIGINT)
  val mixWithdrawStatusCol = Col("withdraw_status", STR)
  val masterSecretCol = Col("master_secret", BIGINT)
  val withdrawAddressCol = Col("withdraw_address", BIGSTR)

  val roundCol = Col("round", UINT)
  val isAliceCol = Col("is_alice", BOOL) // if not Alice then it is Bob

  val fullMixBoxIdCol = Col("full_mix_box_id", STR)
  val halfMixBoxIdCol = Col("half_mix_box_id", STR)
  val isSpentCol = Col("is_spent", BOOL)

  val txCol = Col("tx", BLOB)

  val goBackwardCol = Col("go_backward", BOOL) // used for tracking forks and rescanning
  val isHalfMixTxCol = Col("is_half_mix_tx", BOOL)
  val mixBoxIdCol = Col("mix_box_id", STR)
  // rescan is needed in two cases:
  //  1. If the input of our tx has zero conf (goBackwardCol is true). We create a tx when the input has at least 1 confirmations; we must go backward to check the last confirmed tx
  //  2. If the output of out tx has been spent (isZeroConfCol is false. Already spent, so we have to go forward. This should ideally never occur.


}
