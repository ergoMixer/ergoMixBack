package org.ergoplatform.appkit;

import sigmastate.Values;

import java.util.List;

/**
 * This interface represent a specific context of blockchain for execution
 * of transaction transaction building scenario.
 * It contains methods for accessing UTXO boxes, current blockchain state,
 * node information etc.
 * An instance of this interface can also be used to create new builders
 * for creating new transactions and provers (used for transaction signing).
 */
public interface BlockchainContext {
    /**
     * Creates a new builder of unsigned transaction.
     * A new builder is created for every call.
     */
    UnsignedTransactionBuilder newTxBuilder();

    /**
     * Retrieves UTXO boxes available in this blockchain context.
     *
     * @param boxIds array of string encoded ids of the boxes in the UTXO.
     * @return an array of requested boxes suitable for spending in transactions
     * created using this context.
     * @throws ErgoClientException if some boxes are not avaliable.
     */
    InputBox[] getBoxesById(String... boxIds) throws ErgoClientException;

    /**
     * Creates a new builder of {@link ErgoProver}.
     */
    ErgoProverBuilder newProverBuilder();

    /**
     * Returns a network type of this context.
     */
    NetworkType getNetworkType();

    /**
     * Return the height of the blockchain at the point of time when this
     * context was created.
     * The context is immutable, thus to obtain a new height later in time
     * a new context should be should be created.
     */
    int getHeight();

    /**
     * Sends a signed transaction to a blockchain node. On the blockchain node the transaction
     * is first placed in a pool and then later can be selected by miner and included in the next block.
     * The new transactions are also replicated all over the network.
     *
     * @param tx a signed {@link SignedTransaction transaction} to be sent to the blockchain node
     */
    String sendTransaction(SignedTransaction tx);

    SignedTransaction signedTxFromJson(String json);

    ErgoWallet getWallet();

    ErgoContract newContract(Values.ErgoTree ergoTree);

    ErgoContract compileContract(Constants constants, String ergoScript);

    /**
     * Get unspent boxes owned by the given address
     */
    List<InputBox> getUnspentBoxesFor(Address address);

    /**
     * Get unspent boxes protected by given ergo tree template
     */
    List<InputBox> getUnspentBoxesForErgoTreeTemplate(ErgoTreeTemplate template);
}

