package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.FormatSchema
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.InjectableValues
import org.cirjson.cirjackson.databind.deserialization.DeserializationContextExtended
import org.cirjson.cirjackson.databind.deserialization.DeserializerCache
import org.cirjson.cirjackson.databind.deserialization.DeserializerFactory

/**
 * Factory/builder class that replaces the concept of "blueprint" instance of
 * [DeserializationContext][org.cirjson.cirjackson.databind.DeserializationContext]. It will be constructed and
 * configured during [ObjectMapper][org.cirjson.cirjackson.databind.ObjectMapper] building phase, and will be called
 * once per `readValue` call to construct actual stateful
 * [DeserializationContext][org.cirjson.cirjackson.databind.DeserializationContext] to use during serialization.
 *
 * Note that since this object has to be serializable (to allow JDK serialization of mapper instances),
 * [DeserializationContext][org.cirjson.cirjackson.databind.DeserializationContext] need not be serializable anymore.
 *
 * @property myStreamFactory Low-level [TokenStreamFactory] that may be used for constructing embedded generators.
 *
 * @property myDeserializerFactory Factory responsible for constructing standard serializers.
 *
 * @property myCache Cache for doing type-to-value-serializer lookups.
 */
abstract class DeserializationContexts protected constructor(protected val myStreamFactory: TokenStreamFactory?,
        protected val myDeserializerFactory: DeserializerFactory?, protected val myCache: DeserializerCache?) {

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
    open fun forMapper(mapper: Any, config: DeserializationConfig, tokenStreamFactory: TokenStreamFactory?,
            deserializerFactory: DeserializerFactory?): DeserializationContexts {
        return forMapper(mapper, tokenStreamFactory, deserializerFactory,
                DeserializerCache(config.cacheProvider.forDeserializerCache(config)))
    }

    protected abstract fun forMapper(mapper: Any, tokenStreamFactory: TokenStreamFactory?,
            deserializerFactory: DeserializerFactory?, cache: DeserializerCache?): DeserializationContexts

    /**
     * Factory method for constructing a context object for individual `writeValue` call.
     */
    abstract fun createContext(config: DeserializationConfig, schema: FormatSchema?,
            injectables: InjectableValues?): DeserializationContextExtended

    /*
     *******************************************************************************************************************
     * Vanilla implementation
     *******************************************************************************************************************
     */

    open class DefaultImplementation : DeserializationContexts {

        constructor() : super()

        constructor(tokenStreamFactory: TokenStreamFactory?, deserializerFactory: DeserializerFactory?,
                cache: DeserializerCache?) : super(tokenStreamFactory, deserializerFactory, cache)

        public override fun forMapper(mapper: Any, tokenStreamFactory: TokenStreamFactory?,
                deserializerFactory: DeserializerFactory?, cache: DeserializerCache?): DeserializationContexts {
            return DefaultImplementation(tokenStreamFactory, deserializerFactory, cache)
        }

        override fun createContext(config: DeserializationConfig, schema: FormatSchema?,
                injectables: InjectableValues?): DeserializationContextExtended {
            myStreamFactory ?: throw IllegalStateException("myStreamFactory is null")
            myDeserializerFactory ?: throw IllegalStateException("myDeserializerFactory is null")
            myCache ?: throw IllegalStateException("myCache is null")

            return DeserializationContextExtended.Implementation(myStreamFactory, myDeserializerFactory, myCache,
                    config, schema, injectables)
        }

        fun cacheForTests(): DeserializerCache? {
            return myCache
        }
    }

}