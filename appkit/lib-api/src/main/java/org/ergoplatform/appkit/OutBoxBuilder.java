package org.ergoplatform.appkit;

import org.ergoplatform.SigmaConstants;


/**
 * This interface is used to build a new output box, which can be included
 * in the new unsigned transaction. When transaction is signed, sent to the
 * blockchain and then included by miners in a new block, then the output
 * constructed using this builder will be added to UTXO set.
 *
 * @see UnsignedTransactionBuilder#outBoxBuilder()
 */
public interface OutBoxBuilder {
    /**
     * Configure the Erg amount of the output box.
     * Each transaction is required to
     *
     * @param value amount in NanoErg
     */
    OutBoxBuilder value(long value);

    /**
     * Configure the guarding contract of the output box. This contract will be compiled into
     * ErgoTree, serialized and then sent to the blockchain as part of the signed transaction.
     *
     * @param contract ergo contract (aka `proposition`) protecting this box from illegal spending.
     */
    OutBoxBuilder contract(ErgoContract contract);

    /**
     * Configures amounts for one or more tokens (up to {@link SigmaConstants.MaxTokens}).
     * Each Ergo box can store zero or more tokens (aka assets).
     *
     * @param tokens one or more tokens to be added to the constructed output box.
     * @see ErgoToken
     */
    OutBoxBuilder tokens(ErgoToken... tokens);

    /**
     * Configures one or more optional registers of the output box.
     * Each box have 4 mandatory registers holding value of NanoErgs, guarding script,
     * tokens, creation info.
     * Optional (aka non-mandatory) registers numbered from index 4 up to 9.
     *
     * @param registers array of optional register values,
     *                  where registers[0] corresponds to R4, registers[1] - R5, etc.
     * @see ErgoValue
     * @see org.ergoplatform.ErgoBox.NonMandatoryRegisterId
     */
    OutBoxBuilder registers(ErgoValue<?>... registers);

    /**
     * Creates {@link OutBox} instance using specified parameters.
     *
     * @return output box which can be {@link UnsignedTransactionBuilder#outputs(OutBox...) added}
     * to {@link UnsignedTransaction}
     * @see UnsignedTransaction
     */
    OutBox build();
}
