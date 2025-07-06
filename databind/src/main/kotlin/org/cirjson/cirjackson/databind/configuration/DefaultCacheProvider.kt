package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.DeserializerCache
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.SimpleLookupCache
import org.cirjson.cirjackson.databind.util.TypeKey
import kotlin.math.min

/**
 * The default implementation of [CacheProvider]. Configuration is builder-based via [DefaultCacheProvider.Builder].
 *
 * Users can either use this class or create their own [CacheProvider] implementation.
 */
open class DefaultCacheProvider protected constructor(maxDeserializerCacheSize: Int, maxSerializerCacheSize: Int,
        maxTypeFactoryCacheSize: Int) : CacheProvider {

    /**
     * Maximum size of the [LookupCache] instance constructed by [forDeserializerCache].
     *
     * @see Builder.maxDeserializerCacheSize
     */
    protected val myMaxDeserializerCacheSize = maxDeserializerCacheSize

    /**
     * Maximum size of the [LookupCache] instance constructed by [forSerializerCache]
     *
     * @see Builder.maxSerializerCacheSize
     */
    protected val myMaxSerializerCacheSize = maxSerializerCacheSize

    /**
     * Maximum size of the [LookupCache] instance constructed by [forTypeFactory].
     *
     * @see Builder.maxTypeFactoryCacheSize
     */
    protected val myMaxTypeFactoryCacheSize = maxTypeFactoryCacheSize

    /*
     *******************************************************************************************************************
     * API implementation
     *******************************************************************************************************************
     */

    /**
     * Method to provide a [LookupCache] instance for constructing [DeserializerCache].
     *
     * @return [LookupCache] instance for constructing [DeserializerCache].
     */
    override fun forDeserializerCache(config: DeserializationConfig): LookupCache<KotlinType, ValueDeserializer<Any>> {
        return buildCache(myMaxDeserializerCacheSize)
    }

    /**
     * Method to provide a [LookupCache] instance for constructing [DeserializerCache].
     *
     * @return [LookupCache] instance for constructing [DeserializerCache].
     */
    override fun forSerializerCache(config: SerializationConfig): LookupCache<TypeKey, ValueSerializer<Any>> {
        return buildCache(myMaxSerializerCacheSize)
    }

    /**
     * Method to provide a [LookupCache] instance for constructing [TypeFactory].
     *
     * @return [LookupCache] instance for constructing [TypeFactory].
     */
    override fun forTypeFactory(): LookupCache<Any, KotlinType> {
        return buildCache(myMaxTypeFactoryCacheSize)
    }

    /*
     *******************************************************************************************************************
     * Overridable factory methods
     *******************************************************************************************************************
     */

    protected open fun <K, V> buildCache(maxSize: Int): LookupCache<K, V> {
        val initialSize = min(64, maxSize shr 2)
        return SimpleLookupCache(initialSize, maxSize)
    }

    /*
     *******************************************************************************************************************
     * Builder Config
     *******************************************************************************************************************
     */

    /**
     * Builder offering fluent factory methods to configure [DefaultCacheProvider], keeping it immutable.
     */
    open class Builder internal constructor() {

        /**
         * Maximum Size of the [LookupCache] instance created by [forDeserializerCache]. Corresponds to
         * [DefaultCacheProvider.myMaxDeserializerCacheSize].
         */
        protected var myMaxDeserializerCacheSize = 0

        /**
         * Maximum Size of the [LookupCache] instance created by [forSerializerCache]. Corresponds to
         * [DefaultCacheProvider.myMaxSerializerCacheSize].
         */
        protected var myMaxSerializerCacheSize = 0

        /**
         * Maximum Size of the [LookupCache] instance created by [forTypeFactory]. Corresponds to
         * [DefaultCacheProvider.myMaxTypeFactoryCacheSize].
         */
        protected var myMaxTypeFactoryCacheSize = 0

        /**
         * Define the maximum size of the [LookupCache] instance constructed by [forDeserializerCache] and [buildCache].
         *
         * Note that specifying a maximum size of zero prevents values from being retained in the cache.
         *
         * @param maxDeserializerCacheSize Size for the [LookupCache] to use within [DeserializerCache]
         *
         * @return this builder
         *
         * @throws IllegalArgumentException if `maxDeserializerCacheSize` is negative
         */
        open fun maxDeserializerCacheSize(maxDeserializerCacheSize: Int): Builder {
            if (maxDeserializerCacheSize < 0) {
                throw IllegalArgumentException("Cannot set maxDeserializerCacheSize to a negative value")
            }

            myMaxDeserializerCacheSize = maxDeserializerCacheSize
            return this
        }

        /**
         * Define the maximum size of the [LookupCache] instance constructed by [forSerializerCache] and [buildCache].
         *
         * Note that specifying a maximum size of zero prevents values from being retained in the cache.
         *
         * @param maxSerializerCacheSize Size for the [LookupCache] to use within [SerializerCache]
         *
         * @return this builder
         *
         * @throws IllegalArgumentException if `maxSerializerCacheSize` is negative
         */
        open fun maxSerializerCacheSize(maxSerializerCacheSize: Int): Builder {
            if (maxSerializerCacheSize < 0) {
                throw IllegalArgumentException("Cannot set maxSerializerCacheSize to a negative value")
            }

            myMaxSerializerCacheSize = maxSerializerCacheSize
            return this
        }

        /**
         * Define the maximum size of the [LookupCache] instance constructed by [forTypeFactory] and [buildCache].
         *
         * Note that specifying a maximum size of zero prevents values from being retained in the cache.
         *
         * @param maxDeserializerCacheSize Size for the [LookupCache] to use within [TypeFactory]
         *
         * @return this builder
         *
         * @throws IllegalArgumentException if `maxTypeFactoryCacheSize` is negative
         */
        open fun maxTypeFactoryCacheSize(maxTypeFactoryCacheSize: Int): Builder {
            if (maxTypeFactoryCacheSize < 0) {
                throw IllegalArgumentException("Cannot set maxTypeFactoryCacheSize to a negative value")
            }

            myMaxTypeFactoryCacheSize = maxTypeFactoryCacheSize
            return this
        }

        /**
         * Constructs a [DefaultCacheProvider] with the provided configuration values, using defaults where not
         * specified.
         *
         * @return A [DefaultCacheProvider] instance with the specified configuration
         */
        open fun build(): DefaultCacheProvider {
            return DefaultCacheProvider(myMaxDeserializerCacheSize, myMaxSerializerCacheSize, myMaxTypeFactoryCacheSize)
        }

    }

    companion object {

        val DEFAULT =
                DefaultCacheProvider(DeserializerCache.DEFAULT_MAX_CACHE_SIZE, SerializerCache.DEFAULT_MAX_CACHE_SIZE,
                        TypeFactory.DEFAULT_MAX_CACHE_SIZE)

        /**
         * @return [Builder] instance for configuration.
         */
        fun builder(): Builder {
            return Builder()
        }

    }

}