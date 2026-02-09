package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import kotlin.reflect.KClass

abstract class PropertySerializerMap {

    abstract fun serializerFor(type: KClass<*>): ValueSerializer<Any>?

    fun findAndAddRootValueSerializer(type: KClass<*>, provider: SerializerProvider): SerializerAndMapResult {
        TODO("Not yet implemented")
    }

    fun findAndAddRootValueSerializer(type: KotlinType, provider: SerializerProvider): SerializerAndMapResult {
        TODO("Not yet implemented")
    }

    fun addSerializer(type: KClass<*>, serializer: ValueSerializer<Any>): SerializerAndMapResult {
        TODO("Not yet implemented")
    }

    fun addSerializer(type: KotlinType, serializer: ValueSerializer<Any>): SerializerAndMapResult {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    data class SerializerAndMapResult(val serializer: ValueSerializer<Any>, val map: PropertySerializerMap)

    companion object {

        fun emptyForProperties(): PropertySerializerMap {
            TODO("Not yet implemented")
        }

        fun emptyForRootValues(): PropertySerializerMap {
            TODO("Not yet implemented")
        }

    }

}