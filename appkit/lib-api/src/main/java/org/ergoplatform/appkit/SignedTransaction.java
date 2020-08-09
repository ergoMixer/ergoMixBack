package org.ergoplatform.appkit;

import java.util.List;

/**
 * This interface represents a transaction which is signed by a prover
 * and can be sent to blockchain.
 * All inputs of the signed transaction has attached signatures (aka proofs)
 * which evidence that the prover knows the required secretes.
 *
 * @see ErgoProver
 * @see UnsignedTransaction
 */
public interface SignedTransaction {
    String getId();
    String toJson(boolean prettyPrint);
    List<InputBox> getOutputsToSpend();
}

