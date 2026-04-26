package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.ReferenceTypeDeserializer
import java.util.*

open class Jdk8OptionalDeserializer(fullType: KotlinType, instantiator: ValueInstantiator?,
        typeDeserializer: TypeDeserializer?, deserializer: ValueDeserializer<*>?) :
        ReferenceTypeDeserializer<Optional<*>>(fullType, instantiator, typeDeserializer, deserializer) {

    /*
     *******************************************************************************************************************
     * Abstract method implementations
     *******************************************************************************************************************
     */

    override fun withResolved(typeDeserializer: TypeDeserializer?,
            valueDeserializer: ValueDeserializer<*>?): Jdk8OptionalDeserializer {
        return Jdk8OptionalDeserializer(myFullType, myValueInstantiator, typeDeserializer, valueDeserializer)
    }

    override fun getNullValue(context: DeserializationContext): Optional<*> {
        return Optional.ofNullable(myValueDeserializer!!.getNullValue(context))
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

    override fun referenceValue(contents: Any?): Optional<*>? {
        return Optional.ofNullable(contents)
    }

    override fun getReferenced(reference: Optional<*>): Any? {
        return reference.orElse(null)
    }

    override fun updateReference(reference: Optional<*>, contents: Any?): Optional<*> {
        return Optional.ofNullable(contents)
    }

}