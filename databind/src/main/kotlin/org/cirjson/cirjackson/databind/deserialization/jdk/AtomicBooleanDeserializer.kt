package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import java.util.concurrent.atomic.AtomicBoolean

open class AtomicBooleanDeserializer : StandardScalarDeserializer<AtomicBoolean>(AtomicBoolean::class) {

    override fun logicalType(): LogicalType {
        return LogicalType.BOOLEAN
    }

    override fun getEmptyValue(context: DeserializationContext): Any {
        return AtomicBoolean(false)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): AtomicBoolean? {
        val token = parser.currentToken()

        return if (token == CirJsonToken.VALUE_TRUE) {
            AtomicBoolean(true)
        } else if (token == CirJsonToken.VALUE_FALSE) {
            AtomicBoolean(false)
        } else {
            val boolean = parseBoolean(parser, context, AtomicBoolean::class)
            boolean?.let { AtomicBoolean(it) }
        }
    }

}