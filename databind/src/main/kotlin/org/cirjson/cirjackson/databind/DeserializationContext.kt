package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.deserialization.DeserializerCache
import org.cirjson.cirjackson.databind.deserialization.DeserializerFactory
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.ArrayBuilders
import org.cirjson.cirjackson.databind.util.LinkedNode
import org.cirjson.cirjackson.databind.util.ObjectBuffer
import org.cirjson.cirjackson.databind.util.TokenBuffer
import java.text.DateFormat
import kotlin.reflect.KClass

/**
 * Context for the process of deserialization a single root-level value. Used to allow passing in configuration settings
 * and reusable temporary objects (scrap arrays, containers). Constructed by [ObjectMapper] (and [ObjectReader] based on
 * configuration, used mostly by [ValueDeserializers][ValueDeserializer] to access contextual information.
 * 
 * @property myStreamFactory Low-level [TokenStreamFactory] that may be used for constructing embedded parsers.
 * 
 * @property myFactory Read-only factory instance; exposed to let owners (`ObjectMapper`, `ObjectReader`) access it.
 * 
 * @property myCache Object that handle details of [ValueDeserializer] caching.
 * 
 * @property myConfig Generic deserialization processing configuration
 * 
 * @property mySchema Schema for underlying parser to use, if any.
 * 
 * @property myInjectableValues Object used for resolving references to injectable values.
 */
abstract class DeserializationContext protected constructor(protected val myStreamFactory: TokenStreamFactory,
        protected val myFactory: DeserializerFactory, protected val myCache: DeserializerCache,
        protected val myConfig: DeserializationConfig, protected val mySchema: FormatSchema?,
        protected val myInjectableValues: InjectableValues?) : DatabindContext(), ObjectReadContext {

    /*
     *******************************************************************************************************************
     * Configuration that may vary by ObjectReader
     *******************************************************************************************************************
     */

    /**
     * Bitmap of [DeserializationFeatures][DeserializationFeature] that are enabled
     */
    protected val myFeatureFlags = myConfig.deserializationFeatures

    /**
     * Currently active view, if any.
     */
    protected val myActiveView = config.activeView

    /*
     *******************************************************************************************************************
     * Other State
     *******************************************************************************************************************
     */

    /**
     * Currently active parser used for deserialization. May be different from the outermost parser when content is
     * buffered.
     */
    protected var myParser: CirJsonParser? = null

    /**
     * Capabilities of the input format.
     */
    protected var myReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>? = null

    /*
     *******************************************************************************************************************
     * Per-operation reusable helper objects (not for blueprints)
     *******************************************************************************************************************
     */

    protected var myArrayBuilders: ArrayBuilders? = null

    protected var myObjectBuffer: ObjectBuffer? = null

    protected var myDateFormat: DateFormat? = null

    /**
     * Lazily-constructed holder for per-call attributes.
     */
    protected var myAttributes = myConfig.attributes

    /**
     * Type of [ValueDeserializer] on which [ValueDeserializer.createContextual] is being called currently.
     */
    protected var myCurrentType: LinkedNode<KotlinType>? = null

    /**
     * Lazily constructed [ClassIntrospector] instance: created from "blueprint"
     */
    protected var myClassIntrospector: ClassIntrospector? = null

    /*
     *******************************************************************************************************************
     * DatabindContext implementation
     *******************************************************************************************************************
     */

    override val config: DeserializationConfig
        get() = myConfig

    override val annotationIntrospector: AnnotationIntrospector?
        get() = TODO("Not yet implemented")

    override val typeFactory: TypeFactory
        get() = TODO("Not yet implemented")

    override fun isEnabled(feature: MapperFeature): Boolean {
        TODO("Not yet implemented")
    }

    override fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, config access
     *******************************************************************************************************************
     */

    override val tokenStreamFactory: TokenStreamFactory
        get() = TODO("Not yet implemented")

    override val schema: FormatSchema?
        get() = TODO("Not yet implemented")

    override val streamReadConstraints: StreamReadConstraints
        get() = TODO("Not yet implemented")

    override fun getStreamReadFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getFormatReadFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, Tree creation
     *******************************************************************************************************************
     */

    override fun createArrayNode(): ArrayTreeNode {
        TODO("Not yet implemented")
    }

    override fun createObjectNode(): ObjectTreeNode {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, databind
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValue(parser: CirJsonParser, clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValue(parser: CirJsonParser, typeReference: TypeReference<T>): T {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValue(parser: CirJsonParser, resolvedType: ResolvedType): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, config feature accessors
     *******************************************************************************************************************
     */

    fun isEnabled(feature: DeserializationFeature): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, accessor for helper objects
     *******************************************************************************************************************
     */

    val parser: CirJsonParser?
        get() = TODO("Not implemented")

    /*
     *******************************************************************************************************************
     * Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    override fun introspectBeanDescription(type: KotlinType): BeanDescription {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Miscellaneous config access
     *******************************************************************************************************************
     */

    open fun hasExplicitDeserializerFor(valueType: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Factory methods for getting appropriate TokenBuffer instances (possibly overridden by backends for alternate data
     * formats)
     *******************************************************************************************************************
     */

    open fun bufferForInputBuffering(parser: CirJsonParser): TokenBuffer {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, value deserializer access
     *******************************************************************************************************************
     */

    fun findContextualValueDeserializer(type: KotlinType, property: BeanProperty?): ValueDeserializer<Any>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, type handling
     *******************************************************************************************************************
     */

    override fun constructType(type: KClass<*>?): KotlinType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API: handler instantiation
     *******************************************************************************************************************
     */

    abstract fun deserializerInstance(annotated: Annotated, deserializerDefinition: Any?): ValueDeserializer<Any>?

    abstract fun keyDeserializerInstance(annotated: Annotated, deserializerDefinition: Any?): KeyDeserializer?

    /*
     *******************************************************************************************************************
     * Methods for problem handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun handleUnknownTypeId(baseType: KotlinType, id: String, idResolver: TypeIdResolver,
            extraDescription: String): KotlinType? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    open fun handleMissingTypeId(baseType: KotlinType, idResolver: TypeIdResolver,
            extraDescription: String): KotlinType? {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    open fun handleBadMerge(deserializer: ValueDeserializer<*>) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for problem reporting, in cases where recovery is not considered possible: input problem
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    open fun <T> reportWrongTokenException(targetType: KotlinType, expectedToken: CirJsonToken, message: String): T {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    open fun <T> reportWrongTokenException(targetType: KClass<*>, expectedToken: CirJsonToken, message: String): T {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    open fun <T> reportInputMismatch(targetType: KotlinType?, message: String): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for problem reporting, in cases where recovery is not considered possible: POJO definition problem
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    override fun <T> reportBadTypeDefinition(bean: BeanDescription, message: String): T {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    override fun <T> reportBadDefinition(type: KotlinType, message: String): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for constructing semantic exceptions; usually not to be called directly, call `handleXxx()` instead
     *******************************************************************************************************************
     */

    override fun invalidTypeIdException(baseType: KotlinType, typeId: String,
            extraDescription: String): DatabindException {
        TODO("Not yet implemented")
    }

}