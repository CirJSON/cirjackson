package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.serialization.cirjackson.TokenBufferSerializer
import org.cirjson.cirjackson.databind.serialization.standard.NullSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.util.TokenBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * Object that providers access for serializers used for non-structured JDK types that are serialized as scalars; some
 * using basic [ToStringSerializer][org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer], others
 * explicit serializers.
 */
object JDKMiscellaneousSerializers {

    /**
     * Method called by [BasicSerializerFactory][org.cirjson.cirjackson.databind.serialization.BasicSerializerFactory]
     * to find one of serializers provided here.
     */
    fun find(type: KClass<*>): ValueSerializer<*>? {
        val serializer = JDKStringLikeSerializer.find(type)

        if (serializer != null) {
            return serializer
        }

        return when (type) {
            UUID::class -> UUIDSerializer()
            AtomicBoolean::class -> AtomicBooleanSerializer()
            AtomicInteger::class -> AtomicIntegerSerializer()
            AtomicLong::class -> AtomicLongSerializer()
            TokenBuffer::class -> TokenBufferSerializer()
            Void::class, Void.TYPE.kotlin, Nothing::class -> NullSerializer
            else -> null
        }
    }

    /*
     *******************************************************************************************************************
     * Serializers for atomic types
     *******************************************************************************************************************
     */

    open class AtomicBooleanSerializer : StandardScalarSerializer<AtomicBoolean>(AtomicBoolean::class) {

        @Throws(CirJacksonException::class)
        override fun serialize(value: AtomicBoolean, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeBoolean(value.get())
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitor.expectBooleanFormat(typeHint)
        }

    }

    open class AtomicIntegerSerializer : StandardScalarSerializer<AtomicInteger>(AtomicInteger::class) {

        @Throws(CirJacksonException::class)
        override fun serialize(value: AtomicInteger, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value.get())
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitIntFormat(visitor, typeHint, CirJsonParser.NumberType.INT)
        }

    }

    open class AtomicLongSerializer : StandardScalarSerializer<AtomicLong>(AtomicLong::class) {

        @Throws(CirJacksonException::class)
        override fun serialize(value: AtomicLong, generator: CirJsonGenerator, serializers: SerializerProvider) {
            generator.writeNumber(value.get())
        }

        override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
            visitIntFormat(visitor, typeHint, CirJsonParser.NumberType.LONG)
        }

    }

}