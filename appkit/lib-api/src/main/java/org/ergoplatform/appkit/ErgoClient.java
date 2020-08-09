package org.ergoplatform.appkit;

import java.util.function.Function;

/**
 * This interface is used to represent Ergo blockchain client object. Each
 * instance of ErgoClient represents some kind of connection with Ergo network
 * node. The implementations of ErgoClient may range from communicating with the
 * Ergo network node using REST API down to direct accessing node's state when
 * ErgoClient is executed in the same JVM as Ergo node.
 * <br>
 * ErgoClient can be used as a runner of some action in a blockchain context.
 * The {@link BlockchainContext blockchain context} is created by the specific
 * ErgoClient implementation and passed to the action.<br>
 * Some implementations may connect to network nodes, while others may use mock
 * web server to simulate connection in tests.
 */
public interface ErgoClient {
    /**
     * Execute the given action and return action's result. An instance of
     * {@link BlockchainContext} is created with the current state of the
     * blockchain and passed as the argument of the action.
     */
    <T> T execute(Function<BlockchainContext, T> action);
}

