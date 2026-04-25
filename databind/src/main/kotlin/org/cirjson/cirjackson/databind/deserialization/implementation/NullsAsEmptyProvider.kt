package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.util.AccessPattern

/**
 * Simple [NullValueProvider] that will return "empty value" specified by [ValueDeserializer] provider is constructed
 * with.
 */
open class NullsAsEmptyProvider(protected val myDeserializer: ValueDeserializer<*>) : NullValueProvider {

    override val nullAccessPattern: AccessPattern
        get() = AccessPattern.DYNAMIC

    override fun getNullValue(context: DeserializationContext): Any? {
        return myDeserializer.getNullValue(context)
    }

}