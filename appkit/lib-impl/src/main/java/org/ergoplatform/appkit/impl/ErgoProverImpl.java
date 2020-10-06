package org.ergoplatform.appkit.impl;

import org.ergoplatform.ErgoBox;
import org.ergoplatform.ErgoLikeTransaction;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.*;
import scala.collection.IndexedSeq;
import sigmastate.basics.DLogProtocol;
import sigmastate.eval.CostingSigmaDslBuilder$;
import special.sigma.BigInt;

public class ErgoProverImpl implements ErgoProver {
    private final BlockchainContextImpl _ctx;
    private final AppkitProvingInterpreter _prover;

    public ErgoProverImpl(BlockchainContextImpl ctx, AppkitProvingInterpreter prover) {
        _ctx = ctx;
        _prover = prover;
    }

    @Override
    public P2PKAddress getP2PKAddress() {
        DLogProtocol.ProveDlog pk = _prover.pubKeys().apply(0);
        return JavaHelpers.createP2PKAddress(pk, _ctx.getNetworkType().networkPrefix);
    }

    @Override
    public Address getAddress() {
        return new Address(getP2PKAddress());
    }

    @Override
    public BigInt getSecretKey() {
        return CostingSigmaDslBuilder$.MODULE$.BigInt(_prover.secretKeys().get(0).key().w());
    }

    @Override
    public SignedTransaction sign(UnsignedTransaction tx) {
        UnsignedTransactionImpl txImpl = (UnsignedTransactionImpl)tx;
        IndexedSeq<ErgoBox> boxesToSpend = JavaHelpers.toIndexedSeq(txImpl.getBoxesToSpend());
        IndexedSeq<ErgoBox> dataBoxes = JavaHelpers.toIndexedSeq(txImpl.getDataBoxes());
        ErgoLikeTransaction signed =
                _prover.sign(txImpl.getTx(), boxesToSpend, dataBoxes, txImpl.getStateContext()).get();
        return new SignedTransactionImpl(_ctx, signed);
    }
}
