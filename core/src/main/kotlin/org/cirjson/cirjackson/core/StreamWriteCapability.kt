package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.util.CirJacksonFeature

/**
 * Set of on/off capabilities that a [CirJsonGenerator] for given format (or in case of buffering, original format) has.
 * Used in some cases to adjust aspects of things like content conversions and coercions by format-agnostic
 * functionality. Specific or expected usage documented by individual capability entry.
 */
enum class StreamWriteCapability(override val isEnabledByDefault: Boolean) : CirJacksonFeature {

    /**
     * Capability that indicates that the data format is able to express binary data natively, without using textual
     * encoding like Base64.
     *
     * Capability is currently enabled for all binary formats and none of textual formats.
     *
     * Feature is disabled by default.
     */
    CAN_WRITE_BINARY_NATIVELY(false),

    /**
     * Capability that indicates that the data format is able to write "formatted numbers": that is, output of numbers
     * is done as Strings and caller is allowed to pass in logical number values as Strings.
     *
     * Capability is currently enabled for most textual formats and none of binary formats.
     *
     * Feature is disabled by default.
     */
    CAN_WRITE_FORMATTED_NUMBERS(false);

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