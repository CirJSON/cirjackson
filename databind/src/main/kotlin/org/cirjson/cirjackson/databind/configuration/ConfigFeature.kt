package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.util.enumConstants
import kotlin.reflect.KClass

/**
 * Interface that actual SerializationFeature enumerations used by [MapperConfig] implementations must implement.
 * Necessary since enums cannot be extended using normal inheritance, but can implement interfaces
 */
interface ConfigFeature {

    /**
     * Accessor for checking whether this feature is enabled by default.
     */
    val isEnabledByDefault: Boolean

    /**
     * Returns bit mask for this feature instance
     */
    val mask: Int

    /**
     * Convenience method for checking whether feature is enabled in given bitmask
     */
    fun enabledIn(flags: Int): Boolean

    companion object {

        fun <F> collectFeatureDefaults(kClass: KClass<F>): Int where F : Enum<F>, F : ConfigFeature {
            var flags = 0

            for (value in kClass.enumConstants) {
                if (value.isEnabledByDefault) {
                    flags = flags or value.mask
                }
            }

            return flags
        }

    }

}