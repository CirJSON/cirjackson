package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import org.cirjson.cirjackson.databind.deserialization.bean.CreatorCandidate
import org.cirjson.cirjackson.databind.deserialization.bean.CreatorCollector
import org.cirjson.cirjackson.databind.deserialization.jdk.*
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.EnumResolver
import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.isBogusClass
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
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
        val type = creator.getRawParameterType(0)

        return when (type) {
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

    protected open fun hasCreatorAnnotation(config: DeserializationConfig, annotated: Annotated): Boolean {
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