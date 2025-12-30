package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Numeric node that contains simple 64-bit integer values.
 */
open class LongNode constructor(protected val myValue: Long) : NumericNode() {

    /*
     *******************************************************************************************************************
     * CirJsonNode implementation
     *******************************************************************************************************************
     */

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_NUMBER_INT
    }

    override val numberType: CirJsonParser.NumberType
        get() = CirJsonParser.NumberType.LONG

    override val isIntegralNumber: Boolean
        get() = true

    override val isLong: Boolean
        get() = true

    override fun canConvertToInt(): Boolean {
        return myValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    }

    override fun canConvertToLong(): Boolean {
        return true
    }

    override fun numberValue(): Number {
        return myValue
    }

    override fun byteValue(): Byte {
        return myValue.toByte()
    }

    override fun shortValue(): Short {
        return myValue.toShort()
    }

    override fun intValue(): Int {
        return myValue.toInt()
    }

    override fun longValue(): Long {
        return myValue
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
        return BigInteger.valueOf(myValue)
    }

    override fun asText(): String {
        return myValue.toString()
    }

    override fun asBoolean(): Boolean {
        return myValue != 0L
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

        if (other !is LongNode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        return myValue.toInt() xor (myValue shr 32).toInt()
    }

    companion object {

        fun valueOf(value: Long): LongNode {
            return LongNode(value)
        }

    }

}