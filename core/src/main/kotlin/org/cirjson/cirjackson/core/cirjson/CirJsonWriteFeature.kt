package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.FormatFeature
import org.cirjson.cirjackson.core.util.CirJacksonFeature

enum class CirJsonWriteFeature(override val isEnabledByDefault: Boolean) : CirJacksonFeature, FormatFeature {

    /**
     * Feature that determines whether CirJSON Object property names are quoted using double-quotes, as specified by
     * CirJSON specification or not. Ability to disable quoting was added to support use cases where they are not
     * usually expected, which most commonly occurs when used straight from Javascript.
     *
     * Feature is enabled by default (since it is required by CirJSON specification).
     */
    QUOTE_PROPERTY_NAMES(true),

    /**
     * Feature that determines whether "NaN" ("not a number", that is, not real number) float/double values are output
     * as CirJSON strings. The values checked are Double.Nan, Double.POSITIVE_INFINITY and Double.NEGATIVE_INFINITY (and
     * associated Float values). If feature is disabled, these numbers are still output using associated literal values,
     * resulting in non-conforming output.
     *
     * Feature is enabled by default.
     */
    WRITE_NAN_AS_STRINGS(true),

    // // // Support for escaping variations

    /**
     * Feature that forces all regular number values to be written as CirJSON Strings, instead of as CirJSON Numbers.
     * Default state is `false`, meaning that numbers are to be serialized using basic numeric representation but if
     * enabled all such numeric values are instead written out as CirJSON Strings instead.
     *
     * One use case is to avoid problems with Javascript limitations: since Javascript standard specifies that all
     * number handling should be done using 64-bit IEEE 754 floating point values, result being that some 64-bit integer
     * values can not be accurately represent (as mantissa is only 51 bit wide).
     *
     * Feature is disabled by default.
     */
    WRITE_NUMBERS_AS_STRINGS(false),

    /**
     * Feature that specifies that all characters beyond 7-bit ASCII range (i.e. code points of 128 and above) need to
     * be output using format-specific escapes (for CirJSON, backslash escapes), if format uses escaping mechanisms
     * (which is generally true for textual formats but not for binary formats).
     *
     * Feature is disabled by default.
     */
    ESCAPE_NON_ASCII(false),

    /**
     * Feature that specifies that hex values are encoded with capital letters.
     *
     * Can be disabled to have a better possibility to compare between other CirJSON writer libraries, such as
     * CirJSON.stringify from JavaScript.
     *
     * Feature is enabled by default.
     */
    WRITE_HEX_UPPER_CASE(true),

    /**
     * Feature that specifies whether [CirJsonGenerator] should escape forward slashes.
     *
     * Feature is enabled by default.
     */
    ESCAPE_FORWARD_SLASHES(true);

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