package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.*
import org.cirjson.cirjackson.databind.deserialization.implementation.*
import org.cirjson.cirjackson.databind.deserialization.standard.StandardConvertingDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.exception.IgnoredPropertyException
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedWithParams
import org.cirjson.cirjackson.databind.type.ClassKey
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaConstructor

/**
 * Base class for [BeanDeserializer].
 */
abstract class BeanDeserializerBase : StandardDeserializer<Any>, ValueInstantiator.Gettable {

    /*
     *******************************************************************************************************************
     * Information regarding type being deserialized
     *******************************************************************************************************************
     */

    /**
     * Declared type of the bean this deserializer handles.
     */
    protected val myBeanType: KotlinType

    /**
     * Requested shape from bean class annotations.
     */
    protected val myDeserializationShape: CirJsonFormat.Shape?

    /*
     *******************************************************************************************************************
     * Configuration for creating value instance
     *******************************************************************************************************************
     */

    /**
     * Object that handles details of constructing initial bean value (to which bind data to), unless instance is passed
     * (via [updateValue])
     */
    protected val myValueInstantiator: ValueInstantiator

    /**
     * Deserializer that is used iff delegate-based creator is to be used for deserializing from CirJSON Object.
     *
     * NOTE: cannot be `val` because we need to get it during [resolve] method (and not contextualization).
     */
    protected var myDelegateDeserializer: ValueDeserializer<Any>?

    /**
     * Deserializer that is used iff array-delegate-based creator is to be used for deserializing from CirJSON Object.
     *
     * NOTE: cannot be `final` because we need to get it during [resolve] method (and not contextualization).
     */
    protected var myArrayDelegateDeserializer: ValueDeserializer<Any>?

    /**
     * If the bean needs to be instantiated using constructor or factory method that takes one or more named properties
     * as argument(s), this creator is used for instantiation. This value gets resolved during general resolution.
     */
    protected var myPropertyBasedCreator: PropertyBasedCreator?

    /**
     * Flag that is set to mark cases where deserialization from Object value using otherwise "standard" property
     * binding will need to use non-default creation method: namely, either "full" delegation (array-delegation does not
     * apply), or properties-based Creator method is used.
     *
     * Note that flag is somewhat mis-named as it is not affected by scalar-delegating creators; it only has effect on
     * Object Value binding.
     */
    protected var myNonStandardCreation: Boolean

    /**
     * Flag that indicates that no "special features" whatsoever are enabled, so the simplest processing is possible.
     */
    protected var myVanillaProcessing: Boolean

    /*
     *******************************************************************************************************************
     * Property information, setters
     *******************************************************************************************************************
     */

    /**
     * Mapping of property names to properties, built when all properties to use have been successfully resolved.
     */
    protected val myBeanProperties: BeanPropertyMap?

    /**
     * List of [ValueInjectors][ValueInjector], if any injectable values are expected by the bean; otherwise `null`.
     * This includes injectors used for injecting values via setters and fields, but not ones passed through constructor
     * parameters.
     */
    protected val myInjectables: Array<ValueInjector>?

    /**
     * Fallback setter used for handling any properties that are not mapped to regular setters. If setter is not `null`,
     * it will be called once for each such property.
     */
    protected var myAnySetter: SettableAnyProperty?

    /**
     * In addition to properties that are set, we will also keep track of recognized but ignorable properties: these
     * will be skipped without errors or warnings.
     */
    protected val myIgnorableProperties: Set<String>?

    /**
     * Keep track of the properties that needs to be specifically included.
     */
    protected val myIncludableProperties: Set<String>?

    /**
     * Flag that can be set to ignore and skip unknown properties. If set, will not throw an exception for unknown
     * properties.
     */
    protected val myIgnoreAllUnknown: Boolean

    /**
     * Flag that indicates that some aspect of deserialization depends on active view used (if any)
     */
    protected val myNeedViewProcessing: Boolean

    /**
     * We may also have one or more back reference fields (usually zero or one).
     */
    protected val myBackReferences: Map<String, SettableBeanProperty>?

    /*
     *******************************************************************************************************************
     * Related handlers
     *******************************************************************************************************************
     */

    /**
     * Lazily constructed map used to contain deserializers needed for polymorphic subtypes. Note that this is **only
     * needed** for polymorphic types, that is, when the actual type is not statically known. For other types, this
     * remains `null`.
     */
    @Transient
    @Volatile
    protected var mySubDeserializers: ConcurrentHashMap<ClassKey, ValueDeserializer<Any>>? = null

    /**
     * If one of properties has "unwrapped" value, we need separate helper object
     */
    protected var myUnwrappedPropertyHandler: UnwrappedPropertyHandler?

    /**
     * Handler that we need if any of properties uses external type id.
     */
    protected var myExternalTypeIdHandler: ExternalTypeHandler?

    /**
     * If an Object ID is to be used for value handled by this deserializer, this reader is used for handling.
     */
    protected val myObjectIdReader: ObjectIdReader?

    /*
     *******************************************************************************************************************
     * Lifecycle, construction, initialization
     *******************************************************************************************************************
     */

    /**
     * Constructor used when initially building a deserializer instance, given a [BeanDeserializerBuilder] that contains
     * configuration.
     */
    protected constructor(builder: BeanDeserializerBuilder, beanDescription: BeanDescription,
            properties: BeanPropertyMap?, backReferences: Map<String, SettableBeanProperty>?,
            ignorableProperties: Set<String>?, ignoreAllUnknown: Boolean, includableProperties: Set<String>?,
            hasViews: Boolean) : super(beanDescription.type) {
        myBeanType = beanDescription.type
        myDeserializationShape = beanDescription.findExpectedFormat(myBeanType.rawClass)!!.shape

        myValueInstantiator = builder.valueInstantiator!!
        myDelegateDeserializer = null
        myArrayDelegateDeserializer = null
        myPropertyBasedCreator = null
        myNonStandardCreation = myValueInstantiator.canCreateUsingDelegate() ||
                myValueInstantiator.canCreateFromObjectWith() || !myValueInstantiator.canCreateUsingDefault()
        val injectables = builder.injectables?.takeUnless { it.isEmpty() }?.toTypedArray()
        val objectIdReader = builder.objectIdReader
        myVanillaProcessing = !myNonStandardCreation && injectables == null && !hasViews && objectIdReader == null

        myBeanProperties = properties
        myInjectables = injectables
        myAnySetter = builder.anySetter
        myIgnorableProperties = ignorableProperties
        myIncludableProperties = includableProperties
        myIgnoreAllUnknown = ignoreAllUnknown
        myNeedViewProcessing = hasViews
        myBackReferences = backReferences

        myUnwrappedPropertyHandler = null
        myExternalTypeIdHandler = null
        myObjectIdReader = objectIdReader
    }

    protected constructor(source: BeanDeserializerBase) : this(source, source.myIgnoreAllUnknown)

    protected constructor(source: BeanDeserializerBase, ignoreAllUnknown: Boolean) : super(source.myBeanType) {
        myBeanType = source.myBeanType
        myDeserializationShape = source.myDeserializationShape

        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myArrayDelegateDeserializer = source.myArrayDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myNonStandardCreation = source.myNonStandardCreation
        myVanillaProcessing = source.myVanillaProcessing

        myBeanProperties = source.myBeanProperties
        myInjectables = source.myInjectables
        myAnySetter = source.myAnySetter
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myIgnoreAllUnknown = ignoreAllUnknown
        myNeedViewProcessing = source.myNeedViewProcessing
        myBackReferences = source.myBackReferences

        myUnwrappedPropertyHandler = source.myUnwrappedPropertyHandler
        myExternalTypeIdHandler = source.myExternalTypeIdHandler
        myObjectIdReader = source.myObjectIdReader
    }

    /**
     * Constructor used in cases where unwrapping-with-name-change has been invoked and lookup indices need to be
     * updated.
     */
    protected constructor(source: BeanDeserializerBase, unwrapHandler: UnwrappedPropertyHandler?,
            renamedProperties: BeanPropertyMap?, ignoreAllUnknown: Boolean) : super(source.myBeanType) {
        myBeanType = source.myBeanType
        myDeserializationShape = source.myDeserializationShape

        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myArrayDelegateDeserializer = source.myArrayDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myNonStandardCreation = source.myNonStandardCreation
        myVanillaProcessing = source.myVanillaProcessing

        myBeanProperties = renamedProperties
        myInjectables = source.myInjectables
        myAnySetter = source.myAnySetter
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myIgnoreAllUnknown = ignoreAllUnknown
        myNeedViewProcessing = source.myNeedViewProcessing
        myBackReferences = source.myBackReferences

        myUnwrappedPropertyHandler = unwrapHandler
        myExternalTypeIdHandler = source.myExternalTypeIdHandler
        myObjectIdReader = source.myObjectIdReader
    }

    protected constructor(source: BeanDeserializerBase, objectIdReader: ObjectIdReader?) : super(source.myBeanType) {
        myBeanType = source.myBeanType
        myDeserializationShape = source.myDeserializationShape

        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myArrayDelegateDeserializer = source.myArrayDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myNonStandardCreation = source.myNonStandardCreation

        myInjectables = source.myInjectables
        myAnySetter = source.myAnySetter
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myIgnoreAllUnknown = source.myIgnoreAllUnknown
        myNeedViewProcessing = source.myNeedViewProcessing
        myBackReferences = source.myBackReferences

        myUnwrappedPropertyHandler = source.myUnwrappedPropertyHandler
        myExternalTypeIdHandler = source.myExternalTypeIdHandler
        myObjectIdReader = objectIdReader

        if (objectIdReader == null) {
            myBeanProperties = source.myBeanProperties
            myVanillaProcessing = source.myVanillaProcessing
        } else {
            val idProperty = ObjectIdValueProperty(objectIdReader, PropertyMetadata.STANDARD_REQUIRED)
            myBeanProperties = source.myBeanProperties!!.withProperty(idProperty)
            myVanillaProcessing = false
        }
    }

    protected constructor(source: BeanDeserializerBase, ignorableProperties: Set<String>?,
            includableProperties: Set<String>?) : super(source.myBeanType) {
        myBeanType = source.myBeanType
        myDeserializationShape = source.myDeserializationShape

        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myArrayDelegateDeserializer = source.myArrayDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myNonStandardCreation = source.myNonStandardCreation
        myVanillaProcessing = source.myVanillaProcessing

        myBeanProperties = source.myBeanProperties!!.withoutProperties(ignorableProperties, includableProperties)
        myInjectables = source.myInjectables
        myAnySetter = source.myAnySetter
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myIgnoreAllUnknown = source.myIgnoreAllUnknown
        myNeedViewProcessing = source.myNeedViewProcessing
        myBackReferences = source.myBackReferences

        myUnwrappedPropertyHandler = source.myUnwrappedPropertyHandler
        myExternalTypeIdHandler = source.myExternalTypeIdHandler
        myObjectIdReader = source.myObjectIdReader
    }

    protected constructor(source: BeanDeserializerBase, beanProperties: BeanPropertyMap?) : super(source.myBeanType) {
        myBeanType = source.myBeanType
        myDeserializationShape = source.myDeserializationShape

        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myArrayDelegateDeserializer = source.myArrayDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myNonStandardCreation = source.myNonStandardCreation
        myVanillaProcessing = source.myVanillaProcessing

        myBeanProperties = beanProperties
        myInjectables = source.myInjectables
        myAnySetter = source.myAnySetter
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myIgnoreAllUnknown = source.myIgnoreAllUnknown
        myNeedViewProcessing = source.myNeedViewProcessing
        myBackReferences = source.myBackReferences

        myUnwrappedPropertyHandler = source.myUnwrappedPropertyHandler
        myExternalTypeIdHandler = source.myExternalTypeIdHandler
        myObjectIdReader = source.myObjectIdReader
    }

    abstract fun withObjectIdReader(objectIdReader: ObjectIdReader?): BeanDeserializerBase

    abstract fun withByNameInclusion(ignorableProperties: Set<String>?,
            includableProperties: Set<String>?): BeanDeserializerBase

    abstract fun withIgnoreAllUnknown(ignoreAllUnknown: Boolean): BeanDeserializerBase

    /**
     * Mutant factory method that custom subclasses must override.
     */
    abstract fun withBeanProperties(beanProperties: BeanPropertyMap?): BeanDeserializerBase

    abstract override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<Any>

    /**
     * Fluent factory for creating a variant that can handle POJO output as a CirJSON Array. Implementations may ignore
     * this request if no such input is possible.
     */
    protected abstract fun asArrayDeserializer(): BeanDeserializerBase

    protected abstract fun initializeNameMatcher(context: DeserializationContext)

    /*
     *******************************************************************************************************************
     * Validation, postprocessing
     *******************************************************************************************************************
     */

    /**
     * Method called to finalize setup of this deserializer, after deserializer itself has been registered. This is
     * needed to handle recursive and transitive dependencies.
     */
    override fun resolve(context: DeserializationContext) {
        val creatorProperties = if (myValueInstantiator.canCreateFromObjectWith()) {
            myValueInstantiator.getFromObjectArguments(context.config)?.also { creatorProperties ->
                if (myIgnorableProperties == null && myIncludableProperties == null) {
                    return@also
                }

                for (property in creatorProperties) {
                    if (IgnorePropertiesUtil.shouldIgnore(property.name, myIgnorableProperties,
                                    myIncludableProperties)) {
                        property.markAsIgnorable()
                    }
                }
            }
        } else {
            null
        }

        for (property in myBeanProperties!!) {
            if (property.hasValueDeserializer() || property.isInjectionOnly) {
                continue
            }

            val deserializer =
                    findConvertingDeserializer(context, property) ?: context.findNonContextualValueDeserializer(
                            property.type)
            val newProperty = property.withValueDeserializer(deserializer)

            if (newProperty !== property) {
                replaceProperty(myBeanProperties, creatorProperties, property, newProperty)
            }
        }

        var unwrappedHandler: UnwrappedPropertyHandler? = null
        var externalTypes: ExternalTypeHandler.Builder? = null

        for (originalProperty in myBeanProperties) {
            var property = originalProperty
            val deserializer = property.valueDeserializer.let {
                context.handlePrimaryContextualization(it, property, property.type)
            }!!
            property = property.withValueDeserializer(deserializer)
            property = resolveManagedReferenceProperty(context, property)

            if (property !is ManagedReferenceProperty) {
                property = resolvedObjectIdProperty(context, property)
            }

            val unwrapper = findPropertyUnwrapper(context, property)

            if (unwrapper != null) {
                val original = property.valueDeserializer!!
                val unwrapping = original.unwrappingDeserializer(context, unwrapper)

                if (unwrapping !== original) {
                    property = property.withValueDeserializer(unwrapping)

                    if (unwrappedHandler == null) {
                        unwrappedHandler = UnwrappedPropertyHandler()
                    }

                    unwrappedHandler.addProperty(property)
                    myBeanProperties.remove(property)
                    continue
                }
            }

            val propertyMetadata = property.metadata
            property = resolveMergeAndNullSettings(context, property, propertyMetadata)

            property = resolveInnerClassValuedProperty(context, property)

            if (property !== originalProperty) {
                replaceProperty(myBeanProperties, creatorProperties, originalProperty, property)
            }

            if (!property.hasValueTypeDeserializer()) {
                continue
            }

            val typeDeserializer = property.valueTypeDeserializer!!

            if (typeDeserializer.typeInclusion != CirJsonTypeInfo.As.EXTERNAL_PROPERTY) {
                continue
            }

            if (externalTypes == null) {
                externalTypes = ExternalTypeHandler.builder(myBeanType)
            }

            externalTypes.addExternal(property, typeDeserializer)
            myBeanProperties.remove(property)
        }

        myAnySetter?.takeIf { !it.hasValueDeserializer() }
                ?.let { myAnySetter = it.withValueDeserializer(findDeserializer(context, it.type, it.property)) }

        if (myValueInstantiator.canCreateUsingDelegate()) {
            val delegateType =
                    myValueInstantiator.getDelegateType(context.config) ?: return context.reportBadDefinition(
                            myBeanType,
                            "Invalid delegate-creator definition for ${myBeanType.typeDescription}: value instantiator (${myValueInstantiator.className}) returned `true` for 'canCreateUsingDelegate()', but `null` for 'getDelegateType()'")
            myDelegateDeserializer =
                    findDelegateDeserializer(context, delegateType, myValueInstantiator.delegateCreator)
        }

        if (myValueInstantiator.canCreateUsingArrayDelegate()) {
            val delegateType =
                    myValueInstantiator.getArrayDelegateType(context.config) ?: return context.reportBadDefinition(
                            myBeanType,
                            "Invalid delegate-creator definition for ${myBeanType.typeDescription}: value instantiator (${myValueInstantiator.className}) returned `true` for 'canCreateUsingArrayDelegate()', but `null` for 'getArrayDelegateType()'")
            myArrayDelegateDeserializer =
                    findDelegateDeserializer(context, delegateType, myValueInstantiator.arrayDelegateCreator)
        }

        if (creatorProperties != null) {
            myPropertyBasedCreator =
                    PropertyBasedCreator.construct(context, myValueInstantiator, creatorProperties, myBeanProperties)
        }

        if (externalTypes != null) {
            myExternalTypeIdHandler = externalTypes.build(myBeanProperties)
        }

        myUnwrappedPropertyHandler = unwrappedHandler?.also { myNonStandardCreation = true }
        myVanillaProcessing = myVanillaProcessing && !myNonStandardCreation
    }

    protected open fun replaceProperty(properties: BeanPropertyMap, creatorProperties: Array<SettableBeanProperty>?,
            originalProperty: SettableBeanProperty, newProperty: SettableBeanProperty) {
        properties.replace(originalProperty, newProperty)

        creatorProperties ?: return

        for ((i, property) in creatorProperties.withIndex()) {
            if (property === originalProperty) {
                creatorProperties[i] = newProperty
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun findDelegateDeserializer(context: DeserializationContext, delegateType: KotlinType,
            delegateCreator: AnnotatedWithParams?): ValueDeserializer<Any> {
        val property = if (delegateCreator != null && delegateCreator.parameterCount == 1) {
            val delegator = delegateCreator.getParameter(0)
            val propertyMetadata = getSetterInfo(context, delegator, delegateType)
            BeanProperty.Standard(TEMPORARY_PROPERTY_NAME, delegateType, null, delegator, propertyMetadata)
        } else {
            BeanProperty.Standard(TEMPORARY_PROPERTY_NAME, delegateType, null, delegateCreator,
                    PropertyMetadata.STANDARD_OPTIONAL)
        }

        val typeDeserializer =
                delegateType.typeHandler as TypeDeserializer? ?: context.findTypeDeserializer(delegateType)
        val deserializer = (delegateType.typeHandler as ValueDeserializer<Any>?)?.let {
            context.handleSecondaryContextualization(it, property, delegateType) as ValueDeserializer<Any>
        } ?: findDeserializer(context, delegateType, property)

        return typeDeserializer?.let { TypeWrappedDeserializer(it.forProperty(property), deserializer) } ?: deserializer
    }

    /**
     * Method essentially copied from `BasicDeserializerFactory`, needed to find [PropertyMetadata] for Delegating
     * Creator, for access to annotation-derived info.
     */
    protected open fun getSetterInfo(context: DeserializationContext, accessor: AnnotatedMember,
            type: KotlinType): PropertyMetadata {
        val introspector = context.annotationIntrospector
        val config = context.config

        var metadata = PropertyMetadata.STANDARD_OPTIONAL
        var valueNulls: Nulls? = null
        var contentNulls: Nulls? = null

        if (introspector != null) {
            introspector.findSetterInfo(config, accessor)?.let { setterInfo ->
                valueNulls = setterInfo.nonDefaultNulls()
                contentNulls = setterInfo.nonDefaultContentNulls()
            }
        }

        val configOverride = config.getConfigOverride(type.rawClass)
        var setterInfo = configOverride.nullHandling

        if (setterInfo != null) {
            if (valueNulls == null) {
                valueNulls = setterInfo.nonDefaultNulls()
            }

            if (contentNulls == null) {
                contentNulls = setterInfo.nonDefaultContentNulls()
            }
        }

        setterInfo = config.defaultNullHandling

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

    /**
     * Helper method that can be used to see if specified property is annotated to indicate use of a converter for
     * property value (in case of container types, it is container type itself, not key or content type).
     * 
     * NOTE: returned deserializer is NOT yet contextualized, caller needs to take care to do that.
     */
    protected open fun findConvertingDeserializer(context: DeserializationContext,
            property: SettableBeanProperty): ValueDeserializer<Any>? {
        val introspector = context.annotationIntrospector ?: return null
        val converterDefinition =
                introspector.findDeserializationConverter(context.config, property.member!!) ?: return null
        val converter = context.converterInstance(property.member!!, converterDefinition)!!
        val delegateType = converter.getInputType(context.typeFactory)
        val deserializer = context.findNonContextualValueDeserializer(delegateType)
        return StandardConvertingDeserializer(converter, delegateType, deserializer)
    }

    /**
     * Although most of the postprocessing is done in [resolve], we only get access to referring property's annotations
     * here; and this is needed to support per-property ObjectIds. We will also consider Shape transformations (read
     * from Array) at this point, since it may come from either Class definition or property.
     */
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        var objectIdReader = myObjectIdReader

        val introspector = context.annotationIntrospector
        val config = context.config

        val accessor = property?.takeIf { introspector != null }?.member

        if (accessor != null) {
            var objectIdInfo = introspector!!.findObjectIdInfo(config, accessor)

            if (objectIdInfo != null) {
                objectIdInfo = introspector.findObjectReferenceInfo(config, accessor, objectIdInfo)!!
                val implementationClass = objectIdInfo.generatorType

                val idType: KotlinType
                val idProperty: SettableBeanProperty?
                val idGenerator: ObjectIdGenerator<*>

                val resolver = context.objectIdResolverInstance(accessor, objectIdInfo)

                if (implementationClass == ObjectIdGenerators.PropertyGenerator::class) {
                    val propertyName = objectIdInfo.propertyName
                    idProperty = findProperty(propertyName) ?: return context.reportBadDefinition(myBeanType,
                            "Invalid Object Id definition for ${handledType().name}: cannot find property with name ${propertyName.name()}")
                    idType = idProperty.type
                    idGenerator = PropertyBasedObjectIdGenerator(objectIdInfo.scope!!)
                } else {
                    val type = context.constructType(implementationClass)!!
                    idType = context.typeFactory.findTypeParameters(type, ObjectIdGenerator::class)[0]!!
                    idProperty = null
                    idGenerator = context.objectIdGeneratorInstance(accessor, objectIdInfo)
                }

                val deserializer = context.findRootValueDeserializer(idType)
                objectIdReader = ObjectIdReader.construct(idType, objectIdInfo.propertyName, idGenerator, deserializer,
                        idProperty, resolver)
            }
        }

        var contextual = this

        if (objectIdReader != null && objectIdReader !== myObjectIdReader) {
            contextual = contextual.withObjectIdReader(objectIdReader)
        }

        accessor?.let { contextual = handleByNameInclusion(context, introspector!!, contextual, it) }

        val format = findFormatOverrides(context, property, handledType())
        val shape = format.takeIf { it.hasShape() }?.shape
        val caseInsensitive = format.getFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)

        if (caseInsensitive != null) {
            val originalProperties = myBeanProperties!!
            val properties = originalProperties.withCaseInsensitivity(caseInsensitive)

            if (properties !== originalProperties) {
                contextual = contextual.withBeanProperties(properties)
            }
        }

        contextual.initializeNameMatcher(context)

        return if ((shape ?: myDeserializationShape) == CirJsonFormat.Shape.ARRAY) {
            contextual.asArrayDeserializer()
        } else {
            contextual
        }
    }

    protected open fun handleByNameInclusion(context: DeserializationContext, introspector: AnnotationIntrospector,
            contextual: BeanDeserializerBase, accessor: AnnotatedMember): BeanDeserializerBase {
        var realContextual = contextual
        val config = context.config
        val ignorals = introspector.findPropertyIgnoralByName(config, accessor)!!

        if (ignorals.ignoreUnknown && !myIgnoreAllUnknown) {
            realContextual = realContextual.withIgnoreAllUnknown(true)
        }

        val namesToIgnore = ignorals.findIgnoredForDeserialization()
        val previousNamesToIgnore = realContextual.myIgnorableProperties

        val newNamesToIgnore = if (namesToIgnore.isEmpty()) {
            previousNamesToIgnore
        } else if (previousNamesToIgnore.isNullOrEmpty()) {
            namesToIgnore
        } else {
            HashSet(previousNamesToIgnore).apply { addAll(namesToIgnore) }
        }

        val previousNamesToInclude = realContextual.myIncludableProperties
        val newNamesToInclude = IgnorePropertiesUtil.combineNamesToInclude(previousNamesToInclude,
                introspector.findPropertyInclusionByName(config, accessor)!!.included)

        return if (newNamesToIgnore !== previousNamesToIgnore || newNamesToInclude !== previousNamesToInclude) {
            realContextual.withByNameInclusion(newNamesToIgnore, newNamesToInclude)
        } else {
            realContextual
        }
    }

    /**
     * Helper method called to see if given property is part of 'managed' property pair (managed + back reference), and
     * if so, handle resolution details.
     */
    protected open fun resolveManagedReferenceProperty(context: DeserializationContext,
            property: SettableBeanProperty): SettableBeanProperty {
        val referenceName = property.managedReferenceName ?: return property
        val valueDeserializer = property.valueDeserializer!!
        val backProperty =
                valueDeserializer.findBackReference(referenceName) ?: return context.reportBadDefinition(myBeanType,
                        "Cannot handle managed/back reference ${referenceName.name()}: no back reference property found from type ${property.type.typeDescription}")
        val referredType = myBeanType
        val backReferenceType = backProperty.type
        val isContainer = property.type.isContainerType

        if (!backReferenceType.rawClass.isAssignableFrom(referredType.rawClass)) {
            return context.reportBadDefinition(myBeanType,
                    "Cannot handle managed/back reference ${referenceName.name()}: back reference type (${backReferenceType.typeDescription}) not compatible with managed type (${referredType::class.qualifiedName})")
        }

        return ManagedReferenceProperty(property, referenceName, backProperty, isContainer)
    }

    /**
     * Method that wraps given property with [ObjectIdReferenceProperty] in case where object id resolution is required.
     */
    protected open fun resolvedObjectIdProperty(context: DeserializationContext,
            property: SettableBeanProperty): SettableBeanProperty {
        val objectIdInfo = property.objectIdInfo
        val valueDeserializer = property.valueDeserializer
        valueDeserializer?.getObjectIdReader(context) ?: return property
        return objectIdInfo?.let { ObjectIdReferenceProperty(property, it) } ?: property
    }

    /**
     * Helper method called to see if given property might be so-called unwrapped property: these require special
     * handling.
     */
    protected open fun findPropertyUnwrapper(context: DeserializationContext,
            property: SettableBeanProperty): NameTransformer? {
        val member = property.member ?: return null
        val unwrapper =
                context.annotationIntrospector!!.findUnwrappingNameTransformer(context.config, member) ?: return null

        if (property is CreatorProperty) {
            return context.reportBadDefinition(valueType,
                    "Cannot define Creator property \"${property.name}\" as `@CirJsonUnwrapped`: combination not yet supported")
        }

        return unwrapper
    }

    /**
     * Helper method that will handle gruesome details of dealing with properties that have inner class as value...
     */
    @OptIn(ExperimentalStdlibApi::class)
    protected open fun resolveInnerClassValuedProperty(context: DeserializationContext,
            property: SettableBeanProperty): SettableBeanProperty {
        val deserializer = property.valueDeserializer

        if (deserializer !is BeanDeserializerBase) {
            return property
        }

        val valueInstantiator = deserializer.valueInstantiator

        if (valueInstantiator.canCreateUsingDefault()) {
            return property
        }

        val valueClass = property.type.rawClass
        val enclosing = valueClass.outerClass ?: return property

        if (enclosing != myBeanType.rawClass) {
            return property
        }

        for (constructor in valueClass.constructors) {
            if (constructor.parameters.size != 1) {
                continue
            }

            val parameters = constructor.javaConstructor!!.parameterTypes.map { it.kotlin }

            if (enclosing != parameters[0]) {
                continue
            }

            if (context.canOverrideAccessModifiers()) {
                constructor.javaConstructor!!.checkAndFixAccess(
                        context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            }

            return InnerClassProperty(property, constructor)
        }

        return property
    }

    protected open fun resolveMergeAndNullSettings(context: DeserializationContext, property: SettableBeanProperty,
            propertyMetadata: PropertyMetadata): SettableBeanProperty {
        val merge = propertyMetadata.mergeInfo ?: return findValueNullProvider(context, property,
                propertyMetadata)?.let { property.withNullProvider(it) } ?: property
        val valueDeserializer = property.valueDeserializer!!
        val mayMerge = valueDeserializer.supportsUpdate(context.config)

        if (mayMerge == null) {
            if (merge.fromDefaults) {
                return property
            }
        } else if (!mayMerge) {
            if (!merge.fromDefaults) {
                context.handleBadMerge(valueDeserializer)
            }

            return property
        }

        val accessor = merge.getter
        accessor.fixAccess(context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))

        return (property as? SetterlessProperty ?: MergingSettableBeanProperty.construct(property, accessor)).let {
            findValueNullProvider(context, it, propertyMetadata)?.let { nuller -> it.withNullProvider(nuller) } ?: it
        }
    }

    /*
     *******************************************************************************************************************
     * Public accessors; null/empty value providers
     *******************************************************************************************************************
     */

    override val nullAccessPattern: AccessPattern
        get() = AccessPattern.ALWAYS_NULL

    override val emptyAccessPattern: AccessPattern
        get() = AccessPattern.DYNAMIC

    @Throws(CirJacksonException::class)
    override fun getEmptyValue(context: DeserializationContext): Any? {
        return myValueInstantiator.createUsingDefaultOrWithoutArguments(context)
    }

    /*
     *******************************************************************************************************************
     * Public accessors; other
     *******************************************************************************************************************
     */

    override val isCacheable: Boolean
        get() = true

    /**
     * Accessor for checking whether this deserializer is operating in case-insensitive manner.
     *
     * @return `true` if this deserializer should match property names without considering casing; `false` if case has
     * to match exactly.
     */
    open val isCaseInsensitive: Boolean
        get() = myBeanProperties!!.isCaseInsensitive

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return true
    }

    override fun handledType(): KClass<*> {
        return myBeanType.rawClass
    }

    override fun getObjectIdReader(context: DeserializationContext): ObjectIdReader? {
        return myObjectIdReader
    }

    open fun hasProperty(propertyName: String): Boolean {
        return myBeanProperties!!.findDefinition(propertyName) != null
    }

    open fun hasViews(): Boolean {
        return myNeedViewProcessing
    }

    /**
     * Accessor for checking number of deserialized properties.
     */
    open val propertyCount: Int
        get() = myBeanProperties!!.size

    override val knownPropertyNames: Collection<Any>
        get() = myBeanProperties!!.map { it.name }

    override val valueType: KotlinType
        get() = myBeanType

    override fun logicalType(): LogicalType? {
        return LogicalType.POJO
    }

    /**
     * Method for iterating over properties this deserializer uses; with the exception that properties passed via
     * Creator methods (specifically, "property-based constructor") are not included, but can be accessed separate by
     * calling [creatorProperties].
     */
    open fun properties(): Iterator<SettableBeanProperty> {
        return myBeanProperties?.iterator() ?: throw IllegalStateException(
                "Can only call after BeanDeserializer has been resolved")
    }

    /**
     * Method for finding properties that represents values to pass through property-based creator method (constructor
     * or factory method)
     */
    open fun creatorProperties(): Iterator<SettableBeanProperty> {
        return myPropertyBasedCreator?.properties()?.iterator() ?: emptyIterator()
    }

    open fun findProperty(propertyName: PropertyName): SettableBeanProperty? {
        return findProperty(propertyName.simpleName)
    }

    /**
     * Accessor for finding the property with given name, if POJO has one. Name used is the external name, i.e. name
     * used in external data representation (CirJSON).
     */
    open fun findProperty(propertyName: String): SettableBeanProperty? {
        return myBeanProperties?.findDefinition(propertyName) ?: myPropertyBasedCreator?.findCreatorProperty(
                propertyName)
    }

    /**
     * Alternate find method that tries to locate a property with given `propertyIndex`. Note that access by index is
     * not necessarily faster than by name, since properties are not directly indexable; however, for most instances the
     * difference is not significant as the number of properties is low.
     */
    open fun findProperty(propertyIndex: Int): SettableBeanProperty? {
        return myBeanProperties?.findDefinition(propertyIndex) ?: myPropertyBasedCreator?.findCreatorProperty(
                propertyIndex)
    }

    override fun findBackReference(referenceName: String): SettableBeanProperty? {
        return myBackReferences?.get(referenceName)
    }

    override val valueInstantiator: ValueInstantiator
        get() = myValueInstantiator

    /*
     *******************************************************************************************************************
     * Partial deserializer implementation
     *******************************************************************************************************************
     */

    /**
     * General version used when handling needs more advanced features.
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any?

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        myObjectIdReader ?: return typeDeserializer.deserializeTypedFromObject(parser, context)

        val id = parser.objectId

        if (id != null) {
            val obj = typeDeserializer.deserializeTypedFromObject(parser, context)!!
            return handleTypedObjectId(parser, context, obj, id)
        }

        var token: CirJsonToken? =
                parser.currentToken() ?: return typeDeserializer.deserializeTypedFromObject(parser, context)

        if (token!!.isScalarValue) {
            return deserializeFromObjectId(parser, context)
        }

        if (token == CirJsonToken.START_OBJECT) {
            token = parser.nextToken()
        }

        return if (token == CirJsonToken.PROPERTY_NAME && myObjectIdReader.maySerializeAsObject() &&
                myObjectIdReader.isValidReferencePropertyName(parser.currentName()!!, parser)) {
            deserializeFromObjectId(parser, context)
        } else {
            typeDeserializer.deserializeTypedFromObject(parser, context)
        }
    }

    /**
     * Off-lined method called to handle "native" Object ID that has been read and known to be associated with given deserialized POJO.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleTypedObjectId(parser: CirJsonParser, context: DeserializationContext, pojo: Any,
            rawId: Any): Any {
        val idDeserializer = myObjectIdReader!!.deserializer

        val id = if (idDeserializer.handledType() == rawId::class) {
            rawId
        } else {
            convertObjectId(parser, context, rawId, idDeserializer)!!
        }

        val readableObjectId = context.findObjectId(id, myObjectIdReader.generator, myObjectIdReader.resolver)
        readableObjectId.bindItem(pojo)
        return myObjectIdReader.idProperty?.setAndReturn(pojo, id) ?: pojo
    }

    /**
     * Helper method we need to do necessary conversion from whatever native object id type is, into declared type that
     * CirJackson internals expect. This may be simple cast (for String ids), or something more complicated; in latter
     * case we may need to create bogus content buffer to allow use of id deserializer.
     */
    @Throws(CirJacksonException::class)
    protected open fun convertObjectId(parser: CirJsonParser, context: DeserializationContext, rawId: Any,
            idDeserializer: ValueDeserializer<Any>): Any? {
        val buffer = context.bufferForInputBuffering(parser)

        when (rawId) {
            is String -> buffer.writeString(rawId)
            is Long -> buffer.writeNumber(rawId)
            is Int -> buffer.writeNumber(rawId)
            else -> buffer.writePOJO(rawId)
        }

        val bufferParser = buffer.asParserOnFirstToken(context)
        return idDeserializer.deserialize(bufferParser, context)
    }

    /**
     * Alternative deserialization method used when we expect to see Object ID; if so, we will need to ensure that the
     * ID is seen before anything else, to ensure that it is available for solving references, even if CirJSON itself is
     * not ordered that way. This may require buffering in some cases, but usually just a simple lookup to ensure that
     * ordering is correct.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeWithObjectId(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserializeFromObject(parser, context)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromObjectId(parser: CirJsonParser, context: DeserializationContext): Any? {
        val id = myObjectIdReader!!.readObjectReference(parser, context)!!
        val readableObjectId = context.findObjectId(id, myObjectIdReader.generator, myObjectIdReader.resolver)
        return readableObjectId.resolve() ?: throw UnresolvedForwardReferenceException(parser,
                "Could not resolve Object ID [$id] (for $myBeanType).", parser.currentLocation(), readableObjectId)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromObjectUsingNonDefault(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        val delegateDeserializer = delegateDeserializer()

        if (delegateDeserializer != null) {
            val bean =
                    myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))

            if (myInjectables != null) {
                injectValues(context, bean!!)
            }

            return bean
        }

        if (myPropertyBasedCreator != null) {
            return deserializeUsingPropertyBased(parser, context)
        }

        val raw = myBeanType.rawClass

        return if (raw.isNonStaticInnerClass) {
            context.handleMissingInstantiator(raw, null, parser,
                    "non-static inner classes like this can only by instantiated using default, no-argument constructor")
        } else if (NativeImageUtil.needsReflectionConfiguration(raw)) {
            context.handleMissingInstantiator(raw, null, parser,
                    "cannot deserialize from Object value (no delegate- or property-based Creator): this appears to be a native image, in which case you may need to configure reflection for the class that is to be deserialized")
        } else {
            context.handleMissingInstantiator(raw, valueInstantiator, parser,
                    "cannot deserialize from Object value (no delegate- or property-based Creator)")
        }
    }

    @Throws(CirJacksonException::class)
    protected abstract fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any?

    @Throws(CirJacksonException::class)
    open fun deserializeFromNumber(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myObjectIdReader != null) {
            return deserializeFromObjectId(parser, context)
        }

        val delegateDeserializer = delegateDeserializer()
        val numberType = parser.numberType

        return when (numberType) {
            CirJsonParser.NumberType.INT -> {
                if (delegateDeserializer == null) {
                    myValueInstantiator.createFromInt(context, parser.intValue)
                } else if (myValueInstantiator.canCreateFromInt()) {
                    myValueInstantiator.createFromInt(context, parser.intValue)
                } else {
                    myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))
                            ?.also {
                                if (myInjectables != null) {
                                    injectValues(context, it)
                                }
                            }
                }
            }

            CirJsonParser.NumberType.LONG -> {
                if (delegateDeserializer == null) {
                    myValueInstantiator.createFromLong(context, parser.longValue)
                } else if (myValueInstantiator.canCreateFromLong()) {
                    myValueInstantiator.createFromLong(context, parser.longValue)
                } else {
                    myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))
                            ?.also {
                                if (myInjectables != null) {
                                    injectValues(context, it)
                                }
                            }
                }
            }

            CirJsonParser.NumberType.DOUBLE -> {
                if (delegateDeserializer == null) {
                    myValueInstantiator.createFromDouble(context, parser.doubleValue)
                } else if (myValueInstantiator.canCreateFromDouble()) {
                    myValueInstantiator.createFromDouble(context, parser.doubleValue)
                } else {
                    myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))
                            ?.also {
                                if (myInjectables != null) {
                                    injectValues(context, it)
                                }
                            }
                }
            }

            else -> {
                context.handleMissingInstantiator(handledType(), valueInstantiator, parser,
                        "no suitable creator method found to deserialize from Number value (${parser.numberValue})")
            }
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeFromString(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myObjectIdReader != null) {
            return deserializeFromObjectId(parser, context)
        }

        val delegateDeserializer = delegateDeserializer() ?: return super.deserializeFromString(parser, context)

        return if (myValueInstantiator.canCreateFromString()) {
            super.deserializeFromString(parser, context)
        } else {
            myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))?.also {
                if (myInjectables != null) {
                    injectValues(context, it)
                }
            }
        }
    }

    /**
     * Method called to deserialize POJO value from a CirJSON floating-point number.
     */
    @Throws(CirJacksonException::class)
    open fun deserializeFromDouble(parser: CirJsonParser, context: DeserializationContext): Any? {
        val delegateDeserializer = delegateDeserializer()
        val numberType = parser.numberType

        return when (numberType) {
            CirJsonParser.NumberType.DOUBLE, CirJsonParser.NumberType.FLOAT -> {
                if (delegateDeserializer == null) {
                    myValueInstantiator.createFromDouble(context, parser.doubleValue)
                } else if (myValueInstantiator.canCreateFromDouble()) {
                    myValueInstantiator.createFromDouble(context, parser.doubleValue)
                } else {
                    myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))
                            ?.also {
                                if (myInjectables != null) {
                                    injectValues(context, it)
                                }
                            }
                }
            }

            CirJsonParser.NumberType.BIG_DECIMAL -> {
                if (delegateDeserializer == null) {
                    myValueInstantiator.createFromBigDecimal(context, parser.bigDecimalValue)
                } else if (myValueInstantiator.canCreateFromBigDecimal()) {
                    myValueInstantiator.createFromBigDecimal(context, parser.bigDecimalValue)
                } else {
                    myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))
                            ?.also {
                                if (myInjectables != null) {
                                    injectValues(context, it)
                                }
                            }
                }
            }

            else -> {
                context.handleMissingInstantiator(handledType(), valueInstantiator, parser,
                        "no suitable creator method found to deserialize from floating-point Number value (${parser.numberValue})")
            }
        }
    }

    /**
     * Method called to deserialize POJO value from a CirJSON boolean value (`true`, `false`)
     */
    @Throws(CirJacksonException::class)
    open fun deserializeFromBoolean(parser: CirJsonParser, context: DeserializationContext): Any? {
        val delegateDeserializer = delegateDeserializer()

        return if (delegateDeserializer == null) {
            val value = parser.hasToken(CirJsonToken.VALUE_TRUE)
            myValueInstantiator.createFromBoolean(context, value)
        } else if (myValueInstantiator.canCreateFromBoolean()) {
            val value = parser.hasToken(CirJsonToken.VALUE_TRUE)
            myValueInstantiator.createFromBoolean(context, value)
        } else {
            myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))?.also {
                if (myInjectables != null) {
                    injectValues(context, it)
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    open fun deserializeFromEmbedded(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myObjectIdReader != null) {
            return deserializeFromObjectId(parser, context)
        }

        val delegateDeserializer = delegateDeserializer()

        if (delegateDeserializer != null && !myValueInstantiator.canCreateFromString()) {
            return myValueInstantiator.createUsingDelegate(context, delegateDeserializer.deserialize(parser, context))
                    ?.also {
                        if (myInjectables != null) {
                            injectValues(context, it)
                        }
                    }
        }

        val value = parser.embeddedObject ?: return null

        return if (!myBeanType.isTypeOrSuperTypeOf(value::class)) {
            context.handleWeirdNativeValue(myBeanType, value, parser)
        } else {
            value
        }
    }

    protected fun delegateDeserializer(): ValueDeserializer<Any>? {
        return myDelegateDeserializer ?: myArrayDelegateDeserializer
    }

    /*
     *******************************************************************************************************************
     * Overridable helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun injectValues(context: DeserializationContext, bean: Any) {
        for (injector in myInjectables!!) {
            injector.inject(context, bean)
        }
    }

    /**
     * Method called to handle set of one or more unknown properties, stored in their entirety in given [TokenBuffer]
     * (as field entries, name and value).
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnknownProperties(context: DeserializationContext, bean: Any,
            unknownTokens: TokenBuffer): Any {
        unknownTokens.writeEndObject()

        val bufferParser = unknownTokens.asParser(context)

        while (bufferParser.nextToken() != CirJsonToken.END_OBJECT) {
            val propertyName = bufferParser.currentName()!!
            bufferParser.nextToken()
            handleUnknownProperty(bufferParser, context, bean, propertyName)
        }

        return bean
    }

    /**
     * Helper method called for an unknown property, when using "vanilla" processing.
     *
     * @param beanOrBuilder Either POJO instance (if constructed), or builder (in case of builder-based approach), that
     * has property we haven't been able to handle yet.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnknownVanilla(parser: CirJsonParser, context: DeserializationContext, beanOrBuilder: Any,
            propertyName: String) {
        if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
            handleIgnoredProperty(parser, context, beanOrBuilder, propertyName)
        } else if (myAnySetter != null) {
            try {
                myAnySetter!!.deserializeAndSet(parser, context, beanOrBuilder, propertyName)
            } catch (e: Exception) {
                wrapAndThrow(e, beanOrBuilder, propertyName, context)
            }
        } else {
            handleUnknownProperty(parser, context, beanOrBuilder, propertyName)
        }
    }

    /**
     * Method called when a CirJSON property is encountered that has not matching setter, any-setter or field, and thus
     * cannot be assigned.
     */
    @Throws(CirJacksonException::class)
    override fun handleUnknownProperty(parser: CirJsonParser, context: DeserializationContext, instanceOrClass: Any?,
            propertyName: String) {
        if (myIgnoreAllUnknown) {
            parser.skipChildren()
            return
        }

        if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
            handleIgnoredProperty(parser, context, instanceOrClass, propertyName)
        }

        super.handleUnknownProperty(parser, context, instanceOrClass, propertyName)
    }

    /**
     * Method called when an explicitly ignored property (one specified with a name to match, either by property
     * annotation or class annotation) is encountered.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleIgnoredProperty(parser: CirJsonParser, context: DeserializationContext,
            instanceOrClass: Any?, propertyName: String) {
        if (context.isEnabled(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)) {
            throw IgnoredPropertyException.from(parser, instanceOrClass ?: handledType(), propertyName,
                    knownPropertyNames)
        }

        parser.skipChildren()
    }

    /**
     * Method called in cases where we may have polymorphic deserialization case: that is, type of Creator-constructed
     * bean is not the type of deserializer itself. It should be a subclass or implementation class; either way, we may
     * have more specific deserializer to use for handling it.
     *
     * @param parser (optional) If not `null`, parser that has more properties to handle (in addition to buffered
     * properties); if `null`, all properties are passed in buffer
     */
    @Throws(CirJacksonException::class)
    protected open fun handlePolymorphic(parser: CirJsonParser?, context: DeserializationContext, bean: Any,
            unknownTokens: TokenBuffer): Any {
        var realBean = bean
        val subDeserializer = findSubclassDeserializer(context, realBean, unknownTokens)
        unknownTokens.writeEndObject()
        val otherParser = unknownTokens.asParserOnFirstToken(context, parser)
        realBean = subDeserializer.deserialize(otherParser, context, realBean)!!

        return parser?.let { subDeserializer.deserialize(it, context, realBean) } ?: realBean
    }

    /**
     * Helper method called to (try to) locate deserializer for given subtype of type that this deserializer handles.
     */
    @Throws(CirJacksonException::class)
    protected open fun findSubclassDeserializer(context: DeserializationContext, bean: Any,
            unknownTokens: TokenBuffer): ValueDeserializer<Any> {
        val classKey = ClassKey(bean::class)
        var subDeserializer = mySubDeserializers?.get(classKey)

        if (subDeserializer != null) {
            return subDeserializer
        }

        val type = context.constructType(bean::class)!!
        subDeserializer = context.findRootValueDeserializer(type)

        if (mySubDeserializers == null) {
            synchronized(this) {
                if (mySubDeserializers == null) {
                    mySubDeserializers = ConcurrentHashMap()
                }
            }
        }

        mySubDeserializers!![classKey] = subDeserializer
        return subDeserializer
    }

    /*
     *******************************************************************************************************************
     * Helper methods for error reporting
     *******************************************************************************************************************
     */

    /**
     * Method that will modify caught exception (passed in as argument) as necessary to include reference information,
     * and to ensure it is a subtype of [IOException][java.io.IOException], or an unchecked exception.
     *
     * Rules for wrapping and unwrapping are a bit complicated; essentially:
     *
     * * Errors are to be passed as is (if uncovered via unwrapping)
     *
     * * "Plain" IOExceptions (ones that are not of type [DatabindException] are to be passed as is
     */
    @Throws(DatabindException::class, CirJacksonException::class)
    open fun wrapAndThrow(throwable: Throwable, bean: Any, fieldName: String?,
            context: DeserializationContext): Nothing {
        throw CirJacksonException.wrapWithPath(throwOrReturnThrowable(throwable, context), bean, fieldName ?: "")
    }

    @Throws(CirJacksonException::class)
    private fun throwOrReturnThrowable(throwable: Throwable, context: DeserializationContext): Throwable {
        var realThrowable = throwable

        while (realThrowable is InvocationTargetException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        realThrowable = realThrowable.throwIfError()

        if (realThrowable is RuntimeException && realThrowable !is CirJacksonException) {
            val wrap = context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)

            if (!wrap) {
                throw realThrowable
            }
        }

        return realThrowable
    }

    @Throws(CirJacksonException::class)
    protected open fun wrapInstantiationProblem(throwable: Throwable, context: DeserializationContext): Any? {
        var realThrowable = throwable

        while (realThrowable is InvocationTargetException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        realThrowable = realThrowable.throwIfError().throwIfCirJacksonException()
        val wrap = context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)

        if (!wrap) {
            realThrowable = realThrowable.throwIfRuntimeException()
        }

        return context.handleInstantiationProblem(myBeanType.rawClass, null, realThrowable)
    }

    companion object {

        val TEMPORARY_PROPERTY_NAME = PropertyName("#temporary-name")

    }

}