package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.filter.CirJsonPointerBasedFilter
import org.cirjson.cirjackson.core.filter.FilteringParserDelegate
import org.cirjson.cirjackson.core.filter.TokenFilter
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.configuration.ContextAttributes
import org.cirjson.cirjackson.databind.configuration.DatatypeFeature
import org.cirjson.cirjackson.databind.configuration.DeserializationContexts
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.deserialization.DeserializationContextExtended
import org.cirjson.cirjackson.databind.deserialization.DeserializationProblemHandler
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.node.TreeTraversingParser
import org.cirjson.cirjackson.databind.type.SimpleType
import org.cirjson.cirjackson.databind.type.TypeFactory
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Builder object that can be used for per-serialization configuration of deserialization parameters, such as root type
 * to use or object to update (instead of constructing new instance).
 * 
 * Uses "mutant factory" pattern so that instances are immutable (and thus fully thread-safe with no external
 * synchronization); new instances are constructed for different configurations. Instances are initially constructed by
 * [ObjectMapper] and can be reused, shared, cached; both because of thread-safety and because instances are relatively
 * light-weight.
 * 
 * NOTE: this class is NOT meant as subclassable by users. It is left as non-final mostly to allow frameworks that
 * require byte code generation for proxying and similar use cases, but there is no expectation that functionality
 * should be extended by subclassing.
 */
open class ObjectReader : Versioned, TreeCodec {

    /*
     *******************************************************************************************************************
     * Immutable configuration from ObjectMapper
     *******************************************************************************************************************
     */

    /**
     * General serialization configuration settings; while immutable, can use copy-constructor to create modified
     * instances as necessary.
     */
    protected val myConfig: DeserializationConfig

    /**
     * Blueprint instance of deserialization context; used for creating actual instance when needed.
     */
    protected val myContexts: DeserializationContexts

    /**
     * Factory used for constructing [CirJsonParsers][CirJsonParser].
     */
    protected val myParserFactory: TokenStreamFactory

    /**
     * Flag that indicates whether root values are expected to be unwrapped or not
     */
    protected val myUnwrapRoot: Boolean

    /**
     * Filter to be considered for CirJsonParser. Default value to be `null` as filter not considered.
     */
    private val myFilter: TokenFilter?

    /*
     *******************************************************************************************************************
     * Configuration that can be changed during building
     *******************************************************************************************************************
     */

    /**
     * Declared type of value to instantiate during deserialization. Defines which deserializer to use; as well as base
     * type of instance to construct if an updatable value is not configured to be used (subject to changes by embedded
     * type information, for polymorphic types). If [myValueToUpdate] is non-`null`, only used for locating
     * deserializer.
     */
    protected val myValueType: KotlinType?

    /**
     * We may pre-fetch deserializer as soon as [myValueType] is known, and if so, reuse it afterward. This allows
     * avoiding further deserializer lookups and increases performance a bit on cases where readers are reused.
     */
    protected val myRootDeserializer: ValueDeserializer<Any>?

    /**
     * Instance to update with data binding; if any. If `null`, a new instance is created, if non-`null`, properties of
     * this value object will be updated instead. Note that value can be of almost any type, except not
     * [org.cirjson.cirjackson.databind.type.ArrayType]; array types cannot be modified because array size is immutable.
     */
    protected val myValueToUpdate: Any?

    /**
     * When using data format that uses a schema, schema is passed to parser.
     */
    protected val mySchema: FormatSchema?

    /**
     * Values that can be injected during deserialization, if any.
     */
    protected val myInjectableValues: InjectableValues?

    /*
     *******************************************************************************************************************
     * Caching
     *******************************************************************************************************************
     */

    /**
     * Root-level cached deserializers. Passed by [ObjectMapper], shared with it.
     */
    protected val myRootDeserializers: ConcurrentHashMap<KotlinType, ValueDeserializer<Any>>

    /*
     *******************************************************************************************************************
     * Lifecycle, construction
     *******************************************************************************************************************
     */

    /**
     * Constructor used by [ObjectMapper] for initial instantiation
     */
    protected constructor(mapper: ObjectMapper, config: DeserializationConfig) : this(mapper, config, null, null, null,
            null)

    /**
     * Constructor called when a root deserializer should be fetched based on other configuration.
     */
    protected constructor(mapper: ObjectMapper, config: DeserializationConfig, valueType: KotlinType?,
            valueToUpdate: Any?, schema: FormatSchema?, injectableValues: InjectableValues?) {
        myConfig = config
        myContexts = mapper.deserializationContexts()

        myRootDeserializers = mapper.rootDeserializers()
        myParserFactory = mapper.streamFactory()

        myValueType = valueType
        myValueToUpdate = valueToUpdate
        mySchema = schema
        myInjectableValues = injectableValues
        myUnwrapRoot = config.useRootWrapping()

        myRootDeserializer = prefetchRootDeserializer(valueType)
        myFilter = null
    }

    /**
     * Copy constructor used for building variations.
     */
    protected constructor(base: ObjectReader, config: DeserializationConfig, valueType: KotlinType?,
            rootDeserializer: ValueDeserializer<Any>?, valueToUpdate: Any?, schema: FormatSchema?,
            injectableValues: InjectableValues?) {
        myConfig = config
        myContexts = base.myContexts

        myRootDeserializers = base.myRootDeserializers
        myParserFactory = base.myParserFactory

        myValueType = valueType
        myValueToUpdate = valueToUpdate
        mySchema = schema
        myInjectableValues = injectableValues
        myUnwrapRoot = config.useRootWrapping()

        myRootDeserializer = rootDeserializer
        myFilter = null
    }

    /**
     * Copy constructor used when modifying simple feature flags
     */
    protected constructor(base: ObjectReader, config: DeserializationConfig) {
        myConfig = config
        myContexts = base.myContexts

        myRootDeserializers = base.myRootDeserializers
        myParserFactory = base.myParserFactory

        myValueType = base.myValueType
        myValueToUpdate = base.myValueToUpdate
        mySchema = base.mySchema
        myInjectableValues = base.myInjectableValues
        myUnwrapRoot = config.useRootWrapping()

        myRootDeserializer = base.myRootDeserializer
        myFilter = base.myFilter
    }

    protected constructor(base: ObjectReader, filter: TokenFilter?) {
        myConfig = base.myConfig
        myContexts = base.myContexts

        myRootDeserializers = base.myRootDeserializers
        myParserFactory = base.myParserFactory

        myValueType = base.myValueType
        myValueToUpdate = base.myValueToUpdate
        mySchema = base.mySchema
        myInjectableValues = base.myInjectableValues
        myUnwrapRoot = base.myUnwrapRoot

        myRootDeserializer = base.myRootDeserializer
        myFilter = filter
    }

    /**
     * Method that will return version information stored in and read from jar that contains this class.
     */
    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Helper methods used internally for invoking constructors. Need to be overridden if subclassing (not recommended)
     * is used.
     *******************************************************************************************************************
     */

    /**
     * Factory method called by various `withXxx()` methods
     */
    protected open fun new(base: ObjectReader, config: DeserializationConfig): ObjectReader {
        return ObjectReader(base, config)
    }

    /**
     * Factory method called by various `withXxx()` methods
     */
    protected open fun new(base: ObjectReader, config: DeserializationConfig, valueType: KotlinType?,
            rootDeserializer: ValueDeserializer<Any>?, valueToUpdate: Any?, schema: FormatSchema?,
            injectableValues: InjectableValues?): ObjectReader {
        return ObjectReader(base, config, valueType, rootDeserializer, valueToUpdate, schema, injectableValues)
    }

    /**
     * Factory method used to create [MappingIterator] instances; either default, or custom subtype.
     */
    protected open fun <T : Any> newIterator(parser: CirJsonParser, context: DeserializationContext,
            deserializer: ValueDeserializer<*>, parserManaged: Boolean): MappingIterator<T> {
        return MappingIterator.construct(myValueType, parser, context, deserializer, parserManaged, myValueToUpdate)
    }

    /*
     *******************************************************************************************************************
     * Methods for initializing parser instance to use
     *******************************************************************************************************************
     */

    protected open fun initForReading(context: DeserializationContextExtended, parser: CirJsonParser): CirJsonToken {
        context.assignParser(parser)

        return parser.currentToken() ?: parser.nextToken() ?: context.reportInputMismatch(myValueType!!,
                "No content to map due to end-of-input")
    }

    /**
     * Alternative to [initForReading] used in cases where reading of multiple values means that we may or may not want
     * to advance the stream, but need to do other initialization.
     * 
     * Base implementation only sets configured [FormatSchema], if any, on parser.
     */
    protected open fun initForMultiRead(context: DeserializationContextExtended, parser: CirJsonParser) {
        context.assignParser(parser)
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factory methods for DeserializationFeatures
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new reader instance that is configured with specified feature enabled.
     */
    open fun withConfig(feature: DeserializationFeature): ObjectReader {
        return withConfig(myConfig.with(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features enabled.
     */
    open fun withConfig(first: DeserializationFeature, vararg other: DeserializationFeature): ObjectReader {
        return withConfig(myConfig.with(first, *other))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: DeserializationFeature): ObjectReader {
        return withConfig(myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified feature disabled.
     */
    open fun without(feature: DeserializationFeature): ObjectReader {
        return withConfig(myConfig.without(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features disabled.
     */
    open fun without(first: DeserializationFeature, vararg other: DeserializationFeature): ObjectReader {
        return withConfig(myConfig.without(first, *other))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: DeserializationFeature): ObjectReader {
        return withConfig(myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factory methods for DatatypeFeatures
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new reader instance that is configured with specified feature enabled.
     */
    open fun withConfig(feature: DatatypeFeature): ObjectReader {
        return withConfig(myConfig.with(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: DatatypeFeature): ObjectReader {
        return withConfig(myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified feature disabled.
     */
    open fun without(feature: DatatypeFeature): ObjectReader {
        return withConfig(myConfig.without(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: DatatypeFeature): ObjectReader {
        return withConfig(myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factory methods for StreamReadFeatures
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new reader instance that is configured with specified feature enabled.
     */
    open fun withConfig(feature: StreamReadFeature): ObjectReader {
        return withConfig(myConfig.with(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: StreamReadFeature): ObjectReader {
        return withConfig(myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified feature disabled.
     */
    open fun without(feature: StreamReadFeature): ObjectReader {
        return withConfig(myConfig.without(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: StreamReadFeature): ObjectReader {
        return withConfig(myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factory methods for FormatFeatures
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a new reader instance that is configured with specified feature enabled.
     */
    open fun withConfig(feature: FormatFeature): ObjectReader {
        return withConfig(myConfig.with(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features enabled.
     */
    open fun withFeatures(vararg features: FormatFeature): ObjectReader {
        return withConfig(myConfig.withFeatures(*features))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified feature disabled.
     */
    open fun without(feature: FormatFeature): ObjectReader {
        return withConfig(myConfig.without(feature))
    }

    /**
     * Method for constructing a new reader instance that is configured with specified features disabled.
     */
    open fun withoutFeatures(vararg features: FormatFeature): ObjectReader {
        return withConfig(myConfig.withoutFeatures(*features))
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, fluent factory methods, other
     *******************************************************************************************************************
     */

    /**
     * Convenience method to bind from [CirJsonPointer]. [CirJsonPointerBasedFilter] is registered and will be used for
     * parsing later.
     */
    open fun at(pointerExpression: String): ObjectReader {
        return ObjectReader(this, CirJsonPointerBasedFilter(pointerExpression))
    }

    /**
     * Convenience method to bind from [CirJsonPointer]. [CirJsonPointerBasedFilter] is registered and will be used for
     * parsing later.
     */
    open fun at(pointer: CirJsonPointer): ObjectReader {
        return ObjectReader(this, CirJsonPointerBasedFilter(pointer))
    }

    /**
     * Mutant factory method that will construct a new instance that has specified underlying [DeserializationConfig].
     * 
     * NOTE: use of this method is not recommended, as there are many other re-configuration methods available.
     */
    open fun with(config: DeserializationConfig): ObjectReader {
        return withConfig(config)
    }

    /**
     * Method for constructing a new instance with configuration that uses passed [InjectableValues] to provide
     * injectable values.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance if the provided values aren't the same object as the one this ObjectReader uses.
     */
    open fun with(injectableValues: InjectableValues?): ObjectReader {
        if (myInjectableValues === injectableValues) {
            return this
        }

        return new(this, myConfig, myValueType, myRootDeserializer, myValueToUpdate, mySchema, injectableValues)
    }

    /**
     * Method for constructing a new reader instance with configuration that uses passed [CirJsonNodeFactory] for
     * constructing [CirJsonNode] instances.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun with(factory: CirJsonNodeFactory): ObjectReader {
        return withConfig(myConfig.with(factory))
    }

    /**
     * Method for constructing a new instance with configuration that specifies what root name to expect for "root name
     * unwrapping". See [DeserializationConfig.withRootName] for details.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun withRootName(rootName: String?): ObjectReader {
        return withConfig(myConfig.withRootName(rootName))
    }

    /**
     * Method for constructing a new instance with configuration that specifies what root name to expect for "root name
     * unwrapping". See [DeserializationConfig.withRootName] for details.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun withRootName(rootName: PropertyName): ObjectReader {
        return withConfig(myConfig.withRootName(rootName))
    }

    /**
     * Convenience method that is same as calling `withRootName("")` which will forcibly prevent use of root name
     * wrapping when writing values with this [ObjectReader].
     */
    open fun withoutRootName(): ObjectReader {
        return withConfig(myConfig.withRootName(PropertyName.NO_NAME))
    }

    /**
     * Method for constructing a new instance with configuration that passes specified [FormatSchema] to [CirJsonParser]
     * that is constructed for parsing content.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun with(schema: FormatSchema?): ObjectReader {
        if (mySchema === schema) {
            return this
        }

        verifySchemaType(schema)
        return new(this, myConfig, myValueType, myRootDeserializer, myValueToUpdate, schema, myInjectableValues)
    }

    /**
     * Method for constructing a new reader instance that is configured to data bind into specified type.
     * 
     * Note that the method does not change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun forType(valueType: KotlinType?): ObjectReader {
        if (valueType != null && valueType == myValueType) {
            return this
        }

        val rootDeserializer = prefetchRootDeserializer(valueType)
        return new(this, myConfig, valueType, rootDeserializer, myValueToUpdate, mySchema, myInjectableValues)
    }

    /**
     * Method for constructing a new reader instance that is configured to data bind into specified type.
     * 
     * Note that the method does not change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun forType(valueType: KClass<*>): ObjectReader {
        return forType(myConfig.constructType(valueType))
    }

    /**
     * Method for constructing a new reader instance that is configured to data bind into specified type.
     * 
     * Note that the method does not change state of this reader, but rather construct and returns a newly configured
     * instance.
     */
    open fun forType(valueType: TypeReference<*>): ObjectReader {
        return forType(myConfig.typeFactory.constructType(valueType.type))
    }

    open fun withValueToUpdate(value: Any?): ObjectReader {
        if (myValueToUpdate === value) {
            return this
        }

        value ?: return new(this, myConfig, myValueType, myRootDeserializer, null, mySchema, myInjectableValues)

        val type = myValueType ?: myConfig.constructType(value::class)
        return new(this, myConfig, type, myRootDeserializer, value, mySchema, myInjectableValues)
    }

    /**
     * Method for constructing a new instance with configuration that uses specified View for filtering.
     * 
     * Note that the method does NOT change state of this reader, but rather construct and returns a newly configured instance.
     */
    open fun withView(activeView: KClass<*>): ObjectReader {
        return withConfig(myConfig.withView(activeView))
    }

    open fun with(locale: Locale): ObjectReader {
        return withConfig(myConfig.with(locale))
    }

    open fun with(timeZone: TimeZone): ObjectReader {
        return withConfig(myConfig.with(timeZone))
    }

    open fun withHandler(handler: DeserializationProblemHandler): ObjectReader {
        return withConfig(myConfig.withHandler(handler))
    }

    open fun with(base64Variant: Base64Variant): ObjectReader {
        return withConfig(myConfig.with(base64Variant))
    }

    /**
     * Mutant factory for overriding set of (default) attributes for [ObjectReader] to use.
     * 
     * Note that this will replace defaults passed by [ObjectMapper].
     *
     * @param attributes Default [ContextAttributes] to use with a reader
     *
     * @return [ObjectReader] instance with specified default attributes (which is usually a newly constructed reader
     * instance with otherwise identical settings)
     */
    open fun with(attributes: ContextAttributes): ObjectReader {
        return withConfig(myConfig.with(attributes))
    }

    open fun withAttributes(attributes: Map<*, *>): ObjectReader {
        return withConfig(myConfig.withAttributes(attributes))
    }

    open fun withAttribute(key: Any, value: Any): ObjectReader {
        return withConfig(myConfig.withAttribute(key, value))
    }

    open fun withoutAttribute(key: Any): ObjectReader {
        return withConfig(myConfig.withoutAttribute(key))
    }

    /*
     *******************************************************************************************************************
     * Internal factory methods
     *******************************************************************************************************************
     */

    protected fun withConfig(newConfig: DeserializationConfig): ObjectReader {
        if (newConfig === myConfig) {
            return this
        }

        return new(this, newConfig)
    }

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    open fun isEnabled(feature: DeserializationFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: MapperFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: DatatypeFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open fun isEnabled(feature: StreamReadFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    open val config: DeserializationConfig
        get() = myConfig

    open fun parserFactory(): TokenStreamFactory {
        return myParserFactory
    }

    open fun typeFactory(): TypeFactory {
        return myConfig.typeFactory
    }

    open val attributes: ContextAttributes
        get() = myConfig.attributes

    open val injectableValues: InjectableValues?
        get() = myInjectableValues

    open val valueType: KotlinType?
        get() = myValueType

    /*
     *******************************************************************************************************************
     * Public API: constructing Parsers that are properly linked to ObjectReadContext
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(source: File): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, source))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(source: Path): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, source))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(source: URL): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, source))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(source: InputStream): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, source))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(source: Reader): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, source))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: ByteArray): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: ByteArray, offset: Int, length: Int): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, content, offset, length))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: String): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: CharArray): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: CharArray, offset: Int, length: Int): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, content, offset, length))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls the related [TokenStreamFactory.createParser].
     */
    @Throws(CirJacksonException::class)
    open fun createParser(content: DataInput): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createParser(context, content))
    }

    /**
     * Factory method for constructing [CirJsonParser] that is properly wired to allow callbacks for deserialization:
     * basically constructs a [ObjectReadContext] and then calls [TokenStreamFactory.createNonBlockingByteArrayParser].
     */
    @Throws(CirJacksonException::class)
    open fun createNonBlockingByteArrayParser(): CirJsonParser {
        val context = deserializationContext()
        return context.assignAndReturnParser(myParserFactory.createNonBlockingByteArrayParser(context))
    }

    /*
     *******************************************************************************************************************
     * TreeCodec implementation
     *******************************************************************************************************************
     */

    override fun createObjectNode(): ObjectNode {
        return myConfig.nodeFactory.objectNode()
    }

    override fun createArrayNode(): ArrayNode {
        return myConfig.nodeFactory.arrayNode()
    }

    override fun booleanNode(boolean: Boolean): CirJsonNode {
        return myConfig.nodeFactory.booleanNode(boolean)
    }

    override fun stringNode(text: String?): CirJsonNode {
        return myConfig.nodeFactory.textNode(text)
    }

    override fun missingNode(): CirJsonNode {
        return myConfig.nodeFactory.missingNode()
    }

    override fun nullNode(): CirJsonNode {
        return myConfig.nodeFactory.nullNode()
    }

    override fun treeAsTokens(node: TreeNode): CirJsonParser {
        return treeAsTokens(node as CirJsonNode, deserializationContext())
    }

    protected open fun treeAsTokens(node: CirJsonNode, context: DeserializationContext): CirJsonParser {
        return TreeTraversingParser(node, context)
    }

    /**
     * Convenience method that binds content read using given parser, using configuration of this reader, except that
     * content is bound as CirJSON tree instead of configured root value type. Returns [CirJsonNode] that represents the
     * root of the resulting tree, if there was content to read, or `null` if no more content is accessible via passed
     * [CirJsonParser].
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and all other methods
     * that take input source: latter will return "missing node", NOT `null`
     * 
     * Note: if an object was specified with [withValueToUpdate], it will be ignored.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        return bindAsTreeOrNull(deserializationContext(parser), parser) as T?
    }

    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        throw UnsupportedOperationException()
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods; first ones for pre-constructed parsers
     *******************************************************************************************************************
     */

    /**
     * Method that binds content read using given parser, using configuration of this reader, including expected result
     * type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(parser: CirJsonParser): T? {
        val context = deserializationContext(parser)
        return bind(context, parser, myValueToUpdate) as T?
    }

    /**
     * Convenience method that is equivalent to:
     * ```
     * forType(valueType).readValues(parser)
     * ```
     * 
     * Method reads a sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a JSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValues(parser: CirJsonParser, valueType: KClass<T>): Iterator<T> {
        return forType(valueType).readValues<Any>(parser) as Iterator<T>
    }

    /**
     * Convenience method that is equivalent to:
     * ```
     * forType(valueTypeReference).readValues(parser)
     * ```
     * 
     * Method reads a sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a JSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValues(parser: CirJsonParser, valueTypeReference: TypeReference<T>): Iterator<T> {
        return forType(valueTypeReference).readValues<Any>(parser) as Iterator<T>
    }

    /**
     * Convenience method that is equivalent to:
     * ```
     * forType(valueType).readValues(parser)
     * ```
     * 
     * Method reads a sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a JSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(parser: CirJsonParser, valueType: ResolvedType): Iterator<T> {
        return readValues(parser, valueType as KotlinType)
    }

    /**
     * Convenience method that is equivalent to:
     * ```
     * forType(valueType).readValues(parser)
     * ```
     * 
     * Method reads a sequence of Objects from parser stream. Sequence can be either root-level "unwrapped" sequence
     * (without surrounding CirJSON array), or a sequence contained in a JSON Array. In either case [CirJsonParser]
     * **MUST** point to the first token of the first element, OR not point to any token (in which case it is advanced
     * to the next token). This means, specifically, that for wrapped sequences, parser MUST NOT point to the
     * surrounding `START_ARRAY` (one that contains values to read) but rather to the token following it which is the
     * first token of the first value to read.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValues(parser: CirJsonParser, valueType: KotlinType): Iterator<T> {
        return forType(valueType).readValues<Any>(parser) as Iterator<T>
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods; others similar to what ObjectMapper has
     *******************************************************************************************************************
     */

    /**
     * Method that binds content read using given input source, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param input Source to read content from
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(input: InputStream): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, input), false)) as T?
    }

    /**
     * Method that binds content read using given input source, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param reader Source to read content from
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(reader: Reader): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, reader), false)) as T?
    }

    /**
     * Method that binds content read using given CirJSON string, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param content String that contains content to read
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(content: String): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, content), false)) as T?
    }

    /**
     * Method that binds content read using given ByteArray, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param content Byte array that contains encoded content to read
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(content: ByteArray): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, content), false)) as T?
    }

    /**
     * Method that binds content read using given ByteArray, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param buffer Byte array that contains encoded content to read
     * 
     * @param offset Offset of the first content byte in `buffer`
     * 
     * @param length Length of content in `buffer`, in bytes
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(buffer: ByteArray, offset: Int, length: Int): T? {
        val context = deserializationContext()
        return bindAndClose(context,
                considerFilter(myParserFactory.createParser(context, buffer, offset, length), false)) as T?
    }

    /**
     * Method that binds content read using given [File], using configuration of this reader, including expected result
     * type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param file File that contains content to read
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(file: File): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, file), false)) as T?
    }

    /**
     * Method that binds content read using given [Path], using configuration of this reader, including expected result
     * type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param path Path that contains content to read
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(path: Path): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, path), false)) as T?
    }

    /**
     * Method that binds content read from given input source, using configuration of this reader. Value return is
     * either newly constructed, or root value that was specified with [withValueToUpdate].
     * 
     * NOTE: handling of [URL] is delegated to [TokenStreamFactory.createParser] and usually simply calls
     * [URL.openStream], meaning no special handling is done. If different HTTP connection options are needed you will
     * need to create [InputStream] separately.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(url: URL): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, url), false)) as T?
    }

    /**
     * Convenience method for converting results from given JSON tree into given
     * value type. Basically short-cut for:
     * ```
     * objectReader.readValue(src.traverse())
     * ```
     *
     * @param node Tree that contains content to convert
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(node: CirJsonNode): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(treeAsTokens(node, context), false)) as T?
    }

    /**
     * Method that binds content read using given input source, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param input DataInput that contains content to read
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(input: DataInput): T? {
        val context = deserializationContext()
        return bindAndClose(context, considerFilter(myParserFactory.createParser(context, input), false)) as T?
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods; CirJsonNode ("tree")
     *******************************************************************************************************************
     */

    /**
     * Method that reads content from given input source, using configuration of this reader, and binds it as CirJSON
     * Tree. Returns [CirJsonNode] that represents the root of the resulting tree, if there was content to read, or
     * "missing node" (instance of [CirJsonNode] for which [CirJsonNode.isMissingNode] returns true, and behaves
     * otherwise similar to "`null` node") if no more content is accessible through passed-in input source.
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and [readTree] --
     * latter returns `null` for "no content" case.
     * 
     * Note that if an object was specified with a call to [withValueToUpdate] it will just be ignored; result is always
     * a newly constructed [CirJsonNode] instance.
     */
    @Throws(CirJacksonException::class)
    open fun readTree(source: InputStream): CirJsonNode? {
        val context = deserializationContext()
        return bindAndCloseAsTree(context, considerFilter(myParserFactory.createParser(context, source), false))
    }

    /**
     * Method that reads content from given input source, using configuration of this reader, and binds it as CirJSON
     * Tree. Returns [CirJsonNode] that represents the root of the resulting tree, if there was content to read, or
     * "missing node" (instance of [CirJsonNode] for which [CirJsonNode.isMissingNode] returns true, and behaves
     * otherwise similar to "`null` node") if no more content is accessible through passed-in input source.
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and [readTree] --
     * latter returns `null` for "no content" case.
     * 
     * Note that if an object was specified with a call to [withValueToUpdate] it will just be ignored; result is always
     * a newly constructed [CirJsonNode] instance.
     */
    @Throws(CirJacksonException::class)
    open fun readTree(source: Reader): CirJsonNode? {
        val context = deserializationContext()
        return bindAndCloseAsTree(context, considerFilter(myParserFactory.createParser(context, source), false))
    }

    /**
     * Method that reads content from given input source, using configuration of this reader, and binds it as CirJSON
     * Tree. Returns [CirJsonNode] that represents the root of the resulting tree, if there was content to read, or
     * "missing node" (instance of [CirJsonNode] for which [CirJsonNode.isMissingNode] returns true, and behaves
     * otherwise similar to "`null` node") if no more content is accessible through passed-in input source.
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and [readTree] --
     * latter returns `null` for "no content" case.
     * 
     * Note that if an object was specified with a call to [withValueToUpdate] it will just be ignored; result is always
     * a newly constructed [CirJsonNode] instance.
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: String): CirJsonNode? {
        val context = deserializationContext()
        return bindAndCloseAsTree(context, considerFilter(myParserFactory.createParser(context, content), false))
    }

    /**
     * Method that reads content from given input source, using configuration of this reader, and binds it as CirJSON
     * Tree. Returns [CirJsonNode] that represents the root of the resulting tree, if there was content to read, or
     * "missing node" (instance of [CirJsonNode] for which [CirJsonNode.isMissingNode] returns true, and behaves
     * otherwise similar to "`null` node") if no more content is accessible through passed-in input source.
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and [readTree] --
     * latter returns `null` for "no content" case.
     * 
     * Note that if an object was specified with a call to [withValueToUpdate] it will just be ignored; result is always
     * a newly constructed [CirJsonNode] instance.
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: ByteArray): CirJsonNode? {
        val context = deserializationContext()
        return bindAndCloseAsTree(context, considerFilter(myParserFactory.createParser(context, content), false))
    }

    /**
     * Method that reads content from given input source, using configuration of this reader, and binds it as CirJSON
     * Tree. Returns [CirJsonNode] that represents the root of the resulting tree, if there was content to read, or
     * "missing node" (instance of [CirJsonNode] for which [CirJsonNode.isMissingNode] returns true, and behaves
     * otherwise similar to "`null` node") if no more content is accessible through passed-in input source.
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and [readTree] --
     * latter returns `null` for "no content" case.
     * 
     * Note that if an object was specified with a call to [withValueToUpdate] it will just be ignored; result is always
     * a newly constructed [CirJsonNode] instance.
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: ByteArray, offset: Int, length: Int): CirJsonNode? {
        val context = deserializationContext()
        return bindAndCloseAsTree(context,
                considerFilter(myParserFactory.createParser(context, content, offset, length), false))
    }

    /**
     * Method that reads content from given input source, using configuration of this reader, and binds it as CirJSON
     * Tree. Returns [CirJsonNode] that represents the root of the resulting tree, if there was content to read, or
     * "missing node" (instance of [CirJsonNode] for which [CirJsonNode.isMissingNode] returns true, and behaves
     * otherwise similar to "`null` node") if no more content is accessible through passed-in input source.
     * 
     * NOTE! Behavior with end-of-input (no more content) differs between this `readTree` method, and [readTree] --
     * latter returns `null` for "no content" case.
     * 
     * Note that if an object was specified with a call to [withValueToUpdate] it will just be ignored; result is always
     * a newly constructed [CirJsonNode] instance.
     */
    @Throws(CirJacksonException::class)
    open fun readTree(content: DataInput): CirJsonNode? {
        val context = deserializationContext()
        return bindAndCloseAsTree(context, considerFilter(myParserFactory.createParser(context, content), false))
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods; reading sequence of values
     *******************************************************************************************************************
     */

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either root-level "unwrapped" sequence (without surrounding CirJSON array), or a sequence
     * contained in a CirJSON Array. In either case [CirJsonParser] must point to the first token of the first element,
     * OR not point to any token (in which case it is advanced to the next token). This means, specifically, that for
     * wrapped sequences, parser MUST NOT point to the surrounding `START_ARRAY` but rather to the token following it.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(parser: CirJsonParser): MappingIterator<T> {
        val context = deserializationContext(parser)
        return newIterator(parser, context, findRootDeserializer(context), false)
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param source InputStream that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(source: InputStream): MappingIterator<T> {
        val context = deserializationContext()
        return bindAndReadValues(context, considerFilter(myParserFactory.createParser(context, source), true))
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param source Reader that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(source: Reader): MappingIterator<T> {
        val context = deserializationContext()
        val parser = considerFilter(myParserFactory.createParser(context, source), true)
        initForMultiRead(context, parser)
        parser.nextToken()
        parser.nextToken()
        return newIterator(parser, context, findRootDeserializer(context), true)
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param content String that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(content: String): MappingIterator<T> {
        val context = deserializationContext()
        val parser = considerFilter(myParserFactory.createParser(context, content), true)
        initForMultiRead(context, parser)
        parser.nextToken()
        parser.nextToken()
        return newIterator(parser, context, findRootDeserializer(context), true)
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param content ByteArray that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(content: ByteArray): MappingIterator<T> {
        return readValues(content, 0, content.size)
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param content ByteArray that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(content: ByteArray, offset: Int, length: Int): MappingIterator<T> {
        val context = deserializationContext()
        return bindAndReadValues(context, considerFilter(myParserFactory.createParser(context, content), true))
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param source File that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(source: File): MappingIterator<T> {
        val context = deserializationContext()
        return bindAndReadValues(context, considerFilter(myParserFactory.createParser(context, source), true))
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param source Path that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(source: Path): MappingIterator<T> {
        val context = deserializationContext()
        return bindAndReadValues(context, considerFilter(myParserFactory.createParser(context, source), true))
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     * 
     * NOTE: handling of [URL] is delegated to [TokenStreamFactory.createParser] and usually simply calls
     * [URL.openStream], meaning no special handling is done. If different HTTP connection options are needed you will
     * need to create [InputStream] separately.
     *
     * @param source URL to read to access CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(source: URL): MappingIterator<T> {
        val context = deserializationContext()
        return bindAndReadValues(context, considerFilter(myParserFactory.createParser(context, source), true))
    }

    /**
     * Method for reading sequence of Objects from parser stream.
     * 
     * Sequence can be either wrapped or unwrapped root-level sequence: wrapped means that the elements are enclosed in
     * CirJSON Array; and unwrapped that elements are directly accessed at main level. Assumption is that iff the first
     * token of the document is `START_ARRAY`, we have a wrapped sequence; otherwise unwrapped. For wrapped sequences,
     * leading `START_ARRAY` and `VALUE_STRING` are skipped, so that for both cases, underlying [CirJsonParser] will
     * point to what is expected to be the first token of the first element.
     * 
     * Note that the wrapped vs unwrapped logic means that it is NOT possible to use this method for reading an
     * unwrapped sequence of elements written as CirJSON Arrays: to read such sequences, one has to use [readValues]
     * using a [CirJsonParser], making sure parser points to the first token of the first element (i.e. the second
     * `START_ARRAY` which is part of the first element).
     *
     * @param source DataInput that contains CirJSON content to parse
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValues(source: DataInput): MappingIterator<T> {
        val context = deserializationContext()
        return bindAndReadValues(context, considerFilter(myParserFactory.createParser(context, source), true))
    }

    /*
     *******************************************************************************************************************
     * Implementation of rest of ObjectCodec methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun <T : Any> treeToValue(node: TreeNode, valueType: KClass<T>): T? {
        return forType(valueType).readValue(treeAsTokens(node))
    }

    /**
     * Same as [treeToValue] but with type-resolved target value type.
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> treeToValue(node: TreeNode, valueType: KotlinType): T? {
        return forType(valueType).readValue(treeAsTokens(node))
    }

    /*
     *******************************************************************************************************************
     * Helper methods, databinding
     *******************************************************************************************************************
     */

    /**
     * Actual implementation of value reading+binding operation.
     */
    @Throws(CirJacksonException::class)
    protected open fun bind(context: DeserializationContextExtended, parser: CirJsonParser, valueToUpdate: Any?): Any? {
        val token = initForReading(context, parser)

        val result = when (token) {
            CirJsonToken.VALUE_NULL -> valueToUpdate ?: findRootDeserializer(context).getNullValue(context)
            CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> valueToUpdate
            else -> context.readRootValue(parser, myValueType!!, findRootDeserializer(context), myValueToUpdate)
        }

        parser.clearCurrentToken()

        if (myConfig.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
            verifyNoTrailingTokens(parser, context, myValueType)
        }

        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun bindAndClose(context: DeserializationContextExtended, parser: CirJsonParser): Any? {
        parser.use {
            val token = initForReading(context, it)

            val result = when (token) {
                CirJsonToken.VALUE_NULL -> myValueToUpdate ?: findRootDeserializer(context).getNullValue(context)
                CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> myValueToUpdate
                else -> context.readRootValue(parser, myValueType!!, findRootDeserializer(context), myValueToUpdate)
            }

            if (myConfig.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
                verifyNoTrailingTokens(parser, context, myValueType)
            }

            return result
        }
    }

    @Throws(CirJacksonException::class)
    protected fun bindAndCloseAsTree(context: DeserializationContextExtended, parser: CirJsonParser): CirJsonNode? {
        context.assignAndReturnParser(parser).use {
            return bindAsTree(context, it)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun bindAsTree(context: DeserializationContextExtended, parser: CirJsonParser): CirJsonNode {
        if (myValueToUpdate != null) {
            return bind(context, parser, myValueToUpdate) as CirJsonNode
        }

        val token = parser.currentToken() ?: parser.nextToken() ?: return myConfig.nodeFactory.missingNode()

        val resultNode = if (token == CirJsonToken.VALUE_NULL) {
            context.nodeFactory.nullNode()
        } else {
            context.readRootValue(parser, CIRJSON_NODE_TYPE, findTreeDeserializer(context), null) as CirJsonNode
        }

        parser.clearCurrentToken()

        if (myConfig.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
            verifyNoTrailingTokens(parser, context, CIRJSON_NODE_TYPE)
        }

        return resultNode
    }

    /**
     * Same as [bindAsTree] except end-of-input is reported by returning `null`, not "missing node"
     */
    @Throws(CirJacksonException::class)
    protected fun bindAsTreeOrNull(context: DeserializationContextExtended, parser: CirJsonParser): CirJsonNode? {
        if (myValueToUpdate != null) {
            return bind(context, parser, myValueToUpdate) as CirJsonNode?
        }

        context.assignParser(parser)
        val token = parser.currentToken() ?: parser.nextToken() ?: return null

        val resultNode = if (token == CirJsonToken.VALUE_NULL) {
            context.nodeFactory.nullNode()
        } else {
            context.readRootValue(parser, CIRJSON_NODE_TYPE, findTreeDeserializer(context), null) as CirJsonNode?
        }

        parser.clearCurrentToken()

        if (myConfig.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)) {
            verifyNoTrailingTokens(parser, context, CIRJSON_NODE_TYPE)
        }

        return resultNode
    }

    @Throws(CirJacksonException::class)
    protected open fun <T : Any> bindAndReadValues(context: DeserializationContextExtended,
            parser: CirJsonParser): MappingIterator<T> {
        initForMultiRead(context, parser)
        parser.nextToken()
        return newIterator(parser, context, findRootDeserializer(context), true)
    }

    /**
     * Consider filter when creating JsonParser.
     */
    protected open fun considerFilter(parser: CirJsonParser, multiValue: Boolean): CirJsonParser {
        myFilter ?: return parser
        return parser as? FilteringParserDelegate ?: FilteringParserDelegate(parser, myFilter,
                TokenFilter.Inclusion.ONLY_INCLUDE_ALL, multiValue)
    }

    @Throws(CirJacksonException::class)
    protected fun verifyNoTrailingTokens(parser: CirJsonParser, context: DeserializationContext,
            bindType: KotlinType?) {
        val token = parser.nextToken() ?: return
        val type = bindType?.rawClass ?: myValueToUpdate?.let { it::class } ?: Any::class
        return context.reportTrailingTokens(type, parser, token)
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other
     *******************************************************************************************************************
     */

    protected open fun verifySchemaType(schema: FormatSchema?) {
        schema ?: return

        if (!myParserFactory.canUseSchema(schema)) {
            throw IllegalArgumentException(
                    "Cannot use FormatSchema of type ${schema::class.qualifiedName} for format ${myParserFactory.formatName}")
        }
    }

    /**
     * Internal helper method called to create an instance of [DeserializationContext] for deserializing a single root
     * value. Can be overridden if a custom context is needed.
     */
    protected open fun deserializationContext(): DeserializationContextExtended {
        return myContexts.createContext(myConfig, mySchema, myInjectableValues)
    }

    protected open fun deserializationContext(parser: CirJsonParser): DeserializationContextExtended {
        return myContexts.createContext(myConfig, mySchema, myInjectableValues).assignParser(parser)
    }

    @Throws(CirJacksonException::class)
    protected open fun inputStream(source: URL): InputStream {
        try {
            return source.openStream()
        } catch (e: IOException) {
            throw CirJacksonIOException.construct(e)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun inputStream(source: File): InputStream {
        try {
            return FileInputStream(source)
        } catch (e: IOException) {
            throw CirJacksonIOException.construct(e)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun inputStream(source: Path): InputStream {
        try {
            return Files.newInputStream(source)
        } catch (e: IOException) {
            throw CirJacksonIOException.construct(e)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods, locating deserializers etc
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun findRootDeserializer(context: DeserializationContext): ValueDeserializer<Any> {
        if (myRootDeserializer != null) {
            return myRootDeserializer
        }

        val type = myValueType ?: return context.reportBadDefinition(Any::class,
                "No value type configured for ObjectReader")
        return myRootDeserializers[type] ?: context.findRootValueDeserializer(type)
                .also { myRootDeserializers[type] = it }
    }

    @Throws(CirJacksonException::class)
    protected open fun findTreeDeserializer(context: DeserializationContext): ValueDeserializer<Any> {
        val type = CIRJSON_NODE_TYPE
        return myRootDeserializers[type] ?: context.findRootValueDeserializer(type)
                .also { myRootDeserializers[type] = it }
    }

    /**
     * Method called to locate deserializer ahead of time, if permitted by configuration. Method also is NOT to throw an
     * exception if access fails.
     */
    protected open fun prefetchRootDeserializer(valueType: KotlinType?): ValueDeserializer<Any>? {
        if (valueType == null || !myConfig.isEnabled(DeserializationFeature.EAGER_DESERIALIZER_FETCH)) {
            return null
        }

        val deserializer = myRootDeserializers[valueType]

        if (deserializer != null) {
            return deserializer
        }

        return try {
            val context = deserializationContext()
            context.findRootValueDeserializer(valueType).also { myRootDeserializers[valueType] = it }
        } catch (_: CirJacksonException) {
            null
        }
    }

    companion object {

        val CIRJSON_NODE_TYPE: KotlinType = SimpleType.constructUnsafe(CirJsonNode::class)

        internal fun construct(mapper: ObjectMapper, config: DeserializationConfig): ObjectReader {
            return ObjectReader(mapper, config)
        }

        internal fun construct(mapper: ObjectMapper, config: DeserializationConfig, valueType: KotlinType?,
                valueToUpdate: Any?, schema: FormatSchema?, injectableValues: InjectableValues?): ObjectReader {
            return ObjectReader(mapper, config, valueType, valueToUpdate, schema, injectableValues)
        }

    }

}