package org.ergoplatform.appkit.impl;

import org.ergoplatform.*;
import org.ergoplatform.appkit.*;
import org.ergoplatform.appkit.impl.ScalaBridge;
import org.ergoplatform.wallet.protocol.context.ErgoLikeStateContext;
import scala.collection.IndexedSeq;
import special.collection.Coll;
import special.sigma.GroupElement;
import special.sigma.Header;
import special.sigma.PreHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static org.ergoplatform.appkit.Parameters.MinChangeValue;
import static org.ergoplatform.appkit.Parameters.MinFee;

public class UnsignedTransactionBuilderImpl implements UnsignedTransactionBuilder {

    private final BlockchainContextImpl _ctx;
    ArrayList<UnsignedInput> _inputs = new ArrayList<>();
    ArrayList<DataInput> _dataInputs = new ArrayList<>();
    private List<InputBoxImpl> _dataInputBoxes = new ArrayList<>();
    ArrayList<ErgoBoxCandidate> _outputCandidates = new ArrayList<>();
    private List<InputBoxImpl> _inputBoxes;
    private long _feeAmount;
    private ErgoAddress _changeAddress;
    private ErgoValue<?>[] _registers = {};


    public UnsignedTransactionBuilderImpl(
            BlockchainContextImpl ctx) {
        _ctx = ctx;
    }

    @Override
    public UnsignedTransactionBuilder withDataInputs(List<InputBox> inputBoxes) {
        List<DataInput> items = inputBoxes
                .stream()
                .map(box -> JavaHelpers.createDataInput(box.getId().getBytes()))
                .collect(Collectors.toList());
        _dataInputs.addAll(items);
        _dataInputBoxes = inputBoxes.stream()
                .map(b -> (InputBoxImpl)b)
                .collect(Collectors.toList());
        return this;
    }

    @Override
    public UnsignedTransactionBuilder boxesToSpend(List<InputBox> inputBoxes) {
        List<UnsignedInput> items = inputBoxes
                .stream()
                .map(box -> JavaHelpers.createUnsignedInput(box.getId().getBytes()))
                .collect(Collectors.toList());
        _inputs.addAll(items);
        _inputBoxes = inputBoxes.stream()
                .map(b -> (InputBoxImpl)b)
                .collect(Collectors.toList());
        return this;
    }

    @Override
    public UnsignedTransactionBuilder outputs(OutBox... outputs) {
        checkState(_outputCandidates.isEmpty(), "Outputs already specified.");
        _outputCandidates = new ArrayList<>();
        appendOutputs(outputs);
        return this;
    }

    @Override
    public UnsignedTransactionBuilder fee(long feeAmount) {
        checkState(_feeAmount == 0, "Fee already defined");
        _feeAmount = feeAmount;
        return this;
    }

    private void appendOutputs(OutBox... outputs) {
        ErgoBoxCandidate[] boxes =
                Stream.of(outputs).map(c -> ((OutBoxImpl)c).getErgoBoxCandidate()).toArray(n -> new ErgoBoxCandidate[n]);
        Collections.addAll(_outputCandidates, boxes);
    }

    @Override
    public UnsignedTransactionBuilder sendChangeTo(ErgoAddress changeAddress, ErgoValue<?>... registers) {
        checkState(_changeAddress == null, "Change address is already specified");
        _changeAddress = changeAddress;
        if (registers.length > 0) _registers = registers;
        return this;
    }

    @Override
    public UnsignedTransaction build() {
        IndexedSeq<UnsignedInput> inputs = JavaHelpers.toIndexedSeq(_inputs);
        IndexedSeq<DataInput> dataInputs = JavaHelpers.toIndexedSeq(_dataInputs);
        List<ErgoBox> dataInputBoxes = _dataInputBoxes.stream()
                .map(b -> b.getErgoBox())
                .collect(Collectors.toList());

        checkState(_feeAmount >= 0, "Fee amount should be defined (using fee() method).");

        Long inputTotal = _inputBoxes.stream().map(b -> b.getValue()).reduce(0L, (x, y) -> x + y);
        Long outputSum = _outputCandidates.stream().map(b -> b.value()).reduce(0L, (x, y) -> x + y);
        long outputTotal = outputSum + _feeAmount;

        long changeAmt = inputTotal - outputTotal;
        boolean noChange = changeAmt < MinChangeValue;

        // if computed changeAmt is too small give it to miner as tips
        long actualFee = noChange ? _feeAmount + changeAmt : _feeAmount;

//        checkState(actualFee >= MinFee,
//                String.format("Fee must be greater then minimum amount (%d NanoErg)", MinFee));

        OutBox feeOut = outBoxBuilder()
                .value(actualFee)
                .contract(_ctx.newContract(ErgoScriptPredef.feeProposition(Parameters.MinerRewardDelay)))
                .build();
        appendOutputs(feeOut);

        if (!noChange) {
            checkState(_changeAddress != null, "Change address is not defined");

            OutBoxBuilder changeOutBuilder = outBoxBuilder()
                    .value(changeAmt)
                    .contract(_ctx.newContract(_changeAddress.script()));

            OutBox changeOut;

            if (_registers.length == 0) {
                changeOut = changeOutBuilder.build();
            } else {
                changeOut = changeOutBuilder.registers(_registers).build();
            }

            appendOutputs(changeOut);
        }

        IndexedSeq<ErgoBoxCandidate> outputCandidates = JavaHelpers.toIndexedSeq(_outputCandidates);
        UnsignedErgoLikeTransaction tx =
                new UnsignedErgoLikeTransaction(inputs, dataInputs, outputCandidates);
        List<ErgoBox> boxesToSpend =
                _inputBoxes.stream().map(b -> b.getErgoBox()).collect(Collectors.toList());
        ErgoLikeStateContext stateContext = createErgoLikeStateContext();

        return new UnsignedTransactionImpl(tx, boxesToSpend, dataInputBoxes, stateContext);
    }

    private ErgoLikeStateContext createErgoLikeStateContext() {
        return new ErgoLikeStateContext() {
            private Coll<Header> _allHeaders = Iso.JListToColl(ScalaBridge.isoBlockHeader(),
                    ErgoType.headerType().getRType()).to(_ctx.getHeaders());
            private Coll<Header> _headers = _allHeaders.slice(1, _allHeaders.length());
            private PreHeader _preHeader = JavaHelpers.toPreHeader(_allHeaders.apply(0));

            @Override
            public Coll<Header> sigmaLastHeaders() {
                return _headers;
            }

            @Override
            public byte[] previousStateDigest() {
                return JavaHelpers.getStateDigest(_headers.apply(0).stateRoot());
            }

            @Override
            public PreHeader sigmaPreHeader() {
                return _preHeader;
            }
        };
    }

    @Override
    public BlockchainContext getCtx() {
        return _ctx;
    }

    @Override
    public OutBoxBuilder outBoxBuilder() {
        return new OutBoxBuilderImpl(_ctx, this);
    }

    @Override
    public NetworkType getNetworkType() {
        return _ctx.getNetworkType();
    }

    @Override
    public List<InputBox> getInputBoxes() {
        return _inputBoxes.stream().collect(Collectors.toList());
    }
}
