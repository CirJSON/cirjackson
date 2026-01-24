package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.filter.TokenFilter
import org.cirjson.cirjackson.databind.configuration.DeserializationContexts
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.deserialization.DeserializationContextExtended
import org.cirjson.cirjackson.databind.node.ArrayNode
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.type.SimpleType
import java.util.concurrent.ConcurrentHashMap

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
     * TreeCodec implementation
     *******************************************************************************************************************
     */

    override fun createObjectNode(): ObjectNode {
        TODO("Not yet implemented")
    }

    override fun createArrayNode(): ArrayNode {
        TODO("Not yet implemented")
    }

    override fun booleanNode(boolean: Boolean): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun stringNode(text: String?): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun missingNode(): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun nullNode(): CirJsonNode {
        TODO("Not yet implemented")
    }

    override fun treeAsTokens(node: TreeNode): CirJsonParser {
        TODO("Not yet implemented")
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
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        TODO("Not yet implemented")
    }

    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods; others similar to what ObjectMapper has
     *******************************************************************************************************************
     */

    /**
     * Method that binds content read using given ByteArray, using configuration of this reader, including expected
     * result type. Value return is either newly constructed, or root value that was specified with [withValueToUpdate].
     *
     * @param content Byte array that contains encoded content to read
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readValue(content: ByteArray): T? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other
     *******************************************************************************************************************
     */

    /**
     * Internal helper method called to create an instance of [DeserializationContext] for deserializing a single root
     * value. Can be overridden if a custom context is needed.
     */
    protected open fun deserializationContext(): DeserializationContextExtended {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods, locating deserializers etc
     *******************************************************************************************************************
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
        } catch (e: CirJacksonException) {
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