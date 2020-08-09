package org.ergoplatform.appkit;

import org.ergoplatform.ErgoAddress;
import org.ergoplatform.ErgoAddressEncoder;

/**
 * Enumeration of network types as they are defined by Ergo specification of {@link ErgoAddress}.
 */
public enum NetworkType {
    /**
     * Mainnet network type.
     * @see ErgoAddressEncoder#MainnetNetworkPrefix()
     */
    MAINNET(ErgoAddressEncoder.MainnetNetworkPrefix()),

    /**
     * Testnet network type.
     * @see ErgoAddressEncoder#TestnetNetworkPrefix()
     */
    TESTNET(ErgoAddressEncoder.TestnetNetworkPrefix());

    /**
     * The network prefix code used in Ergo addresses
     */
    public final byte networkPrefix;

    NetworkType(byte networkPrefix) {
        this.networkPrefix = networkPrefix;
    }
}
