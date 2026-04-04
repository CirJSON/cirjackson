package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.TypeKey
import kotlin.reflect.KClass

/**
 * Optimized lookup table for accessing two types of serializers; typed and non-typed. Only accessed from a single
 * thread, so no synchronization needed for accessors.
 *
 * @property mySharedCache Shared cache used for call-throughs in cases where we do not have local matches.
 */
class ReadOnlyClassToSerializerMap private constructor(private val mySharedCache: SerializerCache,
        source: LookupCache<TypeKey, ValueSerializer<Any>>) {

    private val mySize = findSize(source.size)

    private val myMask = mySize - 1

    private val myBuckets = arrayOfNulls<Bucket>(mySize).also {
        source.contents { key, serializer ->
            val index = key.hashCode() and myMask
            it[index] = Bucket(it[index], key, serializer)
        }
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    fun typedValueSerializer(type: KotlinType): ValueSerializer<Any>? {
        var bucket = myBuckets[TypeKey.typedHash(type) and myMask] ?: return mySharedCache.typedValueSerializer(type)

        do {
            if (bucket.matchesTyped(type)) {
                return bucket.value
            }
        } while (bucket.next?.also { bucket = it } != null)

        return mySharedCache.typedValueSerializer(type)
    }

    fun typedValueSerializer(rawType: KClass<*>): ValueSerializer<Any>? {
        var bucket =
                myBuckets[TypeKey.typedHash(rawType) and myMask] ?: return mySharedCache.typedValueSerializer(rawType)

        do {
            if (bucket.matchesTyped(rawType)) {
                return bucket.value
            }
        } while (bucket.next?.also { bucket = it } != null)

        return mySharedCache.typedValueSerializer(rawType)
    }

    fun untypedValueSerializer(type: KotlinType): ValueSerializer<Any>? {
        var bucket =
                myBuckets[TypeKey.untypedHash(type) and myMask] ?: return mySharedCache.untypedValueSerializer(type)

        do {
            if (bucket.matchesUntyped(type)) {
                return bucket.value
            }
        } while (bucket.next?.also { bucket = it } != null)

        return mySharedCache.untypedValueSerializer(type)
    }

    fun untypedValueSerializer(rawType: KClass<*>): ValueSerializer<Any>? {
        var bucket = myBuckets[TypeKey.untypedHash(rawType) and myMask] ?: return mySharedCache.untypedValueSerializer(
                rawType)

        do {
            if (bucket.matchesUntyped(rawType)) {
                return bucket.value
            }
        } while (bucket.next?.also { bucket = it } != null)

        return mySharedCache.untypedValueSerializer(rawType)
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    private class Bucket(val next: Bucket?, key: TypeKey, val value: ValueSerializer<Any>) {

        private val myClass = key.rawType

        private val myType = key.type

        private val myIsTyped = key.isTyped

        fun matchesTyped(key: KClass<*>): Boolean {
            return myClass == key && myIsTyped
        }

        fun matchesUntyped(key: KClass<*>): Boolean {
            return myClass == key && !myIsTyped
        }

        fun matchesTyped(key: KotlinType): Boolean {
            return myIsTyped && key == myType
        }

        fun matchesUntyped(key: KotlinType): Boolean {
            return !myIsTyped && key == myType
        }

    }

    companion object {

        private fun findSize(size: Int): Int {
            val needed = if (size <= 64) {
                size * 2
            } else {
                size + (size shr 2)
            }

            var result = 8

            while (result < needed) {
                result = result shl 1
            }

            return result
        }

        /**
         * Factory method for constructing an instance.
         */
        fun from(shared: SerializerCache,
                source: LookupCache<TypeKey, ValueSerializer<Any>>): ReadOnlyClassToSerializerMap {
            return ReadOnlyClassToSerializerMap(shared, source)
        }

    }

}