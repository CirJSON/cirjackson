package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.TypeKey
import kotlin.reflect.KClass

class ReadOnlyClassToSerializerMap private constructor(private val mySharedCache: SerializerCache,
        source: LookupCache<TypeKey, ValueSerializer<Any>>) {

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    fun typedValueSerializer(type: KotlinType): ValueSerializer<Any>? {
        TODO("Not yet implemented")
    }

    fun typedValueSerializer(rawType: KClass<*>): ValueSerializer<Any>? {
        TODO("Not yet implemented")
    }

    fun untypedValueSerializer(type: KotlinType): ValueSerializer<Any>? {
        TODO("Not yet implemented")
    }

    fun untypedValueSerializer(rawType: KClass<*>): ValueSerializer<Any>? {
        TODO("Not yet implemented")
    }

}