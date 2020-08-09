package org.ergoplatform.appkit.config;

/**
 * Parameters for working with wallet
 */
public class WalletConfig {
    private String mnemonic;
    private String password;
    private String mnemonicPassword;

    /**
     * Mnemonic which is used for generation of keys in the wallet.
     * Should be the same as the one used by the walled of the node (specified in {@link ErgoNodeConfig#getNodeApi()}).
     */
    public String getMnemonic() {
        return mnemonic;
    }

    /**
     * Password which is used by the Ergo node wallet to protect wallet data
     * Empty or null string value means no password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Password which used to protect mnemonic (by default it is the same as `password`)
     */
    public String getMnemonicPassword() {
        return mnemonicPassword;
    }
}
