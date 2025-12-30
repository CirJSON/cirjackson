package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Numeric node that contains simple big integer values.
 */
open class BigIntegerNode constructor(protected val myValue: BigInteger) : NumericNode() {

    /*
     *******************************************************************************************************************
     * CirJsonNode implementation
     *******************************************************************************************************************
     */

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_NUMBER_INT
    }

    override val numberType: CirJsonParser.NumberType
        get() = CirJsonParser.NumberType.BIG_INTEGER

    override val isIntegralNumber: Boolean
        get() = true

    override val isLong: Boolean
        get() = true

    override fun canConvertToInt(): Boolean {
        return myValue in MIN_INT..MAX_INT
    }

    override fun canConvertToLong(): Boolean {
        return myValue in MIN_LONG..MAX_LONG
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
        return BigDecimal(myValue)
    }

    override fun bigIntegerValue(): BigInteger {
        return myValue
    }

    override fun asText(): String {
        return myValue.toString()
    }

    override fun asBoolean(): Boolean {
        return myValue != BigInteger.ZERO
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

        if (other !is BigIntegerNode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        return myValue.hashCode()
    }

    companion object {

        private val MIN_INT = BigInteger.valueOf(Int.MIN_VALUE.toLong())

        private val MAX_INT = BigInteger.valueOf(Int.MAX_VALUE.toLong())

        private val MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE)

        private val MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE)

        fun valueOf(value: BigInteger): BigIntegerNode {
            return BigIntegerNode(value)
        }

    }

}