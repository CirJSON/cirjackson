package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import java.text.DateFormat
import java.util.*

/**
 * Standard serializer for [Calendar]. As with other time/date types, is configurable to produce timestamps (standard
 * 64-bit timestamp) or textual formats (usually ISO-8601).
 */
@CirJacksonStandardImplementation
class JavaUtilCalendarSerializer(useTimestamp: Boolean?, customFormat: DateFormat?) :
        DateTimeSerializerBase<Calendar>(Calendar::class, useTimestamp, customFormat) {

    constructor() : this(null, null)

    override fun withFormat(timestamp: Boolean?, customFormat: DateFormat?): JavaUtilCalendarSerializer {
        return JavaUtilCalendarSerializer(timestamp, customFormat)
    }

    override fun timestamp(value: Calendar?): Long {
        return value?.timeInMillis ?: 0L
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Calendar, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (asTimestamp(serializers)) {
            generator.writeNumber(timestamp(value))
            return
        }

        serializeAsString(value.time, generator, serializers)
    }

    companion object {

        val INSTANCE = JavaUtilCalendarSerializer()

    }

}