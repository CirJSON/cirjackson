package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerBuilder
import org.cirjson.cirjackson.databind.deserialization.SettableAnyProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.implementation.ExternalTypeHandler
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.UnwrappedPropertyHandler
import org.cirjson.cirjackson.databind.deserialization.implementation.ValueInjector
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.type.ClassKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

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
    protected val myNeedViewProcesing: Boolean

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
        myNeedViewProcesing = hasViews
        myBackReferences = backReferences

        myUnwrappedPropertyHandler = null
        myExternalTypeIdHandler = null
        myObjectIdReader = objectIdReader
    }

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
        myNeedViewProcesing = source.myNeedViewProcesing
        myBackReferences = source.myBackReferences

        myUnwrappedPropertyHandler = source.myUnwrappedPropertyHandler
        myExternalTypeIdHandler = source.myExternalTypeIdHandler
        myObjectIdReader = source.myObjectIdReader
    }

    companion object {

        val TEMPORARY_PROPERTY_NAME = PropertyName("#temporary-name")

    }

}