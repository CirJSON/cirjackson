package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import java.util.concurrent.atomic.AtomicLong

open class AtomicLongDeserializer : StandardScalarDeserializer<AtomicLong>(AtomicLong::class) {

    override fun logicalType(): LogicalType {
        return LogicalType.INTEGER
    }

    override fun getEmptyValue(context: DeserializationContext): Any {
        return AtomicLong()
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): AtomicLong? {
        return if (parser.isExpectedNumberIntToken) {
            AtomicLong(parser.longValue)
        } else {
            val int = parseLong(parser, context, AtomicLong::class)
            int?.let { AtomicLong(it) }
        }
    }

}