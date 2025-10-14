package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Base64Variant
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverBuilder
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverProvider
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.isJdkClass
import org.cirjson.cirjackson.databind.util.isRecordType
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass

abstract class MapperConfigBase<CFG : ConfigFeature, T : MapperConfigBase<CFG, T>> : MapperConfig<T> {

    /*
     *******************************************************************************************************************
     * Immutable config, factories
     *******************************************************************************************************************
     */

    /**
     * Specific factory used for creating [KotlinType] instances; needed to allow modules to add more custom type
     * handling (mostly to support types of non-Java JVM languages)
     */
    protected val myTypeFactory: TypeFactory

    protected val myClassIntrospector: ClassIntrospector

    protected val myTypeResolverProvider: TypeResolverProvider

    /**
     * Registered concrete subtypes that can be used instead of (or in addition to) ones declared using annotations.
     *
     * Note that instances are stateful and as such may need to be copied, and may NOT be demoted down to
     * [BaseSettings].
     */
    protected val mySubtypeResolver: SubtypeResolver

    /**
     * Mix-in annotation mappings to use, if any.
     */
    protected val myMixIns: MixInHandler

    /*
     *******************************************************************************************************************
     * Immutable config, factories
     *******************************************************************************************************************
     */

    /**
     * Explicitly defined root name to use, if any. If it is empty String, will disable root-name wrapping. If `null`,
     * will use defaults
     */
    protected val myRootName: PropertyName?

    /**
     * View to use for filtering out properties to serialize or deserialize. `null` if none (will also be assigned
     * `null` if `Any::class` is defined), meaning that all properties are to be included.
     */
    protected val myView: KClass<*>?

    /**
     * Contextual attributes accessible (get and set) during processing, on a per-call basis.
     */
    protected val myAttributes: ContextAttributes

    /**
     * Simple cache used for finding out possible root name for root name wrapping.
     *
     * Note that instances are stateful (for caching) and as such may need to be copied, and may NOT be demoted down to
     * [BaseSettings].
     */
    protected val myRootNames: RootNameLookup

    /**
     * Configuration overrides to apply, keyed by type of property.
     */
    protected val myConfigOverrides: ConfigOverrides

    /**
     * Set of [DatatypeFeatures][DatatypeFeature] enabled.
     */
    protected val myDatatypeFeatures: DatatypeFeatures

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Constructor used when creating a new instance (compared to that of creating fluent copies)
     */
    protected constructor(builder: MapperBuilder<*, *>, mapperFeatures: Long, typeFactory: TypeFactory,
            classIntrospector: ClassIntrospector, mixins: MixInHandler, subtypeResolver: SubtypeResolver,
            configOverrides: ConfigOverrides, defaultAttributes: ContextAttributes, rootNames: RootNameLookup) : super(
            builder.baseSettings(), mapperFeatures) {
        myTypeFactory = typeFactory
        myClassIntrospector = classIntrospector
        myTypeResolverProvider = builder.typeResolverProvider()
        mySubtypeResolver = subtypeResolver
        myMixIns = mixins
        myRootNames = rootNames
        myRootName = null
        myView = null
        myAttributes = defaultAttributes
        myConfigOverrides = configOverrides
        myDatatypeFeatures = builder.datatypeFeatures()
    }

    protected constructor(source: MapperConfigBase<CFG, T>) : super(source) {
        myTypeFactory = source.myTypeFactory
        myClassIntrospector = source.myClassIntrospector
        myTypeResolverProvider = source.myTypeResolverProvider
        mySubtypeResolver = source.mySubtypeResolver
        myMixIns = source.myMixIns
        myRootNames = source.myRootNames
        myRootName = source.myRootName
        myView = source.myView
        myAttributes = source.myAttributes
        myConfigOverrides = source.myConfigOverrides
        myDatatypeFeatures = source.myDatatypeFeatures
    }

    protected constructor(source: MapperConfigBase<CFG, T>, base: BaseSettings) : super(source, base) {
        myTypeFactory = source.myTypeFactory
        myClassIntrospector = source.myClassIntrospector
        myTypeResolverProvider = source.myTypeResolverProvider
        mySubtypeResolver = source.mySubtypeResolver
        myMixIns = source.myMixIns
        myRootNames = source.myRootNames
        myRootName = source.myRootName
        myView = source.myView
        myAttributes = source.myAttributes
        myConfigOverrides = source.myConfigOverrides
        myDatatypeFeatures = source.myDatatypeFeatures
    }

    protected constructor(source: MapperConfigBase<CFG, T>, rootName: PropertyName) : super(source) {
        myTypeFactory = source.myTypeFactory
        myClassIntrospector = source.myClassIntrospector
        myTypeResolverProvider = source.myTypeResolverProvider
        mySubtypeResolver = source.mySubtypeResolver
        myMixIns = source.myMixIns
        myRootNames = source.myRootNames
        myRootName = rootName
        myView = source.myView
        myAttributes = source.myAttributes
        myConfigOverrides = source.myConfigOverrides
        myDatatypeFeatures = source.myDatatypeFeatures
    }

    protected constructor(source: MapperConfigBase<CFG, T>, view: KClass<*>?) : super(source) {
        myTypeFactory = source.myTypeFactory
        myClassIntrospector = source.myClassIntrospector
        myTypeResolverProvider = source.myTypeResolverProvider
        mySubtypeResolver = source.mySubtypeResolver
        myMixIns = source.myMixIns
        myRootNames = source.myRootNames
        myRootName = source.myRootName
        myView = view
        myAttributes = source.myAttributes
        myConfigOverrides = source.myConfigOverrides
        myDatatypeFeatures = source.myDatatypeFeatures
    }

    protected constructor(source: MapperConfigBase<CFG, T>, attributes: ContextAttributes) : super(source) {
        myTypeFactory = source.myTypeFactory
        myClassIntrospector = source.myClassIntrospector
        myTypeResolverProvider = source.myTypeResolverProvider
        mySubtypeResolver = source.mySubtypeResolver
        myMixIns = source.myMixIns
        myRootNames = source.myRootNames
        myRootName = source.myRootName
        myView = source.myView
        myAttributes = attributes
        myConfigOverrides = source.myConfigOverrides
        myDatatypeFeatures = source.myDatatypeFeatures
    }

    protected constructor(source: MapperConfigBase<CFG, T>, datatypeFeatures: DatatypeFeatures) : super(source) {
        myTypeFactory = source.myTypeFactory
        myClassIntrospector = source.myClassIntrospector
        myTypeResolverProvider = source.myTypeResolverProvider
        mySubtypeResolver = source.mySubtypeResolver
        myMixIns = source.myMixIns
        myRootNames = source.myRootNames
        myRootName = source.myRootName
        myView = source.myView
        myAttributes = source.myAttributes
        myConfigOverrides = source.myConfigOverrides
        myDatatypeFeatures = datatypeFeatures
    }

    /*
     *******************************************************************************************************************
     * Abstract fluent factory methods to be implemented by subtypes
     *******************************************************************************************************************
     */

    protected abstract fun withBase(newBase: BaseSettings): T

    protected abstract fun with(datatypeFeatures: DatatypeFeatures): T

    protected open fun datatypeFeatures(): DatatypeFeatures {
        return myDatatypeFeatures
    }

    /*
     *******************************************************************************************************************
     * Additional shared fluent factory methods; DatatypeFeatures
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will return a configuration object instance with specified feature enabled: this may
     * be `this` instance (if no changes effected), or a newly constructed instance.
     */
    fun with(feature: DatatypeFeature): T {
        return with(datatypeFeatures().with(feature))
    }

    /**
     * Fluent factory method that will return a configuration object instance with specified features enabled: this may
     * be `this` instance (if no changes effected), or a newly constructed instance.
     */
    fun withFeatures(vararg features: DatatypeFeature): T {
        return with(datatypeFeatures().withFeatures(*features))
    }

    /**
     * Fluent factory method that will return a configuration object instance with specified feature disabled: this may
     * be `this` instance (if no changes effected), or a newly constructed instance.
     */
    fun without(feature: DatatypeFeature): T {
        return with(datatypeFeatures().without(feature))
    }

    /**
     * Fluent factory method that will return a configuration object instance with specified features disabled: this may
     * be `this` instance (if no changes effected), or a newly constructed instance.
     */
    fun withoutFeatures(vararg features: DatatypeFeature): T {
        return with(datatypeFeatures().withoutFeatures(*features))
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun with(feature: DatatypeFeature, state: Boolean): T {
        val features = datatypeFeatures()
        return with(if (state) features.with(feature) else features.without(feature))
    }

    /*
     *******************************************************************************************************************
     * Additional shared fluent factory methods; attributes
     *******************************************************************************************************************
     */

    /**
     * Method for constructing an instance that has specified contextual attributes.
     */
    abstract fun with(attributes: ContextAttributes): T

    /**
     * Method for constructing an instance that has only specified attributes, removing any attributes that exist before
     * the call.
     */
    open fun withAttributes(attributes: Map<*, *>): T {
        return with(this.attributes.withSharedAttributes(attributes))
    }

    /**
     * Method for constructing an instance that has the specified value for attribute for the given key.
     */
    open fun withAttribute(key: Any, value: Any): T {
        return with(this.attributes.withSharedAttribute(key, value))
    }

    /**
     * Method for constructing an instance that has no value for attribute for the given key.
     */
    open fun withoutAttribute(key: Any): T {
        return with(this.attributes.withoutSharedAttribute(key))
    }

    /*
     *******************************************************************************************************************
     * Additional shared fluent factory methods; factories
     *******************************************************************************************************************
     */

    /**
     * Method for constructing and returning a new instance with different [TypeResolverBuilder] to use.
     */
    fun with(typeResolverBuilder: TypeResolverBuilder<*>?): T {
        return withBase(myBase.with(typeResolverBuilder))
    }

    fun with(provider: CacheProvider): T {
        return withBase(myBase.with(provider))
    }

    /*
     *******************************************************************************************************************
     * Additional shared fluent factory methods; factories
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct a new instance with specified [CirJsonNodeFactory].
     */
    fun with(factory: CirJsonNodeFactory): T {
        return withBase(myBase.with(factory))
    }

    /**
     * Method for constructing and returning a new instance with different default [Base64Variant] to use with
     * base64-encoded binary values.
     */
    fun with(base64: Base64Variant): T {
        return withBase(myBase.with(base64))
    }

    /**
     * Method for constructing and returning a new instance with different [DateFormat] to use.
     *
     * NOTE: non-final since `SerializationConfig` needs to override this
     */
    open fun with(dateFormat: DateFormat): T {
        return withBase(myBase.with(dateFormat))
    }

    /**
     * Method for constructing and returning a new instance with different default [Locale] to use for formatting.
     */
    fun with(locale: Locale): T {
        return withBase(myBase.with(locale))
    }

    /**
     * Method for constructing and returning a new instance with different default [TimeZone] to use for formatting of
     * date values.
     */
    fun with(timeZone: TimeZone?): T {
        return withBase(myBase.with(timeZone))
    }

    /**
     * Method for constructing and returning a new instance with different root name to use (none, if `null`).
     *
     * Note that when a root name is set to a non-Empty String, this will automatically force use of root element
     * wrapping with the given name. If empty String passed, will disable root name wrapping; and if `null` used, will
     * instead use `SerializationFeature` to determine if to use wrapping, and annotation (or default name) for actual
     * root name to use.
     *
     * @param rootName to use: if `null`, means "use default" (clear setting); if empty String (`""`) means that no root
     * name wrapping is used; otherwise defines root name to use.
     */
    abstract fun withRootName(rootName: PropertyName?): T

    open fun withRootName(rootName: String?): T {
        return withRootName(rootName?.let { PropertyName.construct(it) })
    }

    /**
     * Method for constructing and returning a new instance with different view to use.
     */
    abstract fun withView(view: KClass<*>?): T

    /*
     *******************************************************************************************************************
     * Simple factory access, related
     *******************************************************************************************************************
     */

    final override val typeFactory: TypeFactory
        get() = myTypeFactory

    override fun classIntrospectorInstance(): ClassIntrospector {
        return myClassIntrospector.forOperation(this)
    }

    override val typeResolverProvider: TypeResolverProvider
        get() = myTypeResolverProvider

    /**
     * Accessor for the object used for finding out all reachable subtypes for supertypes; needed when a logical type
     * name is used instead of class name (or custom scheme).
     */
    final override val subtypeResolver: SubtypeResolver
        get() = mySubtypeResolver

    final override fun constructType(clazz: KClass<*>): KotlinType {
        return myTypeFactory.constructType(clazz.java)
    }

    final override fun constructType(valueTypeReference: TypeReference<*>): KotlinType {
        return myTypeFactory.constructType(valueTypeReference.type)
    }

    /*
     *******************************************************************************************************************
     * Simple feature access
     *******************************************************************************************************************
     */

    final override fun isEnabled(feature: DatatypeFeature): Boolean {
        return myDatatypeFeatures.isEnabled(feature)
    }

    final override val datatypeFeatures: DatatypeFeatures
        get() = myDatatypeFeatures

    /*
     *******************************************************************************************************************
     * Simple config property access
     *******************************************************************************************************************
     */

    val fullRootName: PropertyName?
        get() = myRootName

    final override val activeView: KClass<*>?
        get() = myView

    final override val attributes: ContextAttributes
        get() = myAttributes

    /*
     *******************************************************************************************************************
     * Configuration access; default/overrides
     *******************************************************************************************************************
     */

    final override fun getConfigOverride(type: KClass<*>): ConfigOverride {
        return myConfigOverrides.findOverride(type) ?: ConfigOverride.empty()
    }

    final override fun findConfigOverride(type: KClass<*>): ConfigOverride? {
        return myConfigOverrides.findOverride(type)
    }

    final override val defaultPropertyInclusion: CirJsonInclude.Value?
        get() = myConfigOverrides.defaultInclusion

    final override fun getDefaultPropertyInclusion(baseType: KClass<*>): CirJsonInclude.Value? {
        val value = getConfigOverride(baseType).include
        return defaultPropertyInclusion?.withOverrides(value) ?: value
    }

    final override fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>): CirJsonInclude.Value? {
        val value = getConfigOverride(baseType).include
        return getDefaultPropertyInclusion(baseType)?.withOverrides(value) ?: value
    }

    final override fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value {
        return myConfigOverrides.findFormatDefaults(baseType)
    }

    final override fun getDefaultPropertyIgnorals(baseType: KClass<*>): CirJsonIgnoreProperties.Value? {
        return myConfigOverrides.findOverride(baseType)?.ignorals
    }

    final override fun getDefaultPropertyIgnorals(baseType: KClass<*>,
            actualClass: AnnotatedClass): CirJsonIgnoreProperties.Value? {
        val base = annotationIntrospector?.findPropertyIgnoralByName(this, actualClass)
        val overrides = getDefaultPropertyIgnorals(baseType)
        return CirJsonIgnoreProperties.Value.merge(base, overrides)
    }

    final override fun getDefaultPropertyInclusions(baseType: KClass<*>,
            actualClass: AnnotatedClass): CirJsonIncludeProperties.Value? {
        return annotationIntrospector?.findPropertyInclusionByName(this, actualClass)
    }

    final override val defaultVisibilityChecker: VisibilityChecker
        get() = myConfigOverrides.defaultVisibility

    final override fun getDefaultVisibilityChecker(baseType: KClass<*>,
            actualClass: AnnotatedClass): VisibilityChecker {
        var visibilityChecker = if (baseType.isJdkClass) {
            VisibilityChecker.ALL_PUBLIC
        } else if (baseType.isRecordType) {
            myConfigOverrides.defaultRecordVisibility
        } else {
            defaultVisibilityChecker
        }

        val introspector = annotationIntrospector

        if (introspector != null) {
            visibilityChecker = introspector.findAutoDetectVisibility(this, actualClass, visibilityChecker)
        }

        val overrides = myConfigOverrides.findOverride(baseType) ?: return visibilityChecker
        return visibilityChecker.withOverrides(overrides.visibility)
    }

    final override val defaultNullHandling: CirJsonSetter.Value
        get() = myConfigOverrides.defaultNullHandling

    override val defaultMergeable: Boolean?
        get() = myConfigOverrides.defaultMergeable

    override fun getDefaultMergeable(baseType: KClass<*>): Boolean? {
        return myConfigOverrides.findOverride(baseType)?.mergeable ?: myConfigOverrides.defaultMergeable
    }

    /*
     *******************************************************************************************************************
     * Other config access
     *******************************************************************************************************************
     */

    override fun findRootName(context: DatabindContext, rootType: KotlinType): PropertyName {
        return myRootName ?: myRootNames.findRootName(context, rootType)
    }

    override fun findRootName(context: DatabindContext, rawRootType: KClass<*>): PropertyName {
        return myRootName ?: myRootNames.findRootName(context, rawRootType)
    }

    /*
     *******************************************************************************************************************
     * MixInResolver implementation
     *******************************************************************************************************************
     */

    /**
     * Method that will check if there are "mix-in" classes (with mix-in annotations) for given class
     */
    final override fun findMixInClassFor(kClass: KClass<*>): KClass<*>? {
        return myMixIns.findMixInClassFor(kClass)
    }

    override fun hasMixIns(): Boolean {
        return myMixIns.hasMixIns()
    }

    override fun snapshot(): MixInResolver {
        throw UnsupportedOperationException()
    }

    /**
     * Test-only method -- does not reflect the possibly open-ended set that external mix-in resolver might provide.
     */
    fun mixInCount(): Int {
        return myMixIns.localSize()
    }

}