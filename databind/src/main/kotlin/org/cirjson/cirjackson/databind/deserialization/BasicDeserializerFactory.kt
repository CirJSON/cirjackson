package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import org.cirjson.cirjackson.databind.deserialization.bean.CreatorCandidate
import org.cirjson.cirjackson.databind.deserialization.bean.CreatorCollector
import org.cirjson.cirjackson.databind.deserialization.cirjackson.CirJsonNodeDeserializer
import org.cirjson.cirjackson.databind.deserialization.cirjackson.TokenBufferDeserializer
import org.cirjson.cirjackson.databind.deserialization.jdk.*
import org.cirjson.cirjackson.databind.external.OptionalHandlerFactory
import org.cirjson.cirjackson.databind.external.jdk8.Jdk8OptionalDeserializer
import org.cirjson.cirjackson.databind.external.jdk8.OptionalDoubleDeserializer
import org.cirjson.cirjackson.databind.external.jdk8.OptionalIntDeserializer
import org.cirjson.cirjackson.databind.external.jdk8.OptionalLongDeserializer
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.*
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
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
        val config = context.config
        val hasCustom = myFactoryConfig.hasValueInstantiators()

        val annotatedClass = beanDescription.classInfo
        val instantiatorDefinition = config.annotationIntrospector!!.findValueInstantiator(config, annotatedClass)

        var instantiator = instantiatorDefinition?.let { valueInstantiatorInstance(config, annotatedClass, it) }
                ?: JDKValueInstantiators.findStandardValueInstantiator(beanDescription.beanClass) ?: let {
                    var instantiator: ValueInstantiator? = null

                    if (hasCustom) {
                        for (instantiators in myFactoryConfig.valueInstantiators()) {
                            instantiator = instantiators.findValueInstantiator(config, beanDescription)

                            if (instantiator != null) {
                                break
                            }
                        }
                    }

                    instantiator ?: constructDefaultValueInstantiator(context, beanDescription)
                }

        if (hasCustom) {
            for (instantiators in myFactoryConfig.valueInstantiators()) {
                instantiator = instantiators.modifyValueInstantiator(config, beanDescription, instantiator)
            }
        }

        instantiator = instantiator.createContextual(context, beanDescription)

        return instantiator
    }

    /**
     * Method that will construct standard default [ValueInstantiator] using annotations (like @CirJsonCreator) and
     * visibility rules
     */
    protected open fun constructDefaultValueInstantiator(context: DeserializationContext,
            beanDescription: BeanDescription): ValueInstantiator {
        val config = context.config
        val potentialCreators = beanDescription.potentialCreators
        val constructorDetector = config.constructorDetector
        val visibilityChecker = config.getDefaultVisibilityChecker(beanDescription.beanClass, beanDescription.classInfo)

        val creators = CreatorCollector(beanDescription, config)


        if (potentialCreators.hasPropertiesBased()) {
            val primaryPropertiesBased = potentialCreators.propertiesBased!!

            addSelectedPropertiesBasedCreator(context, beanDescription, creators,
                    CreatorCandidate.construct(config, primaryPropertiesBased.creator(),
                            primaryPropertiesBased.propertyDefinitions()))
        }

        val hasExplicitDelegating =
                addExplicitDelegatingCreators(context, beanDescription, creators, potentialCreators.explicitDelegating)

        if (beanDescription.type.isConcrete) {
            val isNonStaticInnerClass = beanDescription.isNonStaticInnerClass

            if (!isNonStaticInnerClass) {
                beanDescription.findDefaultConstructor()?.also { defaultConstructor ->
                    if (!creators.hasDefaultCreator() || hasCreatorAnnotation(config, defaultConstructor)) {
                        creators.defaultCreator = defaultConstructor
                    }
                }

                val findImplicit = constructorDetector.shouldIntrospectorImplicitConstructors(beanDescription.beanClass)

                if (findImplicit) {
                    addImplicitDelegatingCreators(context, beanDescription, creators,
                            potentialCreators.implicitDelegatingConstructors)
                }
            }
        }

        if (!hasExplicitDelegating) {
            addImplicitDelegatingFactories(visibilityChecker, creators, potentialCreators.implicitDelegatingFactories)
        }

        return creators.constructValueInstantiator(context)
    }

    protected open fun valueInstantiatorInstance(config: DeserializationConfig, annotated: Annotated,
            instanceDefinition: Any?): ValueInstantiator? {
        instanceDefinition ?: return null

        return instanceDefinition as? ValueInstantiator ?: if (instanceDefinition !is KClass<*>) {
            throw IllegalStateException(
                    "AnnotationIntrospector returned value instantiator definition of type ${instanceDefinition::class.qualifiedName}; expected type ValueInstantiator or KClass<ValueInstantiator> instead")
        } else if (instanceDefinition.isBogusClass) {
            null
        } else if (!ValueInstantiator::class.isAssignableFrom(instanceDefinition)) {
            throw IllegalStateException(
                    "AnnotationIntrospector returned KClass ${instanceDefinition.qualifiedName}; expected KClass<ValueInstantiator>")
        } else {
            val handlerInstantiator = config.handlerInstantiator
            handlerInstantiator?.valueInstantiatorInstance(config, annotated, instanceDefinition)
                    ?: instanceDefinition.createInstance(config.canOverrideAccessModifiers()) as ValueInstantiator
        }
    }

    /*
     *******************************************************************************************************************
     * Creator introspection: helper methods
     *******************************************************************************************************************
     */

    private fun addExplicitDelegatingCreators(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, potentialCreators: List<PotentialCreator>): Boolean {
        val config = context.config
        var added = false

        for (potentialCreator in potentialCreators) {
            added = added || addExplicitDelegatingCreator(context, beanDescription, creators,
                    CreatorCandidate.construct(config, potentialCreator.creator(), null))
        }

        return added
    }

    private fun addImplicitDelegatingCreators(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, potentialCreators: List<PotentialCreator>) {
        val config = context.config
        val introspector = config.annotationIntrospector

        for (candidate in potentialCreators) {
            val argCount = candidate.parameterCount()
            val creator = candidate.creator()

            if (argCount == 1) {
                handleSingleArgumentCreator(creators, creator, isCreator = false, isVisible = true)
                continue
            }

            val properties = arrayOfNulls<SettableBeanProperty>(argCount)
            var injectCount = 0

            for (i in 0..<argCount) {
                val parameter = creator.getParameter(i)
                val injectable = introspector!!.findInjectableValue(config, parameter)

                if (injectable != null) {
                    ++injectCount
                    properties[i] = constructCreatorProperty(context, beanDescription, null, i, parameter, injectable)
                    continue
                }

                val unwrapper = introspector.findUnwrappingNameTransformer(config, parameter)

                if (unwrapper != null) {
                    reportUnwrappedCreatorProperty(context, beanDescription, parameter)
                }
            }

            if (injectCount + 1 == argCount) {
                creators.addDelegatingCreator(creator, false, properties, 0)
            }
        }
    }

    private fun addImplicitDelegatingFactories(visibilityChecker: VisibilityChecker, creators: CreatorCollector,
            potentialCreators: List<PotentialCreator>) {
        for (potentialCreator in potentialCreators) {
            val argCount = potentialCreator.parameterCount()
            val creator = potentialCreator.creator()

            if (argCount == 1) {
                handleSingleArgumentCreator(creators, creator, false, visibilityChecker.isCreatorVisible(creator))
            }
        }
    }

    /**
     * Helper method called when there is the explicit "is-creator" with mode of "delegating"
     */
    private fun addExplicitDelegatingCreator(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, candidate: CreatorCandidate): Boolean {
        var index = -1
        val argCount = candidate.parameterCount()
        val properties = arrayOfNulls<SettableBeanProperty>(argCount)

        for (i in 0..<argCount) {
            val parameter = candidate.parameter(i)
            val injectable = candidate.injection(i)

            if (injectable != null) {
                properties[i] = constructCreatorProperty(context, beanDescription, null, i, parameter, injectable)
                continue
            } else if (index < 0) {
                index = i
                continue
            }

            context.reportBadTypeDefinition(beanDescription,
                    "More than one argument (#$index and #$i) left as delegating for Creator $candidate: only one allowed")
        }

        if (index == -1) {
            context.reportBadTypeDefinition(beanDescription,
                    "No argument left as delegating for Creator $candidate: exactly one required")
        }

        if (argCount == 1) {
            return handleSingleArgumentCreator(creators, candidate.creator(), isCreator = true, isVisible = true)
        }

        creators.addDelegatingCreator(candidate.creator(), true, properties, index)
        return true
    }

    /**
     * Helper method called to add the single chosen "properties-based" Creator (if any).
     */
    private fun addSelectedPropertiesBasedCreator(context: DeserializationContext, beanDescription: BeanDescription,
            creators: CreatorCollector, candidate: CreatorCandidate) {
        val config = context.config
        val introspector = context.annotationIntrospector
        val parameterCount = candidate.parameterCount()
        var anySetterInder = -1

        val properties = Array(parameterCount) { i ->
            val injectable = candidate.injection(i)
            val parameter = candidate.parameter(i)
            val name = candidate.parameterName(i)

            val isAnySetter = introspector!!.hasAnySetter(config, parameter) ?: false

            if (isAnySetter) {
                if (anySetterInder != -1) {
                    context.reportBadTypeDefinition(beanDescription,
                            "More than one 'any-setter' specified (parameter #$anySetterInder vs #$i)")
                } else {
                    anySetterInder = i
                }
            } else if (name == null) {
                val unwrapper = introspector.findUnwrappingNameTransformer(config, parameter)

                if (unwrapper != null) {
                    reportUnwrappedCreatorProperty(context, beanDescription, parameter)
                } else if (injectable == null) {
                    context.reportBadTypeDefinition(beanDescription,
                            "Argument #$i of Creator $candidate has no property name (and is not Injectable): can not use as property-based Creator")
                }
            }

            constructCreatorProperty(context, beanDescription, name, i, parameter, injectable)
        }

        creators.addPropertyCreator(candidate.creator(), true, properties)
    }

    private fun handleSingleArgumentCreator(creators: CreatorCollector, creator: AnnotatedWithParams,
            isCreator: Boolean, isVisible: Boolean): Boolean {
        return when (val type = creator.getRawParameterType(0)) {
            CLASS_STRING, CLASS_CHAR_SEQUENCE -> {
                if (isCreator || isVisible) {
                    creators.addStringCreator(creator, isCreator)
                }

                true
            }

            Int::class -> {
                if (isCreator || isVisible) {
                    creators.addIntCreator(creator, isCreator)
                }

                true
            }

            Long::class -> {
                if (isCreator || isVisible) {
                    creators.addLongCreator(creator, isCreator)
                }

                true
            }

            Double::class -> {
                if (isCreator || isVisible) {
                    creators.addDoubleCreator(creator, isCreator)
                }

                true
            }

            Boolean::class -> {
                if (isCreator || isVisible) {
                    creators.addBooleanCreator(creator, isCreator)
                }

                true
            }

            else -> {
                if (type == BigInteger::class && (isCreator || isVisible)) {
                    creators.addBigIntegerCreator(creator, isCreator)
                }

                if (type == BigDecimal::class && (isCreator || isVisible)) {
                    creators.addBigDecimalCreator(creator, isCreator)
                }

                if (isCreator) {
                    creators.addDelegatingCreator(creator, true, null, 0)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun reportUnwrappedCreatorProperty(context: DeserializationContext, beanDescription: BeanDescription,
            parameter: AnnotatedParameter): Nothing {
        context.reportBadTypeDefinition(beanDescription,
                "Cannot define Creator parameter ${parameter.index} as `@CirJsonUnwrapped`: combination not yet supported")
    }

    /**
     * Method that will construct a property object that represents a logical property passed via Creator (constructor
     * or static factory method)
     */
    protected open fun constructCreatorProperty(context: DeserializationContext, beanDescription: BeanDescription,
            name: PropertyName?, index: Int, parameter: AnnotatedParameter,
            injectable: CirJacksonInject.Value?): SettableBeanProperty {
        val config = context.config
        val introspector = context.annotationIntrospector

        var (metadata, wrapperName) = introspector?.let {
            val required = it.hasRequiredMarker(config, parameter)
            val description = it.findPropertyDescription(config, parameter)
            val index = it.findPropertyIndex(config, parameter)
            val defaultValue = it.findPropertyDefaultValue(config, parameter)
            PropertyMetadata.construct(required, description, index, defaultValue) to it.findWrapperName(config,
                    parameter)
        } ?: (PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL to null)

        val type = resolveMemberAndTypeAnnotations(context, parameter, parameter.type)
        val property = BeanProperty.Standard(name!!, type, wrapperName, parameter, metadata)
        val typeDeserializer = type.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(type)

        metadata = getSetterInfo(config, property, metadata)

        var beanProperty: SettableBeanProperty =
                CreatorProperty.construct(name, type, property.wrapperName, typeDeserializer,
                        beanDescription.classAnnotations, parameter, index, injectable, metadata)
        var deserializer =
                findDeserializerFromAnnotation(context, parameter) ?: type.valueHandler as ValueDeserializer<*>?

        if (deserializer != null) {
            deserializer = context.handlePrimaryContextualization(deserializer, property, type)!!
            beanProperty = beanProperty.withValueDeserializer(deserializer)
        }

        return beanProperty
    }

    /**
     * Helper method copied from `POJOPropertyBuilder` since that won't be applied to creator parameters.
     */
    private fun getSetterInfo(config: DeserializationConfig, property: BeanProperty,
            metadata: PropertyMetadata): PropertyMetadata {
        val introspector = config.annotationIntrospector

        var valueNulls: Nulls? = null
        var contentNulls: Nulls? = null

        val member = property.member

        if (member != null) {
            if (introspector != null) {
                val setterInfo = introspector.findSetterInfo(config, member)

                if (setterInfo != null) {
                    valueNulls = setterInfo.nonDefaultNulls()
                    contentNulls = setterInfo.nonDefaultContentNulls()
                }
            }

            val configOverride = config.getConfigOverride(property.type.rawClass)
            val setterInfo = configOverride.nullHandling

            if (setterInfo != null) {
                if (valueNulls == null) {
                    valueNulls = setterInfo.nonDefaultNulls()
                }

                if (contentNulls == null) {
                    contentNulls = setterInfo.nonDefaultContentNulls()
                }
            }
        }

        val setterInfo = config.defaultNullHandling

        if (valueNulls == null) {
            valueNulls = setterInfo.nonDefaultNulls()
        }

        if (contentNulls == null) {
            contentNulls = setterInfo.nonDefaultContentNulls()
        }

        return if (valueNulls != null || contentNulls != null) {
            metadata.withNulls(valueNulls, contentNulls)
        } else {
            metadata
        }
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: array deserializers
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun createArrayDeserializer(context: DeserializationContext, type: ArrayType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        val config = context.config
        val elementType = type.contentType

        val elementDeserializer = elementType.valueHandler as ValueDeserializer<Any>?
        val elementTypeDeserializer =
                elementType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(elementType)

        var deserializer: ValueDeserializer<*> =
                findCustomArrayDeserializer(type, config, beanDescription, elementTypeDeserializer, elementDeserializer)
                        ?: let {
                            if (elementDeserializer == null) {
                                if (elementType.isPrimitive) {
                                    PrimitiveArrayDeserializer.forType(elementType.rawClass)
                                } else {
                                    StringArrayDeserializer.INSTANCE
                                }
                            } else {
                                null
                            }
                        } ?: ObjectArrayDeserializer(type, elementDeserializer, elementTypeDeserializer)

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyArrayDeserializer(config, type, beanDescription, deserializer)
            }
        }

        return deserializer
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: Collection(-like) deserializers
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun createCollectionDeserializer(context: DeserializationContext, type: CollectionType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        var realType = type
        var realBeanDescription = beanDescription
        val config = context.config
        val elementType = realType.contentType
        val elementDeserializer = elementType.valueHandler as ValueDeserializer<Any>?
        val elementTypeDeserializer =
                elementType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(elementType)
        var deserializer =
                findCustomCollectionDeserializer(realType, config, realBeanDescription, elementTypeDeserializer,
                        elementDeserializer)

        if (deserializer == null) {
            var collectionClass = realType.rawClass

            if (elementDeserializer == null) {
                if (elementType.isEnumType && collectionClass == MutableSet::class) {
                    collectionClass = EnumSet::class
                    realType = config.typeFactory.constructSpecializedType(realType, collectionClass) as CollectionType
                }

                if (EnumSet::class.isAssignableFrom(collectionClass)) {
                    deserializer = EnumSetDeserializer(elementType, null, elementTypeDeserializer)
                }
            }
        }

        if (deserializer == null) {
            if (realType.isInterface || realType.isAbstract) {
                val implementationType = mapAbstractCollectionType(realType, config)

                if (implementationType == null) {
                    if (realType.typeHandler == null) {
                        throw IllegalArgumentException(
                                "Cannot find a deserializer for non-concrete Collection type $realType")
                    }

                    deserializer = AbstractDeserializer.constructForNonPOJO(realBeanDescription)
                } else {
                    realType = implementationType
                    realBeanDescription = context.introspectBeanDescriptionForCreation(realType)
                }
            }

            if (deserializer == null) {
                val instantiator = findValueInstantiator(context, realBeanDescription)!!

                if (!instantiator.canCreateUsingDefault()) {
                    if (realType.hasRawClass(ArrayBlockingQueue::class)) {
                        return ArrayBlockingQueueDeserializer(realType, elementDeserializer, elementTypeDeserializer,
                                instantiator)
                    }

                    deserializer = JavaUtilCollectionsDeserializers.findForCollection(realType)

                    if (deserializer != null) {
                        return deserializer
                    }
                }

                deserializer = if (elementType.hasRawClass(String::class)) {
                    StringCollectionDeserializer(realType, elementDeserializer, instantiator)
                } else {
                    CollectionDeserializer(realType, elementDeserializer, elementTypeDeserializer, instantiator)
                }
            }
        }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer =
                        modifier.modifyCollectionDeserializer(config, realType, realBeanDescription, deserializer!!)
            }
        }

        return deserializer
    }

    protected open fun mapAbstractCollectionType(type: KotlinType, config: DeserializationConfig): CollectionType? {
        val collectionClass = ContainerDefaultMappings.findCollectionFallbacks(type) ?: return null
        return config.typeFactory.constructSpecializedType(type, collectionClass, true) as CollectionType
    }

    @Suppress("UNCHECKED_CAST")
    override fun createCollectionLikeDeserializer(context: DeserializationContext, type: CollectionLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        val config = context.config
        val elementType = type.contentType
        val elementDeserializer = elementType.valueHandler as ValueDeserializer<Any>?
        val elementTypeDeserializer =
                elementType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(elementType)

        var deserializer = findCustomCollectionLikeDeserializer(type, config, beanDescription, elementTypeDeserializer,
                elementDeserializer) ?: return null

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyCollectionLikeDeserializer(config, type, beanDescription, deserializer)
            }
        }

        return deserializer
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: Map(-like) deserializers
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun createMapDeserializer(context: DeserializationContext, type: MapType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        var realType = type
        var realBeanDescription = beanDescription
        val config = context.config
        val keyType = realType.keyType
        val elementType = realType.contentType

        val keyDeserializer = keyType.valueHandler as KeyDeserializer?
        val elementDeserializer = elementType.valueHandler as ValueDeserializer<Any>?
        val elementTypeDeserializer =
                elementType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(elementType)

        var deserializer: ValueDeserializer<*> =
                findCustomMapDeserializer(realType, config, realBeanDescription, keyDeserializer,
                        elementTypeDeserializer, elementDeserializer) ?: let {
                    var mapClass = realType.rawClass

                    if (mapClass == MutableMap::class && keyType.isEnumType) {
                        mapClass = EnumMap::class
                        realType = config.typeFactory.constructSpecializedType(realType, mapClass) as MapType
                    }

                    if (EnumMap::class.isAssignableFrom(mapClass)) {
                        val instantiator = if (mapClass == EnumMap::class) {
                            null
                        } else {
                            findValueInstantiator(context, realBeanDescription)
                        }

                        if (!keyType.isEnumImplType) {
                            throw IllegalArgumentException("Cannot construct EnumMap; generic (key) type not available")
                        }

                        EnumMapDeserializer(realType, instantiator, null, elementDeserializer, elementTypeDeserializer,
                                null)
                    } else {
                        null
                    }
                } ?: let {
                    if (realType.isInterface || realType.isAbstract) {
                        val fallback = mapAbstractMapType(realType, config)

                        if (fallback != null) {
                            realType = fallback
                            realBeanDescription = context.introspectBeanDescriptionForCreation(realType)
                            null
                        } else {
                            if (realType.typeHandler == null) {
                                throw IllegalArgumentException(
                                        "Cannot find a deserializer for non-concrete Map type $realType")
                            }

                            AbstractDeserializer.constructForNonPOJO(realBeanDescription)
                        }
                    } else {
                        JavaUtilCollectionsDeserializers.findForMap(realType)?.also { return it }
                    }
                } ?: let {
                    val instantiator = findValueInstantiator(context, realBeanDescription)!!
                    MapDeserializer(realType, instantiator, keyDeserializer, elementDeserializer,
                            elementTypeDeserializer).apply {
                        val ignorals = config.getDefaultPropertyIgnorals(Map::class, realBeanDescription.classInfo)
                        val ignored = ignorals?.findIgnoredForDeserialization()
                        ignorableProperties = ignored
                        val inclusions = config.getDefaultPropertyInclusions(Map::class, realBeanDescription.classInfo)
                        val included = inclusions?.included
                        includableProperties = included
                    }
                }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyMapDeserializer(config, realType, realBeanDescription, deserializer)
            }
        }

        return deserializer
    }

    protected open fun mapAbstractMapType(type: KotlinType, config: DeserializationConfig): MapType? {
        val mapClass = ContainerDefaultMappings.findMapFallbacks(type) ?: return null
        return config.typeFactory.constructSpecializedType(type, mapClass, true) as MapType
    }

    @Suppress("UNCHECKED_CAST")
    override fun createMapLikeDeserializer(context: DeserializationContext, type: MapLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        val config = context.config
        val keyType = type.keyType
        val elementType = type.contentType

        val keyDeserializer = keyType.valueHandler as KeyDeserializer?
        val elementDeserializer = elementType.valueHandler as ValueDeserializer<Any>?
        val elementTypeDeserializer =
                elementType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(elementType)

        var deserializer =
                findCustomMapLikeDeserializer(type, config, beanDescription, keyDeserializer, elementTypeDeserializer,
                        elementDeserializer) ?: return null

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyMapLikeDeserializer(config, type, beanDescription, deserializer)
            }
        }

        return deserializer
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: other types
     *******************************************************************************************************************
     */

    override fun createEnumDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        val config = context.config
        val enumClass = type.rawClass

        var deserializer = findCustomEnumDeserializer(enumClass, config, beanDescription) ?: let {
            if (enumClass == Enum::class) {
                return AbstractDeserializer.constructForNonPOJO(beanDescription)
            }

            val valueInstantiator = constructDefaultValueInstantiator(context, beanDescription)
            val creatorProperties = valueInstantiator.getFromObjectArguments(config)

            var result: ValueDeserializer<*>? = null

            for (factory in beanDescription.factoryMethods) {
                if (!hasCreatorAnnotation(config, factory)) {
                    continue
                } else if (factory.parameterCount == 0) {
                    result = EnumDeserializer.deserializerForNoArgsCreator(config, enumClass, factory)
                    break
                }

                val returnType = factory.rawReturnType

                if (!returnType.isAssignableFrom(enumClass)) {
                    context.reportBadDefinition(type,
                            "Invalid `@JsonCreator` annotated Enum factory method [$factory]: needs to return compatible type")
                }

                result = EnumDeserializer.deserializerForCreator(config, enumClass, factory, valueInstantiator,
                        creatorProperties)
            }

            result
        } ?: EnumDeserializer(constructEnumResolver(context, enumClass, beanDescription),
                config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS),
                constructEnumNamingStrategyResolver(config, beanDescription.classInfo),
                EnumResolver.constructUsingToString(config, beanDescription.classInfo))

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyEnumDeserializer(config, type, beanDescription, deserializer)
            }
        }

        return deserializer
    }

    @Suppress("UNCHECKED_CAST")
    override fun createTreeDeserializer(config: DeserializationConfig, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        val nodeType = type.rawClass as KClass<out CirJsonNode>
        return findCustomTreeNodeDeserializer(nodeType, config, beanDescription)
                ?: CirJsonNodeDeserializer.getDeserializer(nodeType)
    }

    @Suppress("UNCHECKED_CAST")
    override fun createReferenceDeserializer(context: DeserializationContext, type: ReferenceType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        val config = context.config
        val contentType = type.contentType
        val contentDeserializer = contentType.valueHandler as ValueDeserializer<Any>?
        val contentTypeDeserializer =
                contentType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(contentType)

        var deserializer = findCustomReferenceDeserializer(type, config, beanDescription, contentTypeDeserializer,
                contentDeserializer) ?: let {
            return if (type.isTypeOrSubTypeOf(Optional::class)) {
                val instantiator = type.takeUnless { it.hasRawClass(Optional::class) }
                        ?.let { findValueInstantiator(context, beanDescription) }
                Jdk8OptionalDeserializer(type, instantiator, contentTypeDeserializer, contentDeserializer)
            } else if (type.isTypeOrSubTypeOf(AtomicReference::class)) {
                val instantiator = type.takeUnless { it.hasRawClass(AtomicReference::class) }
                        ?.let { findValueInstantiator(context, beanDescription) }
                AtomicReferenceDeserializer(type, instantiator, contentTypeDeserializer, contentDeserializer)
            } else if (type.hasRawClass(OptionalInt::class)) {
                OptionalIntDeserializer()
            } else if (type.hasRawClass(OptionalLong::class)) {
                OptionalLongDeserializer()
            } else if (type.hasRawClass(OptionalDouble::class)) {
                OptionalDoubleDeserializer()
            } else {
                null
            }
        }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyReferenceDeserializer(config, type, beanDescription, deserializer)
            }
        }

        return deserializer
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
        return OptionalHandlerFactory.INSTANCE.findDeserializer(context.config, type)
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation (partial): key deserializers
     *******************************************************************************************************************
     */

    override fun createKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer? {
        val config = context.config
        var beanDescription: BeanDescription? = null
        var deserializer: KeyDeserializer? = null

        if (myFactoryConfig.hasKeyDeserializers()) {
            beanDescription = context.introspectBeanDescription(type)

            for (keyDeserializers in myFactoryConfig.keyDeserializers()) {
                deserializer = keyDeserializers.findKeyDeserializer(type, config, beanDescription)

                if (deserializer != null) {
                    break
                }
            }
        }

        if (deserializer == null) {
            if (beanDescription == null) {
                beanDescription = context.introspectBeanDescription(type)
            }

            deserializer = findKeyDeserializerFromAnnotation(context, beanDescription.classInfo) ?: let {
                if (type.isEnumType) {
                    createEnumKeyDeserializer(context, type)
                } else {
                    JDKKeyDeserializers.findStringBasedKeyDeserializer(context, type)
                }
            } ?: return null
        }

        if (myFactoryConfig.hasDeserializerModifiers()) {
            for (modifier in myFactoryConfig.deserializerModifiers()) {
                deserializer = modifier.modifyKeyDeserializer(config, type, deserializer!!)
            }
        }

        return deserializer
    }

    private fun createEnumKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer {
        val config = context.config
        val enumClass = type.rawClass

        val beanDescription = context.introspectBeanDescription(type)

        findKeyDeserializerFromAnnotation(context, beanDescription.classInfo)?.let { return it }

        findCustomEnumDeserializer(enumClass, config, beanDescription)?.let {
            return JDKKeyDeserializers.constructDelegatingKeyDeserializer(type, it)
        }

        findDeserializerFromAnnotation(context, beanDescription.classInfo)?.let {
            return JDKKeyDeserializers.constructDelegatingKeyDeserializer(type, it)
        }

        val byNameResolver = constructEnumResolver(context, enumClass, beanDescription)
        val byEnumNamingResolver = constructEnumNamingStrategyResolver(config, beanDescription.classInfo)
        val byToStringResolver = EnumResolver.constructUsingToString(config, beanDescription.classInfo)
        val byIndexResolver = EnumResolver.constructUsingIndex(config, beanDescription.classInfo)

        for (factory in beanDescription.factoryMethods) {
            if (!hasCreatorAnnotation(config, factory)) {
                continue
            }

            val argCount = factory.parameterCount

            if (argCount != 1) {
                throw IllegalArgumentException(
                        "Unsuitable method ($factory) decorated with @CirJsonCreator (for Enum type ${enumClass.qualifiedName})")
            }

            val returnType = factory.rawReturnType

            if (!returnType.isAssignableFrom(enumClass)) {
                throw IllegalArgumentException(
                        "Unsuitable method ($factory) decorated with @CirJsonCreator (for Enum type ${enumClass.qualifiedName})")
            }

            if (factory.getRawParameterType(0) != String::class) {
                continue
            }

            if (config.canOverrideAccessModifiers()) {
                factory.member.checkAndFixAccess(context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            }

            return JDKKeyDeserializers.constructEnumKeyDeserializer(byNameResolver, factory, byEnumNamingResolver,
                    byToStringResolver, byIndexResolver)
        }

        return JDKKeyDeserializers.constructEnumKeyDeserializer(byNameResolver, byEnumNamingResolver,
                byToStringResolver, byIndexResolver)
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
        var realValueType = valueType

        if (realValueType.isArray) {
            do {
                realValueType = realValueType.componentType
            } while (realValueType.isArray)

            if (realValueType == CLASS_ANY) {
                return true
            }
        }

        if (Enum::class.isAssignableFrom(realValueType)) {
            return true
        }

        val className = realValueType.qualifiedName!!

        return if (className.startsWith("java.") || className.startsWith("kotlin.")) {
            if (Collection::class.isAssignableFrom(realValueType)) {
                true
            } else if (Map::class.isAssignableFrom(realValueType)) {
                true
            } else if (Number::class.isAssignableFrom(realValueType)) {
                NumberDeserializers.find(realValueType) != null
            } else if (JDKMiscDeserializers.hasDeserializerFor(realValueType) || realValueType == CLASS_STRING ||
                    realValueType == Boolean::class || realValueType == EnumMap::class ||
                    realValueType == AtomicReference::class) {
                true
            } else {
                JDKDateDeserializers.hasDeserializerFor(realValueType)
            }
        } else if (className.startsWith("org.cirjson.")) {
            CirJsonNode::class.isAssignableFrom(realValueType) || realValueType == TokenBuffer::class
        } else {
            OptionalHandlerFactory.INSTANCE.hasDeserializerFor(realValueType)
        }
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
    @Suppress("UNCHECKED_CAST")
    open fun findDefaultDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        val rawType = type.rawClass

        if (rawType == CLASS_ANY || rawType == CLASS_SERIALIZABLE) {
            val config = context.config

            val (listType, mapType) = if (config.hasAbstractTypeResolvers()) {
                findRemappedType(config, List::class) to findRemappedType(config, Map::class)
            } else {
                null to null
            }

            return UntypedObjectDeserializer(listType, mapType)
        } else if (rawType == CLASS_STRING || rawType == CLASS_CHAR_SEQUENCE) {
            return StringDeserializer.INSTANCE
        } else if (rawType == CLASS_ITERABLE) {
            val typeFactory = context.typeFactory
            val typeParameters = typeFactory.findTypeParameters(type, CLASS_ITERABLE)
            val elementType = typeParameters.takeIf { it.size == 1 }?.get(0) ?: TypeFactory.unknownType()
            val collectionType = typeFactory.constructCollectionType(Collection::class, elementType)
            return createCollectionDeserializer(context, collectionType, beanDescription)
        } else if (rawType == CLASS_MAP_ENTRY) {
            val keyType = type.containedTypeOrUnknown(0)
            val valueType = type.containedTypeOrUnknown(1)
            val valueTypeDeserializer =
                    valueType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(valueType)
            val valueDeserializer = valueType.valueHandler as ValueDeserializer<Any>?
            val keyDeserializer = keyType.valueHandler as KeyDeserializer?
            return MapEntryDeserializer(type, keyDeserializer, valueDeserializer, valueTypeDeserializer)
        }

        val className = rawType.qualifiedName!!

        if (rawType.isPrimitive || className.startsWith("java.") || className.startsWith("kotlin.")) {
            val deserializer = NumberDeserializers.find(rawType) ?: JDKDateDeserializers.find(rawType, className)

            if (deserializer != null) {
                return deserializer
            }
        }

        return if (rawType == TokenBuffer::class) {
            TokenBufferDeserializer()
        } else {
            findOptionalStdDeserializer(context, type, beanDescription) ?: JDKMiscDeserializers.find(context, rawType,
                    className)
        }
    }

    private fun findRemappedType(config: DeserializationConfig, rawType: KClass<*>): KotlinType? {
        val type = config.mapAbstractType(config.constructType(rawType))
        return type.takeUnless { it.hasRawClass(rawType) }
    }

    /*
     *******************************************************************************************************************
     * Helper methods, finding custom deserializers
     *******************************************************************************************************************
     */

    protected open fun findCustomTreeNodeDeserializer(nodeType: KClass<out CirJsonNode>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer = deserializers.findTreeNodeDeserializer(nodeType, config, beanDescription)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomReferenceDeserializer(referenceType: ReferenceType, config: DeserializationConfig,
            beanDescription: BeanDescription, contentTypeDeserializer: TypeDeserializer?,
            contentDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer = deserializers.findReferenceDeserializer(referenceType, config, beanDescription,
                    contentTypeDeserializer, contentDeserializer)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomBeanDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer = deserializers.findBeanDeserializer(type, config, beanDescription)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomArrayDeserializer(type: ArrayType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer =
                    deserializers.findArrayDeserializer(type, config, beanDescription, elementTypeDeserializer,
                            elementDeserializer)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomCollectionDeserializer(type: CollectionType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer =
                    deserializers.findCollectionDeserializer(type, config, beanDescription, elementTypeDeserializer,
                            elementDeserializer)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomCollectionLikeDeserializer(type: CollectionLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer =
                    deserializers.findCollectionLikeDeserializer(type, config, beanDescription, elementTypeDeserializer,
                            elementDeserializer)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomEnumDeserializer(type: KClass<*>, config: DeserializationConfig,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer = deserializers.findEnumDeserializer(type, config, beanDescription)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomMapDeserializer(type: MapType, config: DeserializationConfig,
            beanDescription: BeanDescription, keyDeserializer: KeyDeserializer?,
            elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer = deserializers.findMapDeserializer(type, config, beanDescription, keyDeserializer,
                    elementTypeDeserializer, elementDeserializer)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
    }

    protected open fun findCustomMapLikeDeserializer(type: MapLikeType, config: DeserializationConfig,
            beanDescription: BeanDescription, keyDeserializer: KeyDeserializer?,
            elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        for (deserializers in myFactoryConfig.deserializers()) {
            val deserializer = deserializers.findMapLikeDeserializer(type, config, beanDescription, keyDeserializer,
                    elementTypeDeserializer, elementDeserializer)

            if (deserializer != null) {
                return deserializer
            }
        }

        return null
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
        val introspector = context.annotationIntrospector ?: return null
        val deserializerDefinition = introspector.findDeserializer(context.config, annotated) ?: return null
        return context.deserializerInstance(annotated, deserializerDefinition)
    }

    /**
     * Helper method called to check if a class or method has annotation that tells which class to use for
     * deserialization of [Map] keys. Returns `null` if no such annotation found.
     */
    protected open fun findKeyDeserializerFromAnnotation(context: DeserializationContext,
            annotated: Annotated): KeyDeserializer? {
        val introspector = context.annotationIntrospector ?: return null
        val deserializerDefinition = introspector.findKeyDeserializer(context.config, annotated) ?: return null
        return context.keyDeserializerInstance(annotated, deserializerDefinition)
    }

    protected open fun findContentDeserializerFromAnnotation(context: DeserializationContext,
            annotated: Annotated): ValueDeserializer<Any>? {
        val introspector = context.annotationIntrospector ?: return null
        val deserializerDefinition = introspector.findContentDeserializer(context.config, annotated) ?: return null
        return context.deserializerInstance(annotated, deserializerDefinition)
    }

    /**
     * Helper method used to resolve additional type-related annotation information like type overrides, or handler
     * (serializer, deserializer) overrides, so that from declared field, property or constructor parameter type is used
     * as the base and modified based on annotations, if any.
     */
    protected open fun resolveMemberAndTypeAnnotations(context: DeserializationContext, member: AnnotatedMember,
            type: KotlinType): KotlinType {
        var realType = type
        val introspector = context.annotationIntrospector ?: return realType

        if (type.isMapLikeType) {
            val keyType = type.keyType

            if (keyType != null) {
                val keyDeserializerDefinition = introspector.findKeyDeserializer(context.config, member)
                val keyDeserializer = context.keyDeserializerInstance(member, keyDeserializerDefinition)

                if (keyDeserializer != null) {
                    realType = (realType as MapType).withKeyValueHandler(keyDeserializer)
                }
            }
        }

        if (realType.hasContentType()) {
            val contentDeserializerDefinition = introspector.findContentDeserializer(context.config, member)
            val contentDeserializer = context.deserializerInstance(member, contentDeserializerDefinition)

            if (contentDeserializer != null) {
                realType = realType.withContentValueHandler(contentDeserializer)
            }

            val contentTypeDeserializer = context.findPropertyContentTypeDeserializer(realType, member)

            if (contentTypeDeserializer != null) {
                realType = realType.withContentTypeHandler(contentTypeDeserializer)
            }
        }

        val valueTypeDeserializer = context.findPropertyTypeDeserializer(type, member)

        if (valueTypeDeserializer != null) {
            realType = realType.withTypeHandler(valueTypeDeserializer)
        }

        return introspector.refineDeserializationType(context.config, member, realType)
    }

    protected open fun constructEnumResolver(context: DeserializationContext, enumClass: KClass<*>,
            beanDescription: BeanDescription): EnumResolver {
        val cirJsonValueAccessor =
                beanDescription.findCirJsonValueAccessor() ?: return EnumResolver.constructFor(context.config,
                        beanDescription.classInfo)

        if (context.canOverrideAccessModifiers()) {
            cirJsonValueAccessor.member!!.checkAndFixAccess(
                    context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
        }

        return EnumResolver.constructUsingMethod(context.config, beanDescription.classInfo, cirJsonValueAccessor)
    }

    /**
     * Factory method used to resolve an instance of [CompactStringObjectMap] with [EnumNamingStrategy] applied for the
     * target class.
     */
    protected open fun constructEnumNamingStrategyResolver(config: DeserializationConfig,
            enumClass: AnnotatedClass): EnumResolver? {
        val namingDefinition = config.annotationIntrospector!!.findEnumNamingStrategy(config, enumClass)
        val enumNamingStrategy = EnumNamingStrategyFactory.createEnumNamingStrategyInstance(namingDefinition,
                config.canOverrideAccessModifiers()) ?: return null
        return EnumResolver.constructUsingEnumNamingStrategy(config, enumClass, enumNamingStrategy)
    }

    protected open fun hasCreatorAnnotation(config: DeserializationConfig, annotated: Annotated): Boolean {
        val introspector = config.annotationIntrospector ?: return false
        val mode = introspector.findCreatorAnnotation(config, annotated) ?: return false
        return mode != CirJsonCreator.Mode.DISABLED
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
            val defaultList = ArrayList::class
            val defaultSet = HashSet::class

            this[java.util.Collection::class.qualifiedName!!] = defaultList
            this[MutableCollection::class.qualifiedName!!] = defaultList
            this[java.util.List::class.qualifiedName!!] = defaultList
            this[MutableList::class.qualifiedName!!] = defaultList
            this[java.util.Set::class.qualifiedName!!] = defaultSet
            this[MutableSet::class.qualifiedName!!] = defaultSet
            this[SortedSet::class.qualifiedName!!] = TreeSet::class
            this[Queue::class.qualifiedName!!] = LinkedList::class

            this[java.util.AbstractList::class.qualifiedName!!] = defaultList
            this[AbstractMutableList::class.qualifiedName!!] = defaultList
            this[java.util.AbstractSet::class.qualifiedName!!] = defaultSet
            this[AbstractMutableSet::class.qualifiedName!!] = defaultSet

            this[Deque::class.qualifiedName!!] = LinkedList::class
            this[NavigableSet::class.qualifiedName!!] = TreeSet::class

            this["java.util.SequencedCollection"] = defaultList
            this["java.util.SequencedSet"] = LinkedHashSet::class
        }

        val ourMapFallbacks = HashMap<String, KClass<out Map<*, *>>>().apply {
            val defaultMap = LinkedHashMap::class

            this[java.util.Map::class.qualifiedName!!] = defaultMap
            this[MutableMap::class.qualifiedName!!] = defaultMap
            this[java.util.AbstractMap::class.qualifiedName!!] = defaultMap
            this[AbstractMutableMap::class.qualifiedName!!] = defaultMap
            this[ConcurrentMap::class.qualifiedName!!] = ConcurrentHashMap::class
            this[SortedMap::class.qualifiedName!!] = TreeMap::class

            this[NavigableMap::class.qualifiedName!!] = TreeMap::class
            this[ConcurrentNavigableMap::class.qualifiedName!!] = ConcurrentSkipListMap::class

            this["java.util.SequencedMap"] = defaultMap
        }

        fun findCollectionFallbacks(type: KotlinType): KClass<*>? {
            return ourCollectionFallbacks[type.rawClass.qualifiedName]
        }

        fun findMapFallbacks(type: KotlinType): KClass<*>? {
            return ourMapFallbacks[type.rawClass.qualifiedName]
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