package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Numeric node that contains simple 64-bit ("double precision") floating point values.
 */
open class BigDecimalNode constructor(protected val myValue: BigDecimal) : NumericNode() {

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

    override val isBigDecimal: Boolean
        get() = true

    override fun canConvertToInt(): Boolean {
        return myValue in MIN_INT..MAX_INT
    }

    override fun canConvertToLong(): Boolean {
        return myValue in MIN_LONG..MAX_LONG
    }

    override fun canConvertToExactIntegral(): Boolean {
        return myValue.signum() == 0 || myValue.scale() <= 0 || myValue.stripTrailingZeros().scale() <= 0
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
        return myValue.toLong()
    }

    override fun floatValue(): Float {
        return myValue.toFloat()
    }

    override fun doubleValue(): Double {
        return myValue.toDouble()
    }

    override fun bigDecimalValue(): BigDecimal {
        return myValue
    }

    override fun bigIntegerValue(): BigInteger {
        return myValue.toBigInteger()
    }

    override fun asText(): String {
        return myValue.toString()
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

        if (other !is BigDecimalNode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        return myValue.hashCode()
    }

    companion object {

        private val MIN_INT = BigDecimal.valueOf(Int.MIN_VALUE.toLong())

        private val MAX_INT = BigDecimal.valueOf(Int.MAX_VALUE.toLong())

        private val MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE)

        private val MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE)

        fun valueOf(value: BigDecimal): BigDecimalNode {
            return BigDecimalNode(value)
        }

    }

}