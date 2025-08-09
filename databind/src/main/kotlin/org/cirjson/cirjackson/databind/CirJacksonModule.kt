package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MutableConfigOverride
import org.cirjson.cirjackson.databind.deserialization.*
import org.cirjson.cirjackson.databind.serialization.Serializers
import org.cirjson.cirjackson.databind.serialization.ValueSerializerModifier
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.type.TypeModifier
import kotlin.reflect.KClass

/**
 * Simple interface for extensions that can be registered with [ObjectMapper] to provide a well-defined set of
 * extensions to default functionality; such as support for new data types.
 */
abstract class CirJacksonModule : Versioned {

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor that returns a display that can be used by CirJackson for informational purposes, as well as in
     * associating extensions with module that provides them.
     */
    abstract val moduleName: String?

    /**
     * Method that returns version of this module. Can be used by CirJackson for informational purposes.
     */
    abstract override fun version(): Version

    /**
     * Accessor that returns an id that may be used to determine if two [CirJacksonModule] instances are considered to
     * be of same type, for purpose of preventing multiple registrations of "same" module,
     *
     * Default implementation returns value of class name ([KClass.qualifiedName]).
     */
    open val registrationId: Any
        get() = this::class.qualifiedName!!

    /**
     * Returns the list of dependent modules this module has, if any. It is called to let modules register other modules
     * as dependencies. Modules returned will be registered before this module is registered, in iteration order.
     */
    open val dependencies: Iterable<CirJacksonModule>
        get() = emptyList()

    /*
     *******************************************************************************************************************
     * Lifecycle: registration
     *******************************************************************************************************************
     */

    /**
     * Method called by [ObjectMapper] when module is registered. It is called to let module register functionality it
     * provides, using callback methods passed-in context object exposes.
     */
    abstract fun setupModule(context: SetupContext)

    /**
     * Interface CirJackson exposes to modules for purpose of registering extended functionality. Usually implemented by
     * [ObjectMapper], but modules should NOT rely on this -- if they do require access to mapper instance, they need to
     * call [SetupContext.owner] accessor.
     */
    interface SetupContext {

        /*
         ***************************************************************************************************************
         * Simple accessors
         ***************************************************************************************************************
         */

        /**
         * Accessor that returns version information about [ObjectMapper] that implements this context. Modules can use
         * this to choose different settings or initialization order; or even decide to fail set up completely if
         * version is compatible with module.
         */
        val mapperVersion: Version

        val formatName: String

        /**
         * Fallback accessor that allows modules to refer to the
         * [org.cirjson.cirjackson.databind.configuration.MapperBuilder] that provided this context. It should NOT be
         * needed by most modules; and ideally should not be used -- however, there may be cases where this may be
         * necessary due to various design constraints.
         *
         * NOTE: use of this method is discouraged, as it allows access to things Modules typically should not modify.
         * It is included, however, to allow access to new features in cases where Module API has not yet been extended,
         * or there are oversights.
         *
         * Return value is chosen to force casting, to make caller aware that this is a fallback accessor, used only
         * when everything else fails: type is, however, guaranteed to be
         * [org.cirjson.cirjackson.databind.configuration.MapperBuilder] (and more specifically format-specific subtype
         * that mapper constructed, in case format-specific access is needed).
         */
        val owner: Any

        /**
         * Accessor for finding [TypeFactory] that is currently configured by the context.
         *
         * NOTE: since it is possible that other modules might change or replace TypeFactory, use of this method adds
         * order-dependency for registrations.
         */
        fun typeFactory(): TypeFactory

        fun tokenStreamFactory(): TokenStreamFactory

        fun isEnabled(feature: MapperFeature): Boolean

        fun isEnabled(feature: DeserializationFeature): Boolean

        fun isEnabled(feature: SerializationFeature): Boolean

        fun isEnabled(feature: TokenStreamFactory.Feature): Boolean

        fun isEnabled(feature: StreamReadFeature): Boolean

        fun isEnabled(feature: StreamWriteFeature): Boolean

        /*
         ***************************************************************************************************************
         * Mutant accessors
         ***************************************************************************************************************
         */

        /**
         * "Mutant accessor" for getting a mutable configuration override object for given type, needed to add or change
         * per-type overrides applied to properties of given type. Usage is through returned object by colling "setter"
         * methods, which directly modify override object and take effect directly. For example, you can do
         * ```
         * mapper.configOverride(Date::class).setFormat(CirJsonFormat.Value.forPattern("yyyy-MM-dd"))
         * ```
         * to change the default format to use for properties of type [java.util.Date] (possibly further overridden by
         * per-property annotations)
         */
        fun configOverride(type: KClass<*>): MutableConfigOverride

        /*
         ***************************************************************************************************************
         * Handler registration; deserializers, related
         ***************************************************************************************************************
         */

        /**
         * Method that module can use to register additional deserializers to use for handling types.
         *
         * @param deserializers Object that can be called to find deserializer for types supported by module (`null`
         * returned for non-supported types)
         */
        fun addDeserializers(deserializers: Deserializers): SetupContext

        /**
         * Method that module can use to register additional deserializers to use for handling Map key values (which are
         * separate from value deserializers because they are always serialized from String values)
         */
        fun addKeyDeserializers(deserializers: KeyDeserializers): SetupContext

        /**
         * Method that module can use to register additional modifier objects to customize configuration and
         * construction of bean deserializers.
         *
         * @param modifier Modifier to register
         */
        fun addDeserializerModifier(modifier: ValueDeserializerModifier): SetupContext

        /**
         * Method that module can use to register additional
         * [ValueInstantiators][org.cirjson.cirjackson.databind.deserialization.ValueInstantiator], by adding
         * [ValueInstantiators] object that gets called when instantiator is needed by a deserializer.
         *
         * @param instantiators Object that can provide
         * [ValueInstantiators][org.cirjson.cirjackson.databind.deserialization.ValueInstantiator] for constructing POJO
         * values during deserialization
         */
        fun addValueInstantiators(instantiators: ValueInstantiators): SetupContext

        /*
         ***************************************************************************************************************
         * Handler registration; serializers, related
         ***************************************************************************************************************
         */

        /**
         * Method that module can use to register additional serializers to use for handling types.
         *
         * @param serializers Object that can be called to find serializer for types supported by module (`null`
         * returned for non-supported types)
         */
        fun addSerializers(serializers: Serializers): SetupContext

        /**
         * Method that module can use to register additional serializers to use for handling Map key values (which are
         * separate from value serializers because they must write `CirJsonToken.FIELD_NAME` instead of String value).
         */
        fun addKeySerializers(serializers: Serializers): SetupContext

        /**
         * Method that module can use to register additional modifier objects to customize configuration and
         * construction of bean serializers.
         *
         * @param modifier Modifier to register
         */
        fun addSerializerModifier(modifier: ValueSerializerModifier): SetupContext

        /**
         * Method that module can use to override handler called to write JSON Object key for [Map] values.
         *
         * @param serializer Serializer called to write output for CirJSON Object key of which value on Kotlin side is
         * `null`
         */
        fun overrideDefaultNullKeySerializer(serializer: ValueSerializer<*>): SetupContext

        /**
         * Method that module can use to override handler called to write Kotlin `null` as a value (Property or Map
         * value, Collection/array element).
         *
         * @param serializer Serializer called to write output for Kotlin `null` as value (as distinct from key)
         */
        fun overrideDefaultNullValueSerializer(serializer: ValueSerializer<*>): SetupContext

        /*
         ***************************************************************************************************************
         * Handler registration; annotation introspectors
         ***************************************************************************************************************
         */

        /**
         * Method for registering specified [AnnotationIntrospector] as the highest priority introspector (will be
         * chained with existing introspector(s) which will be used as fallbacks for cases this introspector does not
         * handle)
         *
         * @param annotationIntrospector Annotation introspector to register.
         */
        fun insertAnnotationIntrospector(annotationIntrospector: AnnotationIntrospector): SetupContext

        /**
         * Method for registering specified [AnnotationIntrospector] as the lowest priority introspector, chained with existing introspector(s) and called as fallback for cases not otherwise handled.
         *
         * @param annotationIntrospector Annotation introspector to register.
         */
        fun appendAnnotationIntrospector(annotationIntrospector: AnnotationIntrospector): SetupContext

        /*
         ***************************************************************************************************************
         * Type handling
         ***************************************************************************************************************
         */

        /**
         * Method that module can use to register additional [AbstractTypeResolver] instance, to handle resolution of
         * abstract to concrete types (either by defaulting, or by materializing).
         *
         * @param resolver Resolver to add.
         */
        fun addAbstractTypeResolver(resolver: AbstractTypeResolver): SetupContext

        /**
         * Method that module can use to register additional [TypeModifier] instance, which can augment [KotlinType]
         * instances constructed by [org.cirjson.cirjackson.databind.type.TypeFactory].
         *
         * @param modifier to add
         */
        fun addTypeModifier(modifier: TypeModifier): SetupContext

        /**
         * Method for registering specified classes as subtypes (of supertype(s) they have).
         */
        fun registerSubtypes(vararg subtypes: KClass<*>): SetupContext

        /**
         * Method for registering specified classes as subtypes (of supertype(s) they have), using specified type names.
         */
        fun registerSubtypes(vararg subtypes: NamedType): SetupContext

        /**
         * Method for registering specified classes as subtypes (of supertype(s) they have).
         */
        fun registerSubtypes(subtypes: Collection<KClass<*>>): SetupContext

        /*
         ***************************************************************************************************************
         * Handler registration; other
         ***************************************************************************************************************
         */

        /**
         * Add a deserialization problem handler
         *
         * @param handler The deserialization problem handler
         */
        fun addHandler(handler: DeserializationProblemHandler): SetupContext

        /**
         * Replace default [InjectableValues] that have been configured to be used for mapper being built.
         */
        fun overrideInjectableValues(values: (InjectableValues?) -> InjectableValues?): SetupContext

        /**
         * Method used for defining mix-in annotations to use for augmenting specified class or interface. All
         * annotations from `mixinSource` are taken to override annotations that `target` (or its supertypes) has.
         *
         * Note: mix-ins are registered both for serialization and deserialization (which can be different internally).
         *
         * Note: currently only one set of mix-in annotations can be defined for a single class; so if multiple modules
         * register mix-ins, highest priority one (last one registered) will have priority over other modules.
         *
         * @param target Class (or interface) whose annotations to effectively override
         *
         * @param mixinSource Class (or interface) whose annotations are to be "added" to target's annotations,
         * overriding as necessary
         */
        fun setMixIn(target: KClass<*>, mixinSource: KClass<*>): SetupContext

    }

}