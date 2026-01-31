package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.type.SimpleType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.verifyMustOverride
import java.io.Serial
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * ObjectMapper provides functionality for reading and writing CirJSON, either to and from basic POJOs, or to and from a
 * general-purpose CirJSON Tree Model ([CirJsonNode]), as well as related functionality for performing conversions. In
 * addition to directly reading and writing JSON (and with different underlying [TokenStreamFactory] configuration,
 * other formats), it is also the mechanism for creating [ObjectReaders][ObjectReader] and [ObjectWriters][ObjectWriter]
 * which offer more advancing reading/writing functionality.
 * 
 * Construction of mapper instances proceeds either via no-arguments constructor (producing instance with default
 * configuration); or through one of two build methods. First build method is the static `builder()` on exact type and
 * second [rebuild] method on an existing mapper. Former starts with default configuration (same as one that
 * no-arguments constructor created mapper has), and latter starts with configuration of the mapper it is called on. In
 * both cases, after configuration (including addition of [CirJacksonModules][CirJacksonModule]) is complete, instance
 * is created by calling [MapperBuilder.build] method.
 * 
 * Mapper (and [ObjectReaders][ObjectReader], [ObjectWriters][ObjectWriter] it constructs) will use instances of
 * [CirJsonParser] and [CirJsonGenerator] for implementing actual reading/writing of CirJSON. Note that although most
 * read and write methods are exposed through this class, some of the functionality is only exposed via [ObjectReader]
 * and [ObjectWriter]: specifically, reading/writing of longer sequences of values is only available through
 * [ObjectReader.readValues] and [ObjectWriter.writeValues].
 * 
 * Simplest usage is of form:
 * ```
 * val mapper = ObjectMapper() // can use static singleton, inject: just make sure to reuse!
 * val value = MyValue()
 * // ... and configure
 * val newState = File("my-stuff.cirjson")
 * mapper.writeValue(newState, value) // writes CirJSON serialization of MyValue instance
 * // or, read
 * val older = mapper.readValue(File("my-older-stuff.cirjson"), MyValue::class)
 *
 * // Or if you prefer CirJSON Tree representation:
 * val root = mapper.readTree(newState)
 * // and find values by, for example, using a CirJsonPointer expression:
 * val age = root.at("/personal/age").valueAsInt
 * ```
 * 
 * Mapper instances are fully thread-safe.
 * 
 * Note on caching: root-level deserializers are always cached, and accessed using full (generics-aware) type
 * information. This is different from caching of referenced types, which is more limited and is done only for a subset
 * of all deserializer types. The main reason for difference is that at root-level there is no incoming reference (and
 * hence no referencing property, no referral information or annotations to produce differing deserializers), and that
 * the performance impact greatest at root level (since it'll essentially cache the full graph of deserializers
 * involved).
 * 
 * @constructor Constructor usually called either by [MapperBuilder.build] or by subclass constructor: will get all
 * the settings through passed-in builder, including registration of any modules added to builder.
 */
open class ObjectMapper protected constructor(builder: MapperBuilder<*, *>) : TreeCodec, Versioned {

    /*
     *******************************************************************************************************************
     * Configuration settings, shared
     *******************************************************************************************************************
     */

    /**
     * Factory used to create [CirJsonParser] and [CirJsonGenerator] instances as necessary.
     */
    protected val myStreamFactory = builder.streamFactory()

    /**
     * Specific factory used for creating [KotlinType] instances; needed to allow modules to add more custom type
     * handling.
     */
    protected val myTypeFactory: TypeFactory

    /**
     * Provider for values to inject in deserialized POJOs.
     */
    protected val myInjectableValues: InjectableValues?

    /*
     *******************************************************************************************************************
     * Configuration settings, serialization
     *******************************************************************************************************************
     */

    /**
     * Factory used for constructing per-call [SerializerProviders][SerializerProvider].
     * 
     * Note: while serializers are only exposed [SerializerProvider], mappers and readers need to access additional API
     * defined by [SerializationContextExtended]
     */
    protected val mySerializationContexts: SerializationContexts

    /**
     * Configuration object that defines basic global settings for the serialization process
     */
    protected val mySerializationConfig: SerializationConfig

    /*
     *******************************************************************************************************************
     * Configuration settings, deserialization
     *******************************************************************************************************************
     */

    /**
     * Factory used for constructing per-call [DeserializationContexts][DeserializationContext].
     */
    protected val myDeserializationContexts: DeserializationContexts

    /**
     * Configuration object that defines basic global settings for the deserialization process
     */
    protected val myDeserializationConfig: DeserializationConfig

    /*
     *******************************************************************************************************************
     * Caching
     *******************************************************************************************************************
     */

    /**
     * We will use a separate main-level Map for keeping track of root-level deserializers. This is where most
     * successful cache lookups get resolved. Map will contain resolvers for all kinds of types, including container
     * types: this is different from the component cache which will only cache bean deserializers.
     * 
     * Given that we don't expect much concurrency for additions (should very quickly converge to zero after startup),
     * let's explicitly define a low concurrency setting.
     * 
     * These may are either "raw" deserializers (when no type information is needed for base type), or type-wrapped
     * deserializers (if it is needed)
     */
    protected val myRootDeserializers = ConcurrentHashMap<KotlinType, ValueDeserializer<Any>>(64, 0.6f, 2)

    /*
     *******************************************************************************************************************
     * Saved state to allow rebuilding
     *******************************************************************************************************************
     */

    /**
     * Minimal state retained to allow both re-building (by creating new builder) and JDK serialization of this mapper.
     */
    protected val mySavedBuilderState = builder.saveStateApplyModules()

    /*
     *******************************************************************************************************************
     * Lifecycle: builder-style construction
     *******************************************************************************************************************
     */

    init {
        val configOverrides = let {
            val ref = AtomicReference<ConfigOverrides>()
            builder.withAllConfigOverrides { ref.set(it) }
            ref.get()!!.snapshot()
        }
        val coercionConfigs = let {
            val ref = AtomicReference<CoercionConfigs>()
            builder.withAllCoercionConfigs { ref.set(it) }
            ref.get()!!.snapshot()
        }

        myTypeFactory = builder.typeFactory().snapshot()
        val classIntrospector = builder.classIntrospector().forMapper()
        val subtypeResolver = builder.subtypeResolver().snapshot()
        val mixInHandler = builder.mixInHandler().snapshot() as MixInHandler

        val rootNames = RootNameLookup()
        val filterProvider = builder.filterProvider()?.snapshot()

        mySerializationConfig =
                builder.buildSerializationConfig(configOverrides, mixInHandler, myTypeFactory, classIntrospector,
                        subtypeResolver, rootNames, filterProvider)
        mySerializationContexts = builder.serializationContexts()
                .forMapper(this, mySerializationConfig, myStreamFactory, builder.serializerFactory())

        myDeserializationConfig =
                builder.buildDeserializationConfig(configOverrides, mixInHandler, myTypeFactory, classIntrospector,
                        subtypeResolver, rootNames, coercionConfigs)
        myDeserializationContexts = builder.deserializationContexts()
                .forMapper(this, myDeserializationConfig, myStreamFactory, builder.deserializerFactory())

        myInjectableValues = builder.injectableValues()?.snapshot()
    }

    /**
     * Method for creating a new [MapperBuilder] for constructing differently configured [ObjectMapper] instance,
     * starting with current configuration including base settings and registered modules.
     */
    @Suppress("UNCHECKED_CAST")
    open fun <M : ObjectMapper, B : MapperBuilder<M, B>> rebuild(): MapperBuilder<M, B> {
        verifyMustOverride(ObjectMapper::class, this, "rebuild")
        return PrivateBuilder(mySavedBuilderState) as MapperBuilder<M, B>
    }

    /*
     *******************************************************************************************************************
     * Lifecycle: other construction
     *******************************************************************************************************************
     */

    /**
     * Default constructor, which will construct the default CirJSON-handling [TokenStreamFactory] as necessary and all
     * other unmodified default settings, and no additional registered modules.
     */
    constructor() : this(PrivateBuilder(CirJsonFactory()))

    /**
     * Constructs instance that uses specified [TokenStreamFactory] for constructing necessary
     * [CirJsonParsers][CirJsonParser] and/or [CirJsonGenerators][CirJsonGenerator], but without registering additional
     * modules.
     */
    constructor(tokenStreamFactory: TokenStreamFactory) : this(PrivateBuilder(tokenStreamFactory))

    /*
     *******************************************************************************************************************
     * Versioned implementation
     *******************************************************************************************************************
     */

    /**
     * Method that will return version information stored in and read from jar that contains this class.
     */
    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Configuration: main config object access
     *******************************************************************************************************************
     */

    /**
     * Accessor for internal configuration object that contains settings for serialization operations (`writeValue(...)`
     * methods).
     * 
     * NOTE: Not to be used by application code; needed by some tests.
     */
    internal open fun serializationConfig(): SerializationConfig {
        return mySerializationConfig
    }

    /**
     * Accessor for internal configuration object that contains settings for deserialization operations
     * (`readValue(...)` methods).
     * 
     * NOTE: Not to be used by application code; needed by some tests.
     */
    internal open fun deserializationConfig(): DeserializationConfig {
        return myDeserializationConfig
    }

    /**
     * Method that can be used to get hold of [TokenStreamFactory] that this mapper uses if it needs to construct
     * [CirJsonParsers][CirJsonParser] and/or [CirJsonGenerators][CirJsonGenerator].
     * 
     * WARNING: note that all [ObjectReader] and [ObjectWriter] instances created by this mapper usually share the same
     * configured [TokenStreamFactory], so changes to its configuration will "leak". To avoid such observed changes you
     * should always use `with()` and `without()` methods of [ObjectReader] and [ObjectWriter] for changing
     * [StreamReadFeature] and [StreamWriteFeature] settings to use on per-call basis.
     *
     * @return [TokenStreamFactory] that this mapper uses when it needs to construct CirJSON parser and generators
     */
    open fun tokenStreamFactory(): TokenStreamFactory {
        return myStreamFactory
    }

    internal fun streamFactory(): TokenStreamFactory {
        return myStreamFactory
    }

    /*
     *******************************************************************************************************************
     * Configuration: type factory and type resolution access
     *******************************************************************************************************************
     */

    /**
     * Accessor for getting currently configured [TypeFactory] instance.
     */
    open val typeFactory: TypeFactory
        get() = myTypeFactory

    /**
     * Convenience method for constructing [KotlinType] out of given type (typically `java.lang.Class`), but without
     * explicit context.
     */
    open fun constructType(type: Type): KotlinType {
        return myTypeFactory.constructType(type)
    }

    /**
     * Convenience method for constructing [KotlinType] out of given type reference.
     */
    open fun constructType(typeReference: TypeReference<*>): KotlinType {
        return myTypeFactory.constructType(typeReference)
    }

    /*
     *******************************************************************************************************************
     * Configuration: features access
     *******************************************************************************************************************
     */

    open fun isEnabled(feature: TokenStreamFactory.Feature): Boolean {
        return myStreamFactory.isEnabled(feature)
    }

    open fun isEnabled(feature: StreamReadFeature): Boolean {
        return myDeserializationConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: StreamWriteFeature): Boolean {
        return mySerializationConfig.isEnabled(feature)
    }

    /**
     * Method for checking whether given [MapperFeature] is enabled.
     */
    open fun isEnabled(feature: MapperFeature): Boolean {
        return mySerializationConfig.isEnabled(feature)
    }

    /**
     * Method for checking whether given deserialization-specific feature is enabled.
     */
    open fun isEnabled(feature: DeserializationFeature): Boolean {
        return myDeserializationConfig.isEnabled(feature)
    }

    /**
     * Method for checking whether given serialization-specific feature is enabled.
     */
    open fun isEnabled(feature: SerializationFeature): Boolean {
        return mySerializationConfig.isEnabled(feature)
    }

    /*
     *******************************************************************************************************************
     * Configuration: module information access
     *******************************************************************************************************************
     */

    /**
     * Accessor that may be used to find out [CirJacksonModules][CirJacksonModule] that were registered when creating
     * this mapper (if any).
     */
    open val registeredModules: Collection<CirJacksonModule>
        get() = mySavedBuilderState.modules()

    /*
     *******************************************************************************************************************
     * Configuration: internal access
     *******************************************************************************************************************
     */

    internal fun serializationContexts(): SerializationContexts {
        return mySerializationContexts
    }

    internal fun deserializationContexts(): DeserializationContexts {
        return myDeserializationContexts
    }

    internal fun rootDeserializers(): ConcurrentHashMap<KotlinType, ValueDeserializer<Any>> {
        return myRootDeserializers
    }

    /*
     *******************************************************************************************************************
     * TreeCodec implementation
     *******************************************************************************************************************
     */

    /**
     * Note: return type is co-variant, as basic ObjectCodec abstraction cannot refer to concrete node types (as it's
     * part of core package, whereas implementations are part of mapper package)
     */
    override fun createObjectNode(): ObjectTreeNode {
        TODO("Not yet implemented")
    }

    /**
     * Note: return type is co-variant, as basic ObjectCodec abstraction cannot refer to concrete node types (as it's
     * part of core package, whereas implementations are part of mapper package)
     */
    override fun createArrayNode(): ArrayTreeNode {
        TODO("Not yet implemented")
    }

    override fun booleanNode(boolean: Boolean): TreeNode {
        TODO("Not yet implemented")
    }

    override fun stringNode(text: String?): TreeNode {
        TODO("Not yet implemented")
    }

    override fun missingNode(): TreeNode {
        TODO("Not yet implemented")
    }

    override fun nullNode(): TreeNode {
        TODO("Not yet implemented")
    }

    /**
     * Method for constructing a [CirJsonParser] out of CirJSON tree representation.
     *
     * @param node Root node of the tree that resulting parser will read from
     */
    override fun treeAsTokens(node: TreeNode): CirJsonParser {
        TODO("Not yet implemented")
    }

    /**
     * Method to deserialize CirJSON content as a tree [CirJsonNode]. Returns [CirJsonNode] that represents the root of
     * the resulting tree, if there was content to read, or `null` if no more content is accessible via passed
     * [CirJsonParser].
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and all other methods
     * that take input source: latter will return "missing node", NOT `null`
     *
     * @return a [CirJsonNode], if valid CirJSON content found; null if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws CirJacksonIOException if a low-level I/O problem (unexpected end-of-input, network error) occurs (passed
     * through as-is without additional wrapping -- note that this is one case where
     * [DeserializationFeature.WRAP_EXCEPTIONS] does NOT result in wrapping of exception even if enabled)
     * 
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API: serialization (mapping from types to external format)
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize any value as a ByteArray. Functionally equivalent to calling [writeValue]
     * with [ByteArrayOutputStream] and getting bytes, but more efficient. Encoding used will be UTF-8.
     */
    @Throws(CirJacksonException::class)
    open fun writeValueAsBytes(value: Any?): ByteArray {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API: constructing ObjectWriters for more advanced configuration
     *******************************************************************************************************************
     */

    /**
     * Convenience method for constructing [ObjectWriter] with default settings.
     */
    open fun writer(): ObjectWriter {
        TODO("Not yet implemented")
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using the default pretty printer for
     * indentation.
     */
    open fun writerWithDefaultPrettyPrinter(): ObjectWriter {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API: constructing ObjectReaders for more advanced configuration
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing [ObjectReader] that will read or update instances of specified type.
     */
    open fun readerFor(type: KClass<*>): ObjectReader {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Base implementation for "Vanilla" [ObjectMapper].
     */
    private class PrivateBuilder : MapperBuilder<ObjectMapper, PrivateBuilder> {

        constructor(tokenStreamFactory: TokenStreamFactory) : super(tokenStreamFactory)

        constructor(state: MapperBuilderState) : super(state)

        override fun build(): ObjectMapper {
            return ObjectMapper(this)
        }

        override fun saveState(): MapperBuilderState {
            return StateImplementation(this)
        }

        class StateImplementation(builder: PrivateBuilder) : MapperBuilderState(builder) {

            @Serial
            fun readResolve(): Any {
                return PrivateBuilder(this).build()
            }

        }

    }

    companion object {

        private val CIRJSON_NODE_TYPE: KotlinType = SimpleType.constructUnsafe(CirJsonNode::class)

    }

}