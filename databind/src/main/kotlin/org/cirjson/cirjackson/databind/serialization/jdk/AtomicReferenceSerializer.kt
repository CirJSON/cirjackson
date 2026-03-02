package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ReferenceTypeSerializer
import org.cirjson.cirjackson.databind.type.ReferenceType
import org.cirjson.cirjackson.databind.util.NameTransformer
import java.util.concurrent.atomic.AtomicReference

open class AtomicReferenceSerializer : ReferenceTypeSerializer<AtomicReference<*>> {

    /*
     *******************************************************************************************************************
     * Constructors, factory methods
     *******************************************************************************************************************
     */

    constructor(fullType: ReferenceType, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?) : super(fullType, valueTypeSerializer, valueSerializer)

    protected constructor(source: AtomicReferenceSerializer, property: BeanProperty?,
            valueTypeSerializer: TypeSerializer?, valueSerializer: ValueSerializer<*>?, unwrapper: NameTransformer?,
            suppressableValue: Any?, suppressNulls: Boolean) : super(source, property, valueTypeSerializer,
            valueSerializer, unwrapper, suppressableValue, suppressNulls)

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?,
            unwrapper: NameTransformer?): ReferenceTypeSerializer<AtomicReference<*>> {
        return AtomicReferenceSerializer(this, property, valueTypeSerializer, valueSerializer, unwrapper,
                mySuppressableValue, mySuppressNulls)
    }

    override fun withContentInclusion(suppressableValue: Any?,
            suppressNulls: Boolean): ReferenceTypeSerializer<AtomicReference<*>> {
        return AtomicReferenceSerializer(this, myProperty, myValueTypeSerializer, myValueSerializer, myUnwrapper,
                suppressableValue, suppressNulls)
    }

    /*
     *******************************************************************************************************************
     * ReferenceTypeSerializer implementation
     *******************************************************************************************************************
     */

    override fun isValuePresent(value: AtomicReference<*>): Boolean {
        return value.get() != null
    }

    override fun getReferenced(value: AtomicReference<*>): Any? {
        return value.get()
    }

    override fun getReferencedIfPresent(value: AtomicReference<*>): Any? {
        return value.get()
    }

}