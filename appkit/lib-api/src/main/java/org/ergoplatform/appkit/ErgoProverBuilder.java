package org.ergoplatform.appkit;

import special.sigma.GroupElement;

import java.math.BigInteger;

/**
 * This interface is used to configure and build a new {@link ErgoProver prover}.
 */
public interface ErgoProverBuilder {
    /**
     * Configure this builder to use the given seed when building a new prover.
     *
     * @param mnemonicPhrase secret seed phrase to be used in prover for generating proofs.
     * @param mnemonicPass   password to protect secret seed phrase.
     */
    ErgoProverBuilder withMnemonic(SecretString mnemonicPhrase, SecretString mnemonicPass);

    /**
     * Configure this builder to use the given mnemonic when building a new prover.
     *
     * @param mnemonic {@link Mnemonic} instance containing secret seed phrase to be used in prover for
     *                 generating proofs.
     */
    ErgoProverBuilder withMnemonic(Mnemonic mnemonic);

    /**
     * Configure this builder to use the given {@link SecretStorage} when building a new prover.
     *
     * @param storage {@link SecretStorage} instance containing encrypted secret seed phrase to be used in
     *                prover for generating proofs.
     */
    ErgoProverBuilder withSecretStorage(SecretStorage storage);

    /**
     * Add DHT prover input using this prover's secret.
     *
     * @param g {@Link GroupElement} instance defining g
     * @param h {@Link GroupElement} instance defining h
     * @param u {@Link GroupElement} instance defining u
     * @param v {@Link GroupElement} instance defining v
     * @param x {@Link BigInteger} instance defining x
     * @return
     *
     * ProveDHTuple is of the form (g, h, u, v) with secret x (and unknown y), where:
     *   h = g^y
     *   u = g^x
     *   v = g^xy
     *
     */
    ErgoProverBuilder withDHTData(GroupElement g, GroupElement h, GroupElement u, GroupElement v, BigInteger x);

    /**
     * Add Dlog prover input using this prover's secret.
     *
     * @param x {@Link BigInteger} instance defining x
     */
    ErgoProverBuilder withDLogSecret(BigInteger x);

    /**
     * Builds a new prover using provided configuration.
     */
    ErgoProver build();
}

