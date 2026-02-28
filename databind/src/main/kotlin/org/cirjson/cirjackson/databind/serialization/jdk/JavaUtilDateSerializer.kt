package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import java.text.DateFormat
import java.util.*

/**
 * For efficiency, we will serialize Dates as longs, instead of potentially more readable Strings.
 */
@CirJacksonStandardImplementation
open class JavaUtilDateSerializer(useTimestamp: Boolean?, customFormat: DateFormat?) :
        DateTimeSerializerBase<Date>(Date::class, useTimestamp, customFormat) {

    constructor() : this(null, null)

    override fun withFormat(timestamp: Boolean?, customFormat: DateFormat?): JavaUtilDateSerializer {
        return JavaUtilDateSerializer(timestamp, customFormat)
    }

    override fun timestamp(value: Date?): Long {
        return value?.time ?: 0L
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Date, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (asTimestamp(serializers)) {
            generator.writeNumber(timestamp(value))
            return
        }

        serializeAsString(value, generator, serializers)
    }

    companion object {

        /**
         * Default instance that is used when no contextual configuration is needed.
         */
        val INSTANCE = JavaUtilDateSerializer()

    }

}