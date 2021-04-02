package org.ergoplatform.appkit;

import org.bouncycastle.math.ec.ECPoint;
import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConverters;
import sigmastate.AvlTreeData;
import sigmastate.Values;
import special.collection.Coll;
import special.collection.PairColl;
import special.sigma.AvlTree;
import special.sigma.BigInt;
import special.sigma.GroupElement;
import special.sigma.SigmaProp;
import scala.collection.immutable.Map;

import java.math.BigInteger;
import java.util.HashMap;
import sigmastate.SType;
import sigmastate.serialization.ValueSerializer;


/**
 * This class is used to represent any valid value of ErgoScript language.
 * Any such value comes equipped with {@link ErgoType} descriptor.
 */
public class ErgoValue<T> {
    private final T _value;
    private final ErgoType<T> _type;

    ErgoValue(T value, ErgoType<T> type) {
        _value = value;
        _type = type;
    }

    public T getValue() {
        return _value;
    }

    public ErgoType<T> getType() {
        return _type;
    }

    static public ErgoValue<Byte> of(byte value) {
        return new ErgoValue<>(value, ErgoType.byteType());
    }

    static public ErgoValue<Short> of(short value) {
        return new ErgoValue<>(value, ErgoType.shortType());
    }

    static public ErgoValue<Integer> of(int value) {
        return new ErgoValue<>(value, ErgoType.integerType());
    }

    static public ErgoValue<Long> of(long value) {
        return new ErgoValue<>(value, ErgoType.longType());
    }

    static public ErgoValue<BigInt> of(BigInteger value) {
        return new ErgoValue<>(JavaHelpers.SigmaDsl().BigInt(value), ErgoType.bigIntType());
    }

    static public ErgoValue<GroupElement> of(ECPoint value) {
        return new ErgoValue<>(JavaHelpers.SigmaDsl().GroupElement(value), ErgoType.groupElementType());
    }

    static public ErgoValue<GroupElement> of(GroupElement ge) {
        return new ErgoValue<>(ge, ErgoType.groupElementType());
    }

    static public ErgoValue<SigmaProp> of(Values.SigmaBoolean value) {
        return new ErgoValue<>(JavaHelpers.SigmaDsl().SigmaProp(value), ErgoType.sigmaPropType());
    }

    static public ErgoValue<AvlTree> of(AvlTreeData value) {
        return new ErgoValue<>(JavaHelpers.SigmaDsl().avlTree(value), ErgoType.avlTreeType());
    }

    static public ErgoValue<Coll<Byte>> of(byte[] arr) {
        Coll value = JavaHelpers.collFrom(arr);
        ErgoType<Coll<Byte>> type = ErgoType.collType(ErgoType.byteType());
        return new ErgoValue<Coll<Byte>>(value, type);
    }

    static public <T> ErgoValue<Coll<T>> of(T[] arr, ErgoType<T> tT) {
        Coll<T> value = JavaHelpers.SigmaDsl().Colls().fromArray(arr, tT.getRType());
        return new ErgoValue<>(value, ErgoType.collType(tT));
    }

    static public ErgoValue<Coll<Integer>> of(Integer[] arr) {
        ErgoType<Coll<Integer>> type = ErgoType.collType(ErgoType.integerType());
        Coll<Integer> value = JavaHelpers.SigmaDsl().Colls().fromArray(arr, ErgoType.integerType().getRType());
        return new ErgoValue<>(value, type);
    }

    public static <A, B> Map<A, B> toScalaMap(HashMap<A, B> m) {
        return JavaConverters.mapAsScalaMapConverter(m).asScala().toMap(
                Predef.<Tuple2<A, B>>conforms()
        );
    }

    static public ErgoValue<Coll<Integer>> of(HashMap<Integer, Long> mp) {
        ErgoType type = ErgoType.collType(ErgoType.pairType(ErgoType.integerType(), ErgoType.longType()));
        Coll value = JavaHelpers.SigmaDsl().Colls().fromMap(toScalaMap(mp), ErgoType.integerType().getRType(), ErgoType.longType().getRType());
        return new ErgoValue<>(value, type);
    }

    /**
     * Creates ErgoValue from hex encoded serialized bytes of Constant values.
     * <p>
     * In order to create ErgoValue you need to provide both value instance and
     * ErgoType descriptor. This is similar to how values are represented in sigma
     * ConstantNode. Each ConstantNode also have value instance and `tpe: SType`
     * descriptor.
     * Thus having ConstantNode we can use `Iso.isoErgoValueToSValue.from` method of to
     * convert ConstantNode to ErgoValue.
     *
     * @param hex the string is obtained as hex encoding of serialized ConstantNode.
     *            (The bytes obtained by ConstantSerializer in sigma)
     * @return new deserialized ErgoValue instance
     */
    static public ErgoValue<?> fromHex(String hex) {
        byte[] bytes = JavaHelpers.decodeStringToBytes(hex);
        Values.EvaluatedValue<SType> c = (Values.EvaluatedValue<SType>)ValueSerializer.deserialize(bytes, 0);
        ErgoValue<?> res = Iso.isoErgoValueToSValue().from(c);
        return res;
    }

}
