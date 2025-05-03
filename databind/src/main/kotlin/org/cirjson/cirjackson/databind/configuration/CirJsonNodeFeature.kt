package org.cirjson.cirjackson.databind.configuration

/**
 * Datatype-specific configuration options related to handling of [org.cirjson.cirjackson.databind.CirJsonNode] types.
 */
enum class CirJsonNodeFeature(override val isEnabledByDefault: Boolean) : DatatypeFeature {

    /**
     * When reading [CirJsonNodes][org.cirjson.cirjackson.databind.CirJsonNode] are `null`-valued properties included as
     * explicit [NullNodes][org.cirjson.cirjackson.databind.node.NullNode] in resulting
     * [org.cirjson.cirjackson.databind.node.ObjectNode] or skipped?
     *
     * The feature is enabled by default.
     */
    READ_NULL_PROPERTIES(true),

    /**
     * When writing [CirJsonNodes][org.cirjson.cirjackson.databind.CirJsonNode] are `null`-valued properties written as
     * explicit CirJSON `nulls` or skipped?
     *
     * The feature is enabled by default.
     */
    WRITE_NULL_PROPERTIES(true),

    /**
     * When writing [CirJsonNodes][org.cirjson.cirjackson.databind.CirJsonNode] are Object properties (for
     * [ObjectNodes][org.cirjson.cirjackson.databind.node.ObjectNode]) sorted alphabetically (using natural order of
     * [String]) or not? If not sorted, order is the insertion order; when reading this also means retaining order from
     * the input document.
     *
     * The feature is disabled by default.
     */
    WRITE_PROPERTIES_SORTED(false),

    /**
     * Feature that determines whether [java.math.BigDecimal] values will be "normalized" by stripping trailing zeroes
     * off, when constructing nodes with [org.cirjson.cirjackson.databind.node.CirJsonNodeFactory.numberNode]. If
     * enabled, [java.math.BigDecimal.stripTrailingZeros] will be called prior to node creation; if disabled, numeric
     * value will be used as is.
     *
     * The feature is disabled by default.
     */
    STRIP_TRAILING_BIGDECIMAL_ZEROES(false),

    /**
     * Determines the behavior when coercing `NaN` to [java.math.BigDecimal] with
     * [org.cirjson.cirjackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS] enabled.
     *
     * 1. If set to `true`, will throw an [org.cirjson.cirjackson.databind.exception.InvalidFormatException] for
     * attempting to coerce `NaN` into [java.math.BigDecimal].
     *
     * 2. If set to `false`, will simply let coercing `NaN` into [java.math.BigDecimal] happen, regardless of how such
     * coercion will behave. It will simply stay as `NaN` of original floating-point type node.
     *
     * The feature is disabled by default.
     */
    FAIL_ON_NAN_TO_BIG_DECIMAL_COERCION(false);

    override val mask = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return flags and mask != 0
    }

    override fun featureIndex(): Int {
        return FEATURE_INDEX
    }

    companion object {

        const val FEATURE_INDEX = DatatypeFeatures.FEATURE_INDEX_CIRJSON_NODE

    }
}