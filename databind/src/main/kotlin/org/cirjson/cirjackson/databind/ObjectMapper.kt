package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.SegmentedStringWriter
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.deserialization.DeserializationContextExtended
import org.cirjson.cirjackson.databind.exception.MismatchedInputException
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.POJONode
import org.cirjson.cirjackson.databind.node.TreeTraversingParser
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.serialization.SerializationContextExtended
import org.cirjson.cirjackson.databind.type.SimpleType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.closeOnFailAndThrowAsCirJacksonException
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.verifyMustOverride
import java.io.*
import java.lang.reflect.Type
import java.net.URL
import java.nio.file.Path
import java.text.DateFormat
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
     * Note: this method should NOT be used if the result type is a container ([Collection] or [Map]). The reason is
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
     * so-called "super type token" and specifically needs to be used if the root type is a parameterized (generic)
     * container type.
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
    open fun <T : Any> readValue(parser: CirJsonParser, valueTypeReference: TypeReference<T>): T? {
        return readValue(deserializationContext(parser), parser, myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content into a type, reference to which is passed as argument. Type is passed using
     * CirJackson specific type; instance of which can be constructed using [TypeFactory].
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
    fun <T : Any> readValue(parser: CirJsonParser, valueType: ResolvedType): T? {
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
    open fun <T : Any> readValue(parser: CirJsonParser, valueType: KotlinType): T? {
        return readValue(deserializationContext(parser), parser, myTypeFactory.constructType(valueType)) as T?
    }

    /**
     * Convenience method, equivalent in function to:
     * ```
     * readerFor(valueType).readValues(parser)
     * ```
     * 
     * Method for reading sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a CirJSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     * 
     * Note that [ObjectReader] has more complete set of variants.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(parser: CirJsonParser, valueType: KotlinType): MappingIterator<T> {
        val context = deserializationContext(parser)
        val deserializer = findRootDeserializer(context, valueType)
        return MappingIterator.construct(valueType, parser, context, deserializer, false, null)
    }

    /**
     * Convenience method, equivalent in function to:
     * ```
     * readerFor(valueType).readValues(parser)
     * ```
     * 
     * Method for reading sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a CirJSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     * 
     * Type-safe overload of [readValues] using [KotlinType].
     * 
     * Note that [ObjectReader] has more complete set of variants.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(parser: CirJsonParser, valueType: KClass<T>): MappingIterator<T> {
        return readValues(parser, myTypeFactory.constructType(valueType.java))
    }

    /**
     * Convenience method, equivalent in function to:
     * ```
     * readerFor(valueType).readValues(parser)
     * ```
     * 
     * Method for reading sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a CirJSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     * 
     * Note that [ObjectReader] has more complete set of variants.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(parser: CirJsonParser, valueType: TypeReference<T>): MappingIterator<T> {
        return readValues(parser, myTypeFactory.constructType(valueType))
    }

    /*
     *******************************************************************************************************************
     * Public API: deserialization, mapping from token stream to types
     *******************************************************************************************************************
     */

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param inputStream Input stream used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     * 
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(inputStream: InputStream): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, inputStream))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param reader Reader used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(reader: Reader): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, reader))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param content String used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: String): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, content))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param content ByteArray used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: ByteArray): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, content))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param content ByteArray used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: ByteArray, offset: Int, length: Int): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, content, offset, length))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param file File used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(file: File): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, file))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * @param path Path used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(path: Path): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, path))
    }

    /**
     * Method to deserialize CirJSON content as tree expressed using set of [CirJsonNode] instances. Returns root of the
     * resulting tree (where root can consist of just a single node if the current event is a value event, not
     * container).
     *
     * If a low-level I/O problem (missing input, network error) occurs, a [IOException] will be thrown. If a parsing
     * problem occurs (invalid CirJSON),
     * [StreamReadException][org.cirjson.cirjackson.core.exception.StreamReadException] will be thrown. If no content is
     * found from input (end-of-input), `null` will be returned.
     *
     * NOTE: handling of [URL] is delegated to [TokenStreamFactory.createParser] and usually simply calls
     * [URL.openStream], meaning no special handling is done. If different HTTP connection options are needed you will
     * need to create [InputStream] separately.
     *
     * @param url File used to read CirJSON content for building the CirJSON tree.
     *
     * @return a [CirJsonNode], if valid CirJSON content found; `null` if input has no content to bind -- note, however,
     * that if CirJSON `null` token is found, it will be represented as a non-`null` [CirJsonNode] (one that returns
     * `true` for [CirJsonNode.isNull])
     *
     * @throws org.cirjson.cirjackson.core.exception.StreamReadException if underlying input contains invalid content of
     * type [CirJsonParser] supports (CirJSON for default case)
     */
    @Throws(CirJacksonException::class)
    open fun readTree(url: URL): CirJsonNode {
        val context = deserializationContext()
        return readTreeAndClose(context, myStreamFactory.createParser(context, url))
    }

    /*
     *******************************************************************************************************************
     * Public API: serialization, mapping from types to token streams
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize any value as CirJSON output, using provided [CirJsonGenerator].
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(generator: CirJsonGenerator, value: Any?) {
        val config = serializationConfig()

        if (config.isEnabled(SerializationFeature.CLOSE_CLOSEABLE) && value is Closeable) {
            writeCloseableValue(generator, value, config)
        } else {
            serializerProvider(config).serializeValue(generator, value)

            if (config.isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)) {
                generator.flush()
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Public API: Additional Tree Model support beyond TreeCodec
     *******************************************************************************************************************
     */

    /**
     * Convenience conversion method that will bind data given CirJSON tree contains into specific value (usually bean)
     * type.
     * 
     * Functionally equivalent to:
     * ```
     * objectMapper.convertValue(node, valueType)
     * ```
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> treeToValue(node: TreeNode?, valueType: KClass<T>): T? {
        node ?: return null

        if (TreeNode::class.isAssignableFrom(valueType) && valueType.isAssignableFrom(node::class)) {
            return node as T
        }

        val token = node.asToken()

        if (token == CirJsonToken.VALUE_EMBEDDED_OBJECT && node is POJONode) {
            val obj = node.pojo ?: return null

            if (valueType.isInstance(obj)) {
                return obj as T
            }
        }

        return readValue(treeAsTokens(node), valueType)
    }

    /**
     * Convenience conversion method that will bind data given CirJSON tree contains into specific value (usually bean)
     * type.
     * 
     * Functionally equivalent to:
     * ```
     * objectMapper.convertValue(node, valueType)
     * ```
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> treeToValue(node: TreeNode?, valueType: KotlinType): T? {
        node ?: return null

        if (valueType.isTypeOrSubTypeOf(TreeNode::class) && valueType.isTypeOrSuperTypeOf(node::class)) {
            return node as T
        }

        val token = node.asToken()

        if (token == CirJsonToken.VALUE_EMBEDDED_OBJECT && node is POJONode) {
            val obj = node.pojo ?: return null

            if (valueType.isTypeOrSuperTypeOf(obj::class)) {
                return obj as T
            }
        }

        return readValue(treeAsTokens(node), valueType)
    }

    /**
     * Convenience conversion method that will bind data given CirJSON tree contains into specific value (usually bean)
     * type.
     * 
     * Functionally equivalent to:
     * ```
     * objectMapper.convertValue(node, valueType)
     * ```
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> treeToValue(node: TreeNode?, valueTypeReference: TypeReference<T>): T? {
        return treeToValue(node, constructType(valueTypeReference))
    }

    /**
     * Method that is reverse of [treeToValue]: it will convert given value (usually bean) into its equivalent Tree
     * model [CirJsonNode] representation. Functionally similar to serializing value into token stream and parsing that
     * stream back as tree model node, but more efficient as [org.cirjson.cirjackson.databind.util.TokenBuffer] is used
     * to contain the intermediate representation instead of fully serialized contents.
     * 
     * NOTE: while results are usually identical to that of serialization followed by deserialization, this is not
     * always the case. In some cases serialization into intermediate representation will retain encapsulation of things
     * like raw value ([org.cirjson.cirjackson.databind.util.RawValue]) or basic node identity ([CirJsonNode]). If so,
     * result is a valid tree, but values are not re-constructed through actual format representation. So if
     * transformation requires actual materialization of encoded content, it will be necessary to do actual
     * serialization.
     *
     * @param T Actual node type; usually either basic [CirJsonNode] or [org.cirjson.cirjackson.databind.node.ObjectNode]
     * 
     * @param fromValue value to convert
     *
     * @return (non-`null`) Root node of the resulting content tree: in case of `null`, value node for which
     * [CirJsonNode.isNull] returns `true`.
     */
    @Throws(CirJacksonException::class)
    open fun <T : CirJsonNode> valueToTree(fromValue: Any?): T {
        return serializerProvider().valueToTree(fromValue)
    }

    /*
     *******************************************************************************************************************
     * Public API: deserialization, external format to objects
     *******************************************************************************************************************
     */

    /**
     * Method to deserialize CirJSON content from given file into given type.
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
    open fun <T : Any> readValue(source: File, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given file into given type.
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
    open fun <T : Any> readValue(source: File, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given file into given type.
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
    open fun <T : Any> readValue(source: File, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given path into given type.
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
    open fun <T : Any> readValue(source: Path, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given path into given type.
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
    open fun <T : Any> readValue(source: Path, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given path into given type.
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
    open fun <T : Any> readValue(source: Path, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given URL into given type.
     *
     * NOTE: handling of [URL] is delegated to [TokenStreamFactory.createParser] and usually simply calls
     * [URL.openStream], meaning no special handling is done. If different HTTP connection options are needed you will
     * need to create [InputStream] separately.
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
    open fun <T : Any> readValue(source: URL, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given URL into given type.
     *
     * NOTE: handling of [URL] is delegated to [TokenStreamFactory.createParser] and usually simply calls
     * [URL.openStream], meaning no special handling is done. If different HTTP connection options are needed you will
     * need to create [InputStream] separately.
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
    open fun <T : Any> readValue(source: URL, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given URL into given type.
     *
     * NOTE: handling of [URL] is delegated to [TokenStreamFactory.createParser] and usually simply calls
     * [URL.openStream], meaning no special handling is done. If different HTTP connection options are needed you will
     * need to create [InputStream] separately.
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
    open fun <T : Any> readValue(source: URL, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given string into given type.
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
    open fun <T : Any> readValue(content: String, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given string into given type.
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
    open fun <T : Any> readValue(content: String, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given string into given type.
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
    open fun <T : Any> readValue(content: String, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given reader into given type.
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
    open fun <T : Any> readValue(source: Reader, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given reader into given type.
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
    open fun <T : Any> readValue(source: Reader, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given reader into given type.
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
    open fun <T : Any> readValue(source: Reader, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given input stream into given type.
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
    open fun <T : Any> readValue(source: InputStream, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given input stream into given type.
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
    open fun <T : Any> readValue(source: InputStream, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given input stream into given type.
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
    open fun <T : Any> readValue(source: InputStream, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given byte array into given type.
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
    open fun <T : Any> readValue(content: ByteArray, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given byte array into given type.
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
    open fun <T : Any> readValue(content: ByteArray, valueType: KClass<T>, offset: Int, length: Int): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content, offset, length),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given byte array into given type.
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
    open fun <T : Any> readValue(content: ByteArray, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given byte array into given type.
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
    open fun <T : Any> readValue(content: ByteArray, valueTypeReference: TypeReference<T>, offset: Int,
            length: Int): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content, offset, length),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given byte array into given type.
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
    open fun <T : Any> readValue(content: ByteArray, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given byte array into given type.
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
    open fun <T : Any> readValue(content: ByteArray, valueType: KotlinType, offset: Int, length: Int): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, content, offset, length), valueType) as T?
    }

    /**
     * Method to deserialize CirJSON content from given data input into given type.
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
    open fun <T : Any> readValue(source: DataInput, valueType: KClass<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueType.java)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given data input into given type.
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
    open fun <T : Any> readValue(source: DataInput, valueTypeReference: TypeReference<T>): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source),
                myTypeFactory.constructType(valueTypeReference)) as T?
    }

    /**
     * Method to deserialize CirJSON content from given data input into given type.
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
    open fun <T : Any> readValue(source: DataInput, valueType: KotlinType): T? {
        val context = deserializationContext()
        return readMapAndClose(context, myStreamFactory.createParser(context, source), valueType) as T?
    }

    /*
     *******************************************************************************************************************
     * Public API: serialization, mapping from types to external format
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize any value as CirJSON output, written to File provided (using encoding
     * [CirJsonEncoding.UTF8]).
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(file: File, value: Any?) {
        val provider = serializerProvider()
        configAndWriteValue(provider, myStreamFactory.createGenerator(provider, file, CirJsonEncoding.UTF8), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, written to Path provided (using encoding
     * [CirJsonEncoding.UTF8]).
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(path: Path, value: Any?) {
        val provider = serializerProvider()
        configAndWriteValue(provider, myStreamFactory.createGenerator(provider, path, CirJsonEncoding.UTF8), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, written to OutputStream provided (using
     * encoding [CirJsonEncoding.UTF8]).
     * 
     * Note: method does not close the underlying stream explicitly here; however, [TokenStreamFactory] this mapper uses
     * may choose to close the stream depending on its settings (by default, it will try to close it when constructed
     * [CirJsonGenerator] is closed).
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(outputStream: OutputStream, value: Any?) {
        val provider = serializerProvider()
        configAndWriteValue(provider, myStreamFactory.createGenerator(provider, outputStream, CirJsonEncoding.UTF8),
                value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, written to DataOutput provided.
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(dataOutput: DataOutput, value: Any?) {
        val provider = serializerProvider()
        configAndWriteValue(provider, myStreamFactory.createGenerator(provider, dataOutput), value)
    }

    /**
     * Method that can be used to serialize any value as CirJSON output, written to Writer provided.
     */
    @Throws(CirJacksonException::class)
    open fun writeValue(writer: Writer, value: Any?) {
        val provider = serializerProvider()
        configAndWriteValue(provider, myStreamFactory.createGenerator(provider, writer), value)
    }

    /**
     * Method that can be used to serialize any value as a String. Functionally equivalent to calling [writeValue] with
     * [StringWriter] and constructing String, but more efficient.
     */
    @Throws(CirJacksonException::class)
    open fun writeValueAsString(value: Any?): String {
        val bufferRecycler = myStreamFactory.bufferRecycler

        try {
            SegmentedStringWriter(bufferRecycler).use {
                val provider = serializerProvider()
                configAndWriteValue(provider, myStreamFactory.createGenerator(provider, it), value)
                return it.contentAndClear
            }
        } finally {
            bufferRecycler.releaseToPool()
        }
    }

    /**
     * Method that can be used to serialize any value as a ByteArray. Functionally equivalent to calling [writeValue]
     * with [ByteArrayOutputStream] and getting bytes, but more efficient. Encoding used will be UTF-8.
     */
    @Throws(CirJacksonException::class)
    open fun writeValueAsBytes(value: Any?): ByteArray {
        val bufferRecycler = myStreamFactory.bufferRecycler

        try {
            ByteArrayBuilder(bufferRecycler).use {
                val provider = serializerProvider()
                configAndWriteValue(provider, myStreamFactory.createGenerator(provider, it), value)
                return it.getClearAndRelease()
            }
        } finally {
            bufferRecycler.releaseToPool()
        }
    }

    /**
     * Method called to configure the generator as necessary and then call write functionality
     */
    @Throws(CirJacksonException::class)
    protected fun configAndWriteValue(provider: SerializationContextExtended, generator: CirJsonGenerator,
            value: Any?) {
        if (provider.isEnabled(SerializationFeature.CLOSE_CLOSEABLE) && value is Closeable) {
            configAndWriteCloseable(provider, generator, value)
            return
        }

        try {
            provider.serializeValue(generator, value)
        } catch (e: Exception) {
            closeOnFailAndThrowAsCirJacksonException(generator, e)
            return
        }

        generator.close()
    }

    /**
     * Helper method used when value to serialize is [Closeable] and its `close()` method is to be called right after
     * serialization has been called
     */
    @Throws(CirJacksonException::class)
    private fun configAndWriteCloseable(provider: SerializationContextExtended, generator: CirJsonGenerator,
            value: Any) {
        var toClose = value as Closeable?

        try {
            provider.serializeValue(generator, value)
            val tempToClose = toClose!!
            toClose = null
            tempToClose.close()
        } catch (e: Exception) {
            closeOnFailAndThrowAsCirJacksonException(generator, toClose, e)
            return
        }

        generator.close()
    }

    /**
     * Helper method used when value to serialize is [Closeable] and its `close()` method is to be called right after
     * serialization has been called
     */
    @Throws(CirJacksonException::class)
    protected fun writeCloseableValue(generator: CirJsonGenerator, value: Any, config: SerializationConfig) {
        val toClose = value as Closeable

        try {
            serializerProvider(config).serializeValue(generator, value)

            if (config.isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)) {
                generator.flush()
            }
        } catch (e: Exception) {
            closeOnFailAndThrowAsCirJacksonException(null, toClose, e)
            return
        }

        try {
            toClose.close()
        } catch (e: IOException) {
            throw CirJacksonIOException.construct(e)
        }
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
        return newWriter(serializationConfig())
    }

    /**
     * Factory method for constructing [ObjectWriter] with specified feature enabled (compared to settings that this
     * mapper instance has).
     */
    open fun writer(feature: SerializationFeature): ObjectWriter {
        return newWriter(serializationConfig().with(feature))
    }

    /**
     * Factory method for constructing [ObjectWriter] with specified features enabled (compared to settings that this
     * mapper instance has).
     */
    open fun writer(first: SerializationFeature, vararg features: SerializationFeature): ObjectWriter {
        return newWriter(serializationConfig().with(first, *features))
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using specified [DateFormat].
     */
    open fun writer(dateFormat: DateFormat): ObjectWriter {
        return newWriter(serializationConfig().with(dateFormat))
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using specified CirJSON View (filter).
     */
    open fun writerWithView(serializationView: KClass<*>?): ObjectWriter {
        return newWriter(serializationConfig().withView(serializationView))
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using specified root type, instead of
     * actual runtime type of value. Type must be a supertype of runtime type.
     * 
     * Main reason for using this method is performance, as writer is able to pre-fetch serializer to use before write,
     * and if writer is used more than once this avoids addition per-value serializer lookups.
     */
    open fun writerFor(rootType: KClass<*>?): ObjectWriter {
        return newWriter(serializationConfig(), rootType?.let { myTypeFactory.constructType(rootType.java) }, null)
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using specified root type, instead of
     * actual runtime type of value. Type must be a supertype of runtime type.
     * 
     * Main reason for using this method is performance, as writer is able to pre-fetch serializer to use before write,
     * and if writer is used more than once this avoids addition per-value serializer lookups.
     */
    open fun writerFor(rootType: TypeReference<*>?): ObjectWriter {
        return newWriter(serializationConfig(), rootType?.let { myTypeFactory.constructType(rootType) }, null)
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using specified root type, instead of
     * actual runtime type of value. Type must be a supertype of runtime type.
     * 
     * Main reason for using this method is performance, as writer is able to pre-fetch serializer to use before write,
     * and if writer is used more than once this avoids addition per-value serializer lookups.
     */
    open fun writerFor(rootType: KotlinType?): ObjectWriter {
        return newWriter(serializationConfig(), rootType, null)
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using the default pretty printer for
     * indentation.
     */
    open fun writerWithDefaultPrettyPrinter(): ObjectWriter {
        val config = serializationConfig()
        return newWriter(config, null, config.defaultPrettyPrinter)
    }

    /**
     * Factory method for constructing [ObjectWriter] that will serialize objects using specified filter provider.
     */
    open fun writer(filterProvider: FilterProvider?): ObjectWriter {
        return newWriter(serializationConfig().withFilters(filterProvider))
    }

    /**
     * Factory method for constructing [ObjectWriter] that will pass specific schema object to [CirJsonGenerator] used
     * for writing content.
     *
     * @param schema Schema to pass to generator
     */
    open fun writer(schema: FormatSchema?): ObjectWriter {
        return newWriter(serializationConfig(), schema)
    }

    /**
     * Factory method for constructing [ObjectWriter] that will use specified Base64 encoding variant for Base64-encoded
     * binary data.
     */
    open fun writer(base64Variant: Base64Variant): ObjectWriter {
        return newWriter(serializationConfig().with(base64Variant))
    }

    open fun writer(escapes: CharacterEscapes?): ObjectWriter {
        return newWriter(serializationConfig()).with(escapes)
    }

    open fun writer(attributes: ContextAttributes): ObjectWriter {
        return newWriter(serializationConfig().with(attributes))
    }

    /*
     *******************************************************************************************************************
     * Public API: constructing ObjectReaders for more advanced configuration
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing [ObjectReader] with default settings. Note that the resulting instance is NOT
     * usable as is, without defining expected value type.
     */
    open fun reader(): ObjectReader {
        return newReader(deserializationConfig()).with(myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] with specified feature enabled (compared to settings that this
     * mapper instance has). Note that the resulting instance is NOT usable as is, without defining expected value type.
     */
    open fun reader(feature: DeserializationFeature): ObjectReader {
        return newReader(deserializationConfig().with(feature))
    }

    /**
     * Factory method for constructing [ObjectReader] with specified features enabled (compared to settings that this
     * mapper instance has). Note that the resulting instance is NOT usable as is, without defining expected value type.
     */
    open fun reader(first: DeserializationFeature, vararg features: DeserializationFeature): ObjectReader {
        return newReader(deserializationConfig().with(first, *features))
    }

    /**
     * Factory method for constructing [ObjectReader] that will update given object (usually Bean, but can be a
     * [Collection] or [Map] as well, but NOT an [Array]) with CirJSON data. Deserialization occurs normally except that
     * the root-level value in CirJSON is not used for instantiating a new object; instead give updatable object is used
     * as root. Runtime type of value object is used for locating deserializer, unless overridden by other factory
     * methods of [ObjectReader]
     */
    open fun readerForUpdating(valueToUpdate: Any?): ObjectReader {
        val type = valueToUpdate?.let { myTypeFactory.constructType(it::class.java) }
        return newReader(deserializationConfig(), type, valueToUpdate, null, myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will read or update instances of specified type.
     */
    open fun readerFor(type: KotlinType): ObjectReader {
        return newReader(deserializationConfig(), type, null, null, myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will read or update instances of specified type.
     */
    open fun readerFor(type: KClass<*>): ObjectReader {
        return newReader(deserializationConfig(), myTypeFactory.constructType(type.java), null, null,
                myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will read or update instances of specified type.
     */
    open fun readerFor(type: TypeReference<*>): ObjectReader {
        return newReader(deserializationConfig(), myTypeFactory.constructType(type), null, null, myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will read values of a type `Array<type>`. Functionally same
     * as:
     * ```
     * readerFor(Array<type>::class)
     * ```
     */
    open fun readerForArrayOf(type: KClass<*>): ObjectReader {
        return newReader(deserializationConfig(), myTypeFactory.constructArrayType(type), null, null,
                myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will read or update instances of a type `List<type>`.
     * Functionally same as:
     * ```
     * readerFor(object : TypeReference<List<type>>() {})
     * ```
     */
    open fun readerForListOf(type: KClass<*>): ObjectReader {
        return newReader(deserializationConfig(), myTypeFactory.constructCollectionLikeType(List::class, type), null,
                null, myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will read or update instances of a type `Map<String, type>`.
     * Functionally same as:
     * ```
     * readerFor(object : TypeReference<Map<String, type>>() {})
     * ```
     */
    open fun readerForMapOf(type: KClass<*>): ObjectReader {
        return newReader(deserializationConfig(), myTypeFactory.constructMapLikeType(Map::class, String::class, type),
                null, null, myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will use specified [CirJsonNodeFactory] for constructing
     * CirJSON trees.
     */
    open fun reader(nodeFactory: CirJsonNodeFactory): ObjectReader {
        return newReader(deserializationConfig()).with(nodeFactory)
    }

    /**
     * Factory method for constructing [ObjectReader] that will pass specific schema object to [CirJsonParser] used for
     * reading content.
     *
     * @param schema Schema to pass to parser
     */
    open fun reader(schema: FormatSchema?): ObjectReader {
        return newReader(deserializationConfig(), null, null, schema, myInjectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will use specified injectable values.
     *
     * @param injectableValues Injectable values to use
     */
    open fun reader(injectableValues: InjectableValues): ObjectReader {
        return newReader(deserializationConfig(), null, null, null, injectableValues)
    }

    /**
     * Factory method for constructing [ObjectReader] that will deserialize objects using specified CirJSON View
     * (filter).
     */
    open fun readerWithView(view: KClass<*>): ObjectReader {
        return newReader(deserializationConfig().withView(view))
    }

    /**
     * Factory method for constructing [ObjectReader] that will use specified Base64 encoding variant for Base64-encoded
     * binary data.
     */
    open fun reader(base64Variant: Base64Variant): ObjectReader {
        return newReader(deserializationConfig().with(base64Variant))
    }

    /**
     * Factory method for constructing [ObjectReader] that will use specified default attributes.
     */
    open fun reader(attributes: ContextAttributes): ObjectReader {
        return newReader(deserializationConfig().with(attributes))
    }

    /*
     *******************************************************************************************************************
     * Extended Public API: convenience type conversion
     *******************************************************************************************************************
     */

    /**
     * Convenience method for doing two-step conversion from given value, into instance of given value type, by writing
     * value into temporary buffer and reading from the buffer into specified target type.
     * 
     * This method is functionally similar to first serializing given value into CirJSON, and then binding CirJSON data
     * into value of given type, but should be more efficient since full serialization does not (need to) occur.
     * However, same converters (serializers, deserializers) will be used as for data binding, meaning same object
     * mapper configuration works.
     * 
     * Note that it is possible that in some cases behavior does differ from full serialize-then-deserialize cycle: in
     * most case differences are unintentional (that is, flaws to fix) and should be reported, but the behavior is not
     * guaranteed to be 100% the same: the goal is to allow efficient value conversions for structurally compatible
     * objects, according to standard CirJackson configuration.
     * 
     * Further note that this functionality is not designed to support "advanced" use cases, such as conversion of
     * polymorphic values, or cases where Object Identity is used.
     *
     * @throws IllegalArgumentException If conversion fails due to incompatible type; if so, root cause will contain
     * underlying checked exception that data binding functionality threw.
     */
    @Throws(IllegalArgumentException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> convertValue(fromValue: Any?, toValueType: KotlinType): T? {
        return convert(fromValue, toValueType) as T?
    }

    /**
     * See [convertValue]
     */
    @Throws(IllegalArgumentException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> convertValue(fromValue: Any?, toValueType: KClass<T>): T? {
        return convert(fromValue, myTypeFactory.constructType(toValueType.java)) as T?
    }

    /**
     * See [convertValue]
     */
    @Throws(IllegalArgumentException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> convertValue(fromValue: Any?, toValueTypeReference: TypeReference<T>): T? {
        return convert(fromValue, myTypeFactory.constructType(toValueTypeReference)) as T?
    }

    /**
     * Actual conversion implementation: instead of using existing read and write methods, much of code is inlined.
     * Reason for this is that we must avoid root value wrapping/unwrapping both for efficiency and for correctness. If
     * root value wrapping/unwrapping is actually desired, caller must use explicit `writeValue` and `readValue`
     * methods.
     */
    @Throws(CirJacksonException::class)
    protected open fun convert(fromValue: Any?, toValueType: KotlinType): Any? {
        val config = serializationConfig().without(SerializationFeature.WRAP_ROOT_VALUE)
        val context = serializerProvider(config)
        var buffer = context.bufferForValueConversion()

        if (isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
            buffer = buffer.forceUseOfBigDecimal(true)
        }

        context.serializeValue(buffer, fromValue)

        val readContext = deserializationContext()

        buffer.asParser(readContext).use {
            readContext.assignParser(it)
            val token = initForReading(it, toValueType)

            return when (token) {
                CirJsonToken.VALUE_NULL -> findRootDeserializer(readContext, toValueType).getNullValue(readContext)
                CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> null
                else -> findRootDeserializer(readContext, toValueType).deserialize(it, readContext)
            }
        }
    }

    /**
     * Convenience method similar to [convertValue].
     * 
     * Implementation is approximately as follows:
     *
     * 1. Serialize `updateWithValue` into [org.cirjson.cirjackson.databind.util.TokenBuffer];
     *
     * 2. Construct [ObjectReader] with `valueToUpdate` (using [readerForUpdating]);
     *
     * 3. Construct [CirJsonParser] (using [org.cirjson.cirjackson.databind.util.TokenBuffer.asParser]);
     *
     * 4. Update using [ObjectReader.readValue];
     *
     * 5. Return `valueToUpdate`.
     * 
     * Note that update is "shallow" in that only first level of properties (or, immediate contents of container to
     * update) are modified, unless properties themselves indicate that merging should be applied for contents. Such
     * merging can be specified using annotations (see `CirJsonMerge`) as well as using "config overrides" (see
     * [MapperBuilder.withConfigOverride] and [MapperBuilder.defaultMergeable]).
     *
     * @param valueToUpdate Object to update
     *
     * @param overrides Object to conceptually serialize and merge into value to update; can be thought of as a provider
     * for overrides to apply.
     *
     * @return Either the first argument (`valueToUpdate`), if it is mutable; or a result of creating new instance that
     * is result of "merging" values (for example, "updating" an array will create a new array)
     *
     * @throws CirJacksonException if there are structural incompatibilities that prevent update.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> updateValue(valueToUpdate: T?, overrides: Any?): T? {
        valueToUpdate ?: return null
        overrides ?: return valueToUpdate

        val config = serializationConfig().without(SerializationFeature.WRAP_ROOT_VALUE)
        val context = serializerProvider(config)
        var buffer = context.bufferForValueConversion()

        if (isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
            buffer = buffer.forceUseOfBigDecimal(true)
        }

        context.serializeValue(buffer, overrides)

        buffer.asParser(ObjectReadContext.empty()).use {
            return readerForUpdating(valueToUpdate).readValue(it)
        }
    }

    /*
     *******************************************************************************************************************
     * Extended Public API: CirJSON Schema generation
     *******************************************************************************************************************
     */

    /**
     * Method for visiting type hierarchy for given type, using specified visitor.
     *
     * This method can be used for things like generating CirJSON Schema instance for specified type.
     *
     * @param type Type to generate schema for (possibly with generic signature)
     */
    open fun acceptCirJsonFormatVisitor(type: KClass<*>, visitor: CirJsonFormatVisitorWrapper) {
        serializerProvider().acceptCirJsonFormatVisitor(myTypeFactory.constructType(type.java), visitor)
    }

    /**
     * Method for visiting type hierarchy for given type, using specified visitor.
     *
     * This method can be used for things like generating CirJSON Schema instance for specified type.
     *
     * @param type Type to generate schema for (possibly with generic signature)
     */
    open fun acceptCirJsonFormatVisitor(type: TypeReference<*>, visitor: CirJsonFormatVisitorWrapper) {
        serializerProvider().acceptCirJsonFormatVisitor(myTypeFactory.constructType(type), visitor)
    }

    /**
     * Method for visiting type hierarchy for given type, using specified visitor. Visitation uses `Serializer`
     * hierarchy and related properties
     *
     * This method can be used for things like generating CirJSON Schema instance for specified type.
     *
     * @param type Type to generate schema for (possibly with generic signature)
     */
    open fun acceptCirJsonFormatVisitor(type: KotlinType, visitor: CirJsonFormatVisitorWrapper) {
        serializerProvider().acceptCirJsonFormatVisitor(type, visitor)
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
    protected open fun newWriter(config: SerializationConfig): ObjectWriter {
        return ObjectWriter.construct(this, config)
    }

    /**
     * Factory method subclasses must override to produce [ObjectWriter] instances of proper subtype
     */
    protected open fun newWriter(config: SerializationConfig, schema: FormatSchema?): ObjectWriter {
        return ObjectWriter.construct(this, config, schema)
    }

    /**
     * Factory method subclasses must override to produce [ObjectWriter] instances of proper subtype
     */
    protected open fun newWriter(config: SerializationConfig, rootType: KotlinType?,
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