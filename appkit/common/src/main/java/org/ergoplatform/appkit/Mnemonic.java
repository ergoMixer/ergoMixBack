package org.ergoplatform.appkit;

import scala.Option;
import scala.util.Try;

/**
 * BIP39 mnemonic sentence (see: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)
 */
public class Mnemonic {
    private final char[] _phrase;
    private final char[] _password;

    Mnemonic(char[] phrase, char[] password) {
        _phrase = phrase;
        _password = password;
    }

    /**
     * Default strength of mnemonic security (number of bits)
     */
    public static int DEFAULT_STRENGTH = 160;

    /**
     * Generate random bytes with the given security strength (number of bits)
     */
    public static byte[] getEntropy(int strength) { return scorex.utils.Random.randomBytes(strength / 8); }

    /**
     * @param languageId - language identifier to be used in sentence
     * @param strength   - number of bits in the seed
     */
    public static String generate(String languageId, int strength, byte[] entropy) {
        org.ergoplatform.wallet.mnemonic.Mnemonic mnemonic =
                new org.ergoplatform.wallet.mnemonic.Mnemonic(languageId
                        , strength);
        Try<String> resTry = mnemonic.toMnemonic(entropy);
        if (resTry.isFailure())
            throw new RuntimeException(
                    String.format("Cannot create mnemonic for languageId: %s, strength: %d", languageId,
                            strength));
        return resTry.get();
    }

    /**
     * Generates a new mnemonic using english words and default strength parameters.
     */
    public static String generateEnglishMnemonic() {
        byte[] entropy = getEntropy(DEFAULT_STRENGTH);
        return Mnemonic.generate("english", DEFAULT_STRENGTH, entropy);
    }

    /**
     * Creates {@link Mnemonic} instance with the given phrase and password.
     * Both phrase and password is passed by reference. This security sensitive data is not copied.
     */
    public static Mnemonic create(char[] phrase, char[] password) {
        return new Mnemonic(phrase, password);
    }

    /**
     * Creates {@link Mnemonic} instance with the given phrase and password.
     */
    public static Mnemonic create(SecretString phrase, SecretString password) {
        return new Mnemonic(phrase.getData(), password.getData());
    }

    /**
     * Returns secret mnemonic phrase stored in this {@link Mnemonic} instance.
     */
    public SecretString getPhrase() {
        return SecretString.create(_phrase);
    }

    /**
     * Returns secret mnemonic password stored in this {@link Mnemonic} instance.
     */
    public SecretString getPassword() {
        return SecretString.create(_password);
    }

    public byte[] toSeed() {
        Option<String> passOpt = Iso.arrayCharToOptionString().to(getPassword());
        return org.ergoplatform.wallet.mnemonic.Mnemonic.toSeed(String.valueOf(_phrase), passOpt);
    }
}
