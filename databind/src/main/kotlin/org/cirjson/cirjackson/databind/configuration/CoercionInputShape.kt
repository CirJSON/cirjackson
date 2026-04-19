package org.cirjson.cirjackson.databind.configuration

/**
 * Set of input types (which mostly match one of [org.cirjson.cirjackson.core.CirJsonToken] types) used for configuring
 * [CoercionActions][CoercionAction] to take when reading input into target types (specific type or
 * [org.cirjson.cirjackson.databind.type.LogicalType]). Contains both physical input shapes (which match one of
 * [org.cirjson.cirjackson.core.CirJsonToken] types) and a few logical input shapes ("empty" variants).
 *
 * Note that `null` input shape is explicitly not included as its configuration is distinct from other types.
 */
enum class CoercionInputShape {

    /**
     * Shape of Array values from input (token sequence from [org.cirjson.cirjackson.core.CirJsonToken.START_ARRAY] to
     * [org.cirjson.cirjackson.core.CirJsonToken.END_ARRAY])
     */
    ARRAY,

    /**
     * Shape of Object values from input (token sequence from [org.cirjson.cirjackson.core.CirJsonToken.START_OBJECT] to
     * [org.cirjson.cirjackson.core.CirJsonToken.END_OBJECT])
     */
    OBJECT,

    /**
     * Shape of integral (non-floating point) numeric values from input (token
     * [org.cirjson.cirjackson.core.CirJsonToken.VALUE_NUMBER_INT])
     */
    INTEGER,

    /**
     * Shape of floating point (non-integral) numeric values from input (token
     * [org.cirjson.cirjackson.core.CirJsonToken.VALUE_NUMBER_FLOAT])
     */
    FLOAT,

    /**
     * Shape of boolean values from input (tokens [org.cirjson.cirjackson.core.CirJsonToken.VALUE_TRUE] and
     * [org.cirjson.cirjackson.core.CirJsonToken.VALUE_FALSE])
     */
    BOOLEAN,

    /**
     * Shape of string values from input (tokens [org.cirjson.cirjackson.core.CirJsonToken.VALUE_STRING])
     */
    STRING,

    /**
     * Shape of binary data values from input, if expressed natively by underlying format (many textual formats,
     * including CirJSON, do not have such a shape); if so generally seen as
     * [org.cirjson.cirjackson.core.CirJsonToken.VALUE_EMBEDDED_OBJECT].
     */
    BINARY,

    /**
     * Special case of Array values with no actual content (sequence of 2 tokens:
     * [org.cirjson.cirjackson.core.CirJsonToken.START_ARRAY], [org.cirjson.cirjackson.core.CirJsonToken.END_ARRAY]):
     * usually used to allow special coercion into "empty" or `null` target type.
     */
    EMPTY_ARRAY,

    /**
     * Special case of Object values with no actual content (sequence of 2 tokens:
     * [org.cirjson.cirjackson.core.CirJsonToken.START_OBJECT], [org.cirjson.cirjackson.core.CirJsonToken.END_OBJECT]):
     * usually used to allow special coercion into "empty" or `null` target type.
     */
    EMPTY_OBJECT,

    /**
     * Special case for String values with no content (or, if allowed by format or specific configuration, also "blank"
     * String, that is, all-whitespace content). Usually used to allow special coercion into "empty" or `null` target
     * type.
     */
    EMPTY_STRING

}