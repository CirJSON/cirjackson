package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import kotlin.reflect.KClass

/**
 * Simple general purpose serializer, useful for any type for which [Any.toString] returns the desired String
 * serialization value.
 * 
 * NOTE: this is NOT meant to be used as a base class for custom serializers; instead, consider base type
 * [ToStringSerializerBase] if you need similar functionality.
 */
@CirJacksonStandardImplementation
class ToStringSerializer(handledType: KClass<*>) : ToStringSerializerBase(handledType) {

    private constructor() : this(Any::class)

    override fun valueToString(value: Any): String {
        return value.toString()
    }

    companion object {

        val INSTANCE = ToStringSerializer()

    }

}