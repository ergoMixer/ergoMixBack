package org.ergoplatform.appkit;

/**
 * An interface used to build new blockchain contexts.
 */
public interface BlockchainContextBuilder {
    /**
     * Number of headers available in this context.
     * This constant is defined by Ergo protocol and cannot be changed.
     */
    int NUM_LAST_HEADERS = 10;

    /**
     * Builds a new context using parameters collected by this builder.
     */
    BlockchainContext build() throws ErgoClientException;
}
