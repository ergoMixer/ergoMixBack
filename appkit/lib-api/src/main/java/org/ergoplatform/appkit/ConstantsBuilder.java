package org.ergoplatform.appkit;

/**
 * Used to build {@link Constants} instances which can be used in ErgoScript contracts.
 */
public class ConstantsBuilder {

    Constants _constants = new Constants();

    public ConstantsBuilder item(String name, Object value) {
        _constants.put(name, value);
        return this;
    }

    public Constants build() {
        return _constants;
    }

    public static ConstantsBuilder create() { return new ConstantsBuilder(); }

    public static Constants empty() { return create().build(); }
}

