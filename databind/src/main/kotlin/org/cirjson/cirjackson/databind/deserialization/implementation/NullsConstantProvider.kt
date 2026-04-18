package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.util.AccessPattern

/**
 * Simple [NullValueProvider] that will simply return given constant value when a `null` is encountered; or, with a
 * specially constructed instance (see [skipper], indicate the need for special behavior of skipping property altogether
 * (not setting as anything OR throwing exception).
 */
open class NullsConstantProvider protected constructor(protected val myNullValue: Any?) : NullValueProvider {

    protected val myAccess = myNullValue?.let { AccessPattern.CONSTANT } ?: AccessPattern.ALWAYS_NULL

    override val nullAccessPattern: AccessPattern
        get() = myAccess

    override fun getNullValue(context: DeserializationContext): Any? {
        return myNullValue
    }

    companion object {

        private val SKIPPER = NullsConstantProvider(null)

        private val NULLER = NullsConstantProvider(null)

        /**
         * Static accessor for a stateless instance used as marker, to indicate that all input `null` values should be
         * skipped (ignored). Thus, no corresponding property value is set (with POJOs), and no content values
         * (array/Collection elements, Map entries) are added.
         */
        fun skipper(): NullsConstantProvider {
            return SKIPPER
        }

        fun nuller(): NullsConstantProvider {
            return NULLER
        }

        fun forValue(value: Any?): NullsConstantProvider {
            value ?: return NULLER
            return NullsConstantProvider(value)
        }

        /**
         * Utility method that can be used to check if given null value provider is "skipper", marker provider that
         * means that all input `nulls` should be skipped (ignored), instead of converted.
         */
        fun isSkipper(provider: NullValueProvider): Boolean {
            return SKIPPER === provider
        }

        /**
         * Utility method that can be used to check if given null value provider is "nuller", no-operation provider that
         * will always simply return Kotlin `null` for any and all input `nulls`.
         */
        fun isNuller(provider: NullValueProvider): Boolean {
            return NULLER === provider
        }

    }

}