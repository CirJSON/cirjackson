package org.cirjson.cirjackson.databind.external.sql

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.serialization.jdk.DateTimeSerializerBase
import java.sql.Date
import java.text.DateFormat

/**
 * Compared to regular [java.util.Date] serialization, we do use String representation here. Why? Basically to truncate
 * off time part, since that should not be used by plain SQL date.
 */
@CirJacksonStandardImplementation
open class JavaSqlDateSerializer protected constructor(useTimestamp: Boolean?, customFormat: DateFormat?) :
        DateTimeSerializerBase<Date>(Date::class, useTimestamp, customFormat) {

    constructor() : this(null, null)

    override fun withFormat(timestamp: Boolean?, customFormat: DateFormat?): JavaSqlDateSerializer {
        return JavaSqlDateSerializer(timestamp, customFormat)
    }

    override fun timestamp(value: Date?): Long {
        return value?.time ?: 0
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

        val INSTANCE = JavaSqlDateSerializer()

    }

}