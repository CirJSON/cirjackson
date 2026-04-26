package org.cirjson.cirjackson.databind.external.sql

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.jdk.DateBasedDeserializer
import java.sql.Date
import java.text.DateFormat

/**
 * Compared to plain old [java.util.Date], SQL version is easier to deal with: mostly because it is more limited.
 */
open class JavaSqlDateDeserializer : DateBasedDeserializer<Date> {

    constructor() : super(Date::class)

    constructor(source: JavaSqlDateDeserializer, dateFormat: DateFormat?, formatString: String?) : super(source,
            dateFormat, formatString)

    override fun withDateFormat(dateFormat: DateFormat?, formatString: String?): JavaSqlDateDeserializer {
        return JavaSqlDateDeserializer(this, dateFormat, formatString)
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return Date(0L)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Date? {
        val date = parseDate(parser, context)
        return date?.let { Date(it.time) }
    }

}