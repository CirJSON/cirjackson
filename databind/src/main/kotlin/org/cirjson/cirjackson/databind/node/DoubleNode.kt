package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.extensions.isNotFinite
import org.cirjson.cirjackson.databind.SerializerProvider
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.round

/**
 * Numeric node that contains simple 64-bit ("double precision") floating point values.
 */
open class DoubleNode constructor(protected val myValue: Double) : NumericNode() {

    /*
     *******************************************************************************************************************
     * CirJsonNode implementation
     *******************************************************************************************************************
     */

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_NUMBER_FLOAT
    }

    override val numberType: CirJsonParser.NumberType
        get() = CirJsonParser.NumberType.DOUBLE

    override val isFloatingPointNumber: Boolean
        get() = true

    override val isDouble: Boolean
        get() = true

    override fun canConvertToInt(): Boolean {
        return myValue in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
    }

    override fun canConvertToLong(): Boolean {
        return myValue in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()
    }

    override fun canConvertToExactIntegral(): Boolean {
        return !myValue.isNaN() && !myValue.isInfinite() && myValue == round(myValue)
    }

    override fun numberValue(): Number {
        return myValue
    }

    override fun byteValue(): Byte {
        return myValue.toInt().toByte()
    }

    override fun shortValue(): Short {
        return myValue.toInt().toShort()
    }

    override fun intValue(): Int {
        return myValue.toInt()
    }

    override fun longValue(): Long {
        return myValue.toLong()
    }

    override fun floatValue(): Float {
        return myValue.toFloat()
    }

    override fun doubleValue(): Double {
        return myValue.toDouble()
    }

    override fun bigDecimalValue(): BigDecimal {
        return BigDecimal.valueOf(myValue)
    }

    override fun bigIntegerValue(): BigInteger {
        return bigDecimalValue().toBigInteger()
    }

    override fun asText(): String {
        return myValue.toString()
    }

    override fun isNaN(): Boolean {
        return myValue.isNotFinite()
    }

    @Throws(CirJacksonException::class)
    final override fun serialize(generator: CirJsonGenerator, context: SerializerProvider) {
        generator.writeNumber(myValue)
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is DoubleNode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        val value = myValue.toRawBits()
        return value.toInt() xor (value shr 32).toInt()
    }

    companion object {

        fun valueOf(value: Double): DoubleNode {
            return DoubleNode(value)
        }

    }

}