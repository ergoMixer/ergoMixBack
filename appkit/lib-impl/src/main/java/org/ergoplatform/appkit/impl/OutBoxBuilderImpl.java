package org.ergoplatform.appkit.impl;

import com.google.common.base.Preconditions;
import org.ergoplatform.*;
import org.ergoplatform.appkit.*;
import scala.Tuple2;
import sigmastate.Values;

import java.util.ArrayList;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkState;

public class OutBoxBuilderImpl implements OutBoxBuilder {

    private final BlockchainContextImpl _ctx;
    private final UnsignedTransactionBuilderImpl _txB;
    private long _value = 0;
    private ErgoContract _contract;
    private ArrayList<ErgoToken> _tokens = new ArrayList<>();
    private ArrayList<ErgoValue<?>> _registers = new ArrayList<>();

    public OutBoxBuilderImpl(
            BlockchainContextImpl ctx, UnsignedTransactionBuilderImpl txB) {
        _ctx = ctx;
        _txB = txB;
    }

    public OutBoxBuilderImpl value(long value) {
        _value = value;
        return this;
    }

    @Override
    public OutBoxBuilderImpl contract(ErgoContract contract) {
        _contract = contract;
        return this;
    }

    public OutBoxBuilderImpl tokens(ErgoToken... tokens) {
        Preconditions.checkArgument(tokens.length > 0,
                "At least one token should be specified");
        Collections.addAll(_tokens, tokens);
        return this;
    }

    @Override
    public OutBoxBuilderImpl registers(ErgoValue<?>... registers) {
        Preconditions.checkArgument(registers.length > 0,
                "At least one register should be specified");
        Collections.addAll(_registers, registers);
        return this;
    }

    public OutBox build() {
        checkState(_contract != null, "Contract is not defined");
        Values.ErgoTree tree = _contract.getErgoTree();
        ErgoBoxCandidate ergoBoxCandidate = JavaHelpers.createBoxCandidate(_value, tree, _tokens,
                _registers, _txB.getCtx().getHeight());  // TODO pass user specified
        // creationHeight
        return new OutBoxImpl(_ctx, ergoBoxCandidate);
    }
}
