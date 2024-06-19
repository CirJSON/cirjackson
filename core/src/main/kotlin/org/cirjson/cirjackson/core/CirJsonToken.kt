package org.cirjson.cirjackson.core

/**
 * Enumeration for basic token types used for returning results of parsing CirJSON content.
 */
enum class CirJsonToken(val token: String?, val id: Int) {

    /**
     * NOT_AVAILABLE can be returned if [CirJsonParser] implementation can not currently return the requested token
     * (usually next one), or even if any will be available, but that may be able to determine this in the future. This
     * is the case with non-blocking parsers -- they can not block to wait for more data to parse and must return
     * something.
     */
    NOT_AVAILABLE(null, CirJsonTokenId.ID_NOT_AVAILABLE),

    /**
     * START_OBJECT is returned when encountering `{` which signals starting of an Object value.
     */
    START_OBJECT("{", CirJsonTokenId.ID_START_OBJECT),

    /**
     * END_OBJECT is returned when encountering `}` which signals ending of an Object value
     */
    END_OBJECT("}", CirJsonTokenId.ID_END_OBJECT),

    /**
     * START_ARRAY is returned when encountering `[` which signals starting of an Array value
     */
    START_ARRAY("[", CirJsonTokenId.ID_START_ARRAY),

    /**
     * END_ARRAY is returned when encountering `]` which signals ending of an Array value
     */
    END_ARRAY("]", CirJsonTokenId.ID_END_ARRAY),

    /**
     * CIRJSON_ID_PROPERTY_NAME is returned when a String token with the value `"__cirJsonId__"` is encountered as a
     * property name (same lexical value, different function)
     */
    CIRJSON_ID_PROPERTY_NAME("__cirJsonId__", CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME),

    /**
     * PROPERTY_NAME is returned when a String token is encountered as a property name (same lexical value, different
     * function)
     */
    PROPERTY_NAME(null, CirJsonTokenId.ID_PROPERTY_NAME),

    /**
     * Placeholder token returned when the input source has a concept of embedded Object that are not accessible as
     * usual structure (of starting with [START_OBJECT], having values, ending with [END_OBJECT]), but as "raw" objects.
     *
     * Note: this token is never returned by regular JSON readers, but only by readers that expose other kinds of source
     * (like `CirJsonNode`-based CirJSON trees, Maps, Lists and such).
     */
    VALUE_EMBEDDED_OBJECT(null, CirJsonTokenId.ID_EMBEDDED_OBJECT),

    /**
     * VALUE_STRING is returned when a String token is encountered in value context (array element, object property
     * value, or root-level stand-alone value)
     */
    VALUE_STRING(null, CirJsonTokenId.ID_STRING),

    /**
     * VALUE_NUMBER_INT is returned when an integer numeric token is encountered in value context: that is, a number
     * that does not have floating point or exponent marker in it (consists only of an optional sign, followed by one or
     * more digits; or, for binary formats, is indicated as integral number by internal representation).
     */
    VALUE_NUMBER_INT(null, CirJsonTokenId.ID_NUMBER_INT),

    /**
     * VALUE_NUMBER_FLOAT is returned when a numeric token other than integer is encountered: that is, a number that
     * does have floating point or exponent marker in it, in addition to one or more digits (or, for non-textual
     * formats, has internal floating-point representation).
     */
    VALUE_NUMBER_FLOAT(null, CirJsonTokenId.ID_NUMBER_FLOAT),

    /**
     * VALUE_TRUE is returned when encountering literal "true" in value context
     */
    VALUE_TRUE("true", CirJsonTokenId.ID_TRUE),

    /**
     * VALUE_FALSE is returned when encountering literal "false" in value context
     */
    VALUE_FALSE("false", CirJsonTokenId.ID_FALSE),

    /**
     * VALUE_NULL is returned when encountering literal "null" in value context
     */
    VALUE_NULL("null", CirJsonTokenId.ID_NULL);

    val charArrayRepresentation = token?.toCharArray()

    val byteArrayRepresentation = charArrayRepresentation?.let { ByteArray(it.size) { i -> it[i].code.toByte() } }

    /**
     * `true` if this token is `VALUE_NUMBER_INT` or `VALUE_NUMBER_FLOAT`, `false` otherwise
     */
    val isNumeric = id == CirJsonTokenId.ID_NUMBER_INT || id == CirJsonTokenId.ID_NUMBER_FLOAT

    /**
     * Accessor that is functionally equivalent to:
     * ```
     * this == CirJsonToken.START_OBJECT || this == CirJsonToken.START_ARRAY
     * ```
     *
     * `True` if this token is `START_OBJECT` or `START_ARRAY`, `false` otherwise
     */
    val isStructStart = id == CirJsonTokenId.ID_START_OBJECT || id == CirJsonTokenId.ID_START_ARRAY

    /**
     * Accessor that is functionally equivalent to:
     * ```
     * this == CirJsonToken.END_OBJECT || this == CirJsonToken.END_ARRAY
     * ```
     *
     * `True` if this token is `END_OBJECT` or `END_ARRAY`, `false` otherwise
     */
    val isStructEnd = id == CirJsonTokenId.ID_END_OBJECT || id == CirJsonTokenId.ID_END_ARRAY

    /**
     * Method that can be used to check whether this token represents a valid non-structured value. This means all
     * `VALUE_xxx` tokens; excluding `START_xxx` and `END_xxx` tokens as well as `CIRJSON_ID_PROPERTY_NAME` and
     * `PROPERTY_NAME`.
     *
     * `True` if this token is a scalar value token (one of `VALUE_xxx` tokens), `false` otherwise
     */
    val isScalarValue = !isStructStart && !isStructEnd &&
            !(id == CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME || id == CirJsonTokenId.ID_PROPERTY_NAME)

    /**
     * `True` if this token is `VALUE_TRUE` or `VALUE_FALSE`, `false` otherwise
     */
    val isBoolean = id == CirJsonTokenId.ID_TRUE || id == CirJsonTokenId.ID_FALSE

    companion object {

        /**
         * Helper method for constructing description like "Object value" given [CirJsonToken] encountered.
         *
         * @param t Token to get description for
         *
         * @return Description for token
         */
        fun valueDescFor(token: CirJsonToken?): String {
            return when (token) {
                null -> "<end of input>"
                START_OBJECT, END_OBJECT, CIRJSON_ID_PROPERTY_NAME, PROPERTY_NAME -> "Object value"
                START_ARRAY, END_ARRAY -> "Array value"
                VALUE_FALSE, VALUE_TRUE -> "Boolean value"
                VALUE_EMBEDDED_OBJECT -> "Embedded Object value"
                VALUE_NUMBER_FLOAT -> "Floating-point value"
                VALUE_NUMBER_INT -> "Int value"
                VALUE_STRING -> "String value"
                VALUE_NULL -> "Null value"
                NOT_AVAILABLE -> "[Unavailable value]"
            }
        }

    }

}