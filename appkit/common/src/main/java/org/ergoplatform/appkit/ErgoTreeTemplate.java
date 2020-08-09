package org.ergoplatform.appkit;

import scala.NotImplementedError;
import scala.collection.IndexedSeq;
import scorex.util.encode.Base16;
import sigmastate.SType;
import sigmastate.Values;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents ErgoTree template, which is an ErgoTree instance with placeholders.
 * Each placeholder have index and type and can be substituted with a constant of
 * the appropriate type.
 */
public class ErgoTreeTemplate {

    private final Values.ErgoTree _tree;

    private ErgoTreeTemplate(Values.ErgoTree tree) {
        _tree = tree;
    }

    /**
     * Returns serialized bytes of this template.
     *
     * @return template bytes at the tail of the serialized ErgoTree (i.e. exclusing header and segregated
     * constants)
     */
    public byte[] getBytes() { return JavaHelpers.ergoTreeTemplateBytes(_tree); }

    /**
     * Returns template bytes encoded as Base16 string.
     *
     * @see ErgoTreeTemplate#getBytes
     */
    public String getEncodedBytes() { return Base16.encode(getBytes()); }

    /**
     * A number of placeholders in the template, which can be substituted (aka parameters).
     * This is immutable property of a {@link ErgoTreeTemplate}, which counts all the constants in the
     * {@link sigmastate.Values.ErgoTree} which can be replaced by new values using
     * {@link ErgoTreeTemplate#applyParameters} method.
     * In general, constants of ErgoTree cannot be replaced, but every placeholder can.
     */
    public int getParameterCount() { return _tree.constants().length(); }

    /**
     * Returns types of all template parameters (placeholders in the ErgoTree).
     */
    public List<ErgoType<?>> getParameterTypes() {
        Iso<List<Values.Constant<SType>>, IndexedSeq<Values.Constant<SType>>> iso =
         Iso.JListToIndexedSeq(Iso.identityIso());
        List<Values.Constant<SType>> ergoValues = iso.from(_tree.constants());
        return ergoValues.stream().map(v -> Iso.isoErgoTypeToSType().from(v.tpe())).collect(Collectors.toList());
    }

    /**
     * Creates a new ErgoTree with new values for all parameters of this template.
     *
     * <br>Require:
     * <pre>
     * newValues.length == getParameterCount() &&
     * forall i = 0; i < getParameterCount() => newValue[i].getType().equals(getParameterTypes()[i])
     * </pre>
     *
     * @param newValues new values for all parameters
     * @return new ErgoTree with the same template as this but with all it's parameters
     * replaced with `newValues`
     */
    public Values.ErgoTree applyParameters(ErgoValue<?> newValues) {
        throw new NotImplementedError();
    }

    public static ErgoTreeTemplate fromErgoTree(Values.ErgoTree tree) {
        return new ErgoTreeTemplate(tree);
    }
}
