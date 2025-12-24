package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer

class TypeWrappedDeserializer(private val myTypeDeserializer: TypeDeserializer,
        private val myDeserializer: ValueDeserializer<Any>) : ValueDeserializer<Any>() {

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

}