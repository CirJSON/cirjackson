package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.implementation.SubTypeValidator
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer
import org.cirjson.cirjackson.databind.deserialization.implementation.*
import org.cirjson.cirjackson.databind.deserialization.jdk.ThrowableDeserializer
import org.cirjson.cirjackson.databind.exception.InvalidDefinitionException
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.util.*
import kotlin.math.max
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

        addBackReferenceProperties(context, beanDescription, builder)
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

        addBackReferenceProperties(context, builderDescription, builder)
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
        val config = context.config
        var builder = constructBeanDeserializerBuilder(context, beanDescription)
        builder.valueInstantiator = findValueInstantiator(context, beanDescription)

        addBeanProperties(context, beanDescription, builder)

        val iterator = builder.properties

        while (iterator.hasNext()) {
            val property = iterator.next()
            val name = property.member!!.name

            if (name == "setCause" || name == "cause") {
                iterator.remove()
                break
            }
        }

        beanDescription.findMethod("initCause", INIT_CAUSE_PARAMS)?.let { annotatedMethod ->
            val propertyNamingStrategy = config.propertyNamingStrategy
            val name = propertyNamingStrategy?.nameForSetterMethod(config, annotatedMethod, "cause") ?: "cause"
            val propertyDefinition = SimpleBeanPropertyDefinition.construct(config, annotatedMethod, PropertyName(name))
            val property = constructSettableProperty(context, beanDescription, propertyDefinition,
                    annotatedMethod.getParameterType(0)!!) ?: return@let
            builder.addOrReplaceProperty(property, true)
        }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                builder = modifier.updateBuilder(config, beanDescription, builder)
            }
        }

        var deserializer = builder.build()

        if (deserializer is BeanDeserializer) {
            deserializer = ThrowableDeserializer.construct(deserializer)
        }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyDeserializer(config, beanDescription, deserializer)
            }
        }

        return deserializer as ValueDeserializer<Any>
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
        return BeanDeserializerBuilder(beanDescription, context)
    }

    /**
     * Method called to figure out settable properties for the bean deserializer to use.
     *
     * Note: designed to be overridable, and effort is made to keep interface similar between versions.
     */
    protected open fun addBeanProperties(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {
        val config = context.config
        val isConcrete = !beanDescription.type.isAbstract
        val creatorProperties = builder.takeUnless { isConcrete }?.valueInstantiator?.getFromObjectArguments(config)
        val hasCreatorProperties = creatorProperties != null

        val ignorals = config.getDefaultPropertyIgnorals(beanDescription.beanClass, beanDescription.classInfo)

        val ignored = ignorals?.let {
            val ignoreUnknown = it.ignoreUnknown
            builder.ignoreUnknownProperties = ignoreUnknown
            it.findIgnoredForDeserialization().also { ignored ->
                for (propertyName in ignored) {
                    builder.addIgnorable(propertyName)
                }
            }
        } ?: emptySet()

        val inclusions = config.getDefaultPropertyInclusions(beanDescription.beanClass, beanDescription.classInfo)

        val included = inclusions?.included?.also {
            for (propertyName in it) {
                builder.addIncludable(propertyName)
            }
        }

        val anySetter = resolveAnySetter(context, beanDescription, creatorProperties)

        if (anySetter != null) {
            builder.anySetter = anySetter
        } else {
            val ignoredPropertyNames = beanDescription.ignoredPropertyNames

            for (propertyName in ignoredPropertyNames) {
                builder.addIgnorable(propertyName)
            }
        }

        val useGettersAsSetters = context.isEnabled(MapperFeature.USE_GETTERS_AS_SETTERS)

        var propertyDefinitions =
                filterBeanProperties(context, beanDescription, builder, beanDescription.findProperties(), ignored,
                        included)

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                propertyDefinitions = modifier.updateProperties(config, beanDescription, propertyDefinitions)
            }
        }

        for (propertyDefinition in propertyDefinitions) {
            val property = if (propertyDefinition.hasSetter()) {
                val setter = propertyDefinition.setter!!
                val propertyType = setter.getParameterType(0)!!
                constructSettableProperty(context, beanDescription, propertyDefinition, propertyType)
            } else if (propertyDefinition.hasField()) {
                val field = propertyDefinition.field!!
                val propertyType = field.type
                constructSettableProperty(context, beanDescription, propertyDefinition, propertyType)
            } else {
                propertyDefinition.getter?.let { getter ->
                    if (useGettersAsSetters && isSetterlessType(getter.rawType)) {
                        if (!builder.hasIgnorable(propertyDefinition.name)) {
                            constructSetterlessProperty(context, beanDescription, propertyDefinition)
                        } else {
                            null
                        }
                    } else if (!propertyDefinition.hasConstructorParameter()) {
                        val metadata = propertyDefinition.metadata

                        if (metadata.mergeInfo != null) {
                            constructSetterlessProperty(context, beanDescription, propertyDefinition)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }

            if (hasCreatorProperties && propertyDefinition.hasConstructorParameter()) {
                val name = propertyDefinition.name
                var creatorProperty: CreatorProperty? = null

                for (settableCreatorProperty in creatorProperties) {
                    if (name == settableCreatorProperty.name && settableCreatorProperty is CreatorProperty) {
                        creatorProperty = settableCreatorProperty
                        break
                    }
                }

                if (creatorProperty == null) {
                    val names = ArrayList<String>()

                    for (settableCreatorProperty in creatorProperties) {
                        names.add(settableCreatorProperty.name)
                    }

                    context.reportBadPropertyDefinition(beanDescription, propertyDefinition,
                            "Could not find creator property with name ${name.name()} (known Creator properties: $names)")
                }

                if (property != null) {
                    creatorProperty.fallbackSetter = property
                }

                val views = propertyDefinition.findViews() ?: beanDescription.findDefaultViews()
                creatorProperty.views = views
                builder.addCreatorProperty(creatorProperty)
                continue
            }

            property ?: continue
            val views = propertyDefinition.findViews() ?: beanDescription.findDefaultViews()
            property.views = views
            builder.addProperty(property)
        }
    }

    private fun resolveAnySetter(context: DeserializationContext, beanDescription: BeanDescription,
            creatorProperties: Array<SettableBeanProperty>?): SettableAnyProperty? {
        val config = context.config
        beanDescription.findAnySetterAccessor()?.let { anySetter ->
            return constructAnySetter(context, beanDescription, anySetter)
        }

        creatorProperties ?: return null

        for (property in creatorProperties) {
            val member = property.member ?: continue

            if (context.annotationIntrospector!!.hasAnySetter(config, member) ?: false) {
                return constructAnySetter(context, beanDescription, member)
            }
        }

        return null
    }

    private fun isSetterlessType(rawType: KClass<*>): Boolean {
        return Collection::class.isAssignableFrom(rawType) || Map::class.isAssignableFrom(rawType)
    }

    /**
     * Helper method called to filter out explicit ignored properties, as well as properties that have "ignorable
     * types". Note that this will not remove properties that have no setters.
     */
    protected open fun filterBeanProperties(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder, propertyDefinitions: List<BeanPropertyDefinition>, ignored: Set<String>?,
            included: Set<String>?): MutableList<BeanPropertyDefinition> {
        val result = ArrayList<BeanPropertyDefinition>(max(4, propertyDefinitions.size))
        val ignoredTypes = HashMap<KClass<*>, Boolean>()

        for (property in propertyDefinitions) {
            val name = property.name

            if (IgnorePropertiesUtil.shouldIgnore(name, ignored, included)) {
                continue
            }

            if (property.hasConstructorParameter()) {
                result.add(property)
                continue
            }

            val rawPropertyType = property.rawPrimaryType

            if (isIgnorableType(context, property, rawPropertyType, ignoredTypes)) {
                builder.addIncludable(name)
                continue
            }

            result.add(property)
        }

        return result
    }

    /**
     * Method that will find if bean has any managed- or back-reference properties, and if so add them to bean, to be
     * linked during resolution phase.
     */
    protected open fun addBackReferenceProperties(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {
        val referenceProperties = beanDescription.findBackReferences() ?: return

        for (referenceProperty in referenceProperties) {
            val referenceName = referenceProperty.findReferenceName()!!
            builder.addBackReferenceProperty(referenceName,
                    constructSettableProperty(context, beanDescription, referenceProperty,
                            referenceProperty.primaryType)!!)
        }
    }

    /**
     * Method called locate all members used for value injection (if any), constructor
     * [org.cirjson.cirjackson.databind.deserialization.implementation.ValueInjector] instances, and add them to
     * builder.
     */
    protected open fun addInjectables(context: DeserializationContext, beanDescription: BeanDescription,
            builder: BeanDeserializerBuilder) {
        val raw = beanDescription.findInjectables()

        for ((key, member) in raw) {
            builder.addInjectable(PropertyName.construct(member.name), member.type, beanDescription.classAnnotations,
                    member, key)
        }
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
        var parameterIndex = -1

        val (types, property) = if (mutator is AnnotatedMethod) {
            val keyType = mutator.getParameterType(0)!!
            var valueType = mutator.getParameterType(1)!!
            valueType = resolveMemberAndTypeAnnotations(context, mutator, valueType)
            val property = BeanProperty.Standard(PropertyName.construct(mutator.name), valueType, null, mutator,
                    PropertyMetadata.STANDARD_OPTIONAL)
            keyType to valueType to property
        } else if (mutator is AnnotatedField) {
            var fieldType = mutator.type

            if (fieldType.isMapLikeType) {
                fieldType = resolveMemberAndTypeAnnotations(context, mutator, fieldType)
                val keyType = fieldType.keyType!!
                val valueType = fieldType.contentType!!
                val property = BeanProperty.Standard(PropertyName.construct(mutator.name), fieldType, null, mutator,
                        PropertyMetadata.STANDARD_OPTIONAL)
                keyType to valueType to property
            } else if (fieldType.hasRawClass(CirJsonNode::class) || fieldType.hasRawClass(ObjectNode::class)) {
                fieldType = resolveMemberAndTypeAnnotations(context, mutator, fieldType)
                val valueType = context.constructType(CirJsonNode::class)!!
                val property = BeanProperty.Standard(PropertyName.construct(mutator.name), fieldType, null, mutator,
                        PropertyMetadata.STANDARD_OPTIONAL)
                return SettableAnyProperty.constructForCirJsonNodeField(context, property, mutator, valueType,
                        context.findRootValueDeserializer(valueType))
            } else {
                context.reportBadDefinition(beanDescription.type,
                        "Unsupported type for any-setter: ${fieldType.typeDescription} -- only support `Map`, `CirJsonNode` and `ObjectNode`")
            }
        } else if (mutator is AnnotatedParameter) {
            var parameterType = mutator.type
            parameterIndex = mutator.index

            if (parameterType.isMapLikeType) {
                parameterType = resolveMemberAndTypeAnnotations(context, mutator, parameterType)
                val keyType = parameterType.keyType!!
                val valueType = parameterType.contentType!!
                val property = BeanProperty.Standard(PropertyName.construct(mutator.name), parameterType, null, mutator,
                        PropertyMetadata.STANDARD_OPTIONAL)
                keyType to valueType to property
            } else if (parameterType.hasRawClass(CirJsonNode::class) || parameterType.hasRawClass(ObjectNode::class)) {
                parameterType = resolveMemberAndTypeAnnotations(context, mutator, parameterType)
                val valueType = context.constructType(CirJsonNode::class)!!
                val property = BeanProperty.Standard(PropertyName.construct(mutator.name), parameterType, null, mutator,
                        PropertyMetadata.STANDARD_OPTIONAL)
                return SettableAnyProperty.constructForCirJsonNodeParameter(context, property, mutator, valueType,
                        context.findRootValueDeserializer(valueType), parameterIndex)
            } else {
                context.reportBadDefinition(beanDescription.type,
                        "Unsupported type for any-setter: ${parameterType.typeDescription} -- only support `Map`, `CirJsonNode` and `ObjectNode`")
            }
        } else {
            context.reportBadDefinition(beanDescription.type,
                    "Unrecognized mutator type for any-setter: ${mutator::class.name}")
        }

        val (keyType, valueType) = types

        val keyDeserializer = (findKeyDeserializerFromAnnotation(context, mutator)
                ?: keyType.valueHandler as KeyDeserializer?)?.let {
            (it as? ContextualKeyDeserializer)?.createContextual(context, property) ?: it
        } ?: context.findKeyDeserializer(keyType, property)
        val deserializer = (findContentDeserializerFromAnnotation(context, mutator)
                ?: valueType.valueHandler as ValueDeserializer<Any>?)?.let {
            context.handlePrimaryContextualization(it, property, valueType) as ValueDeserializer<Any>
        }
        val typeDeserializer = valueType.typeHandler as TypeDeserializer?

        return when (mutator) {
            is AnnotatedField -> SettableAnyProperty.constructForMapField(context, property, mutator, valueType,
                    keyDeserializer, deserializer, typeDeserializer)

            is AnnotatedParameter -> SettableAnyProperty.constructForMapParameter(property, mutator, valueType,
                    keyDeserializer, deserializer, typeDeserializer, parameterIndex)

            else -> SettableAnyProperty.constructForMethod(property, mutator, valueType, keyDeserializer, deserializer,
                    typeDeserializer)
        }
    }

    /**
     * Method that will construct a regular bean property setter using the given setter method.
     *
     * @return Property constructed, if any; or `null` to indicate that there should be no property based on given
     * definitions.
     */
    protected open fun constructSettableProperty(context: DeserializationContext, beanDescription: BeanDescription,
            propertyDefinition: BeanPropertyDefinition, propertyType: KotlinType): SettableBeanProperty? {
        val mutator = propertyDefinition.nonConstructorMutator ?: context.reportBadPropertyDefinition(beanDescription,
                propertyDefinition, "No non-constructor mutator available")
        val type = resolveMemberAndTypeAnnotations(context, mutator, propertyType)
        val typeDeserializer = type.typeHandler as TypeDeserializer?

        var property = if (mutator is AnnotatedMethod) {
            MethodProperty(propertyDefinition, type, typeDeserializer, beanDescription.classAnnotations, mutator)
        } else {
            FieldProperty(propertyDefinition, type, typeDeserializer, beanDescription.classAnnotations,
                    mutator as AnnotatedField)
        }

        (findDeserializerFromAnnotation(context, mutator) ?: type.valueHandler as ValueDeserializer<*>?)?.let {
            val deserializer = context.handlePrimaryContextualization(it, property, type)!!
            property = property.withValueDeserializer(deserializer)
        }

        val referenceProperty = propertyDefinition.findReferenceType()

        if (referenceProperty?.isManagedReference ?: false) {
            property.managedReferenceName = referenceProperty.name
        }

        propertyDefinition.findObjectIdInfo()?.let { objectIdInfo ->
            property.objectIdInfo = objectIdInfo
        }

        return property
    }

    /**
     * Method that will construct a regular bean property setter using the given setter method.
     */
    protected open fun constructSetterlessProperty(context: DeserializationContext, beanDescription: BeanDescription,
            propertyDefinition: BeanPropertyDefinition): SettableBeanProperty {
        val getter = propertyDefinition.getter!!
        val type = resolveMemberAndTypeAnnotations(context, getter, getter.type)
        val typeDeserializer = type.typeHandler as TypeDeserializer?

        val property =
                SetterlessProperty(propertyDefinition, type, typeDeserializer, beanDescription.classAnnotations, getter)
        val deserializer = findDeserializerFromAnnotation(context, getter) ?: type.valueHandler as ValueDeserializer<*>?

        return if (deserializer != null) {
            val valueDeserializer = context.handlePrimaryContextualization(deserializer, property, type)!!
            property.withValueDeserializer(valueDeserializer)
        } else {
            property
        }
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
        type.canBeABeanType()?.let {
            throw IllegalArgumentException("Cannot deserialize KClass ${type.qualifiedName} (of type $it) as a Bean")
        }

        if (type.isProxyType) {
            throw IllegalArgumentException("Cannot deserialize Proxy class ${type.qualifiedName} as a Bean")
        }

        type.isLocalType(true)?.let {
            throw IllegalArgumentException("Cannot deserialize KClass ${type.qualifiedName} (of type $it) as a Bean")
        }

        return true
    }

    /**
     * Helper method that will check whether given raw type is marked as always ignorable (for purpose of ignoring
     * properties with type)
     */
    protected open fun isIgnorableType(context: DeserializationContext, propertyDefinition: BeanPropertyDefinition,
            type: KClass<*>, ignoredTypes: MutableMap<KClass<*>, Boolean>): Boolean {
        ignoredTypes[type]?.let {
            return it
        }

        if (type == String::class || type.isPrimitive) {
            ignoredTypes[type] = false
            return false
        }

        val config = context.config

        val status =
                config.getConfigOverride(type).isIgnoredType ?: context.annotationIntrospector!!.isIgnorableType(config,
                        context.introspectClassAnnotations(type)) ?: false
        ignoredTypes[type] = status
        return status
    }

    protected open fun validateSubType(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription) {
        SubTypeValidator.INSTANCE.validateSubType(context, type, beanDescription)
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