package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import org.cirjson.cirjackson.databind.deserialization.implementation.ErrorThrowingDeserializer
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.PropertyBasedObjectIdGenerator
import org.cirjson.cirjackson.databind.deserialization.implementation.UnsupportedTypeDeserializer
import org.cirjson.cirjackson.databind.exception.InvalidDefinitionException
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Concrete deserializer factory class that adds full Bean deserializer construction logic using class introspection.
 * Note that factories specifically do not implement any form of caching: aside from configuration they are stateless;
 * caching is implemented by other components.
 * 
 * Instances of this class are fully immutable as all configuration is done by using "fluent factories" (methods that
 * construct new factory instances with different configuration, instead of modifying instance).
 */
open class BeanDeserializerFactory(config: DeserializerFactoryConfig) : BasicDeserializerFactory(config) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Method used by module registration functionality, to construct a new bean deserializer factory with different
     * configuration settings.
     */
    override fun withConfig(factoryConfig: DeserializerFactoryConfig): DeserializerFactory {
        if (myFactoryConfig === factoryConfig) {
            return this
        }

        verifyMustOverride(BeanDeserializerFactory::class, this, "withConfig")
        return BeanDeserializerFactory(factoryConfig)
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory API implementation
     *******************************************************************************************************************
     */

    /**
     * Method that called to create a new deserializer for types other than Collections, Maps, arrays, referential types
     * or enums, or "well-known" JDK scalar types.
     */
    @Suppress("UNCHECKED_CAST")
    override fun createBeanDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any>? {
        val config = context.config

        findCustomBeanDeserializer(type, config, beanDescription)?.let {
            var deserializer = it

            if (myFactoryConfig.hasDeserializerModifiers()) {
                for (modifier in myFactoryConfig.deserializerModifiers()) {
                    deserializer = modifier.modifyDeserializer(config, beanDescription, deserializer)
                }
            }

            return deserializer as ValueDeserializer<Any>
        }

        if (type.isThrowable) {
            return buildThrowableDeserializer(context, type, beanDescription)
        } else if (type.isAbstract && !type.isPrimitive && !type.isEnumType) {
            val concreteType = materializeAbstractType(context, type, beanDescription)

            if (concreteType != null) {
                return buildBeanDeserializer(context, concreteType, context.introspectBeanDescription(concreteType))
            }
        }

        findStandardDeserializer(context, type, beanDescription)?.let {
            return it as ValueDeserializer<Any>
        }

        if (!isPotentialBeanType(type.rawClass)) {
            return null
        }

        validateSubType(context, type, beanDescription)

        return findUnsupportedTypeDeserializer(context, type, beanDescription) ?: buildBeanDeserializer(context, type,
                beanDescription)
    }

    override fun createBuilderBasedDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription, builderClass: KClass<*>): ValueDeserializer<Any> {
        val builderType = if (context.isEnabled(MapperFeature.INFER_BUILDER_TYPE_BINDINGS)) {
            context.typeFactory.constructParametricType(builderClass, type.bindings)
        } else {
            context.constructType(builderClass)!!
        }

        val builderDescription = context.introspectBeanDescriptionForBuilder(builderType, beanDescription)
        return buildBuilderBasedDeserializer(context, type, builderDescription)
    }

    /**
     * Method called by [BeanDeserializerFactory] to see if there might be a standard deserializer registered for given
     * type.
     */
    protected open fun findStandardDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        var deserializer = findDefaultDeserializer(context, type, beanDescription) ?: return null

        if (myFactoryConfig.hasDeserializerModifiers()) {
            val config = context.config

            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyDeserializer(config, beanDescription, deserializer)
            }
        }

        return deserializer
    }

    /**
     * Helper method called to see if given type, otherwise to be taken as POJO type, is "known but not supported" JDK
     * type, and if so, return alternate handler (deserializer). Initially added to support more meaningful error messages when "Java 8 date/time" support module not registered.
     */
    protected open fun findUnsupportedTypeDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any>? {
        val errorMessage = BeanUtil.checkUnsupportedType(type) ?: return null

        return if (context.config.findMixInClassFor(type.rawClass) == null) {
            UnsupportedTypeDeserializer(type, errorMessage)
        } else {
            null
        }
    }

    protected open fun materializeAbstractType(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): KotlinType? {
        val config = context.config

        for (resolver in config.abstractTypeResolvers()) {
            val concrete = resolver.resolveAbstractType(config, beanDescription)

            if (concrete != null) {
                return concrete
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Public construction method beyond DeserializerFactory API: can be called from outside or overridden by subclasses
     *******************************************************************************************************************
     */

    /**
     * Method that is to actually build a bean deserializer instance. All basic sanity checks have been done to know
     * that the instance may be a valid bean type, and that there are no default simple deserializers.
     */
    @Suppress("UNCHECKED_CAST")
    open fun buildBeanDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any> {
        val valueInstantiator = try {
            findValueInstantiator(context, beanDescription)!!
        } catch (e: NoClassDefFoundError) {
            return ErrorThrowingDeserializer(e)
        } catch (e: IllegalArgumentException) {
            throw InvalidDefinitionException.from(context.parser, e.exceptionMessage(), beanDescription, null)
                    .withCause(e)
        }

        var builder = constructBeanDeserializerBuilder(context, beanDescription)
        builder.valueInstantiator = valueInstantiator
        addBeanProperties(context, beanDescription, builder)
        addObjectIdReader(context, beanDescription, builder)

        addBeanProperties(context, beanDescription, builder)
        addInjectables(context, beanDescription, builder)

        val config = context.config

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                builder = modifier.updateBuilder(config, beanDescription, builder)
            }
        }

        var deserializer = if (type.isAbstract && !valueInstantiator.canInstantiate()) {
            builder.buildAbstract()
        } else {
            builder.build()
        }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyDeserializer(config, beanDescription, deserializer)
            }
        }

        return deserializer as ValueDeserializer<Any>
    }

    /**
     * Method for constructing a bean deserializer that uses specified intermediate Builder for binding data, and
     * construction of the value instance. Note that implementation is mostly copied from the regular BeanDeserializer
     * build method.
     */
    @Suppress("UNCHECKED_CAST")
    open fun buildBuilderBasedDeserializer(context: DeserializationContext, valueType: KotlinType,
            builderDescription: BeanDescription): ValueDeserializer<Any> {
        val valueInstantiator = try {
            findValueInstantiator(context, builderDescription)!!
        } catch (e: NoClassDefFoundError) {
            return ErrorThrowingDeserializer(e)
        } catch (e: IllegalArgumentException) {
            throw InvalidDefinitionException.from(context.parser, e.exceptionMessage(), builderDescription, null)
        }

        val config = context.config
        var builder = constructBeanDeserializerBuilder(context, builderDescription)
        builder.valueInstantiator = valueInstantiator
        addBeanProperties(context, builderDescription, builder)
        addObjectIdReader(context, builderDescription, builder)

        addBeanProperties(context, builderDescription, builder)
        addInjectables(context, builderDescription, builder)

        val builderConfig = builderDescription.findPOJOBuilderConfig()
        val buildMethodName = builderConfig?.buildMethodName ?: CirJsonPOJOBuilder.DEFAULT_BUILD_METHOD

        val buildMethod = builderDescription.findMethod(buildMethodName, null)

        if (buildMethod != null && config.canOverrideAccessModifiers()) {
            buildMethod.member.checkAndFixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
        }

        builder.setPOJOBuilder(buildMethod, builderConfig)

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                builder = modifier.updateBuilder(config, builderDescription, builder)
            }
        }

        var deserializer = builder.buildBuilderBased(valueType, buildMethodName)

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyDeserializer(config, builderDescription, deserializer)
            }
        }

        return deserializer as ValueDeserializer<Any>
    }

    protected open fun addObjectIdReader(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {
        val objectIdInfo = beanDescription.objectIdInfo ?: return
        val implementationType = objectIdInfo.generatorType
        val resolver = context.objectIdResolverInstance(beanDescription.classInfo, objectIdInfo)
        val propertyName = objectIdInfo.propertyName

        val (idInfo, generator) = if (implementationType == ObjectIdGenerators.PropertyGenerator::class) {
            val idProperty = builder.findProperty(propertyName) ?: throw IllegalArgumentException(
                    "Invalid Object Id definition for ${beanDescription.type.typeDescription}: cannot find property with name ${propertyName.name()}")
            idProperty to idProperty.type to PropertyBasedObjectIdGenerator(objectIdInfo.scope!!)
        } else {
            val type = context.constructType(implementationType)!!
            null to context.typeFactory.findTypeParameters(type,
                    ObjectIdGenerator::class)[0]!! to context.objectIdGeneratorInstance(beanDescription.classInfo,
                    objectIdInfo)
        }

        val (idProperty, idType) = idInfo
        val deserializer = context.findRootValueDeserializer(idType)
        builder.objectIdReader =
                ObjectIdReader.construct(idType, propertyName, generator, deserializer, idProperty, resolver)
    }

    @Suppress("UNCHECKED_CAST")
    open fun buildThrowableDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods for Bean deserializer, construction
     *******************************************************************************************************************
     */

    /**
     * Overridable method that constructs a [BeanDeserializerBuilder] which is used to accumulate information needed to
     * create deserializer instance.
     */
    protected open fun constructBeanDeserializerBuilder(context: DeserializationContext,
            beanDescription: BeanDescription): BeanDeserializerBuilder {
        TODO("Not yet implemented")
    }

    /**
     * Method called to figure out settable properties for the bean deserializer to use.
     *
     * Note: designed to be overridable, and effort is made to keep interface similar between versions.
     */
    protected open fun addBeanProperties(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {
        TODO("Not yet implemented")
    }

    private fun resolveAnySetter(context: DeserializationContext, beanDescription: BeanDescription,
            creatorProperties: Array<SettableBeanProperty>?): SettableAnyProperty? {
        TODO("Not yet implemented")
    }

    private fun isSetterlessType(rawType: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to filter out explicit ignored properties, as well as properties that have "ignorable
     * types". Note that this will not remove properties that have no setters.
     */
    protected open fun filterBeanProperties(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder, propertyDefinitions: List<BeanPropertyDefinition>, ignored: Set<String>,
            included: Set<String>): List<BeanPropertyDefinition> {
        TODO("Not yet implemented")
    }

    /**
     * Method that will find if bean has any managed- or back-reference properties, and if so add them to bean, to be
     * linked during resolution phase.
     */
    protected open fun addBackReferenceProperties(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {

    }

    /**
     * Method called locate all members used for value injection (if any), constructor
     * [org.cirjson.cirjackson.databind.deserialization.implementation.ValueInjector] instances, and add them to
     * builder.
     */
    protected open fun addInjectables(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {
        TODO("Not yet implemented")
    }

    /**
     * Method called to construct fallback [SettableAnyProperty] for handling unknown bean properties, given a method
     * that has been designated as such setter.
     *
     * @param mutator Either a 2-argument method (setter, with key and value), or a Field or Constructor Parameter of
     * type Map or CirJsonNode/Object; either way accessor used for passing "any values"
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun constructAnySetter(context: DeserializationContext, beanDescription: BeanDescription,
            mutator: AnnotatedMember): SettableAnyProperty? {
        TODO("Not yet implemented")
    }

    /**
     * Method that will construct a regular bean property setter using the given setter method.
     *
     * @return Property constructed, if any; or `null` to indicate that there should be no property based on given
     * definitions.
     */
    protected open fun constructSettableProperty(context: DeserializationContext, beanDescription: BeanDescription,
            propertyDefinition: BeanPropertyDefinition, propertyType: KotlinType): SettableBeanProperty? {
        TODO("Not yet implemented")
    }

    /**
     * Method that will construct a regular bean property setter using the given setter method.
     */
    protected open fun constructSetterlessProperty(context: DeserializationContext, beanDescription: BeanDescription,
            propertyDefinition: BeanPropertyDefinition): SettableBeanProperty {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods for Bean deserializer, other
     *******************************************************************************************************************
     */

    /**
     * Helper method used to skip processing for types that we know cannot be (i.e. are never consider to be) beans:
     * things like primitives, Arrays, Enums, and proxy types.
     *
     * Note that usually we shouldn't really be getting these sort of types anyway; but better safe than sorry.
     */
    protected open fun isPotentialBeanType(type: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Helper method that will check whether given raw type is marked as always ignorable (for purpose of ignoring
     * properties with type)
     */
    protected open fun isIgnorableType(context: DeserializationContext, propertyDefinition: BeanPropertyDefinition,
            type: KClass<*>, ignoredType: MutableMap<KClass<*>, Boolean>): Boolean {
        TODO("Not yet implemented")
    }

    protected open fun validateSubType(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription) {
        TODO("Not yet implemented")
    }

    companion object {

        /**
         * Signature of [Throwable.initCause] method.
         */
        private val INIT_CAUSE_PARAMS = arrayOf<KClass<*>>(Throwable::class)

        /**
         * Globally shareable thread-safe instance which has no additional custom deserializers registered
         */
        val INSTANCE = BeanDeserializerFactory(DeserializerFactoryConfig())

    }

}