package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.TypeKey

/**
 * Interface that defines API CirJackson uses for constructing various internal caches. This allows configuring custom
 * caches and cache configurations. A [CacheProvider] instance will be configured through a builder such as
 * [org.cirjson.cirjackson.databind.cirjson.CirJsonMapper.Builder.cacheProvider].
 */
interface CacheProvider {

    /**
     * Method to provide a [LookupCache] instance for constructing
     * [org.cirjson.cirjackson.databind.deserialization.DeserializerCache].
     *
     * @return [LookupCache] instance for constructing
     * [org.cirjson.cirjackson.databind.deserialization.DeserializerCache].
     */
    fun forDeserializerCache(config: DeserializationConfig): LookupCache<KotlinType, ValueDeserializer>

    /**
     * Method to provide a [LookupCache] instance for constructing
     * [org.cirjson.cirjackson.databind.serialization.SerializerCache].
     *
     * @return [LookupCache] instance for constructing [org.cirjson.cirjackson.databind.serialization.SerializerCache].
     */
    fun forSerializerCache(config: SerializationConfig): LookupCache<TypeKey, ValueSerializer>

    /**
     * Method to provide a [LookupCache] instance for constructing [org.cirjson.cirjackson.databind.type.TypeFactory].
     *
     * @return [LookupCache] instance for constructing [org.cirjson.cirjackson.databind.type.TypeFactory].
     */
    fun forTypeFactory(): LookupCache<Any, KotlinType>

}