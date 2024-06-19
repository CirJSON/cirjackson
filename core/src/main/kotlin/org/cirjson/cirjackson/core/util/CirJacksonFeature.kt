package org.cirjson.cirjackson.core.util

/**
 * Basic API implemented by Enums used for simple CirJackson "features": on/off settings and capabilities exposed as
 * something that can be internally represented as bit sets.
 *
 * Designed to be used with [CirJacksonFeatureSet].
 */
interface CirJacksonFeature {

    /**
     * Accessor for checking whether this feature is enabled by default.
     */
    val isEnabledByDefault: Boolean

    /**
     * Returns bit mask for this feature instance; must be a single bit, that is of form `1 << N`.
     */
    val mask: Int

    /**
     * Convenience method for checking whether feature is enabled in given bitmask.
     *
     * @param flags Bit field that contains a set of enabled features of this type
     *
     * @return True if this feature is enabled in passed bit field
     */
    fun isEnabledIn(flags: Int): Boolean

}