package org.ergoplatform.appkit.impl;

import org.ergoplatform.ErgoBox;
import org.ergoplatform.UnsignedErgoLikeTransaction;
import org.ergoplatform.appkit.UnsignedTransaction;
import org.ergoplatform.wallet.protocol.context.ErgoLikeStateContext;

import java.util.List;

public class UnsignedTransactionImpl implements UnsignedTransaction {
    private final UnsignedErgoLikeTransaction _tx;
    private List<ErgoBox> _boxesToSpend;
    private List<ErgoBox> _dataBoxes;
    private ErgoLikeStateContext _stateContext;

    public UnsignedTransactionImpl(
            UnsignedErgoLikeTransaction tx, List<ErgoBox> boxesToSpend,
            List<ErgoBox> dataBoxes, ErgoLikeStateContext stateContext) {
        _tx = tx;
        _boxesToSpend = boxesToSpend;
        _dataBoxes = dataBoxes;
        _stateContext = stateContext;
    }

    UnsignedErgoLikeTransaction getTx() {
        return _tx;
    }

    public List<ErgoBox> getBoxesToSpend() {
        return _boxesToSpend;
    }

    public List<ErgoBox> getDataBoxes() {
       return _dataBoxes;
    }

    public ErgoLikeStateContext getStateContext() {
        return _stateContext;
    }
}
