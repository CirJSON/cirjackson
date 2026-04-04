package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer

/**
 * Special bogus "serializer" that will throw [DatabindException][org.cirjson.cirjackson.databind.DatabindException] if
 * its [serialize] gets invoked. Most commonly registered as handler for unknown types, as well as for catching
 * unintended usage (like trying to use `null` as Map/Object key).
 */
open class FailingSerializer(protected val myMessage: String) : StandardSerializer<Any>(Any::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        return serializers.reportMappingProblem(myMessage)
    }

}