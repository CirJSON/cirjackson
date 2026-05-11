package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer

/**
 * A deserializer that stores an [Error] caught during constructing of the deserializer, which needs to be deferred and
 * only during actual attempt to deserialize a value of given type. Note that `null` and empty values can be
 * deserialized without error.
 */
class ErrorThrowingDeserializer(private val myCause: NoClassDefFoundError) : ValueDeserializer<Any>() {

    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        throw myCause
    }

}