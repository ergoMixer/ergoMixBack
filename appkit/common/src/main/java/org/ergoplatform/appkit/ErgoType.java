package org.ergoplatform.appkit;

import scala.Option;
import scala.Tuple2;
import scalan.RType;
import special.collection.Coll;
import special.sigma.*;

/**
 * Runtime representation of ErgoScript types. ErgoType is a Java friendly
 * wrapper around {@link RType} type descriptor.
 */
public class ErgoType<T> {
    private static ErgoType<Byte> _byte = new ErgoType<>(RType.ByteType());
    private static ErgoType<Short> _short = new ErgoType<>(RType.ShortType());
    private static ErgoType<Integer> _integer = new ErgoType<>(RType.IntType());
    private static ErgoType<Long> _long = new ErgoType<>(RType.LongType());
    private static ErgoType<BigInt> _bigInt = new ErgoType<>(JavaHelpers.BigIntRType());
    private static ErgoType<GroupElement> _groupElement = new ErgoType<>(JavaHelpers.GroupElementRType());
    private static ErgoType<SigmaProp> _sigmaProp = new ErgoType<>(JavaHelpers.SigmaPropRType());
    private static ErgoType<AvlTree> _avlTree = new ErgoType<>(JavaHelpers.AvlTreeRType());
    private static ErgoType<Header> _header = new ErgoType<>(JavaHelpers.HeaderRType());
    private static ErgoType<PreHeader> _preHeader = new ErgoType<>(JavaHelpers.PreHeaderRType());

    public RType<T> getRType() {
        return (RType<T>)_rtype;
    }

    private final RType<?> _rtype;

    /** Use static methods to create instances. */
    ErgoType(RType<?> rtype) {
        _rtype = rtype;
    }

    static public ErgoType<Byte> byteType() { return _byte; }

    static public ErgoType<Short> shortType() { return _short; }

    static public ErgoType<Integer> integerType() { return _integer; }

    static public ErgoType<Long> longType() { return _long; }

    static public ErgoType<BigInt> bigIntType() { return _bigInt; }

    static public ErgoType<GroupElement> groupElementType() { return _groupElement; }

    static public ErgoType<SigmaProp> sigmaPropType() { return _sigmaProp; }

    static public ErgoType<AvlTree> avlTreeType() { return _avlTree; }

    static public ErgoType<Header> headerType() { return _header; }

    static public ErgoType<PreHeader> preHeaderType() { return _preHeader; }

    static public <A, B> ErgoType<Tuple2<A, B>> pairType(ErgoType<A> tA, ErgoType<B> tB) {
        return new ErgoType<>(RType.pairRType(tA._rtype, tB._rtype));
    }

    static public <A> ErgoType<Coll<A>> collType(ErgoType<A> tItem) {
        return new ErgoType<>(JavaHelpers.collRType(tItem._rtype));
    }

    static public <A> ErgoType<Option<A>> optionType(ErgoType<A> tItem) {
        return new ErgoType<>(RType.optionRType(tItem._rtype));
    }
}
