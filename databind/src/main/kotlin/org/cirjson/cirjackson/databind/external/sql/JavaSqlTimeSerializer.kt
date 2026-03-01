package org.cirjson.cirjackson.databind.external.sql

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonValueFormat
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.sql.Time

@CirJacksonStandardImplementation
open class JavaSqlTimeSerializer : StandardScalarSerializer<Time>(Time::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: Time, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeString(value.toString())
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitStringFormat(visitor, typeHint, CirJsonValueFormat.DATE_TIME)
    }

}