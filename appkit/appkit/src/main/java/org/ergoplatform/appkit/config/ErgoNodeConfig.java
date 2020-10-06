package org.ergoplatform.appkit.config;

import org.ergoplatform.appkit.NetworkType;

/**
 * Parameters of Ergo node used by ErgoTool.
 */
public class ErgoNodeConfig {
    private ApiConfig nodeApi;
    private WalletConfig wallet;
    private NetworkType networkType;

    /**
     * Returns Ergo node API connection parameters
     */
    public ApiConfig getNodeApi() {
        return nodeApi;
    }

    /**
     * Returns parameters for working with the wallet
     */
    public WalletConfig getWallet() {
        return wallet;
    }

    /**
     * Returns expected network type (Mainnet or Testnet)
     */
    public NetworkType getNetworkType() {
        return networkType;
    }
}
