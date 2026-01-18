package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.GeneratorSettings
import org.cirjson.cirjackson.databind.configuration.SerializationContexts
import org.cirjson.cirjackson.databind.serialization.SerializationContextExtension
import org.cirjson.cirjackson.databind.serialization.implementation.TypeWrappedSerializer

/**
 * Builder object that can be used for per-serialization configuration of serialization parameters, such as CirJSON View
 * and root type to use. (and thus fully thread-safe with no external synchronization); new instances are constructed
 * for different configurations. Instances are initially constructed by [ObjectMapper] and can be reused in completely
 * thread-safe manner with no explicit synchronization
 */
open class ObjectWriter : Versioned {

    /*
     *******************************************************************************************************************
     * Immutable configuration from ObjectMapper
     *******************************************************************************************************************
     */

    /**
     * General serialization configuration settings
     */
    protected val myConfig: SerializationConfig

    /**
     * Factory used for constructing per-call [SerializerProviders][SerializerProvider].
     * 
     * Note: while serializers are only exposed [SerializerProvider],
     * mappers and readers need to access additional API defined by
     * [SerializationContextExtension]
     */
    protected val mySerializationContexts: SerializationContexts

    /**
     * Factory used for constructing [CirJsonGenerators][CirJsonGenerator]
     */
    protected val myGeneratorFactory: TokenStreamFactory

    /*
     *******************************************************************************************************************
     * Configuration that can be changed via mutant factories
     *******************************************************************************************************************
     */

    /**
     * Container for settings that need to be passed to [CirJsonGenerator] constructed for serializing values.
     */
    protected val myGeneratorSettings: GeneratorSettings

    /**
     * We may pre-fetch serializer if root type is known (has been explicitly declared), and if so, reuse it afterward.
     * This allows avoiding further serializer lookups and increases performance a bit on cases where readers are
     * reused.
     */
    protected val myPrefetch: Prefetch

    /*
     *******************************************************************************************************************
     * Lifecycle, constructors
     *******************************************************************************************************************
     */

    protected constructor(mapper: ObjectMapper, config: SerializationConfig, rootType: KotlinType?,
            prettyPrinter: PrettyPrinter?) {
        myConfig = config

        mySerializationContexts = mapper.serializationContexts()
        myGeneratorFactory = mapper.streamFactory()

        myGeneratorSettings = prettyPrinter?.let { GeneratorSettings(it, null, null, null) } ?: GeneratorSettings.EMPTY

        myPrefetch = if (rootType == null) {
            Prefetch.EMPTY
        } else if (rootType.hasRawClass(Any::class)) {
            Prefetch.EMPTY.forRootType(this, rootType)
        } else {
            Prefetch.EMPTY.forRootType(this, rootType.withStaticTyping())
        }
    }

    /**
     * Alternative constructor for initial instantiation by [ObjectMapper]
     */
    protected constructor(mapper: ObjectMapper, config: SerializationConfig) {
        myConfig = config

        mySerializationContexts = mapper.serializationContexts()
        myGeneratorFactory = mapper.streamFactory()

        myGeneratorSettings = GeneratorSettings.EMPTY
        myPrefetch = Prefetch.EMPTY
    }

    /**
     * Alternative constructor for initial instantiation by [ObjectMapper]
     */
    protected constructor(mapper: ObjectMapper, config: SerializationConfig, schema: FormatSchema?) {
        myConfig = config

        mySerializationContexts = mapper.serializationContexts()
        myGeneratorFactory = mapper.streamFactory()

        myGeneratorSettings = schema?.let { GeneratorSettings(null, it, null, null) } ?: GeneratorSettings.EMPTY
        myPrefetch = Prefetch.EMPTY
    }

    /**
     * Copy constructor used for building variations.
     */
    protected constructor(base: ObjectWriter, config: SerializationConfig, generatorSettings: GeneratorSettings,
            prefetch: Prefetch) {
        myConfig = config

        mySerializationContexts = base.mySerializationContexts
        myGeneratorFactory = base.myGeneratorFactory

        myGeneratorSettings = generatorSettings
        myPrefetch = prefetch
    }

    /**
     * Copy constructor used for building variations.
     */
    protected constructor(base: ObjectWriter, config: SerializationConfig) {
        myConfig = config

        mySerializationContexts = base.mySerializationContexts
        myGeneratorFactory = base.myGeneratorFactory

        myGeneratorSettings = base.myGeneratorSettings
        myPrefetch = base.myPrefetch
    }

    /**
     * Method that will return version information stored in and read from jar that contains this class.
     */
    override fun version(): Version {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    open fun isEnabled(feature: SerializationFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: MapperFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: StreamWriteFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    /*
     *******************************************************************************************************************
     * Serialization methods, others
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize any Java value as a String. Functionally equivalent to calling [writeValue]
     * with [java.io.StringWriter] and constructing String, but more efficient.
     */
    @Throws(CirJacksonException::class)
    open fun writeValueAsString(value: Any?): String {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper method
     *******************************************************************************************************************
     */

    protected fun serializerProvider(): SerializationContextExtension {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    protected open fun verifySchemaType(schema: FormatSchema?) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper classes for configuration
     *******************************************************************************************************************
     */

    /**
     * As a minor optimization, we will make an effort to pre-fetch a serializer, or at least relevant `TypeSerializer`,
     * if given enough information.
     * 
     * @property myRootType Specified root serialization type to use; can be same as runtime type, but usually one of
     * its super types (parent class or interface it implements).
     * 
     * @property myValueSerializer We may pre-fetch serializer if [myRootType] is known, and if so, reuse it afterward.
     * This allows avoiding further serializer lookups and increases performance a bit on cases where readers are
     * reused.
     * 
     * @property myTypeSerializer When dealing with polymorphic types, we cannot pre-fetch serializer, but can pre-fetch
     * [TypeSerializer].
     */
    class Prefetch private constructor(private val myRootType: KotlinType?,
            private val myValueSerializer: ValueSerializer<Any>?, private val myTypeSerializer: TypeSerializer?) {

        fun forRootType(parent: ObjectWriter, newType: KotlinType?): Prefetch {
            if (newType == null) {
                if (myRootType == null || myValueSerializer == parent) {
                    return this
                }

                return Prefetch(null, null, null)
            }

            if (newType == myRootType) {
                return this
            }

            if (newType.isJavaLangObject) {
                val context = parent.serializerProvider()
                val typeSerializer = context.findTypeSerializer(newType)
                return Prefetch(null, null, typeSerializer)
            }

            if (parent.isEnabled(SerializationFeature.EAGER_SERIALIZER_FETCH)) {
                val context = parent.serializerProvider()

                try {
                    val serializer = context.findTypedValueSerializer(newType, true)

                    return if (serializer is TypeWrappedSerializer) {
                        Prefetch(newType, null, serializer.typeSerializer())
                    } else {
                        Prefetch(newType, serializer, null)
                    }
                } catch (_: CirJacksonException) {
                }
            }

            return Prefetch(newType, null, myTypeSerializer)
        }

        val valueSerializer: ValueSerializer<Any>?
            get() = myValueSerializer

        val typeSerializer: TypeSerializer?
            get() = myTypeSerializer

        fun hasSerializer(): Boolean {
            return myValueSerializer != null || myTypeSerializer != null
        }

        @Throws(CirJacksonException::class)
        fun serialize(generator: CirJsonGenerator, value: Any?, context: SerializationContextExtension) {
            if (myTypeSerializer != null) {
                context.serializePolymorphic(generator, value, myRootType, myValueSerializer, myTypeSerializer)
            } else if (myValueSerializer != null) {
                context.serializeValue(generator, value, myRootType, myValueSerializer)
            } else if (myRootType != null) {
                context.serializeValue(generator, value, myRootType)
            } else {
                context.serializeValue(generator, value)
            }
        }

        companion object {

            val EMPTY = Prefetch(null, null, null)

        }

    }

    companion object {

        internal fun construct(mapper: ObjectMapper, config: SerializationConfig, rootType: KotlinType,
                prettyPrinter: PrettyPrinter): ObjectWriter {
            return ObjectWriter(mapper, config, rootType, prettyPrinter)
        }

        internal fun construct(mapper: ObjectMapper, config: SerializationConfig): ObjectWriter {
            return ObjectWriter(mapper, config)
        }

        internal fun construct(mapper: ObjectMapper, config: SerializationConfig, schema: FormatSchema?): ObjectWriter {
            return ObjectWriter(mapper, config, schema)
        }

    }

}