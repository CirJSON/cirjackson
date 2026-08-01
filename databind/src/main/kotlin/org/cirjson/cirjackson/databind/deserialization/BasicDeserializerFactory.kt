package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import org.cirjson.cirjackson.databind.deserialization.bean.CreatorCandidate
import org.cirjson.cirjackson.databind.deserialization.bean.CreatorCollector
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.EnumResolver
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * Abstract factory base class that can provide deserializers for standard JDK classes, including collection classes and
 * simple heuristics for "upcasting" common collection interface types (such as [Collection]).
 * 
 * Since all simple deserializers are eagerly instantiated, and there is no additional introspection or customizability
 * of these types, this factory is stateless.
 * 
 * @property myFactoryConfig Configuration settings for this factory; immutable instance (just like this factory), new
 * version created via copy-constructor (fluent-style)
 */
abstract class BasicDeserializerFactory protected constructor(
        protected val myFactoryConfig: DeserializerFactoryConfig) : DeserializerFactory() {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Accessor for getting current [DeserializerFactoryConfig].
     * 
     * Note that since instances are immutable, you can NOT change settings by accessing an instance and calling
     * methods: this will simply create new instance of config object.
     */
    open val factoryConfig: DeserializerFactoryConfig
        get() = myFactoryConfig

    protected abstract fun withConfig(factoryConfig: DeserializerFactoryConfig): DeserializerFactory

    /*
     *******************************************************************************************************************
     * Configuration handling: fluent factories
     *******************************************************************************************************************
     */

    final override fun withAdditionalDeserializers(additional: Deserializers): DeserializerFactory {
        return withConfig(myFactoryConfig.withAdditionalDeserializers(additional))
    }

    final override fun withAdditionalKeyDeserializers(additional: KeyDeserializers): DeserializerFactory {
        return withConfig(myFactoryConfig.withAdditionalKeyDeserializers(additional))
    }

    final override fun withDeserializerModifier(modifier: ValueDeserializerModifier): DeserializerFactory {
        return withConfig(myFactoryConfig.withDeserializerModifier(modifier))
    }

    final override fun withValueInstantiators(instantiators: ValueInstantiators): DeserializerFactory {
        return withConfig(myFactoryConfig.withValueInstantiators(instantiators))
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: ValueInstantiators
     *******************************************************************************************************************
     */

    /**
     * Value instantiator is created both based on creator annotations, and on optional externally provided
     * instantiators (registered through module interface).
     */
    override fun findValueInstantiator(context: DeserializationContext,
            beanDescription: BeanDescription): ValueInstantiator? {
        TODO("Not yet implemented")
    }

    /**
     * Method that will construct standard default [ValueInstantiator] using annotations (like @CirJsonCreator) and
     * visibility rules
     */
    protected open fun constructDefaultValueInstantiator(context: DeserializationContext,
            beanDescription: BeanDescription): ValueInstantiator {
        TODO("Not yet implemented")
    }

    protected open fun valueInstantiatorInstance(config: DeserializationConfig, annotated: Annotated,
            instanceDefinition: Any?): ValueInstantiator? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Creator introspection: helper methods
     *******************************************************************************************************************
     */

    private fun addExplicitDelegatingCreators(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, potentialCreators: List<PotentialCreator>): Boolean {
        TODO("Not yet implemented")
    }

    private fun addImplicitDelegatingCreators(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, potentialCreators: List<PotentialCreator>): Boolean {
        TODO("Not yet implemented")
    }

    private fun addImplicitDelegatingFactories(visibilityChecker: VisibilityChecker, creators: CreatorCollector,
            potentialCreators: List<PotentialCreator>) {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called when there is the explicit "is-creator" with mode of "delegating"
     */
    private fun addExplicitDelegatingCreator(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, candidate: CreatorCandidate): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to add the single chosen "properties-based" Creator (if any).
     */
    private fun addSelectedPropertiesBasedCreator(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, candidate: CreatorCandidate) {
        TODO("Not yet implemented")
    }

    private fun handleSingleArgumentCreator(creators: CreatorCollector, constructor: AnnotatedWithParams,
            isCreator: Boolean, isVisible: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    private fun reportUnwrappedCreatorProperty(context: DeserializationContext, beanDescription: BeanDescription,
            parameter: AnnotatedParameter): Nothing {
        TODO("Not yet implemented")
    }

    /**
     * Method that will construct a property object that represents a logical property passed via Creator (constructor
     * or static factory method)
     */
    protected open fun constructCreatorProperty(context: DeserializationContext, beanDescription: BeanDescription,
            name: PropertyName?, index: Int, parameter: AnnotatedParameter,
            injectable: CirJacksonInject.Value?): SettableBeanProperty {
        TODO("Not yet implemented")
    }

    /**
     * Helper method copied from `POJOPropertyBuilder` since that won't be applied to creator parameters.
     */
    private fun getSetterInfo(config: DeserializationConfig, property: BeanProperty,
            metadata: PropertyMetadata): PropertyMetadata {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: array deserializers
     *******************************************************************************************************************
     */

    override fun createArrayDeserializer(context: DeserializationContext, type: ArrayType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: Collection(-like) deserializers
     *******************************************************************************************************************
     */

    override fun createCollectionDeserializer(context: DeserializationContext, type: CollectionType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    protected open fun mapAbstractCollectionType(type: KotlinType, config: DeserializationConfig): CollectionType? {
        TODO("Not yet implemented")
    }

    override fun createCollectionLikeDeserializer(context: DeserializationContext, type: CollectionLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: Map(-like) deserializers
     *******************************************************************************************************************
     */

    override fun createMapDeserializer(context: DeserializationContext, type: MapType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    protected open fun mapAbstractMapType(type: KotlinType, config: DeserializationConfig): MapType? {
        TODO("Not yet implemented")
    }

    override fun createMapLikeDeserializer(context: DeserializationContext, type: MapLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: other types
     *******************************************************************************************************************
     */

    override fun createEnumDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun createTreeDeserializer(config: DeserializationConfig, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun createReferenceDeserializer(context: DeserializationContext, type: ReferenceType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: other deserializers
     *******************************************************************************************************************
     */

    /**
     * Overridable method called after checking all other types.
     */
    protected open fun findOptionalStdDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation (partial): key deserializers
     *******************************************************************************************************************
     */

    override fun createKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer? {
        TODO("Not yet implemented")
    }

    private fun createEnumKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: find explicitly supported types
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to check if databind module has deserializer for given (likely JDK) type: explicit
     * meaning that it is not automatically generated for POJO.
     * 
     * This matches [Deserializers.hasDeserializerFor] method.
     */
    override fun hasExplicitDeserializerFor(context: DatabindContext, valueType: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Helper method called to find one of default deserializers for "well-known" platform types: JDK-provided types,
     * and small number of public Jackson API types.
     */
    open fun findDefaultDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    private fun findRemappedType(context: DeserializationContext, rawType: KClass<*>): KotlinType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods, finding custom deserializers
     *******************************************************************************************************************
     */

    protected open fun findCustomTreeNodeDeserializer(nodeType: KClass<out CirJsonNode>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomReferenceDeserializer(referenceType: ReferenceType, config: DeserializationConfig,
            beanDescription: BeanDescription, contentTypeDeserializer: TypeDeserializer?,
            contentDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomBeanDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomArrayDeserializer(type: ArrayType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomCollectionDeserializer(type: CollectionType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomCollectionLikeDeserializer(type: CollectionLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomEnumDeserializer(type: KClass<*>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomMapDeserializer(type: MapType, config: DeserializationConfig,
            beanDescription: BeanDescription, keyDeserializer: KeyDeserializer?,
            elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    protected open fun findCustomMapLikeDeserializer(type: MapLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, keyDeserializer: KeyDeserializer?,
            elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods, value/content/key type introspection
     *******************************************************************************************************************
     */

    /**
     * Helper method called to check if a class or method has annotation that tells which class to use for
     * deserialization; and if so, to instantiate, that deserializer to use. Note that deserializer will NOT yet be
     * contextualized so caller needs to take care to call contextualization appropriately. Returns `null` if no such
     * annotation found.
     */
    protected open fun findDeserializerFromAnnotation(context: DeserializationContext,
            annotated: Annotated): ValueDeserializer<Any>? {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to check if a class or method has annotation that tells which class to use for
     * deserialization of [Map] keys. Returns `null` if no such annotation found.
     */
    protected open fun findKeyDeserializerFromAnnotation(context: DeserializationContext,
            annotated: Annotated): KeyDeserializer? {
        TODO("Not yet implemented")
    }

    protected open fun findContentDeserializerFromAnnotation(context: DeserializationContext,
            annotated: Annotated): ValueDeserializer<Any>? {
        TODO("Not yet implemented")
    }

    /**
     * Helper method used to resolve additional type-related annotation information like type overrides, or handler
     * (serializer, deserializer) overrides, so that from declared field, property or constructor parameter type is used
     * as the base and modified based on annotations, if any.
     */
    protected open fun resolveMemberAndTypeAnnotations(context: DeserializationContext, member: AnnotatedMember,
            type: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    protected open fun constructEnumResolver(context: DeserializationContext, enumClass: KClass<*>,
            beanDescription: BeanDescription): EnumResolver {
        TODO("Not yet implemented")
    }

    /**
     * Factory method used to resolve an instance of [CompactStringObjectMap] with [EnumNamingStrategy] applied for the
     * target class.
     */
    protected open fun constructEnumNamingStrategyResolver(config: DeserializationConfig,
            enumClass: AnnotatedClass): EnumResolver? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Helper object to contain default mappings for abstract JDK [Collection] and [Map] types. Separated out here to
     * defer cost of creating lookups until mappings are actually needed.
     */
    protected object ContainerDefaultMappings {

        val ourCollectionFallbacks = HashMap<String, KClass<out Collection<*>>>().apply {
        }

        val ourMapFallbacks = HashMap<String, KClass<out Map<*, *>>>().apply {
        }

        fun findCollectionFallbacks(type: KotlinType): KClass<*>? {
            TODO("Not yet implemented")
        }

        fun findMapFallbacks(type: KotlinType): KClass<*>? {
            TODO("Not yet implemented")
        }

    }

    companion object {

        private val CLASS_ANY = Any::class

        private val CLASS_STRING = String::class

        private val CLASS_CHAR_SEQUENCE = CharSequence::class

        private val CLASS_ITERABLE = Iterable::class

        private val CLASS_MAP_ENTRY = Map.Entry::class

        private val CLASS_SERIALIZABLE = Serializable::class

        val UNWRAPPED_CREATOR_PARAM_NAME = PropertyName("@CirJsonUnwrapped")

    }

}