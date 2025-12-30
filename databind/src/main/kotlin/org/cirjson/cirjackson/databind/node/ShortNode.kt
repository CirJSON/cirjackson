package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.SerializerProvider
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Numeric node that contains simple 16-bit integer values.
 */
open class ShortNode constructor(protected val myValue: Short) : NumericNode() {

    /*
     *******************************************************************************************************************
     * CirJsonNode implementation
     *******************************************************************************************************************
     */

    override fun asToken(): CirJsonToken {
        return CirJsonToken.VALUE_NUMBER_INT
    }

    override val numberType: CirJsonParser.NumberType
        get() = CirJsonParser.NumberType.INT

    override val isIntegralNumber: Boolean
        get() = true

    override val isShort: Boolean
        get() = true

    override fun canConvertToInt(): Boolean {
        return true
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
        return myValue
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
        return BigDecimal.valueOf(myValue.toLong())
    }

    override fun bigIntegerValue(): BigInteger {
        return BigInteger.valueOf(myValue.toLong())
    }

    override fun asText(): String {
        return myValue.toString()
    }

    override fun asBoolean(): Boolean {
        return myValue != 0.toShort()
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

        if (other !is ShortNode) {
            return false
        }

        return myValue == other.myValue
    }

    override fun hashCode(): Int {
        return myValue.toInt()
    }

    companion object {

        fun valueOf(value: Short): ShortNode {
            return ShortNode(value)
        }

    }

}