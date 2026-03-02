package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import java.util.stream.DoubleStream

/**
 * [DoubleStream] serializer.
 * 
 * Unfortunately there to common ancestor between number base stream, so we need to define each in a specific class.
 */
class DoubleStreamSerializer private constructor() : StandardSerializer<DoubleStream>(DoubleStream::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: DoubleStream, generator: CirJsonGenerator, serializers: SerializerProvider) {
        try {
            value.use { stream ->
                generator.writeStartArray(stream)
                stream.forEach { generator.writeNumber(it) }
                generator.writeEndArray()
            }
        } catch (e: Exception) {
            wrapAndThrow(serializers, e, value, generator.streamWriteContext().currentIndex)
        }
    }

    companion object {

        val INSTANCE = DoubleStreamSerializer()

    }

}