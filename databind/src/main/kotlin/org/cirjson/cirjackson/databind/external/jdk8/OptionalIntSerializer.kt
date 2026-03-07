package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.util.*

open class OptionalIntSerializer : StandardScalarSerializer<OptionalInt>(OptionalInt::class) {

    override fun isEmpty(provider: SerializerProvider, value: OptionalInt?): Boolean {
        return value == null || !value.isPresent
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: OptionalInt, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            generator.writeNumber(value.asInt)
        } else {
            generator.writeNull()
        }
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectNumberFormat(typeHint)?.numberType(CirJsonParser.NumberType.INT)
    }

}