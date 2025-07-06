package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.util.CirJacksonFeature
import org.cirjson.cirjackson.core.util.Other
import kotlin.enums.EnumEntries

/**
 * Immutable value class that contains settings for multiple [DatatypeFeature] enumerations.
 */
open class DatatypeFeatures protected constructor(private val myEnabledFor1: Int, private val myExplicitFor1: Int,
        private val myEnabledFor2: Int, private val myExplicitFor2: Int) {

    private fun withMasks(enabledFor1: Int, explicitFor1: Int, enabledFor2: Int, explicitFor2: Int): DatatypeFeatures {
        if (myEnabledFor1 == enabledFor1 && myExplicitFor1 == explicitFor1 && myEnabledFor2 == enabledFor2 &&
                myExplicitFor2 == explicitFor2) {
            return this
        }

        return DatatypeFeatures(enabledFor1, explicitFor1, enabledFor2, explicitFor2)
    }

    /*
     *******************************************************************************************************************
     * Public mutant factory methods
     *******************************************************************************************************************
     */

    /**
     * Mutant factory method that returns an instance with the given Feature explicitly enabled.
     *
     * @param feature [DatatypeFeature] to explicitly enable in this set
     *
     * @return Existing instance if there is no change (feature was already explicitly enabled), or a new instance with
     * feature explicitly enabled (if it was not).
     */
    fun with(feature: DatatypeFeature): DatatypeFeatures {
        val mask = feature.mask

        return when (feature.featureIndex()) {
            0 -> withMasks(myEnabledFor1 or mask, myExplicitFor1 or mask, myEnabledFor2, myExplicitFor2)
            1 -> withMasks(myEnabledFor1, myExplicitFor1, myEnabledFor2 or mask, myExplicitFor2 or mask)
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * "Bulk" variant of [with] which allows explicit enabling of multiple features
     *
     * @param features [DatatypeFeatures][DatatypeFeature] to explicitly enable in this set
     *
     * @return Existing instance if there is no change (features were already explicitly enabled), or a new instance
     * with features explicitly enabled (if it was not).
     */
    fun withFeatures(vararg features: DatatypeFeature): DatatypeFeatures {
        val mask = calculateMask(*features)

        return when (features[0].featureIndex()) {
            0 -> withMasks(myEnabledFor1 or mask, myExplicitFor1 or mask, myEnabledFor2, myExplicitFor2)
            1 -> withMasks(myEnabledFor1, myExplicitFor1, myEnabledFor2 or mask, myExplicitFor2 or mask)
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * Mutant factory method that returns an instance with the given Feature explicitly disabled.
     *
     * @param feature [DatatypeFeature] to explicitly disable in this set
     *
     * @return Existing instance if there is no change (feature was already explicitly disabled), or a new instance with
     * feature explicitly disabled (if it was not).
     */
    fun without(feature: DatatypeFeature): DatatypeFeatures {
        val mask = feature.mask

        return when (feature.featureIndex()) {
            0 -> withMasks(myEnabledFor1 and mask.inv(), myExplicitFor1 and mask.inv(), myEnabledFor2, myExplicitFor2)
            1 -> withMasks(myEnabledFor1, myExplicitFor1, myEnabledFor2 and mask.inv(), myExplicitFor2 and mask.inv())
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * "Bulk" variant of [without] which allows explicit disabling of multiple features
     *
     * @param features [DatatypeFeatures][DatatypeFeature] to explicitly disable in this set
     *
     * @return Existing instance if there is no change (features were already explicitly disabled), or a new instance
     * with features explicitly disabled (if it was not).
     */
    fun withoutFeatures(vararg features: DatatypeFeature): DatatypeFeatures {
        val mask = calculateMask(*features)

        if (mask == 0) {
            return this
        }

        return when (features[0].featureIndex()) {
            0 -> withMasks(myEnabledFor1 and mask.inv(), myExplicitFor1 and mask.inv(), myEnabledFor2, myExplicitFor2)
            1 -> withMasks(myEnabledFor1, myExplicitFor1, myEnabledFor2 and mask.inv(), myExplicitFor2 and mask.inv())
            else -> Other.throwInternalReturnAny()
        }
    }

    /*
     *******************************************************************************************************************
     * Public accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor for getting value of specified feature in this set, regardless of whether explicit defined or not (if
     * not explicitly enabled/disabled returns default value, [DatatypeFeature.isEnabledByDefault].
     *
     * @param feature Feature to check
     *
     * @return `true` if the specified Feature is enabled in this set either explicitly or by having enabled by default
     * (if not explicitly enabled or disabled).
     */
    fun isEnabled(feature: DatatypeFeature): Boolean {
        return when (feature.featureIndex()) {
            0 -> feature.isEnabledIn(myEnabledFor1)
            1 -> feature.isEnabledIn(myEnabledFor2)
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * Accessor for checking whether the given feature has been explicitly enabled or disabled in this set or not: if
     * not, it has the default value.
     *
     * @param feature Feature to check
     *
     * @return Whether given feature has been explicitly set (enabled or disabled) in this set or not.
     */
    fun isExplicitlySet(feature: DatatypeFeature): Boolean {
        return when (feature.featureIndex()) {
            0 -> feature.isEnabledIn(myExplicitFor1)
            1 -> feature.isEnabledIn(myExplicitFor2)
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * Convenience method equivalent to:
     * ```
     * isExplicitlySet(feature) && isEnabled(feature)
     * ```
     *
     * @param feature Feature to check
     *
     * @return Whether given feature has been explicitly enabled
     */
    fun isExplicitlyEnabled(feature: DatatypeFeature): Boolean {
        return when (feature.featureIndex()) {
            0 -> feature.isEnabledIn(myExplicitFor1 and myEnabledFor1)
            1 -> feature.isEnabledIn(myExplicitFor2 and myEnabledFor2)
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * Convenience method equivalent to:
     * ```
     * isExplicitlySet(feature) && isEnabled(feature)
     * ```
     *
     * @param feature Feature to check
     *
     * @return Whether given feature has been explicitly disabled
     */
    fun isExplicitlyDisabled(feature: DatatypeFeature): Boolean {
        return when (feature.featureIndex()) {
            0 -> feature.isEnabledIn(myExplicitFor1 and myEnabledFor1.inv())
            1 -> feature.isEnabledIn(myExplicitFor2 and myEnabledFor2.inv())
            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * Accessor for getting explicit state of given feature in this set iff explicitly set, or `null` if not explicitly
     * set (default value)
     *
     * @param feature Feature to check
     *
     * @return `true` if Feature has been explicitly enabled in this set; `false` if Feature has been explicitly
     * disabled in this set; or `null` if Feature still has its default value.
     */
    fun getExplicitState(feature: DatatypeFeature): Boolean? {
        return when (feature.featureIndex()) {
            0 -> if (feature.isEnabledIn(myExplicitFor1)) feature.isEnabledIn(myEnabledFor1) else null
            1 -> if (feature.isEnabledIn(myExplicitFor2)) feature.isEnabledIn(myEnabledFor2) else null
            else -> Other.throwInternalReturnAny()
        }
    }

    companion object {

        const val FEATURE_INDEX_ENUM = 0

        const val FEATURE_INDEX_CIRJSON_NODE = 1

        val DEFAULT_FEATURES =
                DatatypeFeatures(collectDefaults(EnumFeature.entries), 0, collectDefaults(CirJsonNodeFeature.entries),
                        0)

        private fun <F> collectDefaults(features: EnumEntries<F>): Int where F : Enum<F>, F : CirJacksonFeature {
            var flags = 0

            for (feature in features) {
                if (feature.isEnabledByDefault) {
                    flags = flags or feature.mask
                }
            }

            return flags
        }

        private fun calculateMask(vararg features: DatatypeFeature): Int {
            var mask = 0

            for (feature in features) {
                mask = mask or feature.mask
            }

            return mask
        }

    }

}