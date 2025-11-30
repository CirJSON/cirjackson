package org.cirjson.cirjackson.databind.serialization.cirjackson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import kotlin.reflect.KClass

open class RawSerializer<T>(clazz: KClass<*>) : StandardSerializer<T>(clazz) {

    override fun serialize(value: T, generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

}