package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.NullifyingDeserializer
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * Container object that contains serializers for miscellaneous JDK types that require special handling and are not
 * grouped along with a set of other serializers (like date/time)
 */
object JDKMiscDeserializers {

    private val ourClassNames = hashSetOf(UUID::class.qualifiedName!!, AtomicBoolean::class.qualifiedName!!,
            AtomicInteger::class.qualifiedName!!, AtomicLong::class.qualifiedName!!,
            StackTraceElement::class.qualifiedName!!, ByteBuffer::class.qualifiedName!!, Void::class.qualifiedName!!,
            Unit::class.qualifiedName!!, Nothing::class.qualifiedName!!).apply {
        for (klass in JDKFromStringDeserializer.types()) {
            add(klass.qualifiedName!!)
        }
    }

    fun find(context: DeserializationContext, rawType: KClass<*>, rawTypeName: String): ValueDeserializer<*>? {
        if (rawTypeName !in ourClassNames) {
            return null
        }

        val deserializer = JDKFromStringDeserializer.findDeserializer(rawType)

        if (deserializer != null) {
            return deserializer
        }

        return when (rawType) {
            UUID::class -> UUIDDeserializer()
            StackTraceElement::class -> StackTraceElementDeserializer.construct(context)
            AtomicBoolean::class -> AtomicBooleanDeserializer()
            AtomicInteger::class -> AtomicIntegerDeserializer()
            AtomicLong::class -> AtomicLongDeserializer()
            ByteBuffer::class -> ByteBufferDeserializer()
            Void::class, Unit::class, Nothing::class -> NullifyingDeserializer.INSTANCE
            else -> null
        }
    }

    fun hasDeserializerFor(rawType: KClass<*>): Boolean {
        return rawType.qualifiedName!! in ourClassNames
    }

}