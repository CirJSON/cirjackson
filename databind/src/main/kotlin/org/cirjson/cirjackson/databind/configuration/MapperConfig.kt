package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonIgnoreProperties
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.annotations.CirJsonIncludeProperties
import org.cirjson.cirjackson.annotations.CirJsonSetter
import org.cirjson.cirjackson.core.Base64Variant
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.*
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.createInstance
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass

/**
 * Interface that defines functionality accessible through both serialization and deserialization configuration objects;
 * accessors to mode-independent configuration settings and such. In addition, shared features are defined in
 * [MapperFeature].
 *
 * Small part of implementation is included here by aggregating [BaseSettings] instance that contains configuration that
 * is shared between different types of instances.
 *
 * @property myBase Immutable container object for simple configuration settings.
 *
 * @property myBase Set of shared mapper features enabled.
 */
abstract class MapperConfig<T : MapperConfig<T>> protected constructor(protected val myBase: BaseSettings,
        protected val myMapperFeatures: Long) : MixInResolver {

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    protected constructor(source: MapperConfig<T>, base: BaseSettings) : this(base, source.myMapperFeatures)

    protected constructor(source: MapperConfig<T>) : this(source.myBase, source.myMapperFeatures)

    /*
     *******************************************************************************************************************
     * Configuration: simple features
     *******************************************************************************************************************
     */

    /**
     * Accessor for simple mapper features (which are shared for serialization, deserialization)
     */
    fun isEnabled(feature: MapperFeature): Boolean {
        return myMapperFeatures and feature.longMask != 0L
    }

    /**
     * Accessor for checking whether give [DatatypeFeature] is enabled or not.
     *
     * @param feature Feature to check
     *
     * @return `true` if the feature is enabled; `false` otherwise
     */
    abstract fun isEnabled(feature: DatatypeFeature): Boolean

    abstract val datatypeFeatures: DatatypeFeatures

    /**
     * Accessor for determining whether annotation processing is enabled or not (typically, default settings are that it
     * is enabled; must explicitly disable).
     *
     * @return `true` if annotation processing is enabled; `false` if not
     */
    val isAnnotationProcessingEnabled: Boolean
        get() = isEnabled(MapperFeature.USE_ANNOTATIONS)

    /**
     * Accessor for determining whether it is ok to try to force override of access modifiers to be able to get or set
     * values of non-public Methods, Fields; to invoke non-public Constructors, Methods; or to instantiate non-public
     * Classes. By default, this is enabled, but on some platforms it needs to be prevented since if this violates the
     * security constraints and causes failures.
     *
     * @return `true` if access modifier overriding is allowed (and may be done for any Field, Method, Constructor or
     * Class); `false` to prevent any attempts to override.
     */
    fun canOverrideAccessModifiers(): Boolean {
        return isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
    }

    /**
     * Accessor for checking whether default settings for property handling indicate that properties should be
     * alphabetically ordered or not.
     */
    fun shouldSortPropertiesAlphabetically(): Boolean {
        return isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
    }

    /**
     * Accessor for checking whether configuration indicates that "root wrapping" (use of an extra property/name pair at
     * root level) is expected or not.
     */
    abstract fun useRootWrapping(): Boolean

    /*
     *******************************************************************************************************************
     * Configuration: factory methods
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a specialized textual object that can typically be serialized faster than basic [String]
     * (depending on escaping needed if any, char-to-byte encoding if needed).
     *
     * @param string Text to represent
     *
     * @return Optimized text object constructed
     */
    fun compileString(string: String): SerializableString {
        return SerializedString(string)
    }

    /*
     *******************************************************************************************************************
     * Configuration: introspectors, mix-ins
     *******************************************************************************************************************
     */

    /**
     * Accessor for getting a new [ClassIntrospector] instance initialized for per-call usage (with possible local
     * caching)
     */
    abstract fun classIntrospectorInstance(): ClassIntrospector

    /**
     * Method for getting [AnnotationIntrospector] configured
     * to introspect annotation values used for configuration.
     *
     * Non-final since it is actually overridden by subclasses (for now?)
     */
    open val annotationIntrospector: AnnotationIntrospector?
        get() {
            if (isEnabled(MapperFeature.USE_ANNOTATIONS)) {
                return myBase.annotationIntrospector
            }

            return NoOpAnnotationIntrospector.INSTANCE
        }

    val propertyNamingStrategy: PropertyNamingStrategy?
        get() = myBase.propertyNamingStrategy

    val accessorNaming: AccessorNamingStrategy.Provider
        get() = myBase.accessorNaming

    val handlerInstantiator: HandlerInstantiator?
        get() = myBase.handlerInstantiator

    /*
     *******************************************************************************************************************
     * Configuration: type and subtype handling
     *******************************************************************************************************************
     */

    /**
     * Method called to locate a type info handler for types that do not have one explicitly declared via annotations
     * (or other configuration). If such a default handler is configured, it is returned; otherwise `null` is returned.
     */
    fun getDefaultTyper(baseType: KotlinType): TypeResolverBuilder<*>? {
        return myBase.defaultTyper
    }

    abstract val typeResolverProvider: TypeResolverProvider

    abstract val subtypeResolver: SubtypeResolver

    abstract val typeFactory: TypeFactory

    val polymorphicTypeValidator: PolymorphicTypeValidator
        get() = myBase.polymorphicTypeValidator

    /**
     * Helper method that will construct [KotlinType] for given raw class. This is a simple short-cut for:
     * ```
     * typeFactory.constructType(clazz);
     * ```
     */
    abstract fun constructType(clazz: KClass<*>): KotlinType

    /**
     * Helper method that will construct [KotlinType] for given type reference. This is a simple short-cut for:
     * ```
     * typeFactory.constructType(valueTypeReference);
     * ```
     */
    abstract fun constructType(valueTypeReference: TypeReference<*>): KotlinType

    /*
     *******************************************************************************************************************
     * Configuration: default settings with per-type overrides
     *******************************************************************************************************************
     */

    /**
     * Accessor for finding [ConfigOverride] to use for properties of the given type, if any exist; or return `null` if
     * not.
     *
     * Note that only directly associated override is found; no type hierarchy traversal is performed.
     *
     * @return Override object to use for the type, if defined; `null` if none.
     */
    abstract fun findConfigOverride(type: KClass<*>): ConfigOverride?

    /**
     * Accessor for finding [ConfigOverride] to use for properties of the given type, if any exist; or if none, return
     * an immutable "empty" instance with no overrides.
     *
     * Note that only directly associated override is found; no type hierarchy traversal is performed.
     *
     * @return Override object to use for the type, never `null` (but may be empty)
     */
    abstract fun getConfigOverride(type: KClass<*>): ConfigOverride

    /**
     * Accessor for default property inclusion to use for serialization, used unless overridden by per-type or
     * per-property overrides.
     */
    abstract val defaultPropertyInclusion: CirJsonInclude.Value

    /**
     * Accessor for default property inclusion to use for serialization, considering possible per-type override for
     * given base type.
     *
     * NOTE: if no override found, defaults to value returned by [defaultPropertyInclusion].
     */
    abstract fun getDefaultPropertyInclusion(baseType: KClass<*>): CirJsonInclude.Value

    /**
     * Accessor for default property inclusion to use for serialization, considering possible per-type override for
     * given base type; but if none found, returning given `defaultInclusion`
     *
     * @param defaultInclusion Inclusion setting to return if no overrides found.
     */
    open fun getDefaultPropertyInclusion(baseType: KClass<*>,
            defaultInclusion: CirJsonInclude.Value): CirJsonInclude.Value {
        return getConfigOverride(baseType).include ?: defaultInclusion
    }

    /**
     * Accessor for default property inclusion to use for serialization, considering possible per-type override for
     * given base type and possible per-type override for the given property type.
     *
     * NOTE: if no override found, defaults to value returned by [defaultPropertyInclusion].
     *
     * @param baseType Type of the instance containing the targeted property.
     *
     * @param propertyType Type of the property to look up inclusion setting for.
     */
    abstract fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>): CirJsonInclude.Value

    /**
     * Accessor for default property inclusion to use for serialization, considering possible per-type override for
     * given base type and possible per-type override for the given property type; but if none found, returning given
     * `defaultInclusion`
     *
     * @param baseType Type of the instance containing the targeted property.
     *
     * @param propertyType Type of the property to look up inclusion setting for.
     *
     * @param defaultInclusion Inclusion setting to return if no overrides found.
     */
    open fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>,
            defaultInclusion: CirJsonInclude.Value): CirJsonInclude.Value {
        val baseOverride = getConfigOverride(baseType).include
        val propOverride = getConfigOverride(baseType).includeAsProperty
        return CirJsonInclude.Value.mergeAll(defaultInclusion, baseOverride, propOverride)!!
    }

    /**
     * Accessor for default format settings to use for serialization (and, to a degree deserialization), considering
     * baseline settings and per-type defaults for given base type (if any).
     *
     * @return (non-`null`) Format settings to use, possibly `CirJsonFormat.Value.EMPTY`
     */
    abstract fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value

    /**
     * Accessor for default property ignorals to use, if any, for given base type, based on config overrides settings
     * (see [findConfigOverride]).
     */
    abstract fun getDefaultPropertyIgnorals(baseType: KClass<*>): CirJsonIgnoreProperties.Value?

    /**
     * Helper method that may be called to see if there are property ignoral definitions from annotations (via
     * [AnnotatedClass]) or through "config overrides". If both exist, config overrides have precedence over class
     * annotations.
     */
    abstract fun getDefaultPropertyIgnorals(baseType: KClass<*>,
            actualClass: AnnotatedClass): CirJsonIgnoreProperties.Value?

    /**
     * Helper method that may be called to see if there are property inclusion definitions from annotations (via
     * [AnnotatedClass]).
     */
    abstract fun getDefaultPropertyInclusions(baseType: KClass<*>,
            actualClass: AnnotatedClass): CirJsonIncludeProperties.Value?

    /**
     * Accessor for the object used for determining whether specific property elements (method, constructors, fields)
     * can be auto-detected based on their visibility (access modifiers). Can be changed to allow different minimum
     * visibility levels for auto-detection. Note that this is the global handler; individual types (classes) can
     * further override the active checker used (using [CirJsonAutoDetect] annotation)
     */
    abstract val defaultVisibilityChecker: VisibilityChecker

    /**
     * Accessor for the object used for determining whether specific property elements (method, constructors, fields)
     * can be auto-detected based on their visibility (access modifiers). This is based on global defaults (as would be
     * returned by [defaultVisibilityChecker], but then modified by possible class annotation (see [CirJsonAutoDetect])
     * and/or per-type config override (see [ConfigOverride.visibility]).
     */
    abstract fun getDefaultVisibilityChecker(baseType: KClass<*>, actualClass: AnnotatedClass): VisibilityChecker

    /**
     * Accessor for the baseline setter info used as the global baseline, not considering possible per-type overrides.
     *
     * @return Global base settings; never `null`
     */
    abstract val defaultNullHandling: CirJsonSetter.Value

    /**
     * Accessor for the baseline merge info used as the global baseline, not considering possible per-type overrides.
     *
     * @return Global base settings, if any; `null` if none.
     */
    abstract val defaultMergeable: Boolean?

    /**
     * Accessor for the baseline merge info used for given type, including global defaults if no type-specific overrides
     * defined.
     *
     * @return Type-specific settings (if any); global defaults (same as [defaultMergeable]) otherwise, if any defined;
     * or `null` if neither defined
     */
    abstract fun getDefaultMergeable(baseType: KClass<*>): Boolean?

    /*
     *******************************************************************************************************************
     * Configuration: other
     *******************************************************************************************************************
     */

    /**
     * Method for accessing currently configured (textual) date format that will be used for reading or writing date
     * values (in case of writing, only if textual output is configured; not if dates are to be serialized as time
     * stamps).
     *
     * Note that typically [DateFormat] instances are **not thread-safe** (at least ones provided by JDK): this means
     * that calling code should clone format instance before using it.
     *
     * This method is usually only called by framework itself, since there are convenience methods available via
     * [org.cirjson.cirjackson.databind.DeserializationContext] and [org.cirjson.cirjackson.databind.SerializerProvider]
     * that take care of cloning and thread-safe reuse.
     */
    val dateFormat: DateFormat
        get() = myBase.dateFormat

    /**
     * Method for accessing the default [Locale] to use for formatting, unless overridden by local annotations.
     * Initially set to [Locale.getDefault].
     */
    val locale: Locale
        get() = myBase.locale

    /**
     * Method for accessing the default [TimeZone] to use for formatting, unless overridden by local annotations.
     * Initially set to [TimeZone.getDefault].
     */
    val timeZone: TimeZone
        get() = myBase.timeZone

    /**
     * Method for checking whether a [TimeZone] has been explicitly set for this configuring during construction of
     * `ObjectMapper` or if it still has the default timezone/offset (zero-offset, "zulu").
     *
     * @return `true` if this configuration has explicitly specified [TimeZone], or `false` if it uses the default time
     * zone
     */
    open fun hasExplicitTimeZone(): Boolean {
        return myBase.hasExplicitTimeZone()
    }

    /**
     * Accessor for finding currently active view, if any (`null` if none)
     */
    abstract val activeView: KClass<*>?

    /**
     * Accessor called during deserialization if Base64 encoded content needs to be decoded. Default version just returns
     * default CirJackson uses, which is modified-mime which does not add linefeeds (because those would have to be
     * escaped in CirJSON strings); but this can be configured on [ObjectWriter].
     */
    open val base64Variant: Base64Variant
        get() = myBase.base64Variant

    val nodeFactory: CirJsonNodeFactory
        get() = myBase.nodeFactory

    /**
     * Accessor for accessing per-instance shared (baseline/default) attribute values; these are used as the basis for
     * per-call attributes.
     */
    abstract val attributes: ContextAttributes

    abstract fun findRootName(context: DatabindContext, rootType: KotlinType): PropertyName

    abstract fun findRootName(context: DatabindContext, rawRootType: KClass<*>): PropertyName

    val cacheProvider: CacheProvider
        get() = myBase.cacheProvider

    val constructorDetector: ConstructorDetector
        get() = myBase.constructorDetector

    /*
     *******************************************************************************************************************
     * Methods for instantiating handlers
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to get an instance of `TypeResolverBuilder` of specified type.
     */
    open fun typeResolverBuilderInstance(annotated: Annotated,
            builderClass: KClass<out TypeResolverBuilder<*>>): TypeResolverBuilder<*>? {
        return handlerInstantiator?.typeResolverBuilderInstance(this, annotated, builderClass)
                ?: builderClass.createInstance(canOverrideAccessModifiers())
    }

    /**
     * Method that can be called to get an instance of `TypeIdResolver` of specified type.
     */
    open fun typeIdResolverInstance(annotated: Annotated, builderClass: KClass<out TypeIdResolver>): TypeIdResolver? {
        return handlerInstantiator?.typeIdResolverInstance(this, annotated, builderClass)
                ?: builderClass.createInstance(canOverrideAccessModifiers())
    }

}