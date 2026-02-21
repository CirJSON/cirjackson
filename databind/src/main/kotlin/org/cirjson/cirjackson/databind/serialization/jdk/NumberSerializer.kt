package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializerBase
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

/**
 * As a fallback, we may need to use this serializer for other types of [Numbers][Number]: both custom types and "big"
 * numbers like [BigInteger] and [BigDecimal].
 */
@Suppress("UNCHECKED_CAST")
open class NumberSerializer(rawType: KClass<out Number>) : StandardScalarSerializer<Number>(rawType as KClass<Number>) {

    protected val myIsInt = rawType == BigInteger::class

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val handledType = handledType()!!
        val format = findFormatOverrides(provider, property, handledType)

        return if (format.shape != CirJsonFormat.Shape.STRING) {
            this
        } else if (handledType == BigDecimal::class) {
            bigDecimalAsStringSerializer()
        } else {
            ToStringSerializer.INSTANCE
        }
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Number, generator: CirJsonGenerator, serializers: SerializerProvider) {
        when (value) {
            is BigDecimal -> generator.writeNumber(value)
            is BigInteger -> generator.writeNumber(value)
            is Long -> generator.writeNumber(value)
            is Double -> generator.writeNumber(value)
            is Float -> generator.writeNumber(value)
            is Int, is Short, is Byte -> generator.writeNumber(value.toInt())
            else -> generator.writeNumber(value.toString())
        }
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        if (myIsInt) {
            visitIntFormat(visitor, typeHint, CirJsonParser.NumberType.BIG_INTEGER)
        } else if (handledType() == BigDecimal::class) {
            visitFloatFormat(visitor, typeHint, CirJsonParser.NumberType.BIG_DECIMAL)
        } else {
            visitor.expectNumberFormat(typeHint)
        }
    }

    internal class BigDecimalAsStringSerializer : ToStringSerializerBase(BigDecimal::class) {

        override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
            return false
        }

        @Throws(CirJacksonException::class)
        override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
            val text = if (generator.isEnabled(StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN)) {
                if (!verifyBigDecimalRange(value as BigDecimal)) {
                    return serializers.reportMappingProblem(
                            "Attempt to write plain `BigDecimal` (see StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN) with illegal scale (${value.scale()}): needs to be between [-$MAX_BIG_DECIMAL_SCALE, $MAX_BIG_DECIMAL_SCALE]")
                }

                value.toPlainString()
            } else {
                value.toString()
            }

            generator.writeString(text)
        }

        override fun valueToString(value: Any): String {
            throw IllegalStateException()
        }

        private fun verifyBigDecimalRange(value: BigDecimal): Boolean {
            val scale = value.scale()
            return scale in -MAX_BIG_DECIMAL_SCALE..MAX_BIG_DECIMAL_SCALE
        }

        companion object {

            val BIG_DECIMAL_INSTANCE = BigDecimalAsStringSerializer()

        }

    }

    companion object {

        /**
         * Static instance that is only to be used for [Number].
         */
        val INSTANCE = NumberSerializer(Number::class)

        /**
         * Copied from `cirjackson-core` class `GeneratorBase`
         */
        const val MAX_BIG_DECIMAL_SCALE = 9999

        fun bigDecimalAsStringSerializer(): ValueSerializer<*> {
            return BigDecimalAsStringSerializer.BIG_DECIMAL_INSTANCE
        }

    }

}