package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.ByteBufferBackedOutputStream
import java.nio.ByteBuffer

open class ByteBufferDeserializer : StandardScalarDeserializer<ByteBuffer>(ByteBuffer::class) {

    override fun logicalType(): LogicalType {
        return LogicalType.BINARY
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): ByteBuffer? {
        val bytes = parser.binaryValue!!
        return ByteBuffer.wrap(bytes)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: ByteBuffer): ByteBuffer? {
        ByteBufferBackedOutputStream(intoValue).use {
            parser.readBinaryValue(context.base64Variant, it)
        }

        return intoValue
    }

}