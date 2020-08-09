package org.ergoplatform.appkit.impl;

import org.ergoplatform.ErgoBoxCandidate;
import org.ergoplatform.appkit.*;

public class OutBoxImpl implements OutBox {
  private final BlockchainContextImpl _ctx;
  private final ErgoBoxCandidate _ergoBoxCandidate;

  public OutBoxImpl(BlockchainContextImpl ctx, ErgoBoxCandidate ergoBoxCandidate) {
    _ctx = ctx;
    _ergoBoxCandidate = ergoBoxCandidate;
  }

  @Override
  public long getValue() {
    return _ergoBoxCandidate.value();
  }

  @Override
  public ErgoToken token(ErgoId id) {
    return null;
  }

  ErgoBoxCandidate getErgoBoxCandidate() {
    return _ergoBoxCandidate;
  }

  @Override
  public InputBox convertToInputWith(String txId, short boxIndex) {
    return new InputBoxImpl(_ctx, _ergoBoxCandidate.toBox(txId, boxIndex));
  }
}
