package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ReferenceTypeSerializer
import org.cirjson.cirjackson.databind.type.ReferenceType
import org.cirjson.cirjackson.databind.util.NameTransformer
import java.util.*

open class Jdk8OptionalSerializer : ReferenceTypeSerializer<Optional<*>> {

    /*
     *******************************************************************************************************************
     * Constructors, factory methods
     *******************************************************************************************************************
     */

    constructor(fullType: ReferenceType, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?) : super(fullType, valueTypeSerializer, valueSerializer)

    protected constructor(source: Jdk8OptionalSerializer, property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?, unwrapper: NameTransformer?, suppressableValue: Any?,
            suppressNulls: Boolean) : super(source, property, valueTypeSerializer, valueSerializer, unwrapper,
            suppressableValue, suppressNulls)

    override fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?, unwrapper: NameTransformer?): ReferenceTypeSerializer<Optional<*>> {
        return Jdk8OptionalSerializer(this, property, valueTypeSerializer, valueSerializer, unwrapper,
                mySuppressableValue, mySuppressNulls)
    }

    override fun withContentInclusion(suppressableValue: Any?,
            suppressNulls: Boolean): ReferenceTypeSerializer<Optional<*>> {
        return Jdk8OptionalSerializer(this, myProperty, myValueTypeSerializer, myValueSerializer, myUnwrapper,
                suppressableValue, suppressNulls)
    }

    /*
     *******************************************************************************************************************
     * ReferenceTypeSerializer implementation
     *******************************************************************************************************************
     */

    override fun isValuePresent(value: Optional<*>): Boolean {
        return value.isPresent
    }

    override fun getReferenced(value: Optional<*>): Any? {
        return value.get()
    }

    override fun getReferencedIfPresent(value: Optional<*>): Any? {
        return value.orElse(null)
    }

}