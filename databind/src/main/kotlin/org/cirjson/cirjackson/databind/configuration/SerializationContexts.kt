package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.serialization.SerializationContextExtended
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.serialization.SerializerFactory

/**
 * Factory/builder class that replaces Jackson 2.x concept of "blueprint" instance of
 * [org.cirjson.cirjackson.databind.SerializerProvider]. It will be constructed and configured during
 * [org.cirjson.cirjackson.databind.ObjectMapper] building phase, and will be called once per `writeValue` call to
 * construct actual stateful [org.cirjson.cirjackson.databind.SerializerProvider] to use during serialization.
 *
 * Note that since this object has to be serializable (to allow JDK serialization of mapper instances),
 * [org.cirjson.cirjackson.databind.SerializerProvider] need not be serializable anymore.
 *
 * @property myStreamFactory Low-level [TokenStreamFactory] that may be used for constructing embedded generators.
 *
 * @property mySerializerFactory Factory responsible for constructing standard serializers.
 *
 * @property myCache Cache for doing type-to-value-serializer lookups.
 */
abstract class SerializationContexts protected constructor(protected val myStreamFactory: TokenStreamFactory?,
        protected val mySerializerFactory: SerializerFactory?, protected val myCache: SerializerCache?) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor() : this(null, null, null)

    /**
     * Mutant factory method called when instance is actually created for use by mapper (as opposed to coming into
     * existence during building, module registration). Necessary usually to initialize non-configuration state, such as
     * caching.
     */
    open fun forMapper(mapper: Any, config: SerializationConfig, tokenStreamFactory: TokenStreamFactory?,
            serializerFactory: SerializerFactory?): SerializationContexts {
        return forMapper(mapper, tokenStreamFactory, serializerFactory,
                SerializerCache(config.cacheProvider.forSerializerCache(config)))
    }

    protected abstract fun forMapper(mapper: Any, tokenStreamFactory: TokenStreamFactory?,
            serializerFactory: SerializerFactory?, cache: SerializerCache?): SerializationContexts

    /**
     * Factory method for constructing context object for individual `writeValue()` calls.
     */
    abstract fun createContext(config: SerializationConfig,
            generatorSettings: GeneratorSettings): SerializationContextExtended

    /*
     *******************************************************************************************************************
     * Access to caching details
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to determine how many serializers this provider is caching currently (if it does caching:
     * default implementation does) Exact count depends on what kind of serializers get cached; default implementation
     * caches all serializers, including ones that are eagerly constructed (for optimal access speed)
     *
     * The main use case for this method is to allow conditional flushing of serializer cache, if certain number of
     * entries is reached.
     */
    open fun cachedSerializersCount(): Int {
        return myCache!!.size
    }

    /**
     * Method that will drop all serializers currently cached by this provider. This can be used to remove memory usage
     * (in case some serializers are only used once or so), or to force re-construction of serializers after
     * configuration changes for mapper than owns the provider.
     */
    open fun flushCachedSerializers() {
        myCache!!.flush()
    }

    /*
     *******************************************************************************************************************
     * Vanilla implementation
     *******************************************************************************************************************
     */

    open class DefaultImplementation : SerializationContexts {

        constructor() : super(null, null, null)

        constructor(tokenStreamFactory: TokenStreamFactory?, serializerFactory: SerializerFactory?,
                cache: SerializerCache?) : super(tokenStreamFactory, serializerFactory, cache)

        override fun forMapper(mapper: Any, tokenStreamFactory: TokenStreamFactory?,
                serializerFactory: SerializerFactory?, cache: SerializerCache?): SerializationContexts {
            return DefaultImplementation(tokenStreamFactory, serializerFactory, cache)
        }

        override fun createContext(config: SerializationConfig,
                generatorSettings: GeneratorSettings): SerializationContextExtended {
            return SerializationContextExtended.Implementation(myStreamFactory!!, config, generatorSettings,
                    mySerializerFactory!!, myCache!!)
        }

    }

}