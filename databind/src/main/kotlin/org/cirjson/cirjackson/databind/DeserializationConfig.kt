package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.FormatFeature
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.*
import org.cirjson.cirjackson.databind.deserialization.DeserializationProblemHandler
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.ArrayIterator
import org.cirjson.cirjackson.databind.util.LinkedNode
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Object that contains baseline configuration for deserialization process. An instance is owned by [ObjectMapper],
 * which passes an immutable instance to be used for deserialization process.
 * 
 * Note that instances are considered immutable and as such no copies should need to be created for sharing; all copying
 * is done with "fluent factory" methods.
 */
class DeserializationConfig : MapperConfigBase<DeserializationFeature, DeserializationConfig> {

    /*
     *******************************************************************************************************************
     * Deserialization, parser, format features
     *******************************************************************************************************************
     */

    /**
     * States of [DeserializationFeatures][DeserializationFeature] enabled.
     */
    private val myDeserializationFeatures: Int

    /**
     * States of [StreamReadFeatures][StreamReadFeature] enabled.
     */
    private val myStreamReadFeatures: Int

    /**
     * States of [FormatFeatures][FormatFeature] enabled.
     */
    private val myFormatFeatures: Int

    /*
     *******************************************************************************************************************
     * Configured helper objects
     *******************************************************************************************************************
     */

    /**
     * Linked list that contains all registered problem handlers. Implementation as front-added linked list allows for
     * sharing of the list (tail) without copying the list.
     */
    private val myProblemHandlers: LinkedNode<DeserializationProblemHandler>?

    /**
     * List of objects that may be able to resolve abstract types to concrete types. Used by functionality like "mr
     * Bean" to materialize types as needed, although it may be used for other kinds of defaulting as well.
     */
    private val myAbstractTypeResolver: Array<AbstractTypeResolver>

    /**
     * Configured coercion rules for coercions from secondary input shapes.
     */
    private val myCoercionConfigs: CoercionConfigs

    /*
     *******************************************************************************************************************
     * Lifecycle, primary constructors for new instances
     *******************************************************************************************************************
     */

    constructor(builder: MapperBuilder<*, *>, mapperFeatures: Long, deserializationFeatures: Int,
            streamReadFeatures: Int, formatReadFeatures: Int, configOverrides: ConfigOverrides,
            coercionConfigs: CoercionConfigs, typeFactory: TypeFactory, classIntrospector: ClassIntrospector,
            mixins: MixInHandler, subtypeResolver: SubtypeResolver, defaultAttributes: ContextAttributes,
            rootNames: RootNameLookup, abstractTypeResolvers: Array<AbstractTypeResolver>) : super(builder,
            mapperFeatures, typeFactory, classIntrospector, mixins, subtypeResolver, configOverrides, defaultAttributes,
            rootNames) {
        myDeserializationFeatures = deserializationFeatures
        myStreamReadFeatures = streamReadFeatures
        myFormatFeatures = formatReadFeatures
        myProblemHandlers = builder.deserializationProblemHandlers()
        myCoercionConfigs = coercionConfigs
        myAbstractTypeResolver = abstractTypeResolvers
    }

    /*
     *******************************************************************************************************************
     * Life-cycle, secondary constructors to support "mutant factories", with single property changes
     *******************************************************************************************************************
     */

    private constructor(source: DeserializationConfig, deserializationFeatures: Int, streamReadFeatures: Int,
            formatReadFeatures: Int) : super(source) {
        myDeserializationFeatures = deserializationFeatures
        myStreamReadFeatures = streamReadFeatures
        myFormatFeatures = formatReadFeatures
        myProblemHandlers = source.myProblemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = source.myAbstractTypeResolver
    }

    private constructor(source: DeserializationConfig, base: BaseSettings) : super(source, base) {
        myDeserializationFeatures = source.myDeserializationFeatures
        myStreamReadFeatures = source.myStreamReadFeatures
        myFormatFeatures = source.myFormatFeatures
        myProblemHandlers = source.myProblemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = source.myAbstractTypeResolver
    }

    private constructor(source: DeserializationConfig, problemHandlers: LinkedNode<DeserializationProblemHandler>?,
            abstractTypeResolvers: Array<AbstractTypeResolver>) : super(source) {
        myDeserializationFeatures = source.myDeserializationFeatures
        myStreamReadFeatures = source.myStreamReadFeatures
        myFormatFeatures = source.myFormatFeatures
        myProblemHandlers = problemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = abstractTypeResolvers
    }

    private constructor(source: DeserializationConfig, rootName: PropertyName?) : super(source, rootName) {
        myDeserializationFeatures = source.myDeserializationFeatures
        myStreamReadFeatures = source.myStreamReadFeatures
        myFormatFeatures = source.myFormatFeatures
        myProblemHandlers = source.myProblemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = source.myAbstractTypeResolver
    }

    private constructor(source: DeserializationConfig, view: KClass<*>?) : super(source, view) {
        myDeserializationFeatures = source.myDeserializationFeatures
        myStreamReadFeatures = source.myStreamReadFeatures
        myFormatFeatures = source.myFormatFeatures
        myProblemHandlers = source.myProblemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = source.myAbstractTypeResolver
    }

    private constructor(source: DeserializationConfig, attributes: ContextAttributes) : super(source, attributes) {
        myDeserializationFeatures = source.myDeserializationFeatures
        myStreamReadFeatures = source.myStreamReadFeatures
        myFormatFeatures = source.myFormatFeatures
        myProblemHandlers = source.myProblemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = source.myAbstractTypeResolver
    }

    private constructor(source: DeserializationConfig, datatypeFeatures: DatatypeFeatures) : super(source,
            datatypeFeatures) {
        myDeserializationFeatures = source.myDeserializationFeatures
        myStreamReadFeatures = source.myStreamReadFeatures
        myFormatFeatures = source.myFormatFeatures
        myProblemHandlers = source.myProblemHandlers
        myCoercionConfigs = source.myCoercionConfigs
        myAbstractTypeResolver = source.myAbstractTypeResolver
    }

    internal val baseSettings: BaseSettings
        get() = myBase

    /*
     *******************************************************************************************************************
     * Lifecycle, factory methods from MapperConfigBase
     *******************************************************************************************************************
     */

    override fun withBase(newBase: BaseSettings): DeserializationConfig {
        return this.takeUnless { myBase == newBase } ?: DeserializationConfig(this, newBase)
    }

    override fun with(datatypeFeatures: DatatypeFeatures): DeserializationConfig {
        return DeserializationConfig(this, datatypeFeatures)
    }

    override fun withRootName(rootName: PropertyName?): DeserializationConfig {
        if (myRootName == rootName) {
            return this
        }

        return DeserializationConfig(this, rootName)
    }

    override fun withView(view: KClass<*>?): DeserializationConfig {
        if (myView == view) {
            return this
        }

        return DeserializationConfig(this, view)
    }

    override fun with(attributes: ContextAttributes): DeserializationConfig {
        if (myAttributes == attributes) {
            return this
        }

        return DeserializationConfig(this, attributes)
    }

    /*
     *******************************************************************************************************************
     * Life-cycle, DeserializationFeature-based factory methods
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun with(feature: DeserializationFeature): DeserializationConfig {
        val newFeatures = myDeserializationFeatures or feature.mask

        if (myDeserializationFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, newFeatures, myStreamReadFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun with(first: DeserializationFeature, vararg features: DeserializationFeature): DeserializationConfig {
        var newFeatures = myDeserializationFeatures or first.mask

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        if (myDeserializationFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, newFeatures, myStreamReadFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun withFeatures(vararg features: DeserializationFeature): DeserializationConfig {
        var newFeatures = myDeserializationFeatures

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        if (myDeserializationFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, newFeatures, myStreamReadFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun without(feature: DeserializationFeature): DeserializationConfig {
        val newFeatures = myDeserializationFeatures and feature.mask.inv()

        if (myDeserializationFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, newFeatures, myStreamReadFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun without(feature: DeserializationFeature, vararg features: DeserializationFeature): DeserializationConfig {
        var newFeatures = myDeserializationFeatures and feature.mask.inv()

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        if (myDeserializationFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, newFeatures, myStreamReadFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun withoutFeatures(vararg features: DeserializationFeature): DeserializationConfig {
        var newFeatures = myDeserializationFeatures

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        if (myDeserializationFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, newFeatures, myStreamReadFeatures, myFormatFeatures)
    }

    /*
     *******************************************************************************************************************
     * Life-cycle, StreamReadFeature-based factory methods
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun with(feature: StreamReadFeature): DeserializationConfig {
        val newFeatures = myStreamReadFeatures or feature.mask

        if (myStreamReadFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, newFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun withFeatures(vararg features: StreamReadFeature): DeserializationConfig {
        var newFeatures = myStreamReadFeatures

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        if (myStreamReadFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, newFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun without(feature: StreamReadFeature): DeserializationConfig {
        val newFeatures = myStreamReadFeatures and feature.mask.inv()

        if (myStreamReadFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, newFeatures, myFormatFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun withoutFeatures(vararg features: StreamReadFeature): DeserializationConfig {
        var newFeatures = myStreamReadFeatures

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        if (myStreamReadFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, newFeatures, myFormatFeatures)
    }

    /*
     *******************************************************************************************************************
     * Life-cycle, FormatFeature-based factory methods
     *******************************************************************************************************************
     */

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun with(feature: FormatFeature): DeserializationConfig {
        val newFeatures = myFormatFeatures or feature.mask

        if (myFormatFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, myStreamReadFeatures, newFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * enabled.
     */
    fun withFeatures(vararg features: FormatFeature): DeserializationConfig {
        var newFeatures = myFormatFeatures

        for (feature in features) {
            newFeatures = newFeatures or feature.mask
        }

        if (myFormatFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, myStreamReadFeatures, newFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun without(feature: FormatFeature): DeserializationConfig {
        val newFeatures = myFormatFeatures and feature.mask.inv()

        if (myFormatFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, myStreamReadFeatures, newFeatures)
    }

    /**
     * Fluent factory method that will construct and return a new configuration object instance with specified features
     * disabled.
     */
    fun withoutFeatures(vararg features: FormatFeature): DeserializationConfig {
        var newFeatures = myFormatFeatures

        for (feature in features) {
            newFeatures = newFeatures and feature.mask.inv()
        }

        if (myFormatFeatures == newFeatures) {
            return this
        }

        return DeserializationConfig(this, myDeserializationFeatures, myStreamReadFeatures, newFeatures)
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, deserialization-specific factory methods
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to add a handler that can (try to) resolve non-fatal deserialization problems.
     */
    fun withHandler(handler: DeserializationProblemHandler): DeserializationConfig {
        if (LinkedNode.contains(myProblemHandlers, handler)) {
            return this
        }

        return DeserializationConfig(this, LinkedNode(handler, myProblemHandlers), myAbstractTypeResolver)
    }

    /**
     * Method for removing all configured problem handlers; usually done to replace existing handler(s) with different
     * one(s).
     */
    fun withNoProblemHandlers(): DeserializationConfig {
        if (myProblemHandlers == null) {
            return this
        }

        return DeserializationConfig(this, null, myAbstractTypeResolver)
    }

    /*
     *******************************************************************************************************************
     * Support for ObjectReadContext
     *******************************************************************************************************************
     */

    val streamReadFeatures: Int
        get() = myStreamReadFeatures

    val formatReadFeatures: Int
        get() = myFormatFeatures

    /*
     *******************************************************************************************************************
     * MapperConfig implementation/overrides: other
     *******************************************************************************************************************
     */

    override fun useRootWrapping(): Boolean {
        if (myRootName != null) {
            return !myRootName.isEmpty()
        }

        return isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE)
    }

    fun isEnabled(feature: DeserializationFeature): Boolean {
        return myDeserializationFeatures and feature.mask != 0
    }

    fun isEnabled(feature: StreamReadFeature): Boolean {
        return myStreamReadFeatures and feature.mask != 0
    }

    fun hasFormatFeature(feature: FormatFeature): Boolean {
        return myFormatFeatures and feature.mask != 0
    }

    /**
     * Bulk access method for checking that all features specified by mask are enabled.
     */
    fun hasDeserializationFeatures(featureMask: Int): Boolean {
        return myDeserializationFeatures and featureMask == featureMask
    }

    /**
     * Bulk access method for checking that at least one of features specified by mask is enabled.
     */
    fun hasSomeOfFeatures(featureMask: Int): Boolean {
        return myDeserializationFeatures and featureMask != 0
    }

    /**
     * Bulk access method for getting the bit mask of all [DeserializationFeatures][DeserializationFeature] that are
     * enabled.
     */
    val deserializationFeatures: Int
        get() = myDeserializationFeatures

    /**
     * Convenience method equivalent to:
     * ```
     * isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
     * ```
     */
    fun requiresFullValue(): Boolean {
        return DeserializationFeature.FAIL_ON_TRAILING_TOKENS.isEnabledIn(myDeserializationFeatures)
    }

    /*
     *******************************************************************************************************************
     * Abstract type mapping
     *******************************************************************************************************************
     */

    fun hasAbstractTypeResolvers(): Boolean {
        return myAbstractTypeResolver.isNotEmpty()
    }

    fun abstractTypeResolvers(): Iterable<AbstractTypeResolver> {
        return ArrayIterator(myAbstractTypeResolver)
    }

    fun mapAbstractType(type: KotlinType): KotlinType {
        var realType = type

        if (!hasAbstractTypeResolvers()) {
            return realType
        }

        while (true) {
            val next = internalMapAbstractType(realType) ?: return realType
            val previousClass = realType.rawClass
            val nextClass = next.rawClass

            if (previousClass == nextClass || !previousClass.isAssignableFrom(nextClass)) {
                throw IllegalArgumentException(
                        "Invalid abstract type resolution from $realType to $nextClass: latter is not a subtype of former")
            }

            realType = next
        }
    }

    /**
     * Method that will find abstract type mapping for specified type, doing a single lookup through registered abstract
     * type resolvers; will not do recursive lookups.
     */
    private fun internalMapAbstractType(type: KotlinType): KotlinType? {
        val currentClass = type.rawClass

        for (resolver in abstractTypeResolvers()) {
            val concrete = resolver.findTypeMapping(this, type) ?: continue

            if (!concrete.hasRawClass(currentClass)) {
                return concrete
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Other configuration
     *******************************************************************************************************************
     */

    /**
     * Accessor for getting head of the problem handler chain. May be `null`, if no handlers have been added.
     */
    val problemHandlers: LinkedNode<DeserializationProblemHandler>?
        get() = myProblemHandlers

    /*
     *******************************************************************************************************************
     * CoercionConfig access
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
    fun findCoercionAction(targetType: LogicalType?, targetClass: KClass<*>?,
            inputShape: CoercionInputShape): CoercionAction {
        return myCoercionConfigs.findCoercion(this, targetType, targetClass, inputShape)
    }

    /**
     * More specialized accessor called in case of input being a blank String (one consisting of only white space
     * characters with length of at least one). Will basically first determine if "blank as empty" is allowed: if not,
     * returns [actionIfBlankNotAllowed], otherwise returns action for [CoercionInputShape.EmptyString].
     *
     * @param targetType Logical target type of coercion
     *
     * @param targetClass Physical target type of coercion
     *
     * @param actionIfBlankNotAllowed Return value to use in case "blanks as empty" is not allowed
     *
     * @return CoercionAction configured for specified coercion from blank string
     */
    fun findCoercionFromBlankString(targetType: LogicalType?, targetClass: KClass<*>?,
            actionIfBlankNotAllowed: CoercionAction): CoercionAction {
        return myCoercionConfigs.findCoercionFromBlankString(this, targetType, targetClass, actionIfBlankNotAllowed)
    }

}