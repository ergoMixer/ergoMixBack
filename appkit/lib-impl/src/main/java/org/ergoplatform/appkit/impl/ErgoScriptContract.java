package org.ergoplatform.appkit.impl;

import org.ergoplatform.appkit.Constants;
import org.ergoplatform.appkit.ErgoContract;
import org.ergoplatform.appkit.JavaHelpers;
import org.ergoplatform.appkit.NetworkType;
import sigmastate.Values;


public class ErgoScriptContract implements ErgoContract {
    final private Constants _constants;
    final private String _code;
    private NetworkType _networkType;

    private ErgoScriptContract(Constants constants, String code, NetworkType networkType) {
        _constants = constants;
        _code = code;
        _networkType = networkType;
    }

    @Override
    public Constants getConstants() {
        return _constants;
    }

    @Override
    public String getErgoScript() {
        return _code;
    }

    /**
     * Creates a new contract with given parameters.
     */
    public static ErgoScriptContract create(
            Constants constants, String code, NetworkType networkType) {
        return new ErgoScriptContract(constants, code, networkType);
    }

    @Override
    public ErgoScriptContract substConstant(String name, Object value) {
        Constants cloned = (Constants)_constants.clone();
        cloned.replace(name, value);
        return create(cloned, _code, _networkType);
    }

    @Override
    public Values.ErgoTree getErgoTree() {
        Values.ErgoTree ergoTree = JavaHelpers.compile(
                _constants, _code, _networkType.networkPrefix);
        return ergoTree;
    }
}
