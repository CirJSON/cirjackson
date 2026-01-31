package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.deserialization.DeserializationContextExtended
import org.cirjson.cirjackson.databind.exception.MismatchedInputException
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.TreeTraversingParser
import org.cirjson.cirjackson.databind.serialization.SerializationContextExtended
import org.cirjson.cirjackson.databind.type.SimpleType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.verifyMustOverride
import java.io.*
import java.lang.reflect.Type
import java.net.URL
import java.nio.file.Path
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
     */
    protected open fun serializationConfig(): SerializationConfig {
        return mySerializationConfig
    }

    internal fun serializationConfigAccess(): SerializationConfig {
        return serializationConfig()
    }

    /**
     * Accessor for internal configuration object that contains settings for deserialization operations
     * (`readValue(...)` methods).
     */
    protected open fun deserializationConfig(): DeserializationConfig {
        return myDeserializationConfig
    }

    internal fun deserializationConfigAccess(): DeserializationConfig {
        return deserializationConfig()
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

    /**
     * Accessor that can be used to get hold of [CirJsonNodeFactory]
     * that this mapper will use when directly constructing
     * root [CirJsonNode] instances for Trees.
     * 
     * Note: this is just a shortcut for calling
     * ```
     * deserializationConfig.nodeFactory
     * ```
     */
    open val nodeFactory: CirJsonNodeFactory
        get() = myDeserializationConfig.nodeFactory

    open val injectableValues: InjectableValues?
        get() = myInjectableValues

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
     * Public API: constructing Parsers that are properly linked to ObjectReadContext
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(file: File): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, file))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(path: Path): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, path))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(url: URL): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, url))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(inputStream: InputStream): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, inputStream))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(reader: Reader): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, reader))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: ByteArray): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: ByteArray, offset: Int, length: Int): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, content, offset, length))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: String): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: CharArray): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: CharArray, offset: Int, length: Int): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, content, offset, length))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(dataInput: DataInput): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createParser(context, dataInput))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs an [ObjectReadContext] and then calls [TokenStreamFactory.createNonBlockingByteArrayParser].
     */
    @Throws(CirJacksonException::class)
    open fun createNonBlockingByteArrayParser(): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myStreamFactory.createNonBlockingByteArrayParser(context))
    }

    /*
     *******************************************************************************************************************
     * Public API: constructing Generators that are properly linked to ObjectWriteContext
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs an [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun createGenerator(outputStream: OutputStream): CirJsonGenerator {
        val context = serializerProvider()
        return myStreamFactory.createGenerator(context, outputStream)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs an [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun createGenerator(outputStream: OutputStream, encoding: CirJsonEncoding): CirJsonGenerator {
        val context = serializerProvider()
        return myStreamFactory.createGenerator(context, outputStream, encoding)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs an [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun createGenerator(writer: Writer): CirJsonGenerator {
        val context = serializerProvider()
        return myStreamFactory.createGenerator(context, writer)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs an [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun createGenerator(file: File, encoding: CirJsonEncoding): CirJsonGenerator {
        val context = serializerProvider()
        return myStreamFactory.createGenerator(context, file, encoding)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs an [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun createGenerator(path: Path, encoding: CirJsonEncoding): CirJsonGenerator {
        val context = serializerProvider()
        return myStreamFactory.createGenerator(context, path, encoding)
    }

    /**
     * Factory method for constructing [CirJsonGenerator] that is properly wired to allow callbacks for serialization:
     * basically constructs an [ObjectWriteContext] and then calls the related [TokenStreamFactory.createGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun createGenerator(dataOutput: DataOutput): CirJsonGenerator {
        val context = serializerProvider()
        return myStreamFactory.createGenerator(context, dataOutput)
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
        return myDeserializationConfig.nodeFactory.objectNode()
    }

    /**
     * Note: return type is co-variant, as basic ObjectCodec abstraction cannot refer to concrete node types (as it's
     * part of core package, whereas implementations are part of mapper package)
     */
    override fun createArrayNode(): ArrayTreeNode {
        return myDeserializationConfig.nodeFactory.arrayNode()
    }

    override fun booleanNode(boolean: Boolean): TreeNode {
        return myDeserializationConfig.nodeFactory.booleanNode(boolean)
    }

    override fun stringNode(text: String?): TreeNode {
        return myDeserializationConfig.nodeFactory.textNode(text)
    }

    override fun missingNode(): TreeNode {
        return myDeserializationConfig.nodeFactory.missingNode()
    }

    override fun nullNode(): TreeNode {
        return myDeserializationConfig.nodeFactory.nullNode()
    }

    /**
     * Method for constructing a [CirJsonParser] out of CirJSON tree representation.
     *
     * @param node Root node of the tree that resulting parser will read from
     */
    override fun treeAsTokens(node: TreeNode): CirJsonParser {
        val context = deserializationContext()
        return TreeTraversingParser(node as CirJsonNode, context)
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
    @Suppress("UNCHECKED_CAST")
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        val token = parser.currentToken() ?: parser.nextToken() ?: return null
        return readValue(deserializationContext(parser), parser, CIRJSON_NODE_TYPE) as T? ?: nodeFactory.nullNode() as T
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        val config = serializationConfig()
        serializerProvider(config).serializeValue(generator, tree)

        if (config.isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)) {
            generator.flush()
        }
    }

    /*
     *******************************************************************************************************************
     * Public API: deserialization, main methods
     *******************************************************************************************************************
     */

    /**
     * Method to deserialize CirJSON content into a non-container type (it can be an array type, however): typically a
     * bean, array or a wrapper type (like [java.lang.Boolean]).
     * 
     * Note: this method should NOT be used if the result type is a container ([Collection] or [Map]. The reason is
     * that, due to type erasure, key and value types cannot be introspected when using this method.
     *
     * @throws CirJacksonIOException if a low-level I/O problem (unexpected end-of-input, network error) occurs (passed
     * through as-is without additional wrapping -- note that this is one case where
     * [DeserializationFeature.WRAP_EXCEPTIONS] does NOT result in wrapping of exception even if enabled)
     * 
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     * 
     * @throws DatabindException if the input CirJSON structure does not match structure expected for result type (or
     * has other mismatch issues)
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(parser: CirJsonParser, valueType: KClass<T>): T? {
        return readValue(deserializationContext(parser), parser, myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content into a type, reference to which is passed as argument. Type is passed using
     * CirJackson specific type; instance of which can be constructed using [TypeFactory].
     * 
     * Note: this method should NOT be used if the result type is a container ([Collection] or [Map]. The reason is
     * that, due to type erasure, key and value types cannot be introspected when using this method.
     *
     * @throws CirJacksonIOException if a low-level I/O problem (unexpected end-of-input, network error) occurs (passed
     * through as-is without additional wrapping -- note that this is one case where
     * [DeserializationFeature.WRAP_EXCEPTIONS] does NOT result in wrapping of exception even if enabled)
     * 
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     * 
     * @throws DatabindException if the input CirJSON structure does not match structure expected for result type (or
     * has other mismatch issues)
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(parser: CirJsonParser, valueType: ResolvedType): T? {
        return readValue(deserializationContext(parser), parser,
                myTypeFactory.constructType(valueType as KotlinType)) as T?
    }

    /**
     * Type-safe overloaded method to deserialize CirJSON content into a non-container type (it can be an array type,
     * however): typically a bean, array or a wrapper type (like [java.lang.Boolean]).
     * 
     * Note: this method should NOT be used if the result type is a container ([Collection] or [Map]. The reason is
     * that, due to type erasure, key and value types cannot be introspected when using this method.
     *
     * @throws CirJacksonIOException if a low-level I/O problem (unexpected end-of-input, network error) occurs (passed
     * through as-is without additional wrapping -- note that this is one case where
     * [DeserializationFeature.WRAP_EXCEPTIONS] does NOT result in wrapping of exception even if enabled)
     * 
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     * 
     * @throws DatabindException if the input CirJSON structure does not match structure expected for result type (or
     * has other mismatch issues)
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> readValue(parser: CirJsonParser, valueType: KotlinType): T? {
        return readValue(deserializationContext(parser), parser, myTypeFactory.constructType(valueType)) as T?
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
     * Internal methods for serialization, overridable
     *******************************************************************************************************************
     */

    /**
     * Overridable helper method used for constructing [SerializerProvider] to use for serialization.
     */
    protected open fun serializerProvider(config: SerializationConfig): SerializationContextExtended {
        return mySerializationContexts.createContext(config, GeneratorSettings.EMPTY)
    }

    /**
     * Overridable helper method used for constructing [SerializerProvider] to use for serialization.
     */
    protected open fun serializerProvider(): SerializationContextExtended {
        return mySerializationContexts.createContext(serializationConfig(), GeneratorSettings.EMPTY)
    }

    internal fun serializerProviderAccess(): SerializationContextExtended {
        return serializerProvider()
    }

    /*
     *******************************************************************************************************************
     * Internal methods for deserialization, overridable
     *******************************************************************************************************************
     */

    /**
     * Actual implementation of value reading+binding operation.
     */
    @Throws(CirJacksonException::class)
    protected open fun readValue(context: DeserializationContextExtended, parser: CirJsonParser,
            valueType: KotlinType): Any? {
        val token = initForReading(parser, valueType)

        val result = when (token) {
            CirJsonToken.VALUE_NULL -> findRootDeserializer(context, valueType).getNullValue(context)
            CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> null
            else -> context.readRootValue(parser, valueType, findRootDeserializer(context, valueType), null)
        }

        parser.clearCurrentToken()

        if (context.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
            verifyNoTrailingTokens(parser, context, valueType)
        }

        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun readMapAndClose(context: DeserializationContextExtended, parser: CirJsonParser,
            valueType: KotlinType): Any? {
        context.assignParser(parser)

        parser.use {
            val token = initForReading(it, valueType)

            val result = when (token) {
                CirJsonToken.VALUE_NULL -> findRootDeserializer(context, valueType).getNullValue(context)
                CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> null
                else -> context.readRootValue(it, valueType, findRootDeserializer(context, valueType), null)
                        .also { context.checkUnresolvedObjectId() }
            }

            if (context.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
                verifyNoTrailingTokens(it, context, valueType)
            }

            return result
        }
    }

    /**
     * Similar to [readMapAndClose] but specialized for `CirJsonNode` reading.
     */
    @Throws(CirJacksonException::class)
    protected open fun readTreeAndClose(context: DeserializationContextExtended, parser: CirJsonParser): CirJsonNode {
        context.assignAndReturnParser(parser).use {
            val valueType = CIRJSON_NODE_TYPE
            val config = deserializationConfig()

            val token = it.currentToken() ?: it.nextToken() ?: return config.nodeFactory.missingNode()

            val resultNode = if (token == CirJsonToken.VALUE_NULL) {
                config.nodeFactory.nullNode()
            } else {
                context.readRootValue(it, valueType, findRootDeserializer(context, valueType), null) as CirJsonNode
            }

            if (context.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
                verifyNoTrailingTokens(it, context, valueType)
            }

            return resultNode
        }
    }

    /**
     * Internal helper method called to create an instance of [DeserializationContext] for deserializing a single root
     * value. Can be overridden if a custom context is needed.
     */
    protected open fun deserializationContext(parser: CirJsonParser): DeserializationContextExtended {
        return myDeserializationContexts.createContext(deserializationConfig(), null, myInjectableValues)
                .assignParser(parser)
    }

    protected open fun deserializationContext(): DeserializationContextExtended {
        return myDeserializationContexts.createContext(deserializationConfig(), null, myInjectableValues)
    }

    internal open fun deserializationContextAccess(): DeserializationContextExtended {
        return deserializationContext()
    }

    protected open fun deserializationContext(config: DeserializationConfig,
            parser: CirJsonParser): DeserializationContextExtended {
        return myDeserializationContexts.createContext(config, null, myInjectableValues).assignParser(parser)
    }

    /**
     * Method called to ensure that given parser is ready for reading content for data binding.
     *
     * @return First token to be used for data binding after this call: can never be null as exception will be thrown if
     * parser cannot provide more tokens.
     *
     * @throws CirJacksonException if the initialization fails during initialization of the streaming parser
     */
    @Throws(CirJacksonException::class)
    protected open fun initForReading(parser: CirJsonParser, targetType: KotlinType): CirJsonToken {
        return parser.currentToken() ?: parser.nextToken() ?: throw MismatchedInputException.from(parser, targetType,
                "No content to map due to end-of-input")
    }

    @Throws(CirJacksonException::class)
    protected fun verifyNoTrailingTokens(parser: CirJsonParser, context: DeserializationContext, bindType: KotlinType) {
        val token = parser.nextToken() ?: return
        val type = bindType.rawClass
        return context.reportTrailingTokens(type, parser, token)
    }

    /*
     *******************************************************************************************************************
     * Internal factory methods for ObjectReaders/ObjectWriters
     *******************************************************************************************************************
     */

    /**
     * Factory method subclasses must override to produce [ObjectReader] instances of proper subtype
     */
    protected open fun newReader(config: DeserializationConfig): ObjectReader {
        return ObjectReader.construct(this, config)
    }

    /**
     * Factory method subclasses must override to produce [ObjectReader] instances of proper subtype
     */
    protected open fun newReader(config: DeserializationConfig, valueType: KotlinType?, valueToUpdate: Any?,
            schema: FormatSchema?, injectableValues: InjectableValues?): ObjectReader {
        return ObjectReader.construct(this, config, valueType, valueToUpdate, schema, injectableValues)
    }

    /**
     * Factory method subclasses must override to produce [ObjectWriter] instances of proper subtype
     */
    protected open fun newReader(config: SerializationConfig): ObjectWriter {
        return ObjectWriter.construct(this, config)
    }

    /**
     * Factory method subclasses must override to produce [ObjectWriter] instances of proper subtype
     */
    protected open fun newReader(config: SerializationConfig, schema: FormatSchema?): ObjectWriter {
        return ObjectWriter.construct(this, config, schema)
    }

    /**
     * Factory method subclasses must override to produce [ObjectWriter] instances of proper subtype
     */
    protected open fun newReader(config: SerializationConfig, rootType: KotlinType?,
            prettyPrinter: PrettyPrinter?): ObjectWriter {
        return ObjectWriter.construct(this, config, rootType, prettyPrinter)
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other
     *******************************************************************************************************************
     */

    /**
     * Method called to locate deserializer for the passed root-level value.
     */
    @Throws(CirJacksonException::class)
    protected open fun findRootDeserializer(context: DeserializationContext,
            valueType: KotlinType): ValueDeserializer<Any> {
        return myRootDeserializers[valueType] ?: context.findRootValueDeserializer(valueType)
                .also { myRootDeserializers[valueType] = it }
    }

    protected open fun verifySchemaType(schema: FormatSchema?) {
        schema ?: return

        if (!myStreamFactory.canUseSchema(schema)) {
            throw IllegalArgumentException(
                    "Cannot use FormatSchema of type ${schema::class.qualifiedName} for format ${myStreamFactory.formatName}")
        }
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