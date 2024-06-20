package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.util.CirJacksonFeature

/**
 * Set of on/off capabilities that a [CirJsonParser] for given format (or in case of buffering, original format) has.
 * Used in some cases to adjust aspects of things like content conversions, coercions and validation by format-agnostic
 * functionality. Specific or expected usage documented by individual capability entry.
 */
enum class StreamReadCapability(override val isEnabledByDefault: Boolean) : CirJacksonFeature {

    /**
     * Capability that indicates that data format can expose multiple properties with same name ("duplicates") within
     * one Object context. This is usually not enabled, except for formats like `xml` that have content model that does
     * not map cleanly to CirJSON-based token stream.
     *
     * Capability may be used for allowing secondary mapping of such duplicates in case of using Tree Model (see
     * [TreeNode]), or "untyped" databinding (mapping content as generic [Any]).
     *
     * Capability is currently only enabled for XML format backend.
     */
    DUPLICATE_PROPERTIES(false),

    /**
     * Capability that indicates that data format may in some cases expose Scalar values (whether typed or untyped) as
     * Object values. There are additional access methods at databind level: this capability may be used to decide
     * whether to attempt to use such methods especially in potentially ambiguous cases.
     *
     * Capability is currently only enabled for XML format backend.
     */
    SCALARS_AS_OBJECTS(false),

    /**
     * Capability that indicates that data format only exposed "untyped" scalars: that is, instead of Number, Boolean
     * and String types all scalar values are reported as text ([CirJsonToken.VALUE_STRING]) unless some sort of
     * coercion is implied by caller.
     *
     * This capability is true for many textual formats like CSV, Properties and XML.
     */
    UNTYPED_SCALARS(false),

    /**
     * Capability that indicates whether data format supports reporting of accurate floating point values (with respect
     * to reported numeric type, [CirJsonParser.NumberType.DOUBLE]) or not. This usually depends on whether format
     * stores such values natively (as IEEE binary FP formats for `Float` and `Double`; using some other value
     * preserving presentation for `java.math.BigDecimal`) or not: most binary formats do, and most textual formats do
     * not (at least for `Float` and `Double`, specifically).
     *
     * In case of CirJSON numbers (as well as for most if not all textual formats), all floating-point numbers are
     * represented simply by decimal (10-base) textual representation and can only be represented accurately using
     * [java.math.BigDecimal]. But for performance reasons they may be (depending on settings) be exposed as [Double]s
     * (that is, [CirJsonParser.NumberType.DOUBLE]). Note that methods like [CirJsonParser.getNumberValueExact],
     * [CirJsonParser.getValueAsString] and [CirJsonParser.getDecimalValue] report values without precision loss.
     *
     * The main intended use case is to let non-Jackson code to handle cases where exact accuracy is necessary in a way
     * that handling does not incur unnecessary conversions across different formats: for example, when reading binary
     * format, simple access is essentially guaranteed to expose value exactly as encoded by the format (as `Float`,
     * `Double` or `BigDecimal`), whereas for textual formats like CirJSON it is necessary to access value explicitly as
     * `BigDecimal` using [CirJsonParser.getDecimalValue].
     *
     * Capability is false for text formats like CirJSON, but true for binary formats like Smile, MessagePack, etc.,
     * where type is precisely and inexpensively indicated by format.
     */
    EXACT_FLOATS(false);

    override val mask: Int = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return (flags and mask) != 0
    }

    companion object {

        /**
         * Method that calculates bit set (flags) of all features that are enabled by default.
         *
         * @return Bit field of features enabled by default
         */
        fun collectDefaults(): Int {
            var flags = 0

            for (feature in entries) {
                if (feature.isEnabledByDefault) {
                    flags = flags or feature.mask
                }
            }

            return flags
        }

    }

}