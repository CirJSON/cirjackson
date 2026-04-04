package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

/**
 * Simple serializer that will call configured type serializer, passing in configured data serializer, and exposing it
 * all as a simple serializer.
 */
class TypeWrappedSerializer(private val myTypeSerializer: TypeSerializer, serializer: ValueSerializer<*>) :
        ValueSerializer<Any>() {

    @Suppress("UNCHECKED_CAST")
    private val mySerializer = serializer as ValueSerializer<Any>

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        mySerializer.serializeWithType(value, generator, serializers, myTypeSerializer)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        mySerializer.serializeWithType(value, generator, serializers, typeSerializer)
    }

    override fun handledType(): KClass<*> {
        return Any::class
    }

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val serializer = provider.handleSecondaryContextualization(mySerializer, property)!!

        if (serializer === mySerializer) {
            return this
        }

        return TypeWrappedSerializer(myTypeSerializer, serializer)
    }

    /*
     *******************************************************************************************************************
     * Extended API for other core classes
     *******************************************************************************************************************
     */

    fun valueSerializer(): ValueSerializer<Any> {
        return mySerializer
    }

    fun typeSerializer(): TypeSerializer {
        return myTypeSerializer
    }

}