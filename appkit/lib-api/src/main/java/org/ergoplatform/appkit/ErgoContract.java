package org.ergoplatform.appkit;

import sigmastate.Values;

/**
 * Representation of ErgoScript contract using source code and named constants.
 * This information is enough to compile contract into {@link Values.ErgoTree}.
 * Once constructed the instances are immutable.
 * Methods which do transformations produce new instances.
 */
public interface ErgoContract {
    /**
     * Returns named constants used to compile this contract.
     */
    Constants getConstants();

    /**
     * Returns a source code of ErgoScript contract.
     */
    String getErgoScript();

    /**
     * Creates a new contract by substituting the constant {@code name} with the new {@code value}.
     */
    ErgoContract substConstant(String name, Object value);

    Values.ErgoTree getErgoTree();
}
