package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.DefaultBaseTypeLimitingValidator
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverProvider
import org.cirjson.cirjackson.databind.cirjsontype.implementation.StandardSubtypeResolver
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerFactory
import org.cirjson.cirjackson.databind.deserialization.DeserializationProblemHandler
import org.cirjson.cirjackson.databind.deserialization.DeserializerFactory
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.serialization.BeanSerializerFactory
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.LinkedNode
import org.cirjson.cirjackson.databind.util.RootNameLookup
import org.cirjson.cirjackson.databind.util.StandardDateFormat
import java.util.*

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
    protected var myModules: Map<Any, CirJacksonModule>?

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
     * Module registration, discovery, access
     *******************************************************************************************************************
     */

    open fun addModule(module: CirJacksonModule): B {
        TODO("Not yet implemented")
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

    }

}