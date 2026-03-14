package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.util.ByteBufferBackedInputStream
import java.nio.ByteBuffer

open class ByteBufferSerializer : StandardScalarSerializer<ByteBuffer>(ByteBuffer::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: ByteBuffer, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (value.hasArray()) {
            val position = value.position()
            generator.writeBinary(value.array(), value.arrayOffset() + position, value.limit() - position)
            return
        }

        val copy = value.asReadOnlyBuffer()
        ByteBufferBackedInputStream(copy).use {
            generator.writeBinary(it, copy.remaining())
        }
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectArrayFormat(typeHint)?.itemsFormat(CirJsonFormatTypes.INTEGER)
    }

}