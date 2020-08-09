package org.ergoplatform.appkit;

/**
 * Represents ergo token (aka assert) paired with its value.
 * Implements equality and can be used as keys for maps and sets.
 */
public class ErgoToken {
    private final ErgoId _id;
    private final long _value;

    public ErgoToken(ErgoId id, long value) {
        _id = id;
        _value = value;
    }

    public ErgoToken(byte[] idBytes, long value) {
        this(new ErgoId(idBytes), value);
    }

    public ErgoToken(String id, long value) {
        this(JavaHelpers.decodeStringToBytes(id), value);
    }

    public ErgoId getId() {
        return _id;
    }

    public long getValue() {
        return _value;
    }

    @Override
    public int hashCode() {
        return 31 * _id.hashCode() + (int)_value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof ErgoToken) {
            ErgoToken that = (ErgoToken)obj;
            return this._id.equals(that._id) && this._value == that._value;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("ErgoToken(%s, %s)", _id.toString(), _value);
    }
}

