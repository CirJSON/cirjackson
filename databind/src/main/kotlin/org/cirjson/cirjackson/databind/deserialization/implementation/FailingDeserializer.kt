package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import kotlin.reflect.KClass

/**
 * Special bogus "deserializer" that will throw [org.cirjson.cirjackson.databind.exception.MismatchedInputException] if
 * an attempt is made to deserialize a value. This is used as placeholder to avoid NPEs for uninitialized structured
 * serializers or handlers.
 */
open class FailingDeserializer(valueClass: KClass<*>, protected val myMessage: String?) :
        StandardDeserializer<Any>(valueClass) {

    constructor(message: String?) : this(Any::class, message)

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        return context.reportInputMismatch(this, myMessage)
    }

}