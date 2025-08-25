package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import kotlin.reflect.KClass

abstract class StandardDeserializer<T> : ValueDeserializer<T>, ValueInstantiator.Gettable {

    protected constructor(valueClass: KClass<*>) {
    }

}