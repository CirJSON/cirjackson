package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.ReadOnlyClassToSerializerMap
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.SimpleLookupCache
import org.cirjson.cirjackson.databind.util.TypeKey
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.reflect.KClass

/**
 * Simple cache object that allows for doing 2-level lookups: first level is by "local" read-only lookup Map (used
 * without locking) and second backup level is by a shared modifiable HashMap. The idea is that, after a while, most
 * serializers are found from the local Map (to optimize performance, reduce lock contention). But, during buildup, we
 * can use a shared map to reduce both number of distinct read-only maps constructed, and number of serializers
 * constructed.
 *
 * Cache contains three kinds of entries, based on combination of class pair key. First class in key is for the type to
 * serialize, and second one is type used for determining how to resolve value type. One (but not both) of entries can
 * be `null`.
 */
class SerializerCache : Snapshottable<SerializerCache> {

    private val mySharedMap: LookupCache<TypeKey, ValueSerializer<Any>>

    @Transient
    private val myReadOnlyMap: AtomicReference<ReadOnlyClassToSerializerMap?>

    constructor() : this(DEFAULT_MAX_CACHE_SIZE)

    constructor(maxCached: Int) {
        val initial = min(64, maxCached shr 2)
        mySharedMap = SimpleLookupCache(initial, maxCached)
        myReadOnlyMap = AtomicReference()
    }

    constructor(cache: LookupCache<TypeKey, ValueSerializer<Any>>) {
        mySharedMap = cache
        myReadOnlyMap = AtomicReference()
    }

    override fun snapshot(): SerializerCache {
        return SerializerCache(mySharedMap.snapshot())
    }

    /**
     * Accessor that can be called to get a read-only instance populated from the most recent version of the shared
     * lookup Map.
     */
    val readOnlyLookupMap: ReadOnlyClassToSerializerMap
        get() = myReadOnlyMap.get() ?: makeReadOnlyLookupMap()

    @Synchronized
    private fun makeReadOnlyLookupMap(): ReadOnlyClassToSerializerMap {
        return myReadOnlyMap.get() ?: ReadOnlyClassToSerializerMap.from(this, mySharedMap)
                .also { myReadOnlyMap.set(it) }
    }

    /*
     *******************************************************************************************************************
     * Lookup methods for accessing shared (slow) cache
     *******************************************************************************************************************
     */

    val size: Int
        get() = mySharedMap.size

    /**
     * Method that checks if the shared (and hence, synchronized) lookup Map might have untyped serializer for given
     * type.
     */
    fun untypedValueSerializer(type: KClass<*>): ValueSerializer<Any>? {
        return mySharedMap[TypeKey(type, false)]
    }

    /**
     * Method that checks if the shared (and hence, synchronized) lookup Map might have untyped serializer for given
     * type.
     */
    fun untypedValueSerializer(type: KotlinType): ValueSerializer<Any>? {
        return mySharedMap[TypeKey(type, false)]
    }

    /**
     * Method that checks if the shared (and hence, synchronized) lookup Map might have typed serializer for given type.
     */
    fun typedValueSerializer(type: KClass<*>): ValueSerializer<Any>? {
        return mySharedMap[TypeKey(type, true)]
    }

    /**
     * Method that checks if the shared (and hence, synchronized) lookup Map might have typed serializer for given type.
     */
    fun typedValueSerializer(type: KotlinType): ValueSerializer<Any>? {
        return mySharedMap[TypeKey(type, true)]
    }

    /*
     *******************************************************************************************************************
     * Methods for adding shared serializer instances
     *******************************************************************************************************************
     */

    /**
     * Method called if none of lookups succeeded, and caller had to construct a serializer. If so, we will update the
     * shared lookup map so that it can be resolved via it next time.
     */
    fun addTypedSerializer(type: KotlinType, serializer: ValueSerializer<Any>) {
        if (mySharedMap.set(TypeKey(type, true), serializer) == null) {
            myReadOnlyMap.set(null)
        }
    }

    /**
     * Method called if none of lookups succeeded, and caller had to construct a serializer. If so, we will update the
     * shared lookup map so that it can be resolved via it next time.
     */
    fun addTypedSerializer(type: KClass<*>, serializer: ValueSerializer<Any>) {
        if (mySharedMap.set(TypeKey(type, true), serializer) == null) {
            myReadOnlyMap.set(null)
        }
    }

    /**
     * Another alternative that will cover both access via raw type and matching fully resolved type, in one fell swoop.
     */
    fun addAndResolveNonTypedSerializer(type: KClass<*>, serializer: ValueSerializer<Any>,
            provider: SerializerProvider) {
        synchronized(this) {
            if (mySharedMap.set(TypeKey(type, false), serializer) == null) {
                myReadOnlyMap.set(null)
            }

            serializer.resolve(provider)
        }
    }

    /**
     * Another alternative that will cover both access via raw type and matching fully resolved type, in one fell swoop.
     */
    fun addAndResolveNonTypedSerializer(type: KotlinType, serializer: ValueSerializer<Any>,
            provider: SerializerProvider) {
        synchronized(this) {
            if (mySharedMap.set(TypeKey(type, false), serializer) == null) {
                myReadOnlyMap.set(null)
            }

            serializer.resolve(provider)
        }
    }

    /**
     * Another alternative that will cover both access via raw type and matching fully resolved type, in one fell swoop.
     */
    fun addAndResolveNonTypedSerializer(rawType: KClass<*>, fullType: KotlinType, serializer: ValueSerializer<Any>,
            provider: SerializerProvider) {
        synchronized(this) {
            val object1 = mySharedMap.set(TypeKey(rawType, false), serializer)
            val object2 = mySharedMap.set(TypeKey(fullType, false), serializer)

            if (object1 == null || object2 == null) {
                myReadOnlyMap.set(null)
            }

            serializer.resolve(provider)
        }
    }

    /**
     * Method called by [org.cirjson.cirjackson.databind.configuration.SerializationContexts.flushCachedSerializers] to
     * clear all cached serializers
     */
    @Synchronized
    fun flush() {
        mySharedMap.clear()
        myReadOnlyMap.set(null)
    }

    companion object {

        /**
         * By default, allow caching of up to 4000 serializer entries (for possibly up to 1000 types; but, depending on
         * access patterns, may be as few as half of that).
         */
        const val DEFAULT_MAX_CACHE_SIZE = 4000

    }

}