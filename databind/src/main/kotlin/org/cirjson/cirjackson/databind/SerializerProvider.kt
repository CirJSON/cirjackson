package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.ContextAttributes
import org.cirjson.cirjackson.databind.configuration.DatatypeFeature
import org.cirjson.cirjackson.databind.configuration.DatatypeFeatures
import org.cirjson.cirjackson.databind.configuration.GeneratorSettings
import org.cirjson.cirjackson.databind.exception.InvalidDefinitionException
import org.cirjson.cirjackson.databind.exception.InvalidTypeIdException
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.serialization.WritableObjectId
import org.cirjson.cirjackson.databind.serialization.implementation.ReadOnlyClassToSerializerMap
import org.cirjson.cirjackson.databind.serialization.implementation.TypeWrappedSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.UnknownSerializer
import org.cirjson.cirjackson.databind.serialization.standard.NullSerializer
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.*
import java.text.DateFormat
import java.util.*
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
    protected var myDateFormat: DateFormat? = null

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
        return myStreamFactory
    }

    override val schema: FormatSchema?
        get() = myGeneratorConfig.mySchema

    override val characterEscapes: CharacterEscapes?
        get() = myGeneratorConfig.myCharacterEscapes

    override val prettyPrinter: PrettyPrinter?
        get() {
            var prettyPrinter = myGeneratorConfig.myPrettyPrinter

            if (prettyPrinter == null) {
                if (isEnabled(SerializationFeature.INDENT_OUTPUT)) {
                    prettyPrinter = myConfig.constructDefaultPrettyPrinter()
                }
            }

            return prettyPrinter
        }

    override fun hasPrettyPrinter(): Boolean {
        return myGeneratorConfig.hasPrettyPrinter() || isEnabled(SerializationFeature.INDENT_OUTPUT)
    }

    override fun getRootValueSeparator(defaultSeparator: SerializableString?): SerializableString? {
        return myGeneratorConfig.getRootValueSeparator(defaultSeparator)
    }

    override fun getStreamWriteFeatures(defaults: Int): Int {
        return myConfig.streamWriteFeatures
    }

    override fun getFormatWriteFeatures(defaults: Int): Int {
        return myConfig.formatWriteFeatures
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext implementation, databind integration
     *******************************************************************************************************************
     */

    override fun createArrayNode(): ArrayTreeNode {
        return myConfig.nodeFactory.arrayNode()
    }

    override fun createObjectNode(): ObjectTreeNode {
        return myConfig.nodeFactory.objectNode()
    }

    @Throws(CirJacksonException::class)
    override fun writeValue(generator: CirJsonGenerator, value: Any?) {
        val previousGenerator = myGenerator
        myGenerator = generator

        try {
            if (value == null) {
                if (myStandardNullValueSerializer) {
                    generator.writeNull()
                } else {
                    myNullValueSerializer.serializeNullable(null, generator, this)
                }

                return
            }

            val clazz = value::class
            findTypedValueSerializer(clazz, true).serialize(value, generator, this)
        } finally {
            myGenerator = previousGenerator
        }
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(generator: CirJsonGenerator, value: TreeNode?) {
        writeValue(generator, value)
    }

    /*
     *******************************************************************************************************************
     * DatabindContext implementation (and closely related but serialization-specific)
     *******************************************************************************************************************
     */

    final override val config: SerializationConfig
        get() = myConfig

    final override val annotationIntrospector: AnnotationIntrospector?
        get() = myConfig.annotationIntrospector

    final override val typeFactory: TypeFactory
        get() = myConfig.typeFactory

    @Throws(IllegalArgumentException::class)
    override fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        if (baseType.hasRawClass(subclass)) {
            return baseType
        }

        return config.typeFactory.constructSpecializedType(baseType, subclass, true)
    }

    final override val activeView: KClass<*>?
        get() = myActiveView

    final override fun canOverrideAccessModifiers(): Boolean {
        return myConfig.canOverrideAccessModifiers()
    }

    final override fun isEnabled(feature: MapperFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    final override fun isEnabled(feature: DatatypeFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    final override val datatypeFeatures: DatatypeFeatures
        get() = myConfig.datatypeFeatures

    final override fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value {
        return myConfig.getDefaultPropertyFormat(baseType)
    }

    fun getDefaultPropertyInclusion(baseType: KClass<*>): CirJsonInclude.Value? {
        return myConfig.getDefaultPropertyInclusion(baseType)
    }

    /**
     * Accessor for accessing default Locale to use: convenience accessor for
     * ```
     * config.locale
     * ```
     */
    override val locale: Locale
        get() = myConfig.locale

    /**
     * Accessor for accessing default TimeZone to use: convenience accessor for
     * ```
     * config.timeZone
     * ```
     */
    override val timeZone: TimeZone
        get() = myConfig.timeZone

    /*
     *******************************************************************************************************************
     * Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    override fun classIntrospector(): ClassIntrospector {
        return myClassIntrospector ?: myConfig.classIntrospectorInstance().also { myClassIntrospector = it }
    }

    override fun introspectBeanDescription(type: KotlinType): BeanDescription {
        return classIntrospector().introspectForSerialization(type)
    }

    /*
     *******************************************************************************************************************
     * Miscellaneous config access
     *******************************************************************************************************************
     */

    override fun findRootName(rootType: KotlinType): PropertyName {
        return myConfig.findRootName(this, rootType)
    }

    override fun findRootName(rawRootType: KClass<*>): PropertyName {
        return myConfig.findRootName(this, rawRootType)
    }

    /*
     *******************************************************************************************************************
     * Generic attributes
     *******************************************************************************************************************
     */

    override fun getAttribute(key: Any): Any? {
        return myAttributes.getAttribute(key)
    }

    override fun setAttribute(key: Any, value: Any?): SerializerProvider {
        myAttributes = myAttributes.withPerCallAttribute(key, value)
        return this
    }

    /*
     *******************************************************************************************************************
     * Access to other on/off features
     *******************************************************************************************************************
     */

    /**
     * Convenience method for checking whether specified serialization feature is enabled or not. Shortcut for:
     * ```
     * config.isEnabled(feature)
     * ```
     */
    fun isEnabled(feature: SerializationFeature): Boolean {
        return myConfig.isEnabled(feature)
    }

    /**
     * "Bulk" access method for checking that all features specified by mask are enabled.
     */
    fun hasSerializationFeatures(featureMask: Int): Boolean {
        return myConfig.hasSerializationFeatures(featureMask)
    }

    /**
     * Method for checking whether input format has specified capability or not.
     *
     * @return `true` if input format has specified capability; `false` if not
     */
    fun isEnabled(capability: StreamWriteCapability): Boolean {
        return myWriteCapabilities!!.isEnabled(capability)
    }

    /*
     *******************************************************************************************************************
     * Access to other helper objects
     *******************************************************************************************************************
     */

    /**
     * Convenience accessor for accessing provider to find serialization filters used, equivalent to calling:
     * ```
     * config.filterProvider
     * ```
     */
    val filterProvider: FilterProvider?
        get() = myConfig.filterProvider

    open val generator: CirJsonGenerator?
        get() = myGenerator

    /*
     *******************************************************************************************************************
     * Factory methods for getting appropriate TokenBuffer instances (possibly overridden by backends for alternate data
     * formats)
     *******************************************************************************************************************
     */

    /**
     * Specialized factory method used when we are converting values and do not typically have or use "real" parsers or
     * generators.
     */
    open fun bufferForValueConversion(): TokenBuffer {
        return TokenBuffer(this, false)
    }

    /*
     *******************************************************************************************************************
     * Access to Object ID aspects
     *******************************************************************************************************************
     */

    /**
     * Method called to find the Object Id for given POJO, if one has been generated. Will always return a non-`null`
     * Object; contents vary depending on whether an Object ID already exists or not.
     */
    abstract fun findObjectId(forPojo: Any, generatorType: ObjectIdGenerator<*>): WritableObjectId

    /*
     *******************************************************************************************************************
     * Serializer discovery: root/non-property value serializers
     *******************************************************************************************************************
     */

    /**
     * Method called to locate regular serializer, matching type serializer, and if both found, wrap them in a
     * serializer that calls both in correct sequence. This method is mostly used for root-level serializer handling to
     * allow for simpler caching. A call can always be replaced by equivalent calls to access serializer and type
     * serializer separately.
     *
     * @param rawType Type for purpose of locating a serializer; usually dynamic runtime type, but can also be static
     * declared type, depending on configuration
     * 
     * @param cache Whether resulting value serializer should be cached or not
     */
    open fun findTypedValueSerializer(rawType: KClass<*>, cache: Boolean): ValueSerializer<Any> {
        var serializer = myKnownSerializers.typedValueSerializer(rawType)

        if (serializer != null) {
            return serializer
        }

        val fullType = myConfig.constructType(rawType)
        serializer = handleRootContextualization(findValueSerializer(rawType))!!
        var typeSerializer = findTypeSerializer(fullType)

        if (typeSerializer != null) {
            typeSerializer = typeSerializer.forProperty(this, null)
            serializer = TypeWrappedSerializer(typeSerializer, serializer)
        }

        if (cache) {
            mySerializerCache.addTypedSerializer(rawType, serializer)
        }

        return serializer
    }

    /**
     * Method called to locate regular serializer, matching type serializer, and if both found, wrap them in a
     * serializer that calls both in correct sequence. This method is mostly used for root-level serializer handling to
     * allow for simpler caching. A call can always be replaced by equivalent calls to access serializer and type
     * serializer separately.
     *
     * @param valueType Declared type of value being serialized (which may not be actual runtime type); used for finding
     * both value serializer and type serializer to use for adding polymorphic type (if any)
     * 
     * @param cache Whether resulting value serializer should be cached or not
     */
    open fun findTypedValueSerializer(valueType: KotlinType, cache: Boolean): ValueSerializer<Any> {
        var serializer = myKnownSerializers.typedValueSerializer(valueType)

        if (serializer != null) {
            return serializer
        }

        serializer = handleRootContextualization(findValueSerializer(valueType))!!
        var typeSerializer = findTypeSerializer(valueType)

        if (typeSerializer != null) {
            typeSerializer = typeSerializer.forProperty(this, null)
            serializer = TypeWrappedSerializer(typeSerializer, serializer)
        }

        if (cache) {
            mySerializerCache.addTypedSerializer(valueType, serializer)
        }

        return serializer
    }

    /**
     * Method for finding (from cache) or creating (and caching) serializer for given type, without checking for
     * polymorphic typing, and then contextualizing without actual property. This is most often used for root-level
     * values (when writing sequences), but may sometimes be used for more esoteric value handling for delegation.
     */
    open fun findRootValueSerializer(rawType: KClass<*>): ValueSerializer<Any> {
        var serializer = myKnownSerializers.untypedValueSerializer(rawType)

        if (serializer == null) {
            val fullType = myConfig.constructType(rawType)
            serializer = mySerializerCache.untypedValueSerializer(fullType) ?: createAndCacheUntypedSerializer(rawType,
                    fullType)
        }

        return handleRootContextualization(serializer)!!
    }

    /**
     * Method for finding (from cache) or creating (and caching) serializer for given type, without checking for
     * polymorphic typing, and then contextualizing without actual property. This is most often used for root-level
     * values (when writing sequences), but may sometimes be used for more esoteric value handling for delegation.
     */
    open fun findRootValueSerializer(valueType: KotlinType): ValueSerializer<Any> {
        val serializer =
                myKnownSerializers.untypedValueSerializer(valueType) ?: createAndCacheUntypedSerializer(valueType)
        return handleRootContextualization(serializer)!!
    }

    /*
     *******************************************************************************************************************
     * Serializer discovery: property value serializers
     *******************************************************************************************************************
     */

    /**
     * Method used for locating "primary" property value serializer (one directly handling value of the property).
     * Difference (if any) has to do with contextual resolution, and method(s) called: this method should only be called
     * when caller is certain that this is the primary property value serializer.
     *
     * @param property Property that is being handled; will never be `null`, and its type has to match `valueType`
     * parameter.
     */
    open fun findPrimaryPropertySerializer(valueType: KotlinType, property: BeanProperty): ValueSerializer<Any> {
        val serializer =
                myKnownSerializers.untypedValueSerializer(valueType) ?: createAndCachePropertySerializer(valueType,
                        property)
        return handlePrimaryContextualization(serializer, property)!!
    }

    /**
     * See [findPrimaryPropertySerializer].
     */
    open fun findPrimaryPropertySerializer(rawType: KClass<*>, property: BeanProperty): ValueSerializer<Any> {
        var serializer = myKnownSerializers.untypedValueSerializer(rawType)

        if (serializer == null) {
            val fullType = myConfig.constructType(rawType)
            serializer = mySerializerCache.untypedValueSerializer(fullType) ?: createAndCachePropertySerializer(rawType,
                    fullType, property)
        }

        return handlePrimaryContextualization(serializer, property)!!
    }

    /**
     * Method similar to [findPrimaryPropertySerializer] but used for "content values", secondary types used by
     * "primary" serializers for structured types like [Arrays][Array], [Collections][Collection], [Maps][Map], and so
     * on.
     * 
     * Serializer will be contextualized, but will not have type serializer wrapped.
     *
     * @param valueType Type of (secondary / content) values being serialized
     * 
     * @param property (optional) Property that refers to values via primary type (so type DOES NOT necessarily match
     * `valueType`)
     */
    open fun findContentValueSerializer(valueType: KotlinType, property: BeanProperty?): ValueSerializer<Any> {
        val serializer =
                myKnownSerializers.untypedValueSerializer(valueType) ?: createAndCachePropertySerializer(valueType,
                        property)
        return handleSecondaryContextualization(serializer, property)!!
    }

    /**
     * See [findContentValueSerializer].
     */
    open fun findContentValueSerializer(rawType: KClass<*>, property: BeanProperty?): ValueSerializer<Any> {
        var serializer = myKnownSerializers.untypedValueSerializer(rawType)

        if (serializer == null) {
            val fullType = myConfig.constructType(rawType)
            serializer = mySerializerCache.untypedValueSerializer(fullType) ?: createAndCachePropertySerializer(rawType,
                    fullType, property)
        }

        return handleSecondaryContextualization(serializer, property)!!
    }

    /*
     *******************************************************************************************************************
     * Serializer discovery: general serializer locating functionality
     *******************************************************************************************************************
     */

    /**
     * Method variant used when we do NOT want contextualization to happen; it will need to be handled at a later point,
     * but caller wants to be able to do that as needed; sometimes to avoid infinite loops
     */
    open fun findValueSerializer(rawType: KClass<*>): ValueSerializer<Any> {
        val serializer = myKnownSerializers.untypedValueSerializer(rawType)

        if (serializer != null) {
            return serializer
        }

        val fullType = myConfig.constructType(rawType)
        return mySerializerCache.untypedValueSerializer(fullType) ?: createAndCacheUntypedSerializer(rawType, fullType)
    }

    /**
     * Method variant used when we do NOT want contextualization to happen; it will need to be handled at a later point,
     * but caller wants to be able to do that as needed; sometimes to avoid infinite loops
     */
    open fun findValueSerializer(valueType: KotlinType): ValueSerializer<Any> {
        return myKnownSerializers.untypedValueSerializer(valueType) ?: createAndCacheUntypedSerializer(valueType)
    }

    /*
     *******************************************************************************************************************
     * Serializer discovery: type serializers
     *******************************************************************************************************************
     */

    /**
     * Method called to get the [TypeSerializer] to use for including Type ID necessary for serializing for the given
     * class. Useful for schema generators.
     */
    open fun findTypeSerializer(baseType: KotlinType): TypeSerializer? {
        return findTypeSerializer(baseType, introspectClassAnnotations(baseType))
    }

    /**
     * Method called to get the [TypeSerializer] to use for including Type ID necessary for serializing for the given
     * class. Useful for schema generators.
     */
    open fun findTypeSerializer(baseType: KotlinType, annotatedClass: AnnotatedClass): TypeSerializer? {
        return myConfig.typeResolverProvider.findTypeSerializer(this, baseType, annotatedClass)
    }

    /**
     * Like [findTypeSerializer], but for use from specific POJO property. Method called to create a type information
     * serializer for values of given non-container property if one is needed. If not needed (no polymorphic handling
     * configured), should return `null`.
     *
     * @param baseType Declared type to use as the base type for type information serializer
     *
     * @return Type serializer to use for property values, if one is needed; `null` if not.
     */
    open fun findPropertyTypeSerializer(baseType: KotlinType, annotatedMember: AnnotatedMember): TypeSerializer? {
        return myConfig.typeResolverProvider.findPropertyTypeSerializer(this, annotatedMember, baseType)
    }

    /*
     *******************************************************************************************************************
     * Serializer discovery: key serializers
     *******************************************************************************************************************
     */

    open fun findKeySerializer(keyType: KotlinType, property: BeanProperty): ValueSerializer<Any> {
        val serializer = mySerializerFactory.createKeySerializer(this, keyType)
        serializer.resolve(this)
        return handleSecondaryContextualization(serializer, property)!!
    }

    open fun findKeySerializer(rawKeyType: KClass<*>, property: BeanProperty): ValueSerializer<Any> {
        return findKeySerializer(myConfig.constructType(rawKeyType), property)
    }

    open val defaultNullValueSerializer: ValueSerializer<Any>
        get() = myNullValueSerializer

    /**
     * Method called to find a serializer to use for `null` values for given declared type. Note that type is completely
     * based on declared type, since `null` in Kotlin have no type and thus runtime type cannot be determined.
     */
    open fun findNullKeySerializer(serializationType: KotlinType, property: BeanProperty): ValueSerializer<Any> {
        return mySerializerFactory.defaultNullValueSerializer
    }

    /*
     *******************************************************************************************************************
     * Serializer discovery: other miscellaneous serializers, null value, unknown
     *******************************************************************************************************************
     */

    /**
     * Method called to get the serializer to use for serializing `null` values for specified property.
     * 
     * Default implementation simply calls [defaultNullValueSerializer]; can be overridden to add custom `null`
     * serialization for properties of certain type or name. This gives method full granularity to basically override
     * `null` handling for any specific property or class of properties.
     */
    open fun findNullValueSerializer(property: BeanProperty): ValueSerializer<Any> {
        return myNullValueSerializer
    }

    /**
     * Method called to get the serializer to use if provider cannot determine an actual type-specific serializer to
     * use; typically when none of [SerializerFactory] instances are able to construct a serializer.
     * 
     * Typically, returned serializer will throw an exception, although alternatively
     * [org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer] could be returned as well.
     *
     * @param unknownType Type for which no serializer is found
     */
    open fun getUnknownTypeSerializer(unknownType: KClass<*>): ValueSerializer<Any> {
        if (unknownType == Any::class) {
            return DEFAULT_UNKNOWN_SERIALIZER
        }

        return UnknownSerializer(unknownType)
    }

    /**
     * Helper method called to see if given serializer is considered to be something returned by
     * [getUnknownTypeSerializer], that is, something for which no regular serializer was found or constructed.
     * 
     * Note: `null` is considered an [UnknownSerializer], and thus returns `true`.
     */
    open fun isUnknownTypeSerializer(serializer: ValueSerializer<*>?): Boolean {
        if (serializer == null || serializer == DEFAULT_UNKNOWN_SERIALIZER) {
            return true
        }

        if (isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)) {
            return serializer is UnknownSerializer
        }

        return false
    }

    /*
     *******************************************************************************************************************
     * Low-level methods for actually constructing and initializing serializers
     *******************************************************************************************************************
     */

    protected open fun createAndCacheUntypedSerializer(rawType: KClass<*>, fullType: KotlinType): ValueSerializer<Any> {
        val beanDescription = introspectBeanDescription(fullType)

        val serializer = try {
            mySerializerFactory.createSerializer(this, fullType, beanDescription, null)
        } catch (e: IllegalArgumentException) {
            return reportBadTypeDefinition(beanDescription, e.exceptionMessage())
        }

        mySerializerCache.addAndResolveNonTypedSerializer(rawType, fullType, serializer, this)
        return serializer
    }

    protected open fun createAndCacheUntypedSerializer(type: KotlinType): ValueSerializer<Any> {
        val beanDescription = introspectBeanDescription(type)

        val serializer = try {
            mySerializerFactory.createSerializer(this, type, beanDescription, null)
        } catch (e: IllegalArgumentException) {
            return reportBadTypeDefinition(beanDescription, e.exceptionMessage())
        }

        mySerializerCache.addAndResolveNonTypedSerializer(type, serializer, this)
        return serializer
    }

    /**
     * Alternative to [createAndCacheUntypedSerializer], used when serializer is requested for given property.
     */
    protected open fun createAndCachePropertySerializer(rawType: KClass<*>, fullType: KotlinType,
            property: BeanProperty?): ValueSerializer<Any> {
        val beanDescription = introspectBeanDescription(fullType)

        val serializer = try {
            mySerializerFactory.createSerializer(this, fullType, beanDescription, null)
        } catch (e: IllegalArgumentException) {
            throw mappingProblem(e, e.exceptionMessage())
        }

        mySerializerCache.addAndResolveNonTypedSerializer(rawType, fullType, serializer, this)

        if (property == null) {
            return serializer
        }

        return checkShapeShifting(fullType, beanDescription, property, serializer)
    }

    /**
     * Alternative to [createAndCacheUntypedSerializer], used when serializer is requested for given property.
     */
    protected open fun createAndCachePropertySerializer(type: KotlinType,
            property: BeanProperty?): ValueSerializer<Any> {
        val beanDescription = introspectBeanDescription(type)

        val serializer = try {
            mySerializerFactory.createSerializer(this, type, beanDescription, null)
        } catch (e: IllegalArgumentException) {
            throw mappingProblem(e, e.exceptionMessage())
        }

        mySerializerCache.addAndResolveNonTypedSerializer(type, serializer, this)

        if (property == null) {
            return serializer
        }

        return checkShapeShifting(type, beanDescription, property, serializer)
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkShapeShifting(type: KotlinType, beanDescription: BeanDescription, property: BeanProperty,
            serializer: ValueSerializer<*>): ValueSerializer<Any> {
        val overrides = property.findFormatOverrides(myConfig) ?: return serializer as ValueSerializer<Any>
        return (serializer.withFormatOverrides(myConfig, overrides) ?: mySerializerFactory.createSerializer(this, type,
                beanDescription, overrides)) as ValueSerializer<Any>
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun handleResolvable(serializer: ValueSerializer<*>): ValueSerializer<Any> {
        serializer.resolve(this)
        return serializer as ValueSerializer<Any>
    }

    /*
     *******************************************************************************************************************
     * Methods for creating instances based on annotations
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to construct and configure serializer instance, either given a [KClass] to instantiate
     * (with default constructor), or an uninitialized serializer instance. Either way, serializer will be properly
     * resolved (via [ValueSerializer.resolve]).
     *
     * @param annotated Annotated entity that contained definition
     * 
     * @param serializerDefinition Serializer definition: either an instance or class
     */
    abstract fun serializerInstance(annotated: Annotated, serializerDefinition: Any?): ValueSerializer<Any>?

    /**
     * Method that can be called to construct and configure [CirJsonInclude] filter instance, given a [KClass] to
     * instantiate (with default constructor, by default).
     *
     * @param forProperty (optional) If filter is created for a property, that property; `null` if filter created via
     * defaulting, global or per-type.
     */
    abstract fun includeFilterInstance(forProperty: BeanProperty?, filterClass: KClass<*>?): Any?

    /**
     * Follow-up method that may be called after calling [includeFilterInstance], to check handling of `null` values by
     * the filter.
     */
    abstract fun includeFilterSuppressNulls(filter: Any?): Boolean

    /*
     *******************************************************************************************************************
     * Support for contextualization
     *******************************************************************************************************************
     */

    /**
     * Method called for primary property serializers (ones directly created to serialize values of a POJO property), to
     * handle details of contextualization, calling [ValueSerializer.createContextual] with given property context.
     *
     * @param property Property for which the given primary serializer is used; never `null`.
     */
    @Suppress("UNCHECKED_CAST")
    open fun handlePrimaryContextualization(serializer: ValueSerializer<*>?,
            property: BeanProperty): ValueSerializer<Any>? {
        return serializer?.createContextual(this, property) as ValueSerializer<Any>?
    }

    /**
     * Method called for secondary property serializers (ones NOT directly created to serialize values of a POJO
     * property but instead created as a dependant serializer -- such as value serializers for structured types, or
     * serializers for root values) to handle details of contextualization, calling [ValueSerializer.createContextual]
     * with given property context. Given that these serializers are not directly related to given property (or, in case
     * of root value property, to any property), annotations accessible may or may not be relevant.
     *
     * @param property Property for which serializer is used, if any; `null` when deserializing root values
     */
    @Suppress("UNCHECKED_CAST")
    open fun handleSecondaryContextualization(serializer: ValueSerializer<*>?,
            property: BeanProperty?): ValueSerializer<Any>? {
        return serializer?.createContextual(this, property) as ValueSerializer<Any>?
    }

    @Suppress("UNCHECKED_CAST")
    open fun handleRootContextualization(serializer: ValueSerializer<*>?): ValueSerializer<Any>? {
        return serializer?.createContextual(this, null) as ValueSerializer<Any>?
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for serializing using default methods
     *******************************************************************************************************************
     */

    /**
     * Convenience method that will serialize given property with specified value, using the default serializer for
     * runtime type of `value`.
     */
    @Throws(CirJacksonException::class)
    fun defaultSerializeProperty(propertyName: String, value: Any?, generator: CirJsonGenerator) {
        generator.writeName(propertyName)
        writeValue(generator, value)
    }

    /**
     * Method that will handle serialization of Date(-like) values, using [SerializationConfig] settings to determine
     * expected serialization behavior. Note: date here means "full" date, that is, date AND time, as per convention
     * (and not date-only values like in SQL)
     */
    @Throws(CirJacksonException::class)
    fun defaultSerializeDateValue(timestamp: Long, generator: CirJsonGenerator) {
        if (isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
            generator.writeNumber(timestamp)
        } else {
            generator.writeString(dateFormat().format(Date(timestamp)))
        }
    }

    /**
     * Method that will handle serialization of Date(-like) values, using [SerializationConfig] settings to determine
     * expected serialization behavior. Note: date here means "full" date, that is, date AND time, as per convention
     * (and not date-only values like in SQL)
     */
    @Throws(CirJacksonException::class)
    fun defaultSerializeDateValue(date: Date, generator: CirJsonGenerator) {
        if (isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
            generator.writeNumber(date.time)
        } else {
            generator.writeString(dateFormat().format(date))
        }
    }

    /**
     * Method that will handle serialization of Dates used as [Map] keys, based on
     * [SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS] value (and if using textual representation, configured date
     * format)
     */
    @Throws(CirJacksonException::class)
    fun defaultSerializeDateKey(timestamp: Long, generator: CirJsonGenerator) {
        if (isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
            generator.writeName(timestamp.toString())
        } else {
            generator.writeName(dateFormat().format(Date(timestamp)))
        }
    }

    /**
     * Method that will handle serialization of Dates used as [Map] keys, based on
     * [SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS] value (and if using textual representation, configured date
     * format)
     */
    @Throws(CirJacksonException::class)
    fun defaultSerializeDateKey(date: Date, generator: CirJsonGenerator) {
        if (isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
            generator.writeName(date.time.toString())
        } else {
            generator.writeName(dateFormat().format(date))
        }
    }

    /**
     * Method to call when serializing a `null` value (POJO property, Map entry value, Collection/array element) using
     * configured standard mechanism. Note that this does NOT consider filtering any more as value is expected.
     */
    @Throws(CirJacksonException::class)
    fun defaultSerializeNullValue(generator: CirJsonGenerator) {
        if (myStandardNullValueSerializer) {
            generator.writeNull()
        } else {
            myNullValueSerializer.serializeNullable(null, generator, this)
        }
    }

    /*
     *******************************************************************************************************************
     * Serialization-like helper methods
     *******************************************************************************************************************
     */

    /**
     * Method that will convert given Java value (usually bean) into its equivalent Tree mode [CirJsonNode]
     * representation. Functionally similar to serializing value into token stream and parsing that stream back as tree
     * model node, but more efficient as [TokenBuffer] is used to contain the intermediate representation instead of
     * fully serialized contents.
     * 
     * NOTE: while results are usually identical to that of serialization followed by deserialization, this is not
     * always the case. In some cases serialization into intermediate representation will retain encapsulation of things
     * like raw value ([org.cirjson.cirjackson.databind.util.RawValue]) or basic node identity ([CirJsonNode]). If so,
     * result is a valid tree, but values are not re-constructed through actual format representation. So if
     * transformation requires actual materialization of encoded content, it will be necessary to do actual
     * serialization.
     * 
     * @param <T> Actual node type; usually either basic [CirJsonNode] or
     * [org.cirjson.cirjackson.databind.node.ObjectNode]
     * 
     * @param fromValue value to convert
     *
     * @return (non-`null`) Root node of the resulting content tree: in case of `null` value node for which
     * [CirJsonNode.isNull] returns `true`.
     */
    abstract fun <T : CirJsonNode> valueToTree(fromValue: Any?): T

    /*
     *******************************************************************************************************************
     * Error reporting
     *******************************************************************************************************************
     */

    /**
     * Helper method called to indicate problem in POJO (serialization) definitions or settings regarding specific type,
     * unrelated to actual CirJSON content to map. Default behavior is to construct and throw an
     * [InvalidDefinitionException].
     */
    @Throws(DatabindException::class)
    override fun <T> reportBadTypeDefinition(bean: BeanDescription, message: String?): T {
        val description = bean.beanClass.name
        throw InvalidDefinitionException.from(generator, "Invalid type definition for type $description: $message",
                bean, null)
    }

    /**
     * Helper method called to indicate problem in POJO (serialization) definitions or settings regarding specific
     * property (of a type), unrelated to actual CirJSON content to map. Default behavior is to construct and throw an
     * [InvalidDefinitionException].
     */
    open fun <T> reportBadPropertyDefinition(bean: BeanDescription, property: BeanPropertyDefinition?,
            message: String?): T {
        val propertyName = property?.let { quotedString(it.name) } ?: "N/A"
        throw InvalidDefinitionException.from(generator,
                "Invalid definition for property $propertyName (of type $bean): $message", bean, property)
    }

    @Throws(DatabindException::class)
    override fun <T> reportBadDefinition(type: KotlinType, message: String?): T {
        throw InvalidDefinitionException.from(generator, message, type)
    }

    @Throws(DatabindException::class)
    open fun <T> reportBadDefinition(type: KotlinType, message: String?, cause: Throwable): T {
        throw InvalidDefinitionException.from(generator, message, type).withCause(cause)
    }

    @Throws(DatabindException::class)
    open fun <T> reportBadDefinition(rawType: KClass<*>, message: String?, cause: Throwable): T {
        throw InvalidDefinitionException.from(generator, message, constructType(rawType)).withCause(cause)
    }

    /**
     * Helper method called to indicate problem; default behavior is to construct and throw a [DatabindException], but
     * in future may collect more than one and only throw after certain number, or at the end of serialization.
     */
    @Throws(DatabindException::class)
    open fun <T> reportMappingProblem(throwable: Throwable, message: String?): T {
        throw mappingProblem(throwable, message)
    }

    protected open fun mappingProblem(throwable: Throwable, message: String?): DatabindException {
        return DatabindException.from(generator, message, throwable)
    }

    /**
     * Helper method called to indicate problem; default behavior is to construct and throw a [DatabindException], but
     * in future may collect more than one and only throw after certain number, or at the end of serialization.
     */
    @Throws(DatabindException::class)
    open fun <T> reportMappingProblem(message: String?): T {
        throw DatabindException.from(generator, message)
    }

    override fun invalidTypeIdException(baseType: KotlinType, typeId: String?,
            extraDescription: String): DatabindException {
        val message = "Could not resolve type id '$typeId' as a subtype of ${baseType.typeDescription}"
        return InvalidTypeIdException.from(null, colonConcat(message, extraDescription), baseType, typeId)
    }

    @Throws(CirJacksonException::class)
    protected open fun reportIncompatibleRootType(value: Any, rootType: KotlinType) {
        if (rootType.isPrimitive) {
            val wrapperType = rootType.rawClass.wrapperType()

            if (wrapperType.isAssignableFrom(value::class)) {
                return
            }
        }

        return reportBadDefinition(rootType, "Incompatible types: declared root type ($rootType) vs ${value.className}")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other
     *******************************************************************************************************************
     */

    protected fun dateFormat(): DateFormat {
        return myDateFormat ?: (myConfig.dateFormat.clone() as DateFormat).also { myDateFormat = it }
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