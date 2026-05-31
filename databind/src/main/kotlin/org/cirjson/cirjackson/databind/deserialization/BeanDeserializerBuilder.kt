package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer
import org.cirjson.cirjackson.databind.deserialization.bean.BeanPropertyMap
import org.cirjson.cirjackson.databind.deserialization.bean.BuilderBasedDeserializer
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdValueProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ValueInjector
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.util.*

/**
 * Builder class used for aggregating deserialization information about a property-based POJO, in order to build a
 * [ValueDeserializer] for deserializing POJO instances.
 */
open class BeanDeserializerBuilder {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    protected val myConfig: DeserializationConfig

    protected val myContext: DeserializationContext

    /*
     *******************************************************************************************************************
     * General information about POJO
     *******************************************************************************************************************
     */

    /**
     * Introspected information about POJO for deserializer to handle
     */
    protected val myBeanDescription: BeanDescription

    /*
     *******************************************************************************************************************
     * Accumulated information about properties
     *******************************************************************************************************************
     */

    /**
     * Properties to deserialize collected so far.
     */
    protected val myProperties: MutableMap<String, SettableBeanProperty> = LinkedHashMap()

    /**
     * Parameters of the primary properties-based Creator, if any.
     */
    protected var myPropertiesBasedCreatorParameters: Array<SettableBeanProperty>? = null

    /**
     * Value injectors for deserialization
     */
    protected var myInjectables: MutableList<ValueInjector>? = null

    /**
     * Back-reference properties this bean contains (if any)
     */
    protected var myBackReferenceProperties: HashMap<String, SettableBeanProperty>? = null

    /**
     * Set of names of properties that are recognized but are to be ignored for deserialization purposes (meaning no
     * exception is thrown, value is just skipped).
     */
    protected var myIgnorableProperties: HashSet<String>? = null

    /**
     * Set of names of properties that are recognized and are set to be included for deserialization purposes (`null`
     * deactivate this, empty includes nothing).
     */
    protected var myIncludableProperties: HashSet<String>? = null

    /**
     * Object that will handle value instantiation for the bean type.
     */
    protected var myValueInstantiator: ValueInstantiator? = null

    /**
     * Handler for Object ID values, if Object Ids are enabled for the bean type.
     */
    protected var myObjectIdReader: ObjectIdReader? = null

    /**
     * Fallback setter used for handling any properties that are not mapped to regular setters. If setter is not `null`,
     * it will be called once for each such property.
     */
    protected var myAnySetter: SettableAnyProperty? = null

    /**
     * Flag that can be set to ignore and skip unknown properties. If set, will not throw an exception for unknown
     * properties.
     */
    protected var myIgnoreAllUnknown = false

    /**
     * When creating Builder-based deserializers, this indicates method to call on builder to finalize value.
     */
    protected var myBuildMethod: AnnotatedMethod? = null

    /**
     * In addition, Builder may have additional configuration
     */
    protected var myBuilderConfig: CirJsonPOJOBuilder.Value? = null

    /*
     *******************************************************************************************************************
     * Lifecycle: construction
     *******************************************************************************************************************
     */

    constructor(beanDescription: BeanDescription, context: DeserializationContext) {
        myBeanDescription = beanDescription
        myContext = context
        myConfig = context.config
    }

    /**
     * Copy constructor for subclasses to use, when constructing custom builder instances
     */
    protected constructor(source: BeanDeserializerBuilder) {
        myConfig = source.myConfig
        myContext = source.myContext
        myBeanDescription = source.myBeanDescription

        myProperties.putAll(source.myProperties)
        myPropertiesBasedCreatorParameters = source.myPropertiesBasedCreatorParameters
        myInjectables = source.myInjectables.copy()
        myBackReferenceProperties = source.myBackReferenceProperties.copy()
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myValueInstantiator = source.myValueInstantiator
        myObjectIdReader = source.myObjectIdReader

        myAnySetter = source.myAnySetter
        myIgnoreAllUnknown = source.myIgnoreAllUnknown

        myBuildMethod = source.myBuildMethod
        myBuilderConfig = source.myBuilderConfig
    }

    private fun HashMap<String, SettableBeanProperty>?.copy(): HashMap<String, SettableBeanProperty>? {
        return this?.let { HashMap(it) }
    }

    private fun <T> MutableList<T>?.copy(): MutableList<T>? {
        return this?.let { ArrayList(it) }
    }

    /*
     *******************************************************************************************************************
     * Lifecycle: state modification
     *******************************************************************************************************************
     */

    /**
     * Method for adding a new property or replacing a property.
     */
    open fun addOrReplaceProperty(property: SettableBeanProperty, allowOverride: Boolean) {
        val oldProperty = myProperties.put(property.name, property) ?: return

        val propertiesBasedCreatorParameters = myPropertiesBasedCreatorParameters ?: return
        for (i in propertiesBasedCreatorParameters.indices) {
            if (propertiesBasedCreatorParameters[i] === oldProperty) {
                propertiesBasedCreatorParameters[i] = property
            }
        }
    }

    /**
     * Method to add a property setter. Will ensure that there is no unexpected override; if one is found will throw a
     * [IllegalArgumentException].
     */
    open fun addProperty(property: SettableBeanProperty) {
        val old = myProperties.put(property.name, property) ?: return

        if (old !== property) {
            throw IllegalArgumentException("Duplicate property '${property.name}' for ${myBeanDescription.type}")
        }
    }

    /**
     * Method called to add a property that represents so-called back reference; reference that "points back" to object
     * that has a forward reference to currently built bean.
     */
    open fun addBackReferenceProperty(referenceName: String, property: SettableBeanProperty) {
        val backReferenceProperties = myBackReferenceProperties ?: HashMap<String, SettableBeanProperty>(4).also {
            myBackReferenceProperties = it
        }

        if (myConfig.canOverrideAccessModifiers()) {
            try {
                property.fixAccess(myConfig)
            } catch (e: IllegalArgumentException) {
                handleBadAccess(e)
            }
        }

        backReferenceProperties[referenceName] = property
    }

    open fun addInjectable(propertyName: PropertyName, propertyType: KotlinType, contextAnnotations: Annotations,
            member: AnnotatedMember, valueId: Any) {
        val injectables = myInjectables ?: ArrayList<ValueInjector>().also { myInjectables = it }

        if (myConfig.canOverrideAccessModifiers()) {
            try {
                member.fixAccess(myConfig.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            } catch (e: IllegalArgumentException) {
                handleBadAccess(e)
            }
        }

        injectables.add(ValueInjector(propertyName, propertyType, member, valueId))
    }

    /**
     * Method that will add property name as one of properties that can be ignored if not recognized.
     */
    open fun addIgnorable(propertyName: String) {
        myIgnorableProperties ?: HashSet<String>().also { myIgnorableProperties = it }.add(propertyName)
    }

    /**
     * Method that will add property name as one of the properties that will be included.
     */
    open fun addIncludable(propertyName: String) {
        myIncludableProperties ?: HashSet<String>().also { myIncludableProperties = it }.add(propertyName)
    }

    /**
     * Method called by deserializer factory, when a "creator property" (something that is passed via constructor- or
     * factory method argument; instead of setter or field).
     *
     * Default implementation does not do anything.
     */
    open fun addCreatorProperty(property: SettableBeanProperty) {
        addProperty(property)
    }

    open var ignoreUnknownProperties: Boolean
        get() = throw UnsupportedOperationException()
        set(value) {
            myIgnoreAllUnknown = value
        }

    open fun setPOJOBuilder(buildMethod: AnnotatedMethod?, config: CirJsonPOJOBuilder.Value?) {
        myBuildMethod = buildMethod
        myBuilderConfig = config
    }

    /*
     *******************************************************************************************************************
     * Public accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor for all properties that this builder currently contains.
     *
     * Note that properties are returned in order that properties are ordered (explicitly, or by rule), which is the
     * deserialization order.
     */
    open val properties: Iterator<SettableBeanProperty>
        get() = myProperties.values.iterator()

    open fun findProperty(propertyName: PropertyName): SettableBeanProperty? {
        return myProperties[propertyName.name]
    }

    open fun hasProperty(propertyName: PropertyName): Boolean {
        return myProperties[propertyName.name] != null
    }

    open fun removeProperty(propertyName: PropertyName): SettableBeanProperty? {
        return myProperties.remove(propertyName.name)
    }

    open var anySetter: SettableAnyProperty?
        get() = myAnySetter
        set(value) {
            if (myAnySetter != null && value != null) {
                throw IllegalStateException("myAnySetter already set to non-null")
            }

            myAnySetter = value
        }

    open var valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator
        set(value) {
            myValueInstantiator = value!!
            myPropertiesBasedCreatorParameters = value.getFromObjectArguments(myConfig)
        }

    open val injectables: List<ValueInjector>?
        get() = myInjectables

    open var objectIdReader: ObjectIdReader?
        get() = myObjectIdReader
        set(value) {
            myObjectIdReader = value
        }

    open val buildMethod: AnnotatedMethod?
        get() = myBuildMethod

    open val builderConfig: CirJsonPOJOBuilder.Value?
        get() = myBuilderConfig

    open fun hasIgnorable(name: String): Boolean {
        return IgnorePropertiesUtil.shouldIgnore(name, myIgnorableProperties, myIncludableProperties)
    }

    /*
     *******************************************************************************************************************
     * Build method(s)
     *******************************************************************************************************************
     */

    /**
     * Method for constructing a [BeanDeserializer], given all information collected.
     */
    open fun build(): ValueDeserializer<*> {
        var properties: Collection<SettableBeanProperty> = myProperties.values
        fixAccess(properties)

        myObjectIdReader?.also {
            properties = addIdProperty(myProperties, ObjectIdValueProperty(it, PropertyMetadata.STANDARD_REQUIRED))
        }

        return BeanDeserializer(this, myBeanDescription, constructPropertyMap(properties), myBackReferenceProperties,
                myIgnorableProperties, myIgnoreAllUnknown, myIncludableProperties, anyViews(properties))
    }

    /**
     * Alternate build method used when we must be using some form of abstract resolution, usually by using addition
     * Type IT ("polymorphic deserialization")
     */
    open fun buildAbstract(): AbstractDeserializer {
        return AbstractDeserializer(this, myBeanDescription, myBackReferenceProperties, myProperties)
    }

    /**
     * Method for constructing a specialized deserializer that uses additional external Builder object during
     * databinding.
     */
    open fun buildBuilderBased(valueType: KotlinType, expectedBuildMethodName: String): ValueDeserializer<*> {
        val buildMethod = myBuildMethod

        if (buildMethod == null) {
            if (!expectedBuildMethodName.isEmpty()) {
                return myContext.reportBadDefinition(myBeanDescription.type,
                        "Builder class ${myBeanDescription.type.typeDescription} does not have build method (name: '$expectedBuildMethodName')")
            }
        } else {
            val rawBuildType = buildMethod.rawReturnType
            val rawValueType = valueType.rawClass

            if (rawBuildType != rawValueType && !rawBuildType.isAssignableFrom(
                            rawValueType) && !rawValueType.isAssignableFrom(rawBuildType)) {
                return myContext.reportBadDefinition(myBeanDescription.type,
                        "Build method `${buildMethod.fullName}` has wrong return type (${rawBuildType.classDescription}), not compatible with POJO type (${valueType.typeDescription})")
            }
        }

        fixAccess(myProperties.values)

        val properties = myObjectIdReader?.let {
            addIdProperty(myProperties, ObjectIdValueProperty(it, PropertyMetadata.STANDARD_REQUIRED))
        } ?: myProperties.values
        return createBuilderBasedDeserializer(valueType, constructPropertyMap(properties), anyViews(properties))
    }

    /**
     * Extension point for overriding the actual creation of the builder deserializer.
     */
    protected open fun createBuilderBasedDeserializer(valueType: KotlinType, propertyMap: BeanPropertyMap,
            anyViews: Boolean): ValueDeserializer<*> {
        return BuilderBasedDeserializer(this, myBeanDescription, valueType, propertyMap, myBackReferenceProperties,
                myIgnorableProperties, myIgnoreAllUnknown, myIncludableProperties, anyViews)
    }

    /*
     *******************************************************************************************************************
     * Internal helper method(s)
     *******************************************************************************************************************
     */

    protected open fun fixAccess(mainProperties: Collection<SettableBeanProperty>) {
        if (myConfig.canOverrideAccessModifiers()) {
            for (property in mainProperties) {
                try {
                    property.fixAccess(myConfig)
                } catch (e: IllegalArgumentException) {
                    handleBadAccess(e)
                }
            }
        }

        myAnySetter?.also {
            try {
                it.fixAccess(myConfig)
            } catch (e: IllegalArgumentException) {
                handleBadAccess(e)
            }
        }

        myBuildMethod?.also {
            try {
                it.fixAccess(myConfig.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            } catch (e: IllegalArgumentException) {
                handleBadAccess(e)
            }
        }
    }

    protected open fun addIdProperty(properties: Map<String, SettableBeanProperty>,
            idProperty: SettableBeanProperty): Collection<SettableBeanProperty> {
        val name = idProperty.name
        val result = ArrayList(properties.values)

        if (name !in properties) {
            result.add(idProperty)
        } else {
            val iterator = result.listIterator()

            while (true) {
                if (iterator.next().name == name) {
                    iterator.set(idProperty)
                    break
                }
            }
        }

        return result
    }

    protected open fun anyViews(properties: Collection<SettableBeanProperty>): Boolean {
        if (!myConfig.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)) {
            return true
        }

        for (property in properties) {
            if (property.hasViews()) {
                return true
            }
        }

        return false
    }

    protected open fun constructPropertyMap(properties: Collection<SettableBeanProperty>): BeanPropertyMap {
        val format = myBeanDescription.findExpectedFormat(null)!!
        val caseInsensitive =
                format.getFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES) ?: myConfig.isEnabled(
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)

        return BeanPropertyMap.construct(myConfig, properties, collectAliases(properties), caseInsensitive)
    }

    protected open fun collectAliases(properties: Collection<SettableBeanProperty>): Array<Array<PropertyName>?>? {
        var result: Array<Array<PropertyName>?>? = null

        for ((i, property) in properties.withIndex()) {
            val member = property.member ?: continue
            val aliases = property.findAliases(myConfig).takeIf { it.isNotEmpty() } ?: continue

            if (result == null) {
                result = arrayOfNulls(properties.size)
            }

            result[i] = aliases.toTypedArray()
        }

        return result
    }

    /**
     * Helper method for linking root cause to "invalid type definition" exception; needed for troubleshooting issues
     * with forcing access on later JDKs (as module definition boundaries are more strictly enforced).
     */
    protected open fun handleBadAccess(originalException: IllegalArgumentException): Nothing {
        try {
            throw myContext.reportBadTypeDefinition<Exception>(myBeanDescription, originalException.message)
        } catch (e: DatabindException) {
            if (e.cause == null) {
                e.initCause(originalException)
            }

            throw e
        }
    }

}