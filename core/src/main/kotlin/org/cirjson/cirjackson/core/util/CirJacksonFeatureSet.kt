package org.cirjson.cirjackson.core.util

/**
 * Container similar to [java.util.EnumSet] meant for storing sets of [CirJacksonFeature]s (usually [Enum]s): main
 * difference being that these sets are immutable. Also, only supports relatively small sets of features: specifically,
 * up to 31 features.
 *
 * @param F The type of [CirJacksonFeature]s maintained by this set
 *
 * @constructor Constructor for creating instance with specific bitmask, wherein `1` bit means matching
 * [CirJacksonFeature] is enabled and `0` disabled.
 *
 * @param bitmask Bitmask for features that are enabled
 *
 * @property bitmask Bitmask of enabled features
 */
class CirJacksonFeatureSet<F : CirJacksonFeature>(val bitmask: Int) {

    /**
     * Mutant factory for getting a set in which specified feature is enabled: will either return this instance (if no
     * change), or newly created set (if there is change).
     *
     * @param feature Feature to enable in set returned
     *
     * @return Newly created set of state of feature changed; `this` if not
     */
    fun with(feature: F): CirJacksonFeatureSet<F> {
        val newMask = bitmask or feature.mask
        return if (newMask != bitmask) CirJacksonFeatureSet(newMask) else this
    }

    /**
     * Mutant factory for getting a set in which specified feature is disabled: will either return this instance (if no
     * change), or newly created set (if there is change).
     *
     * @param feature Feature to disable in set returned
     *
     * @return Newly created set of state of feature changed; `this` if not
     */
    fun without(feature: F): CirJacksonFeatureSet<F> {
        val newMask = bitmask and feature.mask.inv()
        return if (newMask != bitmask) CirJacksonFeatureSet(newMask) else this
    }

    /**
     * Main accessor for checking whether given feature is enabled in this feature set.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled in this set; `false` otherwise
     */
    fun isEnabled(feature: F): Boolean {
        return feature.mask and bitmask != 0
    }

    companion object {

        /**
         * "Default" factory which will calculate settings based on default-enabled status of all features.
         *
         * @param F Self-reference type for convenience of [CirJacksonFeature]s maintained by this set
         *
         * @param allFeatures Set of all features (enabled or disabled): usually from `Enum.entries`
         *
         * @return Feature set instance constructed
         */
        fun <F : CirJacksonFeature> fromDefaults(allFeatures: List<F>): CirJacksonFeatureSet<F> {
            if (allFeatures.size > 31) {
                val desc = allFeatures[0].javaClass.name
                throw IllegalArgumentException(
                        "Cannot use type `$desc` with JacksonFeatureSet: too many entries (${allFeatures.size} > 31)")
            }

            var flags = 0
            for (feature in allFeatures) {
                if (feature.isEnabledByDefault) {
                    flags = flags or feature.mask
                }
            }

            return CirJacksonFeatureSet(flags)
        }

        fun <F : CirJacksonFeature> fromBitmask(bitmask: Int): CirJacksonFeatureSet<F> {
            return CirJacksonFeatureSet(bitmask)
        }

    }

}