package org.ergoplatform.appkit.impl;

import com.google.common.base.Preconditions;
import org.ergoplatform.appkit.BoxOperations;
import org.ergoplatform.appkit.ErgoWallet;
import org.ergoplatform.appkit.InputBox;
import org.ergoplatform.restapi.client.WalletBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ErgoWalletImpl implements ErgoWallet {
    private final List<WalletBox> _unspentBoxesData;
    private BlockchainContextImpl _ctx;
    private List<InputBox> _unspentBoxes;

    public ErgoWalletImpl(List<WalletBox> unspentBoxesData) {
        Preconditions.checkNotNull(unspentBoxesData);
        _unspentBoxesData = unspentBoxesData;
    }

    void setContext(BlockchainContextImpl ctx) {
        Preconditions.checkState(_ctx == null, "Cannot reset context of wallet %s", this);
        _ctx = ctx;
    }

    @Override
    public Optional<List<InputBox>> getUnspentBoxes(long amountToSpend) {
        if (_unspentBoxes == null) {
            _unspentBoxes = _unspentBoxesData.stream().map(boxInfo -> {
                return new InputBoxImpl(_ctx, boxInfo.getBox());
            }).collect(Collectors.toList());
        }

        List<InputBox> selected = BoxOperations.selectTop(_unspentBoxes, amountToSpend);
        if (selected == null) return Optional.empty();
        return Optional.of(selected);
    }
}
