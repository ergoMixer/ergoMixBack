package org.ergoplatform.appkit;

import sigmastate.lang.SigmaBuilder;

import java.util.LinkedHashMap;

/**
 * This class is used to store values of named constants for ErgoScript compiler.
 * The values are any objects convertible to ErgoScript values.
 * @see SigmaBuilder#liftAny(Object) liftAny method of SigmaBuilder
 */
public class Constants extends LinkedHashMap<String, Object> {
}
