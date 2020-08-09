package org.ergoplatform.appkit;

import java.util.List;
import java.util.Optional;

public interface ErgoWallet {
    Optional<List<InputBox>> getUnspentBoxes(long amountToSpend);
}
