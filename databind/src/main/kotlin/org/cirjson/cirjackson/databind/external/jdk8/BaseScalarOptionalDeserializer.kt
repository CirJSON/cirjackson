package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import kotlin.reflect.KClass

abstract class BaseScalarOptionalDeserializer<T : Any> protected constructor(clazz: KClass<*>,
        protected val myEmpty: T) : StandardScalarDeserializer<T>(clazz) {

    override fun getNullValue(context: DeserializationContext): T {
        return myEmpty
    }

}