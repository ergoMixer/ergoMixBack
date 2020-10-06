package org.ergoplatform.appkit;

import org.bouncycastle.math.ec.custom.sec.SecP256K1Point;
import org.ergoplatform.ErgoAddress;
import org.ergoplatform.ErgoAddressEncoder;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.wallet.secrets.ExtendedSecretKey;
import scala.util.Try;
import scorex.util.encode.Base58;
import sigmastate.basics.DLogProtocol;
import sigmastate.eval.CostingSigmaDslBuilder$;
import sigmastate.utils.Helpers;
import special.sigma.GroupElement;

import static com.google.common.base.Preconditions.checkArgument;

public class Address {
    private final String _base58String;
    private final byte[] _addrBytes;

    ErgoAddress _address;

    public Address(P2PKAddress p2pkAddress) {
        _address = p2pkAddress;
        ErgoAddressEncoder encoder = ErgoAddressEncoder.apply(p2pkAddress.encoder().networkPrefix());
        _base58String = encoder.toString(_address);
        _addrBytes = Base58.decode(_base58String).get();
    }

    /**
     * First byte is used to encode network type and address type.
     *
     * @see ErgoAddressEncoder
     */
    private byte headByte() { return _addrBytes[0]; }

    private Address(String base58String) {
        _base58String = base58String;
        Try<byte[]> res = Base58.decode(base58String);
        if (res.isFailure())
            throw new RuntimeException(
                    "Invalid address encoding, expected base58 string: " + base58String,
                    (Throwable)new Helpers.TryOps(res).toEither().left().get());
        _addrBytes = res.get();
        ErgoAddressEncoder encoder = ErgoAddressEncoder.apply(getNetworkType().networkPrefix);
        Try<ErgoAddress> addrTry = encoder.fromString(base58String);
        if (addrTry.isFailure())
            throw new RuntimeException(
                    "Invalid address encoding, expected base58 string: " + base58String,
                    (Throwable)new Helpers.TryOps(addrTry).toEither().left().get());
        _address = addrTry.get();
    }

    /**
     * @return NetworkType of this address.
     */
    public NetworkType getNetworkType() { return isMainnet() ? NetworkType.MAINNET : NetworkType.TESTNET; }

    /**
     * @return true if this address from Ergo mainnet.
     */
    public boolean isMainnet() { return headByte() < NetworkType.TESTNET.networkPrefix; }

    /**
     * @return true if this address has Pay-To-Public-Key type.
     */
    public boolean isP2PK() { return _address instanceof P2PKAddress; }

    /**
     * Obtain an instance of {@link ErgoAddress} related to this Address instance.
     *
     * @return {@link ErgoAddress} instance associated with this address
     */
    public ErgoAddress getErgoAddress() {
        return _address;
    }

    /**
     * Extract public key from P2PKAddress.
     */
    public DLogProtocol.ProveDlog getPublicKey() {
        checkArgument(isP2PK(), "This instance %s is not P2PKAddress", this);
        return ((P2PKAddress)_address).pubkey();
    }

    /**
     * Extract public key from P2PKAddress and return its group element
     */
    public GroupElement getPublicKeyGE() {
        SecP256K1Point point = getPublicKey().value();
        return CostingSigmaDslBuilder$.MODULE$.GroupElement(point);
    }

    /**
     * Create Ergo Address from base58 string.
     *
     * @param base58Str base58 string representation of address bytes.
     * @return Address instance decoded from string
     */
    public static Address create(String base58Str) { return new Address(base58Str); }

    public static Address fromMnemonic(NetworkType networkType, Mnemonic mnemonic) {
        return fromMnemonic(networkType, mnemonic.getPhrase(), mnemonic.getPassword());
    }

    public static Address fromMnemonic(NetworkType networkType, SecretString mnemonic, SecretString mnemonicPass) {
        ExtendedSecretKey masterKey = JavaHelpers.seedToMasterKey(mnemonic, mnemonicPass);
        DLogProtocol.ProveDlog pk = masterKey.key().publicImage();
        P2PKAddress p2pkAddress = JavaHelpers.createP2PKAddress(pk, networkType.networkPrefix);
        return new Address(p2pkAddress);
    }

    @Override
    public String toString() {
        return _address.toString();
    }

    @Override
    public int hashCode() {
        return _address.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Address) {
            return _address.equals(((Address)obj)._address);
        }
        return false;
    }
}
