package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.databind.configuration.ContextAttributes
import org.cirjson.cirjackson.databind.configuration.GeneratorSettings
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.serialization.implementation.ReadOnlyClassToSerializerMap
import org.cirjson.cirjackson.databind.serialization.implementation.UnknownSerializer
import org.cirjson.cirjackson.databind.serialization.standard.NullSerializer
import org.cirjson.cirjackson.databind.type.TypeFactory
import java.text.DateFormat
import kotlin.reflect.KClass

/**
 * Class that defines API used by [ObjectMapper] and [ValueSerializers][ValueSerializer] to obtain serializers capable
 * of serializing instances of specific types; as well as the default implementation of the functionality.
 * 
 * Provider handles caching aspects of serializer handling; all construction details are delegated to
 * [SerializerFactory] instance.
 */
abstract class SerializerProvider : DatabindContext, ObjectWriteContext {

    /*
     *******************************************************************************************************************
     * Configuration, general
     *******************************************************************************************************************
     */

    /**
     * Serialization configuration to use for serialization processing.
     */
    private val myConfig: SerializationConfig

    /**
     * Configuration to be used by streaming generator when it is constructed.
     */
    protected val myGeneratorConfig: GeneratorSettings

    /**
     * Low-level [TokenStreamFactory] that may be used for constructing embedded generators.
     */
    protected val myStreamFactory: TokenStreamFactory

    /**
     * Token stream generator actively used; only set for per-call instances
     */
    @Transient
    protected var myGenerator: CirJsonGenerator? = null

    /**
     * Capabilities of the output format.
     */
    protected var myWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>? = null

    /**
     * View used for currently active serialization, if any.
     */
    protected val myActiveView: KClass<*>?

    /*
     *******************************************************************************************************************
     * Configuration, serializer access
     *******************************************************************************************************************
     */

    /**
     * Factory used for constructing actual serializer instances. Only set for non-blueprint instances.
     */
    protected val mySerializerFactory: SerializerFactory

    /**
     * Serializer used to output a `null` value. Default implementation writes `null` using
     * [CirJsonGenerator.writeNull].
     */
    protected val myNullValueSerializer: ValueSerializer<Any>

    /**
     * Flag set to indicate that we are using vanilla `null` value serialization
     */
    protected val myStandardNullValueSerializer: Boolean

    /*
     *******************************************************************************************************************
     * Helper objects for caching, reuse
     *******************************************************************************************************************
     */

    /**
     * Cache for doing type-to-value-serializer lookups.
     */
    protected val mySerializerCache: SerializerCache

    /**
     * For fast lookups, we will have a local non-shared read-only map that contains serializers previously fetched.
     */
    protected val myKnownSerializers: ReadOnlyClassToSerializerMap

    /**
     * Lazily acquired and instantiated formatter object: initialized first time it is needed, reused afterward. Used
     * via instances (not blueprints), so that access need not be thread-safe.
     */
    protected val myDateFormat: DateFormat? = null

    /**
     * Lazily constructed [ClassIntrospector] instance: created from "blueprint"
     */
    @Transient
    protected var myClassIntrospector: ClassIntrospector? = null

    /*
     *******************************************************************************************************************
     * Other state
     *******************************************************************************************************************
     */

    /**
     * Lazily-constructed holder for per-call attributes. Only set for non-blueprint instances.
     */
    protected var myAttributes: ContextAttributes

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(streamFactory: TokenStreamFactory, config: SerializationConfig,
            generatorConfig: GeneratorSettings, factory: SerializerFactory,
            serializerCache: SerializerCache) : super() {
        myStreamFactory = streamFactory
        mySerializerFactory = factory
        myConfig = config
        myGeneratorConfig = generatorConfig

        mySerializerCache = serializerCache

        val defaultNullValueSerializer = factory.defaultNullValueSerializer

        myNullValueSerializer = if (defaultNullValueSerializer == null) {
            myStandardNullValueSerializer = true
            NullSerializer
        } else {
            myStandardNullValueSerializer = false
            defaultNullValueSerializer
        }

        myActiveView = config.activeView
        myAttributes = config.attributes

        myKnownSerializers = serializerCache.readOnlyLookupMap
    }

    protected constructor(source: SerializerProvider, serializerCache: SerializerCache) : super() {
        myStreamFactory = source.myStreamFactory
        mySerializerFactory = source.mySerializerFactory
        myConfig = source.myConfig
        myGeneratorConfig = source.myGeneratorConfig

        mySerializerCache = serializerCache

        myStandardNullValueSerializer = source.myStandardNullValueSerializer
        myNullValueSerializer = source.myNullValueSerializer
        myActiveView = source.myActiveView
        myAttributes = source.myAttributes

        myKnownSerializers = source.myKnownSerializers
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext implementation, config access
     *******************************************************************************************************************
     */

    override fun tokenStreamFactory(): TokenStreamFactory {
        TODO("Not yet implemented")
    }

    override val schema: FormatSchema?
        get() = TODO("Not yet implemented")

    override val characterEscapes: CharacterEscapes?
        get() = TODO("Not yet implemented")

    override val prettyPrinter: PrettyPrinter?
        get() = TODO("Not yet implemented")

    override fun hasPrettyPrinter(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getRootValueSeparator(defaultSeparator: SerializableString?): SerializableString? {
        TODO("Not yet implemented")
    }

    override fun getStreamWriteFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getFormatWriteFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext implementation, databind integration
     *******************************************************************************************************************
     */

    override fun createArrayNode(): ArrayTreeNode {
        TODO("Not yet implemented")
    }

    override fun createObjectNode(): ObjectTreeNode {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeValue(generator: CirJsonGenerator, value: Any?) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(generator: CirJsonGenerator, value: TreeNode) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DatabindContext implementation (and closely related but serialization-specific)
     *******************************************************************************************************************
     */

    final override val config: SerializationConfig
        get() = TODO("Not yet implemented")

    final override val annotationIntrospector: AnnotationIntrospector?
        get() = TODO("Not yet implemented")

    override val typeFactory: TypeFactory
        get() = TODO("Not yet implemented")

    override fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        TODO("Not yet implemented")
    }

    override fun isEnabled(feature: MapperFeature): Boolean {
        TODO("Not yet implemented")
    }

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
     * Access to other on/off features
     *******************************************************************************************************************
     */

    fun isEnabled(feature: SerializationFeature): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Access to other helper objects
     *******************************************************************************************************************
     */

    open val generator: CirJsonGenerator
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Serializer discovery: root/non-property value serializers
     *******************************************************************************************************************
     */

    fun findTypedValueSerializer(rawType: KClass<*>, cache: Boolean): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    fun findTypedValueSerializer(valueType: KotlinType, cache: Boolean): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for serializing using default methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun defaultSerializeNullValue(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Error reporting
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    override fun <T> reportBadTypeDefinition(bean: BeanDescription, message: String?): T {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    override fun <T> reportBadDefinition(type: KotlinType, message: String?): T {
        TODO("Not yet implemented")
    }

    override fun invalidTypeIdException(baseType: KotlinType, typeId: String?,
            extraDescription: String): DatabindException {
        TODO("Not yet implemented")
    }

    companion object {

        /**
         * Placeholder serializer used when `Any` typed property is marked to be serialized.
         * 
         * NOTE: this instance is NOT used for any other types, and separate instances are constructed for "empty"
         * Beans.
         */
        val DEFAULT_UNKNOWN_SERIALIZER = UnknownSerializer()

    }

}