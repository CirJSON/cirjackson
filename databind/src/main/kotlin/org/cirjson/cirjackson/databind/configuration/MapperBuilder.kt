package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.annotations.CirJsonSetter
import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.*
import org.cirjson.cirjackson.databind.cirjsontype.implementation.DefaultTypeResolverBuilder
import org.cirjson.cirjackson.databind.cirjsontype.implementation.StandardSubtypeResolver
import org.cirjson.cirjackson.databind.configuration.MapperBuilder.Companion.findModules
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerFactory
import org.cirjson.cirjackson.databind.deserialization.DeserializationProblemHandler
import org.cirjson.cirjackson.databind.deserialization.DeserializerFactory
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.serialization.BeanSerializerFactory
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.type.TypeModifier
import org.cirjson.cirjackson.databind.util.ArrayBuilders
import org.cirjson.cirjackson.databind.util.LinkedNode
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.StandardDateFormat
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass

abstract class MapperBuilder<M : ObjectMapper, B : MapperBuilder<M, B>> {

    /*
     *******************************************************************************************************************
     * Basic settings
     *******************************************************************************************************************
     */

    protected var myBaseSettings: BaseSettings

    internal fun internalBaseSettings(): BaseSettings {
        return myBaseSettings
    }

    /**
     * Underlying stream factory
     */
    protected var myStreamFactory: TokenStreamFactory

    internal fun internalStreamFactory(): TokenStreamFactory {
        return myStreamFactory
    }

    /**
     * Various configuration setting overrides, both global base settings and per-class overrides.
     */
    protected var myConfigOverrides: ConfigOverrides

    internal fun internalConfigOverrides(): ConfigOverrides {
        return myConfigOverrides
    }

    /**
     * Coercion settings (global, per-type overrides)
     */
    protected var myCoercionConfigs: CoercionConfigs

    internal fun internalCoercionConfigs(): CoercionConfigs {
        return myCoercionConfigs
    }

    /*
     *******************************************************************************************************************
     * Modules
     *******************************************************************************************************************
     */

    /**
     * Modules registered for addition, indexed by registration id.
     */
    protected var myModules: MutableMap<Any, CirJacksonModule>?

    internal fun internalModules(): Map<Any, CirJacksonModule>? {
        return myModules
    }

    /*
     *******************************************************************************************************************
     * Handlers, introspection
     *******************************************************************************************************************
     */

    /**
     * Specific factory used for creating [KotlinType] instances; needed to allow modules to add more custom type
     * handling
     */
    protected var myTypeFactory: TypeFactory?

    internal fun internalTypeFactory(): TypeFactory? {
        return myTypeFactory
    }

    /**
     * Introspector used to figure out Bean properties needed for bean serialization and deserialization. Overridable
     * so that it is possible to change low-level details of introspection, like adding new annotation types.
     */
    protected var myClassIntrospector: ClassIntrospector?

    internal fun internalClassIntrospector(): ClassIntrospector? {
        return myClassIntrospector
    }

    /**
     * Entity responsible for construction actual type resolvers
     * ([TypeSerializers][org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer],
     * [TypeDeserializers][org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer]).
     */
    protected var myTypeResolverProvider: TypeResolverProvider?

    internal fun internalTypeResolverProvider(): TypeResolverProvider? {
        return myTypeResolverProvider
    }

    protected var mySubtypeResolver: SubtypeResolver?

    internal fun internalSubtypeResolver(): SubtypeResolver? {
        return mySubtypeResolver
    }

    /**
     * Handler responsible for resolving mix-in classes registered, if any.
     */
    protected var myMixInHandler: MixInHandler?

    internal fun internalMixInHandler(): MixInHandler? {
        return myMixInHandler
    }

    /*
     *******************************************************************************************************************
     * Factories for serialization
     *******************************************************************************************************************
     */

    /**
     * [SerializationContexts] to use as factory for stateful [SerializerProviders][SerializerProvider]
     */
    protected var mySerializationContexts: SerializationContexts?

    internal fun internalSerializationContexts(): SerializationContexts? {
        return mySerializationContexts
    }

    protected var mySerializerFactory: SerializerFactory?

    internal fun internalSerializerFactory(): SerializerFactory? {
        return mySerializerFactory
    }

    protected var myFilterProvider: FilterProvider?

    internal fun internalFilterProvider(): FilterProvider? {
        return myFilterProvider
    }

    protected var myDefaultPrettyPrinter: PrettyPrinter?

    internal fun internalDefaultPrettyPrinter(): PrettyPrinter? {
        return myDefaultPrettyPrinter
    }

    /*
     *******************************************************************************************************************
     * Factories for deserialization
     *******************************************************************************************************************
     */

    /**
     * Factory to use for creating per-operation contexts.
     */
    protected var myDeserializationContexts: DeserializationContexts?

    internal fun internalDeserializationContexts(): DeserializationContexts? {
        return myDeserializationContexts
    }

    protected var myDeserializerFactory: DeserializerFactory?

    internal fun internalDeserializerFactory(): DeserializerFactory? {
        return myDeserializerFactory
    }

    /**
     * Provider for values to inject in deserialized POJOs.
     */
    protected var myInjectableValues: InjectableValues?

    internal fun internalInjectableValues(): InjectableValues? {
        return myInjectableValues
    }

    /**
     * Optional handlers that application may register to try to work around various problem situations during
     * deserialization
     */
    protected var myProblemHandlers: LinkedNode<DeserializationProblemHandler>?

    internal fun internalProblemHandlers(): LinkedNode<DeserializationProblemHandler>? {
        return myProblemHandlers
    }

    protected var myAbstractTypeResolvers: Array<AbstractTypeResolver>

    internal fun internalAbstractTypeResolvers(): Array<AbstractTypeResolver> {
        return myAbstractTypeResolvers
    }

    /*
     *******************************************************************************************************************
     * Handlers/factories, other:
     *******************************************************************************************************************
     */

    /**
     * Explicitly configured default [ContextAttributes], if any.
     */
    protected var myDefaultAttributes: ContextAttributes?

    internal fun internalDefaultAttributes(): ContextAttributes? {
        return myDefaultAttributes
    }

    /*
     *******************************************************************************************************************
     * Feature flags: serialization, deserialization
     *******************************************************************************************************************
     */

    /**
     * Set of shared mapper features enabled.
     */
    protected var myMapperFeatures: Long

    internal fun internalMapperFeatures(): Long {
        return myMapperFeatures
    }

    /**
     * Set of [SerializationFeatures][SerializationFeature] enabled.
     */
    protected var mySerializationFeatures: Int

    internal fun internalSerializationFeatures(): Int {
        return mySerializationFeatures
    }

    /**
     * Set of [DeserializationFeatures][DeserializationFeature] enabled.
     */
    protected var myDeserializationFeatures: Int

    internal fun internalDeserializationFeatures(): Int {
        return myDeserializationFeatures
    }

    protected var myDatatypeFeatures: DatatypeFeatures

    internal fun internalDatatypeFeatures(): DatatypeFeatures {
        return myDatatypeFeatures
    }

    /*
     *******************************************************************************************************************
     * Feature flags: generation, parsing
     *******************************************************************************************************************
     */

    /**
     * States of [StreamReadFeatures][StreamReadFeature] to enable/disable.
     */
    protected var myStreamReadFeatures: Int

    internal fun internalStreamReadFeatures(): Int {
        return myStreamReadFeatures
    }

    /**
     * States of [StreamWriteFeatures][StreamWriteFeature] to enable/disable.
     */
    protected var myStreamWriteFeatures: Int

    internal fun internalStreamWriteFeatures(): Int {
        return myStreamWriteFeatures
    }

    /**
     * Optional per-format parser feature flags.
     */
    protected var myFormatReadFeatures: Int

    internal fun internalFormatReadFeatures(): Int {
        return myFormatReadFeatures
    }

    /**
     * Optional per-format generator feature flags.
     */
    protected var myFormatWriteFeatures: Int

    internal fun internalFormatWriteFeatures(): Int {
        return myFormatWriteFeatures
    }

    /*
     *******************************************************************************************************************
     * Transient state
     *******************************************************************************************************************
     */

    /**
     * Configuration state after direct access, immediately before registration of modules (if any) and construction of
     * actual mapper. Retained after first access, and returned from [saveStateApplyModules], to allow future "rebuild".
     */
    protected var mySavedState: MapperBuilderState? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(streamFactory: TokenStreamFactory) {
        myStreamFactory = streamFactory
        myBaseSettings = DEFAULT_BASE_SETTINGS
        myConfigOverrides = ConfigOverrides()
        myCoercionConfigs = CoercionConfigs()

        myModules = null

        myStreamReadFeatures = streamFactory.streamReadFeatures
        myStreamWriteFeatures = streamFactory.streamWriteFeatures
        myFormatReadFeatures = streamFactory.formatReadFeatures
        myFormatWriteFeatures = streamFactory.formatWriteFeatures

        myMapperFeatures = DEFAULT_MAPPER_FEATURES

        if (streamFactory.isRequiringPropertyOrdering) {
            myMapperFeatures = myMapperFeatures or MapperFeature.SORT_PROPERTIES_ALPHABETICALLY.longMask
        }

        myDeserializationFeatures = DEFAULT_DESERIALIZATION_FEATURES
        mySerializationFeatures = DEFAULT_SERIALIZATION_FEATURES
        myDatatypeFeatures = DatatypeFeatures.DEFAULT_FEATURES

        myTypeFactory = null
        myClassIntrospector = null
        myTypeResolverProvider = null
        mySubtypeResolver = null
        myMixInHandler = null

        mySerializationContexts = null
        mySerializerFactory = null
        myFilterProvider = null
        myDefaultPrettyPrinter = null

        myDeserializationContexts = null
        myDeserializerFactory = null
        myInjectableValues = null
        myProblemHandlers = null
        myAbstractTypeResolvers = NO_ABSTRACT_TYPE_RESOLVERS

        myDefaultAttributes = null
    }

    protected constructor(state: MapperBuilderState) {
        myBaseSettings = state.internalBaseSettings()
        myStreamFactory = state.internalStreamFactory()
        myConfigOverrides = state.internalConfigOverrides().snapshot()
        myCoercionConfigs = state.internalCoercionConfigs().snapshot()

        val stateModules = state.internalModules()

        if (stateModules == null) {
            myModules = null
        } else {
            myModules = LinkedHashMap()

            for (module in stateModules) {
                addModule(module)
            }
        }

        myTypeFactory = state.internalTypeFactory()?.snapshot()
        myClassIntrospector = state.internalClassIntrospector()
        myTypeResolverProvider = state.internalTypeResolverProvider()
        mySubtypeResolver = state.internalSubtypeResolver()?.snapshot()
        myMixInHandler = state.internalMixInHandler()?.snapshot() as MixInHandler?

        mySerializationContexts = state.internalSerializationContexts()
        mySerializerFactory = state.internalSerializerFactory()
        myFilterProvider = state.internalFilterProvider()?.snapshot()
        myDefaultPrettyPrinter = state.internalDefaultPrettyPrinter()

        myDeserializationContexts = state.internalDeserializationContexts()
        myDeserializerFactory = state.internalDeserializerFactory()
        myInjectableValues = state.internalInjectableValues()?.snapshot()
        myProblemHandlers = state.internalProblemHandlers()
        myAbstractTypeResolvers = state.internalAbstractTypeResolvers()

        myDefaultAttributes = state.internalDefaultAttributes()?.snapshot()

        myMapperFeatures = state.internalMapperFeatures()
        mySerializationFeatures = state.internalSerializationFeatures()
        myDeserializationFeatures = state.internalDeserializationFeatures()
        myDatatypeFeatures = state.internalDatatypeFeatures()
        myStreamReadFeatures = state.internalStreamReadFeature()
        myStreamWriteFeatures = state.internalStreamWriteFeature()
        myFormatReadFeatures = state.internalFormatReadFeatures()
        myFormatWriteFeatures = state.internalFormatWriteFeatures()
    }

    /*
     *******************************************************************************************************************
     * Methods for the actual build process
     *******************************************************************************************************************
     */

    /**
     * Method to call to create actual mapper instance.
     *
     * Implementation detail: usually construction occurs by passing `this` builder instance to constructor of specific
     * mapper type builder builds.
     */
    abstract fun build(): M

    /**
     * Method called by mapper being constructed to first save state (delegated to `saveState()` method), then apply
     * modules (if any), and then return the saved state (but retain reference to it). If the method has been called
     * previously, it will simply return retained state.
     */
    open fun saveStateApplyModules(): MapperBuilderState {
        if (mySavedState == null) {
            mySavedState = saveState()

            if (myModules != null) {
                val context = constructModuleContext()
                myModules!!.values.forEach { it.setupModule(context) }
                context.applyChanges(this)
            }
        }

        return mySavedState!!
    }

    protected open fun constructModuleContext(): ModuleContextBase {
        return ModuleContextBase(this, myConfigOverrides)
    }

    protected abstract fun saveState(): MapperBuilderState

    /*
     *******************************************************************************************************************
     * Secondary factory methods
     *******************************************************************************************************************
     */

    open fun buildSerializationConfig(configOverrides: ConfigOverrides, mixins: MixInHandler, typeFactory: TypeFactory,
            classIntrospector: ClassIntrospector, subtypeResolver: SubtypeResolver, rootNames: RootNameLookup,
            filterProvider: FilterProvider?): SerializationConfig {
        return SerializationConfig(this, myMapperFeatures, mySerializationFeatures, myStreamWriteFeatures,
                myFormatWriteFeatures, configOverrides, typeFactory, classIntrospector, mixins, subtypeResolver,
                defaultAttributes(), rootNames, filterProvider)
    }

    open fun buildDeserializationConfig(configOverrides: ConfigOverrides, mixins: MixInHandler,
            typeFactory: TypeFactory, classIntrospector: ClassIntrospector, subtypeResolver: SubtypeResolver,
            rootNames: RootNameLookup, coercionConfigs: CoercionConfigs): DeserializationConfig {
        return DeserializationConfig(this, myMapperFeatures, myDeserializationFeatures, myStreamReadFeatures,
                myFormatReadFeatures, configOverrides, coercionConfigs, typeFactory, classIntrospector, mixins,
                subtypeResolver, defaultAttributes(), rootNames, myAbstractTypeResolvers)
    }

    /*
     *******************************************************************************************************************
     * Accessors, features
     *******************************************************************************************************************
     */

    open fun isEnabled(feature: MapperFeature): Boolean {
        return feature.isEnabledIn(myMapperFeatures)
    }

    open fun isEnabled(feature: DeserializationFeature): Boolean {
        return feature.isEnabledIn(myDeserializationFeatures)
    }

    open fun isEnabled(feature: SerializationFeature): Boolean {
        return feature.isEnabledIn(mySerializationFeatures)
    }

    open fun isEnabled(feature: DatatypeFeature): Boolean {
        return myDatatypeFeatures.isEnabled(feature)
    }

    open fun isEnabled(feature: StreamReadFeature): Boolean {
        return feature.isEnabledIn(myStreamReadFeatures)
    }

    open fun isEnabled(feature: StreamWriteFeature): Boolean {
        return feature.isEnabledIn(myStreamWriteFeatures)
    }

    open fun datatypeFeatures(): DatatypeFeatures {
        return myDatatypeFeatures
    }

    /*
     *******************************************************************************************************************
     * Accessors, base settings
     *******************************************************************************************************************
     */

    open fun baseSettings(): BaseSettings {
        return myBaseSettings
    }

    open fun streamFactory(): TokenStreamFactory {
        return myStreamFactory
    }

    open fun annotationIntrospector(): AnnotationIntrospector? {
        return myBaseSettings.annotationIntrospector
    }

    /**
     * Overridable method for changing default [ContextAttributes] instance to use if not explicitly specified during
     * the build process.
     */
    open fun defaultAttributes(): ContextAttributes {
        return myDefaultAttributes ?: ContextAttributes.EMPTY
    }

    /**
     * Overridable method for changing default [ContextAttributes] instance to use if not explicitly specified during
     * the build process.
     */
    protected open fun defaultDefaultAttributes(): ContextAttributes {
        return ContextAttributes.EMPTY
    }

    /*
     *******************************************************************************************************************
     * Accessors, introspection
     *******************************************************************************************************************
     */

    open fun typeFactory(): TypeFactory {
        return myTypeFactory ?: defaultTypeFactory().also { myTypeFactory = it }
    }

    /**
     * Overridable method for changing default [TypeFactory] instance to use
     */
    protected open fun defaultTypeFactory(): TypeFactory {
        return TypeFactory.DEFAULT_INSTANCE
    }

    open fun classIntrospector(): ClassIntrospector {
        return myClassIntrospector ?: defaultClassIntrospector().also { myClassIntrospector = it }
    }

    /**
     * Overridable method for changing default [ClassIntrospector] instance to use
     */
    protected open fun defaultClassIntrospector(): ClassIntrospector {
        return BasicClassIntrospector()
    }

    open fun typeResolverProvider(): TypeResolverProvider {
        return myTypeResolverProvider ?: defaultTypeResolverProvider().also { myTypeResolverProvider = it }
    }

    /**
     * Overridable method for changing default [TypeResolverProvider] instance to use
     */
    protected open fun defaultTypeResolverProvider(): TypeResolverProvider {
        return TypeResolverProvider()
    }

    open fun subtypeResolver(): SubtypeResolver {
        return mySubtypeResolver ?: defaultSubtypeResolver().also { mySubtypeResolver = it }
    }

    /**
     * Overridable method for changing default [SubtypeResolver] instance to use
     */
    protected open fun defaultSubtypeResolver(): SubtypeResolver {
        return StandardSubtypeResolver()
    }

    open fun mixInHandler(): MixInHandler {
        return myMixInHandler ?: defaultMixInHandler().also { myMixInHandler = it }
    }

    /**
     * Overridable method for changing default [MixInHandler] instance to use
     */
    protected open fun defaultMixInHandler(): MixInHandler {
        return MixInHandler(null)
    }

    /*
     *******************************************************************************************************************
     * Accessors, serialization factories, related
     *******************************************************************************************************************
     */

    open fun serializationContexts(): SerializationContexts {
        return mySerializationContexts ?: defaultSerializationContexts().also { mySerializationContexts = it }
    }

    /**
     * Overridable method for changing default [SerializationContexts] instance to use
     */
    protected open fun defaultSerializationContexts(): SerializationContexts {
        return SerializationContexts.DefaultImplementation()
    }

    open fun serializerFactory(): SerializerFactory {
        return mySerializerFactory ?: defaultSerializerFactory().also { mySerializerFactory = it }
    }

    /**
     * Overridable method for changing default [SerializerFactory] instance to use
     */
    protected open fun defaultSerializerFactory(): SerializerFactory {
        return BeanSerializerFactory.INSTANCE
    }

    open fun filterProvider(): FilterProvider? {
        return myFilterProvider
    }

    open fun defaultPrettyPrinter(): PrettyPrinter {
        return myDefaultPrettyPrinter ?: defaultDefaultPrettyPrinter().also { myDefaultPrettyPrinter = it }
    }

    /**
     * Overridable method for changing default [PrettyPrinter] instance to use
     */
    protected open fun defaultDefaultPrettyPrinter(): PrettyPrinter {
        return DEFAULT_PRETTY_PRINTER
    }

    /*
     *******************************************************************************************************************
     * Accessors, deserialization factories, related
     *******************************************************************************************************************
     */

    open fun deserializationContexts(): DeserializationContexts {
        return myDeserializationContexts ?: defaultDeserializationContexts().also { myDeserializationContexts = it }
    }

    /**
     * Overridable method for changing default [DeserializationContexts] instance to use
     */
    protected open fun defaultDeserializationContexts(): DeserializationContexts {
        return DeserializationContexts.DefaultImplementation()
    }

    open fun deserializerFactory(): DeserializerFactory {
        return myDeserializerFactory ?: defaultDeserializerFactory().also { myDeserializerFactory = it }
    }

    /**
     * Overridable method for changing default [DeserializerFactory] instance to use
     */
    protected open fun defaultDeserializerFactory(): DeserializerFactory {
        return BeanDeserializerFactory.INSTANCE
    }

    open fun injectableValues(): InjectableValues? {
        return myInjectableValues
    }

    open fun deserializationProblemHandlers(): LinkedNode<DeserializationProblemHandler>? {
        return myProblemHandlers
    }

    /*
     *******************************************************************************************************************
     * Changing features: mapper, serialization, deserialization
     *******************************************************************************************************************
     */

    open fun enable(vararg features: MapperFeature): B {
        for (feature in features) {
            myMapperFeatures = myMapperFeatures or feature.longMask
        }

        return castedThis()
    }

    open fun disable(vararg features: MapperFeature): B {
        for (feature in features) {
            myMapperFeatures = myMapperFeatures and feature.longMask.inv()
        }

        return castedThis()
    }

    open fun configure(feature: MapperFeature, state: Boolean): B {
        myMapperFeatures = if (state) {
            myMapperFeatures or feature.longMask
        } else {
            myMapperFeatures and feature.longMask.inv()
        }

        return castedThis()
    }

    open fun enable(vararg features: SerializationFeature): B {
        for (feature in features) {
            mySerializationFeatures = mySerializationFeatures or feature.mask
        }

        return castedThis()
    }

    open fun disable(vararg features: SerializationFeature): B {
        for (feature in features) {
            mySerializationFeatures = mySerializationFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun configure(feature: SerializationFeature, state: Boolean): B {
        mySerializationFeatures = if (state) {
            mySerializationFeatures or feature.mask
        } else {
            mySerializationFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun enable(vararg features: DeserializationFeature): B {
        for (feature in features) {
            myDeserializationFeatures = myDeserializationFeatures or feature.mask
        }

        return castedThis()
    }

    open fun disable(vararg features: DeserializationFeature): B {
        for (feature in features) {
            myDeserializationFeatures = myDeserializationFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun configure(feature: DeserializationFeature, state: Boolean): B {
        myDeserializationFeatures = if (state) {
            myDeserializationFeatures or feature.mask
        } else {
            myDeserializationFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun enable(vararg features: DatatypeFeature): B {
        for (feature in features) {
            myDatatypeFeatures = myDatatypeFeatures.with(feature)
        }

        return castedThis()
    }

    open fun disable(vararg features: DatatypeFeature): B {
        for (feature in features) {
            myDatatypeFeatures = myDatatypeFeatures.without(feature)
        }

        return castedThis()
    }

    open fun configure(feature: DatatypeFeature, state: Boolean): B {
        myDatatypeFeatures = if (state) {
            myDatatypeFeatures.with(feature)
        } else {
            myDatatypeFeatures.without(feature)
        }

        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing features: parser, generator
     *******************************************************************************************************************
     */

    open fun enable(vararg features: StreamReadFeature): B {
        for (feature in features) {
            myStreamReadFeatures = myStreamReadFeatures or feature.mask
        }

        return castedThis()
    }

    open fun disable(vararg features: StreamReadFeature): B {
        for (feature in features) {
            myStreamReadFeatures = myStreamReadFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun configure(feature: StreamReadFeature, state: Boolean): B {
        myStreamReadFeatures = if (state) {
            myStreamReadFeatures or feature.mask
        } else {
            myStreamReadFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun enable(vararg features: StreamWriteFeature): B {
        for (feature in features) {
            myStreamWriteFeatures = myStreamWriteFeatures or feature.mask
        }

        return castedThis()
    }

    open fun disable(vararg features: StreamWriteFeature): B {
        for (feature in features) {
            myStreamWriteFeatures = myStreamWriteFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    open fun configure(feature: StreamWriteFeature, state: Boolean): B {
        myStreamWriteFeatures = if (state) {
            myStreamWriteFeatures or feature.mask
        } else {
            myStreamWriteFeatures and feature.mask.inv()
        }

        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing settings: config overrides
     *******************************************************************************************************************
     */

    /**
     * Method for changing config overrides for specific type, through callback to specific handler.
     */
    open fun withConfigOverride(forType: KClass<*>, handler: (MutableConfigOverride) -> Unit): B {
        handler.invoke(myConfigOverrides.findOrCreateOverride(forType))
        return castedThis()
    }

    /**
     * Method for changing various aspects of configuration overrides.
     */
    open fun withAllConfigOverrides(handler: (ConfigOverrides) -> Unit): B {
        handler.invoke(myConfigOverrides)
        return castedThis()
    }

    /**
     * Method for changing currently configured default [VisibilityChecker], object used for determining whether given
     * property element (method, field, constructor) can be auto-detected or not. Checker to modify is used for all POJO
     * types for which there is no specific per-type checker.
     *
     * @param handler Function that is given current default visibility checker and that needs to return either checker
     * as is, or a new instance created using one or more of `withVisibility` (and similar) calls.
     */
    open fun changeDefaultVisibility(handler: (VisibilityChecker) -> VisibilityChecker): B {
        val oldVisibilityChecker = myConfigOverrides.defaultVisibility
        val newVisibilityChecker = handler.invoke(oldVisibilityChecker)

        if (newVisibilityChecker !== oldVisibilityChecker) {
            myConfigOverrides.setDefaultVisibility(newVisibilityChecker)
        }

        return castedThis()
    }

    /**
     * Method for changing currently default settings for property inclusion, used for determining whether POJO
     * properties with certain value should be excluded or not: the most common case being exclusion of `null` values.
     */
    open fun changeDefaultPropertyInclusion(handler: (CirJsonInclude.Value?) -> CirJsonInclude.Value?): B {
        val oldInclusion = myConfigOverrides.defaultInclusion
        val newInclusion = handler.invoke(oldInclusion)

        if (newInclusion !== oldInclusion) {
            myConfigOverrides.setDefaultInclusion(newInclusion!!)
        }

        return castedThis()
    }

    /**
     * Method for changing currently default settings for handling of `null` values during deserialization, regarding
     * whether they are set as-is, ignored completely, or possible transformed into "empty" value of the target type (if
     * any).
     */
    open fun changeDefaultNullHandling(handler: (CirJsonSetter.Value) -> CirJsonSetter.Value): B {
        val oldNullHandling = myConfigOverrides.defaultNullHandling
        val newNullHandling = handler.invoke(oldNullHandling)

        if (newNullHandling !== oldNullHandling) {
            myConfigOverrides.setDefaultNullHandling(newNullHandling)
        }

        return castedThis()
    }

    /**
     * Method for setting default Setter configuration, regarding things like merging, null-handling; used for
     * properties for which there are no per-type or per-property overrides (via annotations or config overrides).
     */
    open fun defaultMergeable(boolean: Boolean?): B {
        myConfigOverrides.setDefaultMergeable(boolean)
        return castedThis()
    }

    /**
     * Method for setting default Setter configuration, regarding things like merging, null-handling; used for
     * properties for which there are no per-type or per-property overrides (via annotations or config overrides).
     */
    open fun defaultLeniency(boolean: Boolean?): B {
        myConfigOverrides.setDefaultLeniency(boolean)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing settings: coercion config
     *******************************************************************************************************************
     */

    /**
     * Method for changing coercion config for specific logical types, through callback to specific handler.
     */
    open fun withCoercionConfig(forType: LogicalType, handler: (MutableCoercionConfig) -> Unit): B {
        handler.invoke(myCoercionConfigs.findOrCreateCoercion(forType))
        return castedThis()
    }

    /**
     * Method for changing coercion config for specific physical type, through callback to specific handler.
     */
    open fun withCoercionConfig(forType: KClass<*>, handler: (MutableCoercionConfig) -> Unit): B {
        handler.invoke(myCoercionConfigs.findOrCreateCoercion(forType))
        return castedThis()
    }

    /**
     * Method for changing target-type-independent coercion configuration defaults.
     */
    open fun withCoercionConfigDefaults(handler: (MutableCoercionConfig) -> Unit): B {
        handler.invoke(myCoercionConfigs.defaultCoercions())
        return castedThis()
    }

    /**
     * Method for changing various aspects of configuration overrides.
     */
    open fun withAllCoercionConfigs(handler: (CoercionConfigs) -> Unit): B {
        handler.invoke(myCoercionConfigs)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Module registration, discovery, access
     *******************************************************************************************************************
     */

    /**
     * Method that will drop all modules added (via [addModule] and similar calls) to this builder.
     */
    open fun removeAllModules(): B {
        myModules = null
        return castedThis()
    }

    /**
     * Method will add given module to be registered when mapper is built, possibly replacing an earlier instance of the
     * module (as specified by its [CirJacksonModule.registrationId]). Actual registration occurs in addition order
     * (considering last add to count, in case of re-registration for same id) when [build] is called.
     */
    open fun addModule(module: CirJacksonModule): B {
        val moduleId = module.registrationId

        if (myModules == null) {
            myModules = LinkedHashMap()
        } else {
            myModules!!.remove(moduleId)
        }

        for (dependency in module.dependencies) {
            myModules!!.putIfAbsent(dependency.registrationId, dependency)
        }

        myModules!![moduleId] = module
        return castedThis()
    }

    open fun addModules(vararg modules: CirJacksonModule): B {
        for (module in modules) {
            addModule(module)
        }

        return castedThis()
    }

    open fun addModules(modules: Iterable<CirJacksonModule>): B {
        for (module in modules) {
            addModule(module)
        }

        return castedThis()
    }

    /**
     * Convenience method that is functionally equivalent to:
     * ```
     * addModules(findModules())
     * ```
     *
     * As with [findModules], no caching is done for modules, so care needs to be taken to either create and share a
     * single mapper instance; or to cache introspected set of modules.
     */
    open fun findAndAddModules(): B {
        return addModules(findModules())
    }

    /**
     * "Accessor" method that will expose the set of registered modules, in addition order, to given handler.
     */
    open fun withModules(handler: (CirJacksonModule) -> Unit): B {
        myModules?.values?.forEach(handler)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing base settings
     *******************************************************************************************************************
     */

    open fun baseSettings(baseSettings: BaseSettings): B {
        myBaseSettings = baseSettings
        return castedThis()
    }

    /**
     * Method for replacing [AnnotationIntrospector] used by the mapper instance to be built. Note that doing this will
     * replace the current introspector, which may lead to unavailability of core CirJackson annotations. If you want to
     * combine handling of multiple introspectors, have a look at [AnnotationIntrospectorPair].
     *
     * @see AnnotationIntrospectorPair
     */
    open fun annotationIntrospector(introspector: AnnotationIntrospector): B {
        myBaseSettings = myBaseSettings.withAnnotationIntrospector(introspector)
        return castedThis()
    }

    /**
     * Method for replacing default [ContextAttributes] that the mapper uses: usually one initialized with a set of
     * default shared attributes, but potentially also with a custom implementation.
     *
     * NOTE: instance specified will need to be thread-safe for usage, similar to the default
     * ([ContextAttributes.Implementation]).
     *
     * @param attributes Default instance to use, if not `null`, or `null` for "use empty default set".
     *
     * @return This Builder instance to allow call-chaining
     */
    open fun defaultAttributes(attributes: ContextAttributes): B {
        myDefaultAttributes = attributes
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing introspection helpers
     *******************************************************************************************************************
     */

    open fun typeFactory(typeFactory: TypeFactory): B {
        myTypeFactory = typeFactory
        return castedThis()
    }

    open fun addTypeModifier(modifier: TypeModifier?): B {
        myTypeFactory = typeFactory().withModifier(modifier)
        return castedThis()
    }

    open fun typeResolverProvider(provider: TypeResolverProvider?): B {
        myTypeResolverProvider = provider
        return castedThis()
    }

    open fun classIntrospector(classIntrospector: ClassIntrospector?): B {
        myClassIntrospector = classIntrospector
        return castedThis()
    }

    open fun subtypeResolver(resolver: SubtypeResolver?): B {
        mySubtypeResolver = resolver
        return castedThis()
    }

    open fun polymorphicTypeValidator(polymorphicTypeValidator: PolymorphicTypeValidator): B {
        myBaseSettings = myBaseSettings.with(polymorphicTypeValidator)
        return castedThis()
    }

    /**
     * Method for configuring [HandlerInstantiator] to use for creating instances of handlers (such as serializers,
     * deserializers, type and type id resolvers), given a class.
     *
     * @param handlerInstantiator Instantiator to use; if `null`, use the default implementation
     *
     * @return Builder instance itself to allow chaining
     */
    open fun handlerInstantiator(handlerInstantiator: HandlerInstantiator?): B {
        myBaseSettings = myBaseSettings.with(handlerInstantiator)
        return castedThis()
    }

    /**
     * Method for configuring [PropertyNamingStrategy] to use for adapting POJO property names (internal) into content
     * property names (external).
     *
     * @param strategy Strategy instance to use; if `null`, use the default implementation
     *
     * @return Builder instance itself to allow chaining
     */
    open fun propertyNamingStrategy(strategy: PropertyNamingStrategy?): B {
        myBaseSettings = myBaseSettings.with(strategy)
        return castedThis()
    }

    /**
     * Method for configuring [AccessorNamingStrategy] to use for auto-detecting accessor ("getter") and mutator
     * ("setter") methods based on naming of methods.
     *
     * @param strategy Strategy instance to use; if `null`, use the default implementation
     *
     * @return Builder instance itself to allow chaining
     */
    open fun accessorNaming(strategy: AccessorNamingStrategy.Provider?): B {
        myBaseSettings = myBaseSettings.with(strategy ?: DefaultAccessorNamingStrategy.Provider())
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing factories, serialization, related
     *******************************************************************************************************************
     */

    open fun serializerFactory(factory: SerializerFactory?): B {
        mySerializerFactory = factory
        return castedThis()
    }

    open fun serializationContexts(contexts: SerializationContexts?): B {
        mySerializationContexts = contexts
        return castedThis()
    }

    /**
     * Method for configuring this mapper to use specified [FilterProvider] for mapping Filter Ids to actual filter
     * instances.
     *
     * Note that usually it is better to use method in [ObjectWriter], but sometimes this method is more convenient. For
     * example, some frameworks only allow configuring of ObjectMapper instances and not [ObjectWriters][ObjectWriter].
     */
    open fun filterProvider(provider: FilterProvider?): B {
        myFilterProvider = provider
        return castedThis()
    }

    open fun serializationContexts(prettyPrinter: PrettyPrinter?): B {
        myDefaultPrettyPrinter = prettyPrinter
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing factories, deserialization, related
     *******************************************************************************************************************
     */

    open fun deserializerFactory(factory: DeserializerFactory?): B {
        myDeserializerFactory = factory
        return castedThis()
    }

    open fun deserializationContexts(contexts: DeserializationContexts?): B {
        myDeserializationContexts = contexts
        return castedThis()
    }

    open fun injectableValues(values: InjectableValues?): B {
        myInjectableValues = values
        return castedThis()
    }

    open fun nodeFactory(factory: CirJsonNodeFactory): B {
        myBaseSettings = myBaseSettings.with(factory)
        return castedThis()
    }

    /**
     * Method for specifying [ConstructorDetector] to use for determining some aspects of creator auto-detection
     * (specifically auto-detection of constructor, and in particular behavior with single-argument constructors).
     */
    open fun constructorDetector(constructorDetector: ConstructorDetector?): B {
        myBaseSettings = myBaseSettings.with(constructorDetector)
        return castedThis()
    }

    open fun cacheProvider(cacheProvider: CacheProvider): B {
        myBaseSettings = myBaseSettings.with(cacheProvider)
        val typeFactory = typeFactory().withCache(cacheProvider.forTypeFactory())
        return typeFactory(typeFactory)
    }

    /**
     * Method used for adding a [DeserializationProblemHandler] for this builder, at the head of the list (meaning it
     * has priority over handler registered earlier).
     */
    open fun addHandler(handler: DeserializationProblemHandler): B {
        if (!LinkedNode.contains(myProblemHandlers, handler)) {
            myProblemHandlers = LinkedNode(handler, myProblemHandlers)
        }

        return castedThis()
    }

    /**
     * Method that may be used to remove all [DeserializationProblemHandlers][DeserializationProblemHandler] added to
     * this builder (if any).
     */
    open fun clearProblemHandlers(): B {
        myProblemHandlers = null

        return castedThis()
    }

    /**
     * Method for inserting specified [AbstractTypeResolver] as the first resolver in a chain of possibly multiple
     * resolvers.
     */
    open fun addAbstractTypeResolver(resolver: AbstractTypeResolver): B {
        myAbstractTypeResolvers = ArrayBuilders.insertInListNoDup(myAbstractTypeResolvers, resolver)

        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing settings, date/time
     *******************************************************************************************************************
     */

    /**
     * Method for configuring the default [DateFormat] to use when serializing time values as Strings, and deserializing
     * from CirJSON Strings. If you need per-request configuration, factory methods in [ObjectReader] and [ObjectWriter]
     * instead.
     */
    open fun defaultDateFormat(format: DateFormat): B {
        myBaseSettings = myBaseSettings.with(format)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        return castedThis()
    }

    /**
     * Method for overriding default TimeZone to use for formatting. Default value used is UTC (NOT default TimeZone of
     * JVM).
     */
    open fun defaultTimeZone(timeZone: TimeZone?): B {
        myBaseSettings = myBaseSettings.with(timeZone)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        return castedThis()
    }

    /**
     * Method for overriding default locale to use for formatting. Default value used is [Locale.default].
     */
    open fun defaultLocale(locale: Locale): B {
        myBaseSettings = myBaseSettings.with(locale)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Changing settings, formatting
     *******************************************************************************************************************
     */

    /**
     * Method that will configure default [Base64Variant] that `ByteArray` serializers and deserializers will use.
     *
     * @param variant Base64 variant to use
     *
     * @return This mapper, for convenience to allow chaining
     */
    open fun defaultBase64Variant(variant: Base64Variant): B {
        myBaseSettings = myBaseSettings.with(variant)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Configuring Mix-ins
     *******************************************************************************************************************
     */

    /**
     * Method that may be used to completely change mix-in handling by providing alternate [MixInHandler]
     * implementation. Most of the time this is NOT the method you want to call, and rather are looking for
     * [mixInOverrides].
     */
    open fun mixInHandler(handler: MixInHandler?): B {
        myMixInHandler = handler
        return castedThis()
    }

    /**
     * Method that allows defining "override" mix-in resolver: something that is checked first,
     * before simple mix-in definitions.
     */
    open fun mixInOverrides(resolver: MixInResolver?): B {
        myMixInHandler = mixInHandler().withOverrides(resolver)
        return castedThis()
    }

    /**
     * Method to use for defining mix-in annotations to use for augmenting annotations that processable (serializable /
     * deserializable) classes have. This convenience method is equivalent to iterating over all entries and calling
     * [addMixIn] with `key` and `value` of each entry.
     */
    open fun addMixIns(sourceMixins: Map<KClass<*>, KClass<*>?>): B {
        myMixInHandler = mixInHandler().addLocalDefinitions(sourceMixins)
        return castedThis()
    }

    /**
     * Method to use for defining mix-in annotations to use for augmenting annotations that classes have, for
     * configuration serialization and/or deserialization processing purposes. Mixing in is done when introspecting
     * class annotations and properties. Annotations from "mixin" class (and its supertypes) will **override**
     * annotations that target classes (and their super-types) have.
     *
     * Note that standard mixin handler implementations will only allow a single mix-in source class per target, so if
     * there was a previous mix-in defined target, it will be cleared. This also means that you can remove mix-in
     * definition by specifying `mixinSource` of `null` (although preferred mechanism is [removeMixIn])
     *
     * @param target Target class on which to add annotations
     *
     * @param mixinSource Class that has annotations to add
     *
     * @return This builder instance to allow call-chaining
     */
    open fun addMixIn(target: KClass<*>, mixinSource: KClass<*>?): B {
        myMixInHandler = mixInHandler().addLocalDefinition(target, mixinSource)
        return castedThis()
    }

    /**
     * Method that allows making sure that specified `target` class does not have associated mix-in annotations:
     * basically can be used to undo an earlier call to [addMixIn].
     *
     * NOTE: removing mix-ins for given class does not try to remove possible mix-ins for any of its super classes and
     * super interfaces; only direct mix-in addition (if any) is removed.
     *
     * @param target Target class for which no mix-ins should remain after call
     *
     * @return This builder instance to allow call-chaining
     */
    open fun removeMixIn(target: KClass<*>): B {
        myMixInHandler = mixInHandler().addLocalDefinition(target, null)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Subtype registration
     *******************************************************************************************************************
     */

    open fun registerSubtypes(vararg subtypes: KClass<*>): B {
        subtypeResolver().registerSubtypes(*subtypes)
        return castedThis()
    }

    open fun registerSubtypes(vararg subtypes: NamedType): B {
        subtypeResolver().registerSubtypes(*subtypes)
        return castedThis()
    }

    open fun registerSubtypes(subtypes: Collection<KClass<*>>): B {
        subtypeResolver().registerSubtypes(subtypes)
        return castedThis()
    }

    /*
     *******************************************************************************************************************
     * Default typing (temporarily)
     *******************************************************************************************************************
     */

    /**
     * Convenience method that is equivalent to calling
     * ```
     * activateDefaultTyping(subtypeValidator, DefaultTyping.OBJECT_AND_NON_CONCRETE)
     * ```
     *
     * NOTE: choice of [PolymorphicTypeValidator] to configure is of crucial importance to security when deserializing
     * untrusted content: this because allowing deserializing of any type can lead to malicious attacks using
     * "deserialization gadgets". Implementations should use allow-listing to specify acceptable types unless the source
     * of content is fully trusted to only send safe types.
     */
    open fun activateDefaultTyping(subtypeValidator: PolymorphicTypeValidator): B {
        return activateDefaultTyping(subtypeValidator, DefaultTyping.OBJECT_AND_NON_CONCRETE)
    }

    /**
     * Convenience method that is equivalent to calling
     * ```
     * activateDefaultTyping(subtypeValidator, applicability, CirJsonTypeInfo.As.WRAPPER_ARRAY);
     * ```
     *
     * NOTE: choice of [PolymorphicTypeValidator] to configure is of crucial importance to security when deserializing
     * untrusted content: this because allowing deserializing of any type can lead to malicious attacks using
     * "deserialization gadgets". Implementations should use allow-listing to specify acceptable types unless the source
     * of content is fully trusted to only send safe types.
     */
    open fun activateDefaultTyping(subtypeValidator: PolymorphicTypeValidator, applicability: DefaultTyping): B {
        return activateDefaultTyping(subtypeValidator, applicability, CirJsonTypeInfo.As.WRAPPER_ARRAY)
    }

    /**
     * Method for enabling automatic inclusion of type information, needed for proper deserialization of polymorphic
     * types (unless types have been annotated with [CirJsonTypeInfo]).
     *
     * NOTE: use of [CirJsonTypeInfo.As.EXTERNAL_PROPERTY] is **NOT SUPPORTED**; and attempts of do so will throw an
     * [IllegalArgumentException] to make this limitation explicit.
     *
     * NOTE: choice of [PolymorphicTypeValidator] to configure is of crucial importance to security when deserializing
     * untrusted content: this because allowing deserializing of any type can lead to malicious attacks using
     * "deserialization gadgets". Implementations should use allow-listing to specify acceptable types unless the source
     * of content is fully trusted to only send safe types.
     *
     * @param applicability Defines kinds of types for which additional type information is added; see [DefaultTyping]
     * for more information.
     */
    open fun activateDefaultTyping(subtypeValidator: PolymorphicTypeValidator, applicability: DefaultTyping,
            includeAs: CirJsonTypeInfo.As): B {
        if (includeAs == CirJsonTypeInfo.As.EXTERNAL_PROPERTY) {
            throw IllegalArgumentException("Cannot use includeAs of $includeAs for DefaultTyping")
        }

        return setDefaultTyping(defaultDefaultTypingResolver(subtypeValidator, applicability, includeAs))
    }

    /**
     * Method for enabling automatic inclusion of type information -- needed for proper deserialization of polymorphic
     * types (unless types have been annotated with [CirJsonTypeInfo]) -- using "As.PROPERTY" inclusion mechanism and
     * specified property name to use for inclusion (default being "@class" since default type information always uses
     * class name as type identifier)
     *
     * NOTE: choice of [PolymorphicTypeValidator] to configure is of crucial importance to security when deserializing
     * untrusted content: this because allowing deserializing of any type can lead to malicious attacks using
     * "deserialization gadgets". Implementations should use allow-listing to specify acceptable types unless the source
     * of content is fully trusted to only send safe types.
     */
    open fun activateDefaultTypingAsProperty(subtypeValidator: PolymorphicTypeValidator, applicability: DefaultTyping,
            propertyName: String?): B {
        return setDefaultTyping(defaultDefaultTypingResolver(subtypeValidator, applicability, propertyName))
    }

    /**
     * Method for disabling automatic inclusion of type information; if so, only explicitly annotated types (ones with
     * [CirJsonTypeInfo]) will have additional embedded type information.
     */
    open fun deactivateDefaultTyping(): B {
        return setDefaultTyping(null)
    }

    /**
     * Method for enabling automatic inclusion of type information, using the specified handler object for determining
     * which types this affects, as well as details of how information is embedded.
     *
     * NOTE: use of Default Typing can be a potential security risk if incoming content comes from untrusted sources, so
     * care should be taken to use a [TypeResolverBuilder] that can limit allowed classes to deserialize.
     *
     * @param typeResolverBuilder Type information inclusion handler
     */
    open fun setDefaultTyping(typeResolverBuilder: TypeResolverBuilder<*>?): B {
        myBaseSettings = myBaseSettings.with(typeResolverBuilder)
        return castedThis()
    }

    /**
     * Overridable method for changing default [TypeResolverBuilder] to construct for "default typing".
     */
    protected open fun defaultDefaultTypingResolver(subtypeValidator: PolymorphicTypeValidator,
            applicability: DefaultTyping, includeAs: CirJsonTypeInfo.As): TypeResolverBuilder<*> {
        return DefaultTypeResolverBuilder(subtypeValidator, applicability, includeAs)
    }

    /**
     * Overridable method for changing default [TypeResolverBuilder] to construct for "default typing".
     */
    protected open fun defaultDefaultTypingResolver(subtypeValidator: PolymorphicTypeValidator,
            applicability: DefaultTyping, propertyName: String?): TypeResolverBuilder<*> {
        return DefaultTypeResolverBuilder(subtypeValidator, applicability, propertyName)
    }

    /*
     *******************************************************************************************************************
     * Other helper methods
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected fun castedThis(): B {
        return this as B
    }

    companion object {

        val DEFAULT_MAPPER_FEATURES = MapperFeature.collectLongDefaults()

        val DEFAULT_SERIALIZATION_FEATURES = ConfigFeature.collectFeatureDefaults(SerializationFeature::class)

        val DEFAULT_DESERIALIZATION_FEATURES = ConfigFeature.collectFeatureDefaults(DeserializationFeature::class)

        val DEFAULT_PRETTY_PRINTER: PrettyPrinter = DefaultPrettyPrinter()

        val DEFAULT_ANNOTATION_INTROSPECTOR: AnnotationIntrospector = CirJacksonAnnotationIntrospector()

        val DEFAULT_TYPE_VALIDATOR: PolymorphicTypeValidator = DefaultBaseTypeLimitingValidator()

        val DEFAULT_ACCESSOR_NAMING: AccessorNamingStrategy.Provider = DefaultAccessorNamingStrategy.Provider()

        val DEFAULT_BASE_SETTINGS = BaseSettings(DEFAULT_ANNOTATION_INTROSPECTOR, null, DEFAULT_ACCESSOR_NAMING, null,
                DEFAULT_TYPE_VALIDATOR, StandardDateFormat.instance, null, Locale.getDefault(), null,
                Base64Variants.defaultVariant, DefaultCacheProvider.DEFAULT, CirJsonNodeFactory.instance, null)

        val DEFAULT_TYPE_RESOLVER_PROVIDER = TypeResolverProvider()

        val NO_ABSTRACT_TYPE_RESOLVERS = emptyArray<AbstractTypeResolver>()

        /**
         * Method for locating available methods, using JDK [ServiceLoader] facility, along with module-provided SPI.
         *
         * Note that method does not do any caching, so calls should be considered potentially expensive.
         */
        fun findModules(): List<CirJacksonModule> {
            return findModules(null)
        }

        /**
         * Method for locating available methods, using JDK [ServiceLoader] facility, along with module-provided SPI.
         *
         * Note that method does not do any caching, so calls should be considered potentially expensive.
         */
        fun findModules(classLoader: ClassLoader?): List<CirJacksonModule> {
            val modules = arrayListOf<CirJacksonModule>()
            val loader = getServiceLoader(CirJacksonModule::class, classLoader)

            for (module in loader) {
                modules.add(module)
            }

            return modules
        }

        private fun <T : Any> getServiceLoader(clazz: KClass<T>, classLoader: ClassLoader?): ServiceLoader<T> {
            return if (classLoader == null) {
                ServiceLoader.load(clazz.java)
            } else {
                ServiceLoader.load(clazz.java, classLoader)
            }
        }

    }

}