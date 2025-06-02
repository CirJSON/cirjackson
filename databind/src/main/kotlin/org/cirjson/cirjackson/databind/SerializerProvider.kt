package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import kotlin.reflect.KClass

abstract class SerializerProvider {

    val generator: CirJsonGenerator?
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Serializer discovery: root/non-property value serializers
     *******************************************************************************************************************
     */

    fun findTypedValueSerializer(rawType: KClass<*>, cache: Boolean): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    fun findTypedValueSerializer(valueType: KotlinType, cache: Boolean): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for serializing using default methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun defaultSerializeNullValue(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

}