package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import java.text.DateFormat
import java.util.*

/**
 * Simple deserializer for handling [Date] values.
 * 
 * One way to customize Date formats accepted is to override method [DeserializationContext.parseDate] that this basic
 * deserializer calls.
 */
@CirJacksonStandardImplementation
open class JavaUtilDateDeserializer : DateBasedDeserializer<Date> {

    constructor() : super(Date::class)

    constructor(base: JavaUtilDateDeserializer, format: DateFormat?, formatString: String?) : super(base, format,
            formatString)

    override fun withDateFormat(dateFormat: DateFormat?, formatString: String?): JavaUtilDateDeserializer {
        return JavaUtilDateDeserializer(this, dateFormat, formatString)
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return Date(0L)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Date? {
        return parseDate(parser, context)
    }

    companion object {

        val INSTANCE = JavaUtilDateDeserializer()

    }

}