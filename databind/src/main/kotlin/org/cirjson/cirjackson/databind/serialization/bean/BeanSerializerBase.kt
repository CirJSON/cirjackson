package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.serialization.AnyGetterWriter
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.BeanSerializerBuilder
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer

/**
 * Base class both for the standard bean serializer, and a couple of variants that only differ in small details. Can be
 * used for custom bean serializers as well, although that is not the primary design goal.
 */
abstract class BeanSerializerBase : StandardSerializer<Any> {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    protected val myBeanType: KotlinType

    /**
     * Writers used for outputting actual property values
     */
    protected val myProperties: Array<BeanPropertyWriter>

    /**
     * Optional filters used to suppress output of properties that are only to be included in certain views
     */
    protected val myFilteredProperties: Array<BeanPropertyWriter>?

    /**
     * Handler for [org.cirjson.cirjackson.annotations.CirJsonAnyGetter] annotated properties
     */
    protected val myAnyGetterWriter: AnyGetterWriter?

    /**
     * ID of the bean property filter to use, if any; `null` if none.
     */
    protected val myPropertyFilterId: Any?

    /**
     * If using custom type ids (usually via getter, or field), this is the reference to that member.
     */
    protected val myTypeId: AnnotatedMember?

    /**
     * If this POJO can be alternatively serialized using just an object id to denote a reference to previously
     * serialized object, this [ObjectIdWriter] will handle details.
     */
    protected val myObjectIdWriter: ObjectIdWriter?

    /**
     * Requested shape from bean class annotations, if any.
     */
    protected val mySerializationShape: CirJsonFormat.Shape?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructor used by [BeanSerializerBuilder] to create an instance
     *
     * @param type Nominal type of values handled by this serializer
     *
     * @param builder Builder for accessing other collected information
     */
    protected constructor(type: KotlinType, builder: BeanSerializerBuilder?, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter>?) : super(type) {
        myBeanType = type
        myProperties = properties
        myFilteredProperties = filteredProperties

        if (builder != null) {
            myTypeId = builder.typeId
            myAnyGetterWriter = builder.anyGetter
            myPropertyFilterId = builder.filterId
            myObjectIdWriter = builder.objectIdWriter
            mySerializationShape = builder.beanDescription.findExpectedFormat(type.rawClass)!!.shape
        } else {
            myTypeId = null
            myAnyGetterWriter = null
            myPropertyFilterId = null
            myObjectIdWriter = null
            mySerializationShape = null
        }
    }

    companion object {

        val NAME_FOR_OBJECT_REFERENCE = PropertyName("#object-ref")

        val NO_PROPS = emptyArray<BeanPropertyWriter>()

    }

}