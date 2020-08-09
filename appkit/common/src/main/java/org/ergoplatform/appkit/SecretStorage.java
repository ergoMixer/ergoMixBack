package org.ergoplatform.appkit;

import org.ergoplatform.P2PKAddress;
import org.ergoplatform.wallet.secrets.ExtendedSecretKey;
import org.ergoplatform.wallet.secrets.JsonSecretStorage;
import org.ergoplatform.wallet.settings.EncryptionSettings;
import org.ergoplatform.wallet.settings.SecretStorageSettings;
import scala.Option;
import scala.runtime.BoxedUnit;
import scala.util.Failure;
import scala.util.Try;
import sigmastate.basics.DLogProtocol;

import java.io.File;

/**
 * Encrypted storage of mnemonic phrase in a file which can be accessed using password.
 */
public class SecretStorage {
    public static EncryptionSettings DEFAULT_SETTINGS = new EncryptionSettings(
            "HmacSHA256", 128000, 256);

    private final JsonSecretStorage _jsonStorage;

    SecretStorage(JsonSecretStorage jsonStorage) {
        _jsonStorage = jsonStorage;
    }

    /**
     * @return true if this storage is locked (call {@link #unlock(String pass)} to unlock this storage).
     */
    public boolean isLocked() { return _jsonStorage.isLocked(); }

    /**
     * @return underlying storage file
     */
    public File getFile() { return _jsonStorage.secretFile(); }

    public ExtendedSecretKey getSecret() {
        Option<ExtendedSecretKey> secretOpt = _jsonStorage.secret();
        if (secretOpt.isEmpty()) return null;
        return secretOpt.get();
    }

    public Address getAddressFor(NetworkType networkType) {
        DLogProtocol.ProveDlog pk = _jsonStorage.secret().get().key().publicImage();
        P2PKAddress p2pk = JavaHelpers.createP2PKAddress(pk, networkType.networkPrefix);
        return new Address(p2pk);
    }

    public void unlock(SecretString encryptionPass) {
        unlock(encryptionPass.toStringUnsecure());
    }

    public void unlock(String encryptionPass) {
        Try<BoxedUnit> resTry = _jsonStorage.unlock(encryptionPass);
        if (resTry.isFailure()) {
            Throwable cause = ((Failure)resTry).exception();
            throw new RuntimeException("Cannot unlock secrete storage.", cause);
        }
    }

    public static SecretStorage createFromMnemonicIn(
            String secretDir, Mnemonic mnemonic, SecretString encryptionPassword) {
        return createFromMnemonicIn(secretDir, mnemonic, encryptionPassword.toStringUnsecure());
    }

    public static SecretStorage createFromMnemonicIn(
            String secretDir, Mnemonic mnemonic, String encryptionPassword) {
        Option<String> passOpt = Iso.arrayCharToOptionString().to(mnemonic.getPassword());
        SecretStorageSettings settings = new SecretStorageSettings(secretDir, DEFAULT_SETTINGS);

        JsonSecretStorage jsonStorage = JsonSecretStorage
                .restore(mnemonic.getPhrase().toStringUnsecure(), passOpt, encryptionPassword, settings);

        return new SecretStorage(jsonStorage);
    }

    public static SecretStorage loadFrom(String storageFileName) {
        File file = new File(storageFileName);
        return loadFrom(file);
    }

    public static SecretStorage loadFrom(File storageFile) {
        if (!storageFile.exists())
            throw new RuntimeException("SecreteStorage file not found: " + storageFile.getPath());
        return new SecretStorage(new JsonSecretStorage(storageFile, DEFAULT_SETTINGS));
    }
}
