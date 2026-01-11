package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import kotlin.reflect.KClass

open class ToEmptyObjectSerializer : StandardSerializer<Any> {

    protected constructor(raw: KClass<*>) : super(raw)

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

}