package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.configuration.DatatypeFeature
import org.cirjson.cirjackson.databind.configuration.DatatypeFeatures
import org.cirjson.cirjackson.databind.deserialization.*
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.TypeWrappedDeserializer
import org.cirjson.cirjackson.databind.exception.*
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.TreeTraversingParser
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.*
import java.text.DateFormat
import java.text.ParseException
import java.util.*
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
@Suppress("ThrowableNotThrown")
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

    final override val annotationIntrospector: AnnotationIntrospector?
        get() = myConfig.annotationIntrospector

    final override val typeFactory: TypeFactory
        get() = myConfig.typeFactory

    @Throws(IllegalArgumentException::class)
    override fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        if (baseType.hasRawClass(subclass)) {
            return baseType
        }

        return config.typeFactory.constructSpecializedType(baseType, subclass, false)
    }

    /**
     * Accessor for default Locale to use: convenience accessor for
     * ```
     * config.locale
     * ```
     */
    override val locale: Locale
        get() = myConfig.locale

    /**
     * Accessor for default TimeZone to use: convenience accessor for
     * ```
     * config.timeZone
     * ```
     */
    override val timeZone: TimeZone
        get() = myConfig.timeZone

    /*
     *******************************************************************************************************************
     * Access to per-call state, like generic attributes
     *******************************************************************************************************************
     */

    override fun getAttribute(key: Any): Any? {
        return myAttributes.getAttribute(key)
    }

    override fun setAttribute(key: Any, value: Any?): DeserializationContext {
        myAttributes = myAttributes.withPerCallAttribute(key, value)
        return this
    }

    /**
     * Accessor to [KotlinType] of currently contextualized [ValueDeserializer], if any. This is sometimes useful for
     * generic [ValueDeserializers][ValueDeserializer] that do not get passed (or do not retain) type information when
     * being constructed: happens for example for deserializers constructed from annotations.
     *
     * @return Type of [ValueDeserializer] being contextualized, if process is ongoing; `null` if not.
     */
    open val contextualType: KotlinType?
        get() = myCurrentType?.value()

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, config access
     *******************************************************************************************************************
     */

    override val tokenStreamFactory: TokenStreamFactory
        get() = myStreamFactory

    override val schema: FormatSchema?
        get() = mySchema

    override val streamReadConstraints: StreamReadConstraints
        get() = myStreamFactory.streamReadConstraints

    override fun getStreamReadFeatures(defaults: Int): Int {
        return myConfig.streamReadFeatures
    }

    override fun getFormatReadFeatures(defaults: Int): Int {
        return myConfig.formatReadFeatures
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, Tree creation
     *******************************************************************************************************************
     */

    override fun createArrayNode(): ArrayTreeNode {
        return nodeFactory.arrayNode()
    }

    override fun createObjectNode(): ObjectTreeNode {
        return nodeFactory.objectNode()
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, databind
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        val token = parser.currentToken() ?: parser.nextToken() ?: return null

        if (token == CirJsonToken.VALUE_NULL) {
            return nodeFactory.nullNode() as T
        }

        val deserializer = findRootValueDeserializer(ObjectReader.CIRJSON_NODE_TYPE)
        return deserializer.deserialize(parser, this) as T
    }

    /**
     * Convenience method that may be used by composite or container deserializers, for reading one-off values contained
     * (for sequences, it is more efficient to actually fetch deserializer once for the whole collection).
     * 
     * NOTE: when deserializing values of properties contained in composite types, rather use [readPropertyValue]; this
     * method does not allow use of contextual annotations.
     */
    @Throws(CirJacksonException::class)
    override fun <T : Any> readValue(parser: CirJsonParser, clazz: KClass<T>): T? {
        return readValue(parser, typeFactory.constructType(clazz.java))
    }

    @Throws(CirJacksonException::class)
    override fun <T : Any> readValue(parser: CirJsonParser, typeReference: TypeReference<T>): T? {
        return readValue(parser, typeFactory.constructType(typeReference))
    }

    @Throws(CirJacksonException::class)
    override fun <T : Any> readValue(parser: CirJsonParser, resolvedType: ResolvedType): T? {
        if (resolvedType !is KotlinType) {
            throw UnsupportedOperationException(
                    "Only support `JavaType` implementation of `ResolvedType`, not: ${resolvedType::class.qualifiedName}")
        }

        return readValue(parser, resolvedType)
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readValue(parser: CirJsonParser, type: KotlinType): T? {
        val deserializer = findRootValueDeserializer(type)
        return deserializer.deserialize(parser, this) as T?
    }

    /*
     *******************************************************************************************************************
     * Public API, config feature accessors
     *******************************************************************************************************************
     */

    /**
     * Convenience method for checking whether specified on/off feature is enabled
     */
    fun isEnabled(feature: DeserializationFeature): Boolean {
        return myFeatureFlags and feature.mask != 0
    }

    /**
     * Bulk accessor for getting the bit mask of all [DeserializationFeatures][DeserializationFeature] that are enabled.
     */
    val deserializationFeatures: Int
        get() = myFeatureFlags

    /**
     * Bulk access method for checking that all features specified by mask are enabled.
     */
    fun hasDeserializationFeatures(featureMask: Int): Boolean {
        return myFeatureFlags and featureMask == featureMask
    }

    /**
     * Bulk access method for checking that at least one of features specified by mask is enabled.
     */
    fun hasSomeOfFeatures(featureMask: Int): Boolean {
        return myFeatureFlags and featureMask != 0
    }

    /**
     * Accessor for checking whether input format has specified capability or not.
     *
     * @return `true` if input format has specified capability; `false` if not
     */
    fun isEnabled(capability: StreamReadCapability): Boolean {
        return myReadCapabilities!!.isEnabled(capability)
    }

    /*
     *******************************************************************************************************************
     * Public API, accessor for helper objects
     *******************************************************************************************************************
     */

    /**
     * Accessor for the currently active parser. May be different from the outermost parser when content is buffered.
     *
     * Use of this accessor is discouraged: if code has direct access to the active parser, that should be used instead.
     */
    val parser: CirJsonParser?
        get() = myParser

    fun findInjectableValue(valueId: Any, forProperty: BeanProperty, beanInstance: Any): Any? {
        myInjectableValues ?: return reportBadDefinition(valueId::class,
                "No 'injectableValues' configured, cannot inject value with id [$valueId]")

        return myInjectableValues.findInjectableValue(valueId, this, forProperty, beanInstance)
    }

    /**
     * Convenience accessor for the default Base64 encoding used for decoding base64 encoded binary content. Same as
     * calling:
     * ```
     * config.base64Variant
     * ```
     */
    val base64Variant: Base64Variant
        get() = myConfig.base64Variant

    /**
     * Convenience accessor, functionally equivalent to:
     * ```
     * config.nodeFactory
     * ```
     */
    val nodeFactory: CirJsonNodeFactory
        get() = myConfig.nodeFactory

    /*
     *******************************************************************************************************************
     * Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    override fun classIntrospector(): ClassIntrospector {
        return myClassIntrospector ?: myConfig.classIntrospectorInstance().also { myClassIntrospector = it }
    }

    override fun introspectBeanDescription(type: KotlinType): BeanDescription {
        return classIntrospector().introspectForDeserialization(type)
    }

    open fun introspectBeanDescriptionForCreation(type: KotlinType): BeanDescription {
        return classIntrospector().introspectForCreation(type)
    }

    open fun introspectBeanDescriptionForBuilder(builderType: KotlinType,
            valueTypeDescription: BeanDescription): BeanDescription {
        return classIntrospector().introspectForDeserializationWithBuilder(builderType, valueTypeDescription)
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

    /**
     * Method that can be used to see whether there is an explicitly registered deserializer for given type: this is
     * true for supported JDK types, as well as third-party types for which `Module` provides support but is NOT true
     * (that is, returns `false`) for POJO types for which `BeanDeserializer` is generated based on discovered
     * properties.
     * 
     * Note that it is up to `Modules` to implement support for this method: some do (like basic `SimpleModule`).
     *
     * @param valueType Type-erased type to check
     *
     * @return True if this factory has explicit (non-POJO) deserializer for specified type, or has a provider (of type
     * [Deserializers]) that has.
     */
    open fun hasExplicitDeserializerFor(valueType: KClass<*>): Boolean {
        return myFactory.hasExplicitDeserializerFor(this, valueType)
    }

    /*
     *******************************************************************************************************************
     * Public API, CoercionConfig access
     *******************************************************************************************************************
     */

    /**
     * General-purpose accessor for finding what to do when specified coercion from shape that is now always allowed to
     * be coerced from is requested.
     *
     * @param targetType Logical target type of coercion
     * 
     * @param targetClass Physical target type of coercion
     * 
     * @param inputShape Input shape to coerce from
     *
     * @return CoercionAction configured for specific coercion
     */
    open fun findCoercionAction(targetType: LogicalType?, targetClass: KClass<*>?,
            inputShape: CoercionInputShape): CoercionAction {
        return myConfig.findCoercionAction(targetType, targetClass, inputShape)
    }

    /**
     * More specialized accessor called in case of input being a blank String (one consisting of only white space
     * characters with length of at least one). Will basically first determine if "blank as empty" is allowed: if not,
     * returns `actionIfBlankNotAllowed`, otherwise returns action for [CoercionInputShape.EmptyString].
     *
     * @param targetType Logical target type of coercion
     * 
     * @param targetClass Physical target type of coercion
     * 
     * @param actionIfBlankNotAllowed Return value to use in case "blanks as empty" is not allowed
     *
     * @return CoercionAction configured for specified coercion from blank string
     */
    open fun findCoercionFromBlankString(targetType: LogicalType?, targetClass: KClass<*>?,
            actionIfBlankNotAllowed: CoercionAction): CoercionAction {
        return myConfig.findCoercionFromBlankString(targetType, targetClass, actionIfBlankNotAllowed)
    }

    /*
     *******************************************************************************************************************
     * Factory methods for getting appropriate TokenBuffer instances (possibly overridden by backends for alternate data
     * formats)
     *******************************************************************************************************************
     */

    /**
     * Factory method used for creating [TokenBuffer] to temporarily contain copy of content read from specified parser;
     * usually for purpose of reading contents later on (possibly augmented with injected additional content)
     */
    open fun bufferForInputBuffering(parser: CirJsonParser): TokenBuffer {
        return TokenBuffer.forBuffering(parser, this)
    }

    /**
     * Convenience method that is equivalent to:
     * ```
     * context.bufferForInputBuffering(context.parser)
     * ```
     */
    fun bufferForInputBuffering(): TokenBuffer {
        return bufferForInputBuffering(parser!!)
    }

    /**
     * Convenience method, equivalent to:
     * ```
     * val buffer = context.bufferForInputBuffering(parser)
     * buffer.copyCurrentStructure(parser)
     * return buffer
     * ```
     * 
     * NOTE: the whole "current value" that parser points to is read and buffered, including Object and Array values (if
     * parser pointing to start marker).
     */
    @Throws(CirJacksonException::class)
    open fun bufferAsCopyOfValue(parser: CirJsonParser): TokenBuffer {
        val buffer = bufferForInputBuffering(parser)
        buffer.copyCurrentStructure(parser)
        return buffer
    }

    /*
     *******************************************************************************************************************
     * Public API, value deserializer access
     *******************************************************************************************************************
     */

    /**
     * Method for finding a value deserializer, and creating a contextual version if necessary, for value reached via
     * specified property.
     */
    @Suppress("UNCHECKED_CAST")
    fun findContextualValueDeserializer(type: KotlinType, property: BeanProperty?): ValueDeserializer<Any> {
        val deserializer = myCache.findValueDeserializer(this, myFactory, type)
        return handleSecondaryContextualization(deserializer, property, type) as ValueDeserializer<Any>
    }

    /**
     * Variant that will try to locate deserializer for current type, but without performing any contextualization
     * (unlike [findContextualValueDeserializer]) or checking for need to create a [TypeDeserializer] (unlike
     * [findRootValueDeserializer]. This method is usually called from within [ValueDeserializer.resolve], and
     * expectation is that caller then calls either [handlePrimaryContextualization] or
     * [handleSecondaryContextualization] at a later point, as necessary.
     */
    fun findNonContextualValueDeserializer(type: KotlinType): ValueDeserializer<Any> {
        return myCache.findValueDeserializer(this, myFactory, type)
    }

    /**
     * Method for finding a deserializer for root-level value.
     */
    @Suppress("UNCHECKED_CAST")
    fun findRootValueDeserializer(type: KotlinType): ValueDeserializer<Any> {
        var deserializer = myCache.findValueDeserializer(this, myFactory, type)
        deserializer = handleSecondaryContextualization(deserializer, null, type) as ValueDeserializer<Any>

        val typeDeserializer = findTypeDeserializer(type) ?: return deserializer

        return TypeWrappedDeserializer(typeDeserializer.forProperty(null), deserializer)
    }

    /*
     *******************************************************************************************************************
     * Public API, (value) type deserializer access
     *******************************************************************************************************************
     */

    /**
     * Method called to find and create a type information deserializer for given base type, if one is needed. If not
     * needed (no polymorphic handling configured for type), should return `null`.
     * 
     * Note that this method is usually only directly called for values of container (Collection, array, Map) types and
     * root values, but not for bean property values.
     *
     * @param baseType Declared base type of the value to deserializer (actual deserializer type will be this type or
     * its subtype)
     *
     * @return Type deserializer to use for given base type, if one is needed; `null` if not.
     */
    open fun findTypeDeserializer(baseType: KotlinType): TypeDeserializer? {
        return findTypeDeserializer(baseType, introspectClassAnnotations(baseType))
    }

    open fun findTypeDeserializer(baseType: KotlinType, classAnnotations: AnnotatedClass): TypeDeserializer? {
        val exception = try {
            return myConfig.typeResolverProvider.findTypeDeserializer(this, baseType, classAnnotations)
        } catch (e: IllegalArgumentException) {
            e
        } catch (e: IllegalStateException) {
            e
        }

        throw InvalidDefinitionException.from(parser, exception.exceptionMessage()!!, baseType).withCause(exception)
    }

    /**
     * Method called to create a type information deserializer for values of given non-container property, if one is
     * needed. If not needed (no polymorphic handling configured for property), should return `null`.
     * 
     * Note that this method is only called for non-container bean properties, and not for values in container types or
     * root values (or container properties)
     *
     * @param baseType Declared base type of the value to deserializer (actual deserializer type will be this type or
     * its subtype)
     *
     * @return Type deserializer to use for given base type, if one is needed; `null` if not.
     */
    open fun findPropertyTypeDeserializer(baseType: KotlinType, accessor: AnnotatedMember): TypeDeserializer? {
        val exception = try {
            return myConfig.typeResolverProvider.findPropertyTypeDeserializer(this, accessor, baseType)
        } catch (e: IllegalArgumentException) {
            e
        } catch (e: IllegalStateException) {
            e
        }

        throw InvalidDefinitionException.from(parser, exception.exceptionMessage()!!, baseType).withCause(exception)
    }

    /**
     * Method called to find and create a type information deserializer for values of given container (list, array, map)
     * property, if one is needed. If not needed (no polymorphic handling configured for property), should return
     * `null`.
     * 
     * Note that this method is only called for container bean properties, and not for values in container types or root
     * values (or non-container properties)
     *
     * @param containerType Type of property; must be a container type
     * 
     * @param accessor Field or method that contains container property
     */
    open fun findPropertyContentTypeDeserializer(containerType: KotlinType,
            accessor: AnnotatedMethod): TypeDeserializer? {
        val exception = try {
            return myConfig.typeResolverProvider.findPropertyContentTypeDeserializer(this, accessor, containerType)
        } catch (e: IllegalArgumentException) {
            e
        } catch (e: IllegalStateException) {
            e
        }

        throw InvalidDefinitionException.from(parser, exception.exceptionMessage()!!, containerType)
                .withCause(exception)
    }

    /*
     *******************************************************************************************************************
     * Public API, key deserializer access
     *******************************************************************************************************************
     */

    fun findKeyDeserializer(keyType: KotlinType, property: BeanProperty): KeyDeserializer {
        val keyDeserializer = try {
            myCache.findKeyDeserializer(this, myFactory, keyType)
        } catch (e: IllegalArgumentException) {
            return reportBadDefinition(keyType, e.exceptionMessage())
        }

        return (keyDeserializer as? ContextualKeyDeserializer)?.createContextual(this, property) ?: keyDeserializer
    }

    /*
     *******************************************************************************************************************
     * Public API, ObjectId handling
     *******************************************************************************************************************
     */

    /**
     * Method called to find and return entry corresponding to given Object ID: will add an entry if necessary, and
     * never returns `null`
     */
    abstract fun findObjectId(id: Any, generator: ObjectIdGenerator<*>, resolver: ObjectIdResolver): ReadableObjectId

    /**
     * Method called to ensure that every object id encounter during processing are resolved.
     *
     * @throws UnresolvedForwardReferenceException If some object id isn't resolved
     */
    @Throws(UnresolvedForwardReferenceException::class)
    abstract fun checkUnresolvedObjectId()

    /*
     *******************************************************************************************************************
     * Public API, type handling
     *******************************************************************************************************************
     */

    /**
     * Convenience method, functionally equivalent to:
     * ```
     * config.constructType(type)
     * ```
     */
    final override fun constructType(type: KClass<*>?): KotlinType? {
        return type?.let { myConfig.constructType(type) }
    }

    /**
     * Helper method that is to be used when resolving basic class name into KClass instance, the reason being that it
     * may be necessary to work around various ClassLoader limitations, as well as to handle primitive type signatures.
     */
    @Throws(ClassNotFoundException::class)
    open fun findClass(className: String): KClass<*> {
        return typeFactory.findClass(className)
    }

    /*
     *******************************************************************************************************************
     * Public API, helper object recycling
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to get access to a reusable ObjectBuffer, useful for efficiently constructing Object
     * arrays and Lists. Note that leased buffers should be returned once deserializer is done, to allow for reuse
     * during same round of deserialization.
     */
    fun leaseObjectBuffer(): ObjectBuffer {
        var buffer = myObjectBuffer

        if (buffer == null) {
            buffer = ObjectBuffer()
        } else {
            myObjectBuffer = null
        }

        return buffer
    }

    /**
     * Method to call to return object buffer previously leased with [leaseObjectBuffer].
     *
     * @param buffer Returned object buffer
     */
    fun returnObjectBuffer(buffer: ObjectBuffer) {
        if (myObjectBuffer == null || buffer.initialCapacity() >= myObjectBuffer!!.initialCapacity()) {
            myObjectBuffer = buffer
        }
    }

    /**
     * Accessor for object useful for building arrays of primitive types (such as IntArray).
     */
    val arrayBuilders: ArrayBuilders
        get() = myArrayBuilders ?: ArrayBuilders().also { myArrayBuilders = it }

    /*
     *******************************************************************************************************************
     * Extended API: handler instantiation
     *******************************************************************************************************************
     */

    abstract fun deserializerInstance(annotated: Annotated, deserializerDefinition: Any?): ValueDeserializer<Any>?

    abstract fun keyDeserializerInstance(annotated: Annotated, deserializerDefinition: Any?): KeyDeserializer?

    /*
     *******************************************************************************************************************
     * Extended API: resolving contextual deserializers; called by structured deserializers for their value/component
     * deserializers
     *******************************************************************************************************************
     */

    /**
     * Method called for primary property deserializers (ones directly created to deserialize values of a POJO
     * property), to handle details of calling [ValueDeserializer.createContextual] with given property context.
     *
     * @param property Property for which the given primary deserializer is used; never `null`.
     */
    open fun handlePrimaryContextualization(deserializer: ValueDeserializer<*>?, property: BeanProperty,
            type: KotlinType): ValueDeserializer<*>? {
        var realDeserializer = deserializer ?: return null
        myCurrentType = LinkedNode(type, myCurrentType)

        try {
            realDeserializer = realDeserializer.createContextual(this, property)
        } finally {
            myCurrentType = myCurrentType!!.next()
        }

        return realDeserializer
    }

    /**
     * Method called for secondary property deserializers (ones NOT directly created to deal with an annotatable POJO
     * property, but instead created as a component -- such as value deserializers for structured types, or
     * deserializers for root values) to handle details of resolving [ValueDeserializer.createContextual] with given
     * property context. Given that these deserializers are not directly related to given property (or, in case of root
     * value property, to any property), annotations accessible may or may not be relevant.
     *
     * @param property Property for which deserializer is used, if any; `null` when deserializing root values
     */
    open fun handleSecondaryContextualization(deserializer: ValueDeserializer<*>?, property: BeanProperty?,
            type: KotlinType): ValueDeserializer<*>? {
        var realDeserializer = deserializer ?: return null
        myCurrentType = LinkedNode(type, myCurrentType)

        try {
            realDeserializer = realDeserializer.createContextual(this, property)
        } finally {
            myCurrentType = myCurrentType!!.next()
        }

        return realDeserializer
    }

    /*
     *******************************************************************************************************************
     * Parsing methods that may use reusable/-cyclable objects
     *******************************************************************************************************************
     */

    /**
     * Convenience method for parsing a Date from given String, using currently configured date format (accessed using
     * [DeserializationConfig.dateFormat]).
     * 
     * Implementation will handle thread-safety issues related to date formats such that first time this method is
     * called, date format is cloned, and cloned instance will be retained for use during this deserialization round.
     */
    @Throws(IllegalArgumentException::class)
    open fun parseDate(dateString: String): Date {
        try {
            val dateFormat = dateFormat
            return dateFormat.parse(dateString)
        } catch (e: ParseException) {
            throw IllegalArgumentException("Failed to parse Date value '$dateString': ${e.exceptionMessage()}")
        }
    }

    /**
     * Convenience method for constructing Calendar instance set to specified time, to be modified and used by caller.
     */
    open fun constructCalendar(date: Date): Calendar {
        val calendar = Calendar.getInstance(timeZone)
        calendar.time = date
        return calendar
    }

    /*
     *******************************************************************************************************************
     * Extension points for more esoteric data coercion
     *******************************************************************************************************************
     */

    /**
     * Method to call in case incoming shape is Object Value (and parser thereby points to [CirJsonToken.START_OBJECT]
     * token), but a Scalar value (potentially coercible from String value) is expected. This would typically be used to
     * deserializer a Number, Boolean value or some other "simple" unstructured value type.
     *
     * @param parser Actual parser to read content from
     * 
     * @param deserializer Deserializer that needs extracted String value
     * 
     * @param scalarType Immediate type of scalar to extract; usually type deserializer handles but not always (for
     * example, deserializer for `IntArray` would pass scalar type of `Int`)
     *
     * @return String value found; not `null` (exception should be thrown if no suitable value found)
     *
     * @throws CirJacksonException If there are problems either reading content (underlying parser problem) or finding
     * expected scalar value
     */
    @Throws(CirJacksonException::class)
    open fun extractScalarFromObject(parser: CirJsonParser, deserializer: ValueDeserializer<*>,
            scalarType: KClass<*>): String {
        return handleUnexpectedToken(constructType(scalarType)!!, parser)!! as String
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for reading parsed values
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun <T : Any> readPropertyValue(parser: CirJsonParser, property: BeanProperty?, type: KClass<T>): T? {
        return readPropertyValue(parser, property, typeFactory.constructType(type.java))
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> readPropertyValue(parser: CirJsonParser, property: BeanProperty?, type: KotlinType): T? {
        val deserializer = findContextualValueDeserializer(type, property)
        return deserializer.deserialize(parser, this) as T?
    }

    /**
     * Helper method similar to [ObjectReader.treeToValue] which will read contents of given tree ([CirJsonNode]) and
     * bind them into specified target type. This is often used in two-phase deserialization in which content is first
     * read as a tree, then manipulated (adding and/or removing properties of Object values, for example), and finally
     * converted into actual target type using default deserialization logic for the type.
     * 
     * NOTE: deserializer implementations should be careful not to try to recursively deserialize into target type
     * deserializer has registered itself to handle.
     *
     * @param node Tree value to convert, if not `null`: if `null`, will simply return `null`
     * 
     * @param targetType Type to deserialize contents of `n` into (if `n` not `null`)
     *
     * @return Either `null` (if `n` was `null` or a value of type `type` that was read from non-`null` `n` argument
     */
    @Throws(CirJacksonException::class)
    open fun <T : Any> readTreeAsValue(node: CirJsonNode?, targetType: KClass<T>): T? {
        node ?: return null
        treeAsTokens(node).use {
            return readValue(it, targetType)
        }
    }

    @Throws(CirJacksonException::class)
    open fun <T : Any> readTreeAsValue(node: CirJsonNode?, targetType: KotlinType): T? {
        node ?: return null
        treeAsTokens(node).use {
            return readValue(it, targetType)
        }
    }

    private fun treeAsTokens(node: CirJsonNode): TreeTraversingParser {
        val parser = TreeTraversingParser(node, this)
        parser.nextToken()
        return parser
    }

    /*
     *******************************************************************************************************************
     * Methods for problem handling
     *******************************************************************************************************************
     */

    /**
     * Method that deserializers should call if they encounter an unrecognized property (and once that is not explicitly
     * designed as ignorable), to inform possibly configured
     * [DeserializationProblemHandlers][DeserializationProblemHandler] and let it handle the problem.
     *
     * @return `true` if there was a configured problem handler that was able to handle the problem
     */
    @Throws(CirJacksonException::class)
    open fun handleUnknownProperty(parser: CirJsonParser, deserializer: ValueDeserializer<*>?, instanceOrClass: Any,
            propertyName: String): Boolean {
        var handler = config.problemHandlers

        while (handler != null) {
            if (handler.value().handleUnknownProperty(this, parser, deserializer, instanceOrClass, propertyName)) {
                return true
            }

            handler = handler.next()
        }

        if (!isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            parser.skipChildren()
            return true
        }

        val propertyIds = deserializer?.knownPropertyNames
        throw UnrecognizedPropertyException.from(myParser!!, instanceOrClass, propertyName, propertyIds)
    }

    /**
     * Method that deserializers should call if they encounter a String value that cannot be converted to expected key
     * of a [Map] valued property. Default implementation will try to call
     * [DeserializationProblemHandler.handleWeirdNumberValue] on configured handlers, if any, to allow for recovery; if
     * recovery does not succeed, will throw [InvalidFormatException] with given message.
     *
     * @param keyClass Expected type for key
     * 
     * @param keyValue String value from which to deserialize key
     * 
     * @param message Error message caller wants to use if exception is to be thrown
     *
     * @return Key value to use
     *
     * @throws CirJacksonException To indicate unrecoverable problem, usually based on `message`
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdKey(keyClass: KClass<*>, keyValue: String, message: String?): Any? {
        var handler = config.problemHandlers

        while (handler != null) {
            val key = handler.value().handleWeirdKey(this, keyClass, keyValue, message)

            if (key === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (key == null || keyClass.isInstance(key)) {
                return key
            }

            throw weirdStringException(keyValue, keyClass,
                    "DeserializationProblemHandler.handleWeirdKey() for type ${keyClass.classDescription} returned value of type ${key.classDescription}")
        }

        throw weirdKeyException(keyClass, keyValue, message)
    }

    /**
     * Method that deserializers should call if they encounter a String value that cannot be converted to target
     * property type, in cases where some String values could be acceptable (either with different settings, or
     * different value). Default implementation will try to call [DeserializationProblemHandler.handleWeirdStringValue]
     * on configured handlers, if any, to allow for recovery; if recovery does not succeed, will throw
     * [InvalidFormatException] with given message.
     *
     * @param targetClass Type of property into which incoming String should be converted
     * 
     * @param value String value from which to deserialize property value
     * 
     * @param message Error message template caller wants to use if exception is to be thrown
     *
     * @return Property value to use
     *
     * @throws CirJacksonException To indicate unrecoverable problem, usually based on `message`
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdStringValue(targetClass: KClass<*>, value: String, message: String?): Any? {
        var handler = config.problemHandlers

        while (handler != null) {
            val instance = handler.value().handleWeirdStringValue(this, targetClass, value, message)

            if (instance === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (isCompatible(targetClass, instance)) {
                return instance
            }

            throw weirdStringException(value, targetClass,
                    "DeserializationProblemHandler.handleWeirdStringValue() for type ${targetClass.classDescription} returned value of type ${instance.classDescription}")
        }

        throw weirdStringException(value, targetClass, message)
    }

    /**
     * Method that deserializers should call if they encounter a numeric value that cannot be converted to target
     * property type, in cases where some numeric values could be acceptable (either with different settings, or
     * different numeric value). Default implementation will try to call
     * [DeserializationProblemHandler.handleWeirdNumberValue] on configured handlers, if any, to allow for recovery; if
     * recovery does not succeed, will throw [InvalidFormatException] with given message.
     *
     * @param targetClass Type of property into which incoming number should be converted
     * 
     * @param value Number value from which to deserialize property value
     * 
     * @param message Error message template caller wants to use if exception is to be thrown
     *
     * @return Property value to use
     *
     * @throws CirJacksonException To indicate unrecoverable problem, usually based on `message`
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdNumberValue(targetClass: KClass<*>, value: Number, message: String?): Any? {
        var handler = config.problemHandlers

        while (handler != null) {
            val instance = handler.value().handleWeirdNumberValue(this, targetClass, value, message)

            if (instance === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (isCompatible(targetClass, instance)) {
                return instance
            }

            throw weirdNumberException(value, targetClass,
                    "DeserializationProblemHandler.handleWeirdNumberValue() for type ${targetClass.classDescription} returned value of type ${instance.classDescription}")
        }

        throw weirdNumberException(value, targetClass, message)
    }

    @Throws(CirJacksonException::class)
    open fun handleWeirdNativeValue(targetType: KotlinType, badValue: Any, parser: CirJsonParser): Any? {
        var handler = config.problemHandlers
        val raw = targetType.rawClass

        while (handler != null) {
            val goodValue = handler.value().handleWeirdNativeValue(this, targetType, badValue, parser)

            if (goodValue === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (goodValue == null || raw.isInstance(goodValue)) {
                return goodValue
            }

            throw DatabindException.from(parser,
                    "DeserializationProblemHandler.handleWeirdNativeValue() for type ${targetType.classDescription} returned value of type ${goodValue.classDescription}")
        }

        throw weirdNativeValueException(badValue, raw)
    }

    /**
     * Method that deserializers should call if they fail to instantiate value due to lack of viable instantiator
     * (usually creator, that is, constructor or static factory method). Method should be called at point where value
     * has not been decoded, so that handler has a chance to handle decoding using alternate mechanism, and handle
     * underlying content (possibly by just skipping it) to keep input state valid
     *
     * @param instantiatedClass Type that was to be instantiated
     * 
     * @param valueInstantiator (optional) Value instantiator to be used, if any; `null` if type does not use one for
     * instantiation (custom deserializers don't; standard POJO deserializer does)
     * 
     * @param parser Parser that points to the CirJSON value to decode
     *
     * @return Object that should be constructed, if any; has to be of type `instantiatedClass`
     */
    @Throws(CirJacksonException::class)
    open fun handleMissingInstantiator(instantiatedClass: KClass<*>, valueInstantiator: ValueInstantiator?,
            parser: CirJsonParser?, message: String?): Any? {
        var realParser = parser ?: this.parser
        var handler = config.problemHandlers

        while (handler != null) {
            val instance = handler.value()
                    .handleMissingInstantiator(this, instantiatedClass, valueInstantiator, realParser, message)

            if (instance === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (isCompatible(instantiatedClass, instance)) {
                return instance
            }

            return reportBadDefinition(constructType(instantiatedClass)!!,
                    "DeserializationProblemHandler.handleMissingInstantiator() for type ${instantiatedClass.classDescription} returned value of type ${instance.classDescription}")
        }

        valueInstantiator ?: return reportBadDefinition(instantiatedClass,
                "Cannot construct instance of ${instantiatedClass.name}: $message")

        if (!valueInstantiator.canInstantiate()) {
            return reportBadDefinition(instantiatedClass,
                    "Cannot construct instance of ${instantiatedClass.name} (no Creators, like default constructor, exist): $message")
        }

        return reportInputMismatch(instantiatedClass,
                "Cannot construct instance of ${instantiatedClass.name} (although at least one Creator exists): $message")
    }

    /**
     * Method that deserializers should call if they fail to instantiate value due to an exception that was thrown by
     * constructor (or other mechanism used to create instances). Default implementation will try to call
     * [DeserializationProblemHandler.handleInstantiationProblem] on configured handlers, if any, to allow for recovery;
     * if recovery does not succeed, will throw exception constructed with [instantiationException].
     *
     * @param instantiatedClass Type that was to be instantiated
     * 
     * @param argument (optional) Argument that was passed to constructor or equivalent instantiator; often a [String].
     * 
     * @param throwable Exception that caused failure
     *
     * @return Object that should be constructed, if any; has to be of type `instantiatedClass`
     */
    @Throws(CirJacksonException::class)
    open fun handleInstantiationProblem(instantiatedClass: KClass<*>, argument: Any?, throwable: Throwable): Any? {
        var handler = config.problemHandlers

        while (handler != null) {
            val instance = handler.value().handleInstantiationProblem(this, instantiatedClass, argument, throwable)

            if (instance === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (isCompatible(instantiatedClass, instance)) {
                return instance
            }

            return reportBadDefinition(constructType(instantiatedClass)!!,
                    "DeserializationProblemHandler.handleInstantiationProblem() for type ${instantiatedClass.classDescription} returned value of type ${instance.className}")
        }

        throwable.throwIfCirJacksonException()

        if (!isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)) {
            throwable.throwIfRuntimeException()
        }

        throw instantiationException(instantiatedClass, throwable)
    }

    @Throws(CirJacksonException::class)
    open fun handleUnexpectedToken(instantiatedClass: KClass<*>, parser: CirJsonParser): Any? {
        return handleUnexpectedToken(constructType(instantiatedClass)!!, parser.currentToken(), parser, null)
    }

    /**
     * Method that deserializers should call if the first token of the value to deserialize is of unexpected type (that
     * is, type of token that deserializer cannot handle). This could occur, for example, if a Number deserializer
     * encounter [CirJsonToken.START_ARRAY] instead of [CirJsonToken.VALUE_NUMBER_INT] or
     * [CirJsonToken.VALUE_NUMBER_FLOAT].
     *
     * @param targetType Type that was to be instantiated
     * 
     * @param parser Parser that points to the CirJSON value to decode
     *
     * @return Object that should be constructed, if any; has to be of type `targetType,rawClass`
     */
    @Throws(CirJacksonException::class)
    open fun handleUnexpectedToken(targetType: KotlinType, parser: CirJsonParser): Any? {
        return handleUnexpectedToken(targetType, parser.currentToken(), parser, null)
    }

    /**
     * Method that deserializers should call if the first token of the value to deserialize is of unexpected type (that
     * is, type of token that deserializer cannot handle). This could occur, for example, if a Number deserializer
     * encounter [CirJsonToken.START_ARRAY] instead of [CirJsonToken.VALUE_NUMBER_INT] or
     * [CirJsonToken.VALUE_NUMBER_FLOAT].
     *
     * @param targetType Type that was to be instantiated
     * 
     * @param token Token encountered that does not match expected
     * 
     * @param parser Parser that points to the CirJSON value to decode
     *
     * @return Object that should be constructed, if any; has to be of type `targetType,rawClass`
     */
    @Throws(CirJacksonException::class)
    open fun handleUnexpectedToken(targetType: KotlinType, token: CirJsonToken?, parser: CirJsonParser,
            message: String?): Any? {
        var handler = config.problemHandlers

        while (handler != null) {
            val instance = handler.value().handleUnexpectedToken(this, targetType, token, parser, message)

            if (instance === DeserializationProblemHandler.NOT_HANDLED) {
                handler = handler.next()
                continue
            }

            if (isCompatible(targetType.rawClass, instance)) {
                return instance
            }

            return reportBadDefinition(targetType,
                    "DeserializationProblemHandler.handleUnexpectedToken() for type ${targetType.typeDescription} returned value of type ${instance.className}")
        }

        val realMessage = if (message != null) {
            message
        } else {
            val targetDescription = targetType.typeDescription

            if (token == null) {
                "Unexpected end-of-input when trying read value of type $targetDescription"
            } else {
                "Cannot deserialize value of type $targetDescription from ${
                    shapeForToken(token)
                } (token `CirJsonToken.$token`)"
            }
        }

        if (token?.isScalarValue ?: false) {
            parser.text
        }

        return reportInputMismatch(targetType, realMessage)
    }

    /**
     * Method that deserializers should call if they encounter a type id (for polymorphic deserialization) that cannot
     * be resolved to an actual type; usually since there is no mapping defined. Default implementation will try to call
     * [DeserializationProblemHandler.handleUnknownTypeId] on configured handlers, if any, to allow for recovery; if
     * recovery does not succeed, will throw exception constructed with [invalidTypeIdException].
     *
     * @param baseType Base type from which resolution starts
     * 
     * @param id Type id that could not be converted
     * 
     * @param extraDescription Additional problem description to add to default exception message, if resolution fails.
     *
     * @return [KotlinType] that id resolves to
     *
     * @throws CirJacksonException To indicate unrecoverable problem, if resolution cannot be made to work
     */
    @Throws(CirJacksonException::class)
    open fun handleUnknownTypeId(baseType: KotlinType, id: String, idResolver: TypeIdResolver,
            extraDescription: String): KotlinType? {
        var handler = config.problemHandlers

        while (handler != null) {
            val type = handler.value().handleUnknownTypeId(this, baseType, id, idResolver, extraDescription)

            if (type == null) {
                handler = handler.next()
                continue
            }

            if (type.hasRawClass(Unit::class)) {
                return null
            }

            if (type.isTypeOrSubTypeOf(baseType.rawClass)) {
                return type
            }

            throw invalidTypeIdException(baseType, id,
                    "problem handler tried to resolve into non-subtype: ${type.typeDescription}")
        }

        if (!isEnabled(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)) {
            return null
        }

        throw invalidTypeIdException(baseType, id, extraDescription)
    }

    @Throws(CirJacksonException::class)
    open fun handleMissingTypeId(baseType: KotlinType, idResolver: TypeIdResolver,
            extraDescription: String): KotlinType? {
        var handler = config.problemHandlers

        while (handler != null) {
            val type = handler.value().handleMissingTypeId(this, baseType, idResolver, extraDescription)

            if (type == null) {
                handler = handler.next()
                continue
            }

            if (type.hasRawClass(Unit::class)) {
                return null
            }

            if (type.isTypeOrSubTypeOf(baseType.rawClass)) {
                return type
            }

            throw invalidTypeIdException(baseType, null,
                    "problem handler tried to resolve into non-subtype: ${type.typeDescription}")
        }

        throw missingTypeIdException(baseType, extraDescription)
    }

    /**
     * Method that deserializer may call if it is called to do an update ("merge") but deserializer operates on a
     * non-mergeable type. Although this should usually be caught earlier, sometimes it may only be caught during
     * operation and if so this is the method to call. Note that if [MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE] is
     * enabled, this method will simply return `null`; otherwise [InvalidDefinitionException] will be thrown.
     */
    @Throws(DatabindException::class)
    open fun handleBadMerge(deserializer: ValueDeserializer<*>) {
        if (isEnabled(MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE)) {
            return
        }

        val type = constructType(deserializer.handledType())
        val message = "Invalid configuration: values of type ${type.typeDescription} cannot be merged"
        throw InvalidDefinitionException.from(parser, message, type)
    }

    protected open fun isCompatible(target: KClass<*>, value: Any?): Boolean {
        if (value == null || target.isInstance(value)) {
            return true
        }

        return target.isPrimitive && target.wrapperType().isInstance(value)
    }

    /*
     *******************************************************************************************************************
     * Methods for problem reporting, in cases where recovery is not considered possible: input problem
     *******************************************************************************************************************
     */

    /**
     * Method for deserializers to call when the token encountered was of type different from what **should** be seen at
     * that position, usually within a sequence of expected tokens. Note that this method will throw a
     * [DatabindException] and no recovery is attempted (via [DeserializationProblemHandler], as problem is considered
     * to be difficult to recover from, in general.
     */
    @Throws(DatabindException::class)
    open fun <T> reportWrongTokenException(deserializer: ValueDeserializer<*>, expectedToken: CirJsonToken?,
            message: String?): T {
        throw wrongTokenException(parser!!, deserializer.handledType()!!, expectedToken, message)
    }

    /**
     * Method for deserializers to call when the token encountered was of type different from what **should** be seen at
     * that position, usually within a sequence of expected tokens. Note that this method will throw a
     * [DatabindException] and no recovery is attempted (via [DeserializationProblemHandler], as problem is considered
     * to be difficult to recover from, in general.
     */
    @Throws(DatabindException::class)
    open fun <T> reportWrongTokenException(targetType: KotlinType, expectedToken: CirJsonToken, message: String?): T {
        throw wrongTokenException(parser!!, targetType, expectedToken, message)
    }

    /**
     * Method for deserializers to call when the token encountered was of type different from what **should** be seen at
     * that position, usually within a sequence of expected tokens. Note that this method will throw a
     * [DatabindException] and no recovery is attempted (via [DeserializationProblemHandler], as problem is considered
     * to be difficult to recover from, in general.
     */
    @Throws(DatabindException::class)
    open fun <T> reportWrongTokenException(targetType: KClass<*>, expectedToken: CirJsonToken, message: String?): T {
        throw wrongTokenException(parser!!, targetType, expectedToken, message)
    }

    @Throws(DatabindException::class)
    open fun <T> reportUnresolvedObjectId(oldReader: ObjectIdReader, bean: Any): T {
        return reportInputMismatch(oldReader.idProperty!!,
                "No Object Id found for an instance of ${bean.className}, to assign to property '${oldReader.propertyName}'")
    }

    /**
     * Helper method used to indicate a problem with input in cases where more specific `reportXxx()` method was not
     * available.
     */
    @Throws(DatabindException::class)
    open fun <T> reportInputMismatch(source: ValueDeserializer<*>, message: String?): T {
        throw MismatchedInputException.from(parser, source.handledType(), message)
    }

    /**
     * Helper method used to indicate a problem with input in cases where more specific `reportXxx()` method was not
     * available.
     */
    @Throws(DatabindException::class)
    open fun <T> reportInputMismatch(targetType: KClass<*>, message: String?): T {
        throw MismatchedInputException.from(parser, targetType, message)
    }

    /**
     * Helper method used to indicate a problem with input in cases where more specific `reportXxx()` method was not
     * available.
     */
    @Throws(DatabindException::class)
    open fun <T> reportInputMismatch(targetType: KotlinType, message: String?): T {
        throw MismatchedInputException.from(parser, targetType, message)
    }

    /**
     * Helper method used to indicate a problem with input in cases where more specific `reportXxx()` method was not
     * available.
     */
    @Throws(DatabindException::class)
    open fun <T> reportInputMismatch(property: BeanProperty?, message: String?): T {
        val type = property?.type
        val e = MismatchedInputException.from(parser, type, message)
        val member = property?.member ?: throw e
        e.prependPath(member.declaringClass, property.name)
        throw e
    }

    /**
     * Helper method used to indicate a problem with input in cases where more specific `reportXxx()` method was not
     * available.
     */
    @Throws(DatabindException::class)
    open fun <T> reportPropertyInputMismatch(targetType: KClass<*>, propertyName: String?, message: String?): T {
        val e = MismatchedInputException.from(parser, targetType, message)
        propertyName ?: throw e
        e.prependPath(targetType, propertyName)
        throw e
    }

    /**
     * Helper method used to indicate a problem with input in cases where more specific `reportXxx()` method was not
     * available.
     */
    @Throws(DatabindException::class)
    open fun <T> reportPropertyInputMismatch(targetType: KotlinType, propertyName: String?, message: String?): T {
        return reportPropertyInputMismatch(targetType.rawClass, propertyName, message)
    }

    /**
     * Helper method used to indicate a problem with input in cases where specific input coercion was not allowed.
     */
    @Throws(DatabindException::class)
    open fun <T> reportPropertyInputMismatch(source: ValueDeserializer<*>, targetType: KClass<*>, inputValue: Any,
            message: String?): T {
        throw InvalidFormatException.from(parser, message, inputValue, targetType)
    }

    @Throws(DatabindException::class)
    open fun <T> reportTrailingTokens(targetType: KClass<*>, parser: CirJsonParser, trailingToken: CirJsonToken): T {
        throw MismatchedInputException.from(parser, targetType,
                "Trailing token (of type $trailingToken) found after value (bound as ${targetType.name}): not allowed as per `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`")
    }

    /*
     *******************************************************************************************************************
     * Methods for problem reporting, in cases where recovery is not considered possible: POJO definition problem
     *******************************************************************************************************************
     */

    /**
     * Helper method called to indicate problem in POJO (serialization) definitions or settings regarding specific type,
     * unrelated to actual CirJSON content to map. Default behavior is to construct and throw a [DatabindException].
     */
    @Throws(DatabindException::class)
    override fun <T> reportBadTypeDefinition(bean: BeanDescription, message: String?): T {
        val beanDescription = bean.beanClass.name
        val realMessage = "Invalid type definition for type $beanDescription: $message"
        throw InvalidDefinitionException.from(myParser, realMessage, bean, null)
    }

    /**
     * Helper method called to indicate problem in POJO (serialization) definitions or settings regarding specific
     * property (of a type), unrelated to actual CirJSON content to map. Default behavior is to construct and throw a
     * [DatabindException].
     */
    @Throws(DatabindException::class)
    fun <T> reportBadPropertyDefinition(bean: BeanDescription, property: BeanPropertyDefinition, message: String?): T {
        val realMessage =
                "Invalid type definition for property ${property.name} (of type ${bean.beanClass.name}): $message"
        throw InvalidDefinitionException.from(parser, realMessage, bean, property)
    }

    @Throws(DatabindException::class)
    override fun <T> reportBadDefinition(type: KotlinType, message: String?): T {
        throw InvalidDefinitionException.from(myParser, message, type)
    }

    /*
     *******************************************************************************************************************
     * Methods for constructing semantic exceptions; usually not to be called directly, call `handleXxx()` instead
     *******************************************************************************************************************
     */

    /**
     * Helper method for constructing [DatabindException] to indicate that the token encountered was of type different
     * from what **should** be seen at that position, usually within a sequence of expected tokens. Note that most of
     * the time this method should NOT be directly called; instead, [reportWrongTokenException] should be called and
     * will call this method as necessary.
     */
    open fun wrongTokenException(parser: CirJsonParser?, targetType: KotlinType, expectedToken: CirJsonToken?,
            extra: String?): DatabindException {
        val message =
                "Unexpected token (`CirJJsonToken.${parser?.currentToken()}`), expected `CirJJsonToken.$expectedToken`"
        return MismatchedInputException.from(parser, targetType, colonConcat(message, extra))
    }

    open fun wrongTokenException(parser: CirJsonParser?, targetType: KClass<*>, expectedToken: CirJsonToken?,
            extra: String?): DatabindException {
        val message =
                "Unexpected token (`CirJJsonToken.${parser?.currentToken()}`), expected `CirJJsonToken.$expectedToken`"
        return MismatchedInputException.from(parser, targetType, colonConcat(message, extra))
    }

    /**
     * Helper method for constructing exception to indicate that given CirJSON Object field name was not in format to be
     * able to deserialize specified key type. Note that most of the time this method should NOT be called; instead,
     * [handleWeirdKey] should be called which will call this method if necessary.
     */
    open fun weirdKeyException(keyClass: KClass<*>, keyValue: String, message: String?): DatabindException {
        return InvalidFormatException.from(myParser,
                "Cannot deserialize Map key of type ${keyClass.name} from String ${quotedString(keyValue)}: $message",
                keyValue, keyClass)
    }

    /**
     * Helper method for constructing exception to indicate that input CirJSON String was not suitable for deserializing
     * into given target type. Note that most of the time this method should NOT be called; instead,
     * [handleWeirdStringValue] should be called which will call this method if necessary.
     *
     * @param value String value from input being deserialized
     * 
     * @param instantiatedClass Type that String should be deserialized into
     * 
     * @param messageBase Message that describes specific problem
     */
    open fun weirdStringException(value: String, instantiatedClass: KClass<*>,
            messageBase: String?): DatabindException {
        val message = "Cannot deserialize value of type ${instantiatedClass.name} from String ${
            quotedString(value)
        }: $messageBase"
        return InvalidFormatException.from(myParser, message, value, instantiatedClass)
    }

    /**
     * Helper method for constructing exception to indicate that input CirJSON Number was not suitable for deserializing
     * into given target type. Note that most of the time this method should NOT be called; instead,
     * [handleWeirdNumberValue] should be called which will call this method if necessary.
     */
    open fun weirdNumberException(value: Number, instantiatedClass: KClass<*>,
            messageBase: String?): DatabindException {
        val message = "Cannot deserialize value of type ${instantiatedClass.name} from number $value: $messageBase"
        return InvalidFormatException.from(myParser, message, value, instantiatedClass)
    }

    /**
     * Helper method for constructing exception to indicate that input CirJSON token of type "native value" (see
     * [CirJsonToken.VALUE_EMBEDDED_OBJECT]) is of incompatible type (and there is no delegating creator or such to use)
     * and can not be used to construct value of specified type (usually POJO). Note that most of the time this method
     * should NOT be called; instead, [handleWeirdNativeValue] should be called which will call this method
     */
    open fun weirdNativeValueException(value: Any, instantiatedClass: KClass<*>): DatabindException {
        return InvalidFormatException.from(myParser,
                "Cannot deserialize value of type ${instantiatedClass.name} from native value (`CirJsonToken.VALUE_EMBEDDED_OBJECT`) of type ${value.className}: incompatible types",
                value, instantiatedClass)
    }

    /**
     * Helper method for constructing instantiation exception for specified type, to indicate problem with physically
     * constructing instance of specified class (missing constructor, exception from constructor)
     * 
     * Note that most of the time this method should NOT be called directly; instead, [handleInstantiationProblem]
     * should be called which will call this method if necessary.
     */
    open fun instantiationException(instantiatedClass: KClass<*>, cause: Throwable?): DatabindException {
        val exceptionMessage = if (cause == null) {
            "N/A"
        } else {
            cause.exceptionMessage() ?: cause::class.name
        }

        val message = "Cannot construct instance of ${instantiatedClass.name}, problem: $exceptionMessage"
        return ValueInstantiationException.from(myParser, message, constructType(instantiatedClass)!!, cause)
    }

    /**
     * Helper method for constructing instantiation exception for specified type, to indicate problem with physically
     * constructing instance of specified class (missing constructor, exception from constructor)
     * 
     * Note that most of the time this method should NOT be called directly; instead, [handleInstantiationProblem]
     * should be called which will call this method if necessary.
     */
    open fun instantiationException(instantiatedClass: KClass<*>, message: String?): DatabindException {
        return ValueInstantiationException.from(myParser,
                "Cannot construct instance of ${instantiatedClass.name}: $message", constructType(instantiatedClass)!!)
    }

    override fun invalidTypeIdException(baseType: KotlinType, typeId: String?,
            extraDescription: String): DatabindException {
        val message = "Could not resolve type id '$typeId' as a subtype of ${baseType.typeDescription}"
        return InvalidTypeIdException.from(myParser, colonConcat(message, extraDescription), baseType, typeId)
    }

    open fun missingTypeIdException(baseType: KotlinType, extraDescription: String): DatabindException {
        val message = "Could not resolve subtype of $baseType"
        return InvalidTypeIdException.from(myParser, colonConcat(message, extraDescription), baseType, null)
    }

    /*
     *******************************************************************************************************************
     * Other internal methods
     *******************************************************************************************************************
     */

    /**
     * Helper method to get a non-shared instance of [DateFormat] with default configuration; instance is lazily
     * constructed, reused within same instance of context (that is, within same life-cycle of `readValue()` from mapper
     * or reader). Reuse is safe since access will not occur from multiple threads (unless caller somehow manages to
     * share context objects across threads which is not supported).
     */
    protected open val dateFormat: DateFormat
        get() = myDateFormat ?: myConfig.dateFormat.also { myDateFormat = it.clone() as DateFormat }

    /**
     * Helper method for constructing description like "Object value" given
     * [CirJsonToken] encountered.
     */
    protected open fun shapeForToken(token: CirJsonToken?): String {
        return CirJsonToken.valueDescFor(token)
    }

}