package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter

/**
 * Builder class used for aggregating deserialization information about a POJO, in order to build a [ValueSerializer]
 * for serializing instances. Main reason for using separate builder class is that this makes it easier to make actual
 * serializer class fully immutable.
 */
open class BeanSerializerBuilder {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected val myBeanDescription: BeanDescription

    protected val myConfig: SerializationConfig

    /*
     *******************************************************************************************************************
     * Accumulated information about properties
     *******************************************************************************************************************
     */

    /**
     * Bean properties, in order of serialization
     */
    protected var myProperties = emptyList<BeanPropertyWriter>()

    /**
     * Optional array of filtered property writers; if `null`, no view-based filtering is performed.
     */
    protected var myFilteredProperties: Array<BeanPropertyWriter?>? = null

    /**
     * Writer used for "any getter" properties, if any.
     */
    protected var myAnyGetter: AnyGetterWriter? = null

    /**
     * ID of the property filter to use for POJO, if any.
     */
    protected var myFilterId: Any? = null

    /**
     * Property that is used for type id (and not serialized as regular property)
     */
    protected var myTypeId: AnnotatedMember? = null

    /**
     * Object responsible for serializing Object Ids for the handled type, if any.
     */
    protected var myObjectIdWriter: ObjectIdWriter? = null

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    constructor(config: SerializationConfig, beanDescription: BeanDescription) {
        myConfig = config
        myBeanDescription = beanDescription
    }

    /**
     * Copy-constructor that may be used for subclassing
     */
    protected constructor(source: BeanSerializerBuilder) {
        myConfig = source.myConfig
        myBeanDescription = source.myBeanDescription
        myProperties = source.myProperties
        myFilteredProperties = source.myFilteredProperties
        myAnyGetter = source.myAnyGetter
        myFilterId = source.myFilterId
        myTypeId = source.myTypeId
        myObjectIdWriter = source.myObjectIdWriter
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val classInfo: AnnotatedClass
        get() = myBeanDescription.classInfo

    open val beanDescription: BeanDescription
        get() = myBeanDescription

    /**
     * Bean properties, in order of serialization
     */
    open var properties: List<BeanPropertyWriter>
        get() = myProperties
        set(value) {
            myProperties = value
        }

    open fun hasProperties(): Boolean {
        return myProperties.isNotEmpty()
    }

    /**
     * Optional array of filtered property writers; if `null`, no view-based filtering is performed.
     *
     * Number and order of properties here MUST match that of "regular" properties set earlier using [properties]; if
     * not, an [IllegalArgumentException] will be thrown.
     */
    open var filteredProperties: Array<BeanPropertyWriter?>?
        get() = myFilteredProperties
        set(value) {
            if (value != null && myProperties.size != value.size) {
                throw IllegalArgumentException(
                        "Trying to set ${value.size} filtered properties; must match length of non-filtered `properties` (${myProperties.size})")
            }

            myFilteredProperties = value
        }

    /**
     * Writer used for "any getter" properties, if any.
     */
    open var anyGetter: AnyGetterWriter?
        get() = myAnyGetter
        set(value) {
            myAnyGetter = value
        }

    /**
     * ID of the property filter to use for POJO, if any.
     */
    open var filterId: Any?
        get() = myFilterId
        set(value) {
            myFilterId = value
        }

    /**
     * Property that is used for type id (and not serialized as regular property)
     */
    open var typeId: AnnotatedMember?
        get() = myTypeId
        set(value) {
            if (myTypeId != null) {
                throw IllegalArgumentException("Multiple type ids specified with $myTypeId and $value")
            }

            myTypeId = value
        }

    /**
     * Object responsible for serializing Object Ids for the handled type, if any.
     */
    open var objectIdWriter: ObjectIdWriter?
        get() = myObjectIdWriter
        set(value) {
            myObjectIdWriter = value
        }

    /*
     *******************************************************************************************************************
     * Build methods for actually creating serializer instance
     *******************************************************************************************************************
     */

    /**
     * Method called to create [BeanSerializer] instance with all accumulated information. Will construct a serializer
     * if we have enough information, or return `null` if not.
     */
    open fun build(): ValueSerializer<*>? {
        myAnyGetter?.fixAccess(myConfig)

        myTypeId?.takeIf { myConfig.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS) }
                ?.apply { fixAccess(myConfig.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS)) }

        val properties = if (myProperties.isEmpty()) {
            if (myAnyGetter == null && myObjectIdWriter == null) {
                return null
            }

            NO_PROPERTIES
        } else {
            val result = myProperties.toTypedArray()

            if (myConfig.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) {
                for (property in result) {
                    property.fixAccess(myConfig)
                }
            }

            result
        }

        if (myFilteredProperties != null && myFilteredProperties!!.size != myProperties.size) {
            throw IllegalStateException(
                    "Mismatch between `properties` size (${myProperties.size}), `filteredProperties` (${myFilteredProperties!!.size}): should have as many (or `null` for latter)")
        }

        return UnrolledBeanSerializer.tryConstruct(myBeanDescription.type, this, properties, myFilteredProperties)
                ?: BeanSerializer(myBeanDescription.type, this, properties, myFilteredProperties)
    }

    /**
     * Factory method for constructing an "empty" serializer; one that outputs no properties (but handles CirJSON
     * objects properly, including type information)
     */
    open fun createDummy(): BeanSerializer {
        return BeanSerializer.createDummy(myBeanDescription.type, this)
    }

    companion object {

        private val NO_PROPERTIES = emptyArray<BeanPropertyWriter>()

    }

}