package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import kotlin.reflect.KClass

/**
 * This is the special serializer for regular [Strings][String].
 * 
 * Since this is one of "native" types, no type information is ever included on serialization.
 */
@CirJacksonStandardImplementation
@Suppress("UNCHECKED_CAST")
class StringSerializer : StandardScalarSerializer<Any>(String::class as KClass<*> as KClass<Any>) {

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        val string = value as String?
        return string!!.isEmpty()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeString(value as String)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.writeString(value as String)
    }

}