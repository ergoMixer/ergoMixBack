package org.ergoplatform.appkit;

import java.util.Arrays;

/**
 * Identifier of Ergo object which wraps byte array (usually 256 bit hash).
 * ErgoId supports equality.
 */
public class ErgoId {
    private final byte[] _idBytes;

    public ErgoId(byte[] idBytes) {
        _idBytes = idBytes;
    }

    /**
     * Extracts underlying byte array with id bytes.
     */
    public byte[] getBytes() {
        return _idBytes;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_idBytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof ErgoId) {
            return Arrays.equals(this._idBytes, ((ErgoId)obj)._idBytes);
        }
        return false;
    }

    /** String representation of id using Base16 encoding. */
    @Override
    public String toString() {
        return JavaHelpers.Algos().encode(_idBytes);
    }

    public static ErgoId create(String base16Str) {
        return new ErgoId(JavaHelpers.decodeStringToBytes(base16Str));
    }
}
