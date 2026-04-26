package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.ReferenceTypeDeserializer
import java.util.concurrent.atomic.AtomicReference

open class AtomicReferenceDeserializer(fullType: KotlinType, instantiator: ValueInstantiator?,
        typeDeserializer: TypeDeserializer?, deserializer: ValueDeserializer<*>?) :
        ReferenceTypeDeserializer<AtomicReference<Any?>>(fullType, instantiator, typeDeserializer, deserializer) {

    /*
     *******************************************************************************************************************
     * Abstract method implementations
     *******************************************************************************************************************
     */

    override fun withResolved(typeDeserializer: TypeDeserializer?,
            valueDeserializer: ValueDeserializer<*>?): AtomicReferenceDeserializer {
        return AtomicReferenceDeserializer(myFullType, myValueInstantiator, typeDeserializer, valueDeserializer)
    }

    override fun getNullValue(context: DeserializationContext): AtomicReference<Any?> {
        return AtomicReference(myValueDeserializer!!.getNullValue(context))
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return getNullValue(context)
    }

    /**
     * Let's actually NOT coerce missing Creator parameters into empty value.
     */
    override fun getAbsentValue(context: DeserializationContext): Any? {
        return null
    }

    override fun referenceValue(contents: Any?): AtomicReference<Any?>? {
        return AtomicReference(contents)
    }

    override fun getReferenced(reference: AtomicReference<Any?>): Any? {
        return reference.get()
    }

    override fun updateReference(reference: AtomicReference<Any?>, contents: Any?): AtomicReference<Any?> {
        reference.set(contents)
        return reference
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return true
    }

}