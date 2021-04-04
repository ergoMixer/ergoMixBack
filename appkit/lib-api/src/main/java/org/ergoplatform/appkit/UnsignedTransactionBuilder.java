package org.ergoplatform.appkit;

import org.ergoplatform.ErgoAddress;

import java.util.List;

/**
 * This interface is used to build a new {@link UnsignedTransaction} which can later
 * be signed by a {@link ErgoProver prover}. A new instance of this builder can be
 * {@link BlockchainContext#newTxBuilder() obtained} from the {@link BlockchainContext}.
 * Before unsigned transaction can be {@link BlockchainContext#sendTransaction(SignedTransaction) sent} to the
 * blockchain it should be signed by a prover.
 * The proved should be {@link ErgoProverBuilder#build() constructed} by the {@link ErgoProverBuilder builder}
 * {@link BlockchainContext#newProverBuilder() obtained} from the same {@link BlockchainContext context}.
 *
 * @see UnsignedTransaction
 */
public interface UnsignedTransactionBuilder {
    /**
     * Specifies boxes that will be used as data-inputs by the transaction when it will be included in a block.
     *
     * @param boxes list of boxes to be used as data-inputs by the transaction. The boxes can either be
     *              {@link BlockchainContext#getBoxesById(String...) obtained} from context of created from
     *               scratch
     *              as {@link OutBox} and then {@link OutBox#convertToInputWith(String, short) converted} to
     *              {@link InputBox}.
     */
    UnsignedTransactionBuilder withDataInputs(List<InputBox> boxes);

    /**
     * Specifies boxes that will be spent by the transaction when it will be included in a block.
     *
     * @param boxes list of boxes to be spent by the transaction. The boxes can either be
     *              {@link BlockchainContext#getBoxesById(String...) obtained} from context of created from scratch
     *              as {@link OutBox} and then {@link OutBox#convertToInputWith(String, short) converted} to
     *              {@link InputBox}.
     */
    UnsignedTransactionBuilder boxesToSpend(List<InputBox> boxes);

    /**
     * Specifies output boxes of the transaction. After this transaction is
     * {@link UnsignedTransactionBuilder#build() built}, {@link ErgoProver#sign(UnsignedTransaction)} signed,
     * {@link BlockchainContext#sendTransaction(SignedTransaction) sent} to the node and included into a next block
     * the output boxes will be put in the UTXO set.
     *
     * @param outputs output boxes created by the transaction
     */
    UnsignedTransactionBuilder outputs(OutBox... outputs);


    /**
     * Adds transaction fee output.
     * @param feeAmount transaction fee amount in NanoErgs
     */
    UnsignedTransactionBuilder fee(long feeAmount);

    /**
     * Adds change output to the specified address if needed.
     * @param address address to send output
     */
    UnsignedTransactionBuilder sendChangeTo(ErgoAddress address, ErgoValue<?>... registers);

    /**
     * Builds a new unsigned transaction in the {@link BlockchainContext context} inherited from this builder.
     *
     * @return a new instance of {@link UnsignedTransaction}
     */
    UnsignedTransaction build();

    /**
     * Returns the context for which this builder is building transactions.
     */
    BlockchainContext getCtx();

    /**
     * Returns the network type of the blockchain represented by the {@link UnsignedTransactionBuilder#getCtx()
     * context} of this builder.
     */
    NetworkType getNetworkType();

    /**
     * Creates a new builder of output box. The returned builder can be used to
     * {@link OutBoxBuilder#build() build} only single instance of {@link OutBox}.
     * For each new {@link OutBox} a new {@link OutBoxBuilder} should be created.
     */
    OutBoxBuilder outBoxBuilder();

    /**
     * Returns all input boxes attached to this builder.
     */
    List<InputBox> getInputBoxes();
}
