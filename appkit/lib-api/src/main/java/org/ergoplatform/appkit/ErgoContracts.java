package org.ergoplatform.appkit;

/**
 * Collection of standard contracts predefined in Appkit.
 */
public class ErgoContracts {
    /**
     * Pay to public key contract. Require proof of knowledge (aka signature) of private key corresponding
     * to the recipient address.
     *
     * @param ctx       context to interact with blockchain
     * @param recipient address of the pk owner
     */
    public static ErgoContract sendToPK(BlockchainContext ctx, Address recipient) {
        return ctx.compileContract(
                ConstantsBuilder.create()
                        .item("recipientPk", recipient.getPublicKey())
                        .build(),
                "{ recipientPk }");
    }
}
