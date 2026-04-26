package org.cirjson.cirjackson.databind.external.sql

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.jdk.DateBasedDeserializer
import java.sql.Timestamp
import java.text.DateFormat

/**
 * Simple deserializer for handling [Timestamp] values.
 * 
 * One way to customize Timestamp formats accepted is to override method [parseDate] that this basic deserializer calls.
 */
class JavaSqlTimestampDeserializer : DateBasedDeserializer<Timestamp> {

    constructor() : super(Timestamp::class)

    constructor(source: JavaSqlTimestampDeserializer, dateFormat: DateFormat?, formatString: String?) : super(source,
            dateFormat, formatString)

    override fun withDateFormat(dateFormat: DateFormat?, formatString: String?): JavaSqlTimestampDeserializer {
        return JavaSqlTimestampDeserializer(this, dateFormat, formatString)
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return Timestamp(0L)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Timestamp? {
        val date = parseDate(parser, context)
        return date?.let { Timestamp(it.time) }
    }

}