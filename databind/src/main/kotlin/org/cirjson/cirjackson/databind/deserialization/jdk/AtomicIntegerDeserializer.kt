package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import java.util.concurrent.atomic.AtomicInteger

open class AtomicIntegerDeserializer : StandardScalarDeserializer<AtomicInteger>(AtomicInteger::class) {

    override fun logicalType(): LogicalType {
        return LogicalType.INTEGER
    }

    override fun getEmptyValue(context: DeserializationContext): Any {
        return AtomicInteger()
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): AtomicInteger? {
        return if (parser.isExpectedNumberIntToken) {
            AtomicInteger(parser.intValue)
        } else {
            val int = parseInt(parser, context, AtomicInteger::class)
            int?.let { AtomicInteger(it) }
        }
    }

}