package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonParser
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Intermediate value node used for numeric nodes.
 */
abstract class NumericNode protected constructor() : ValueNode() {

    final override val nodeType: CirJsonNodeType
        get() = CirJsonNodeType.NUMBER

    abstract override val numberType: CirJsonParser.NumberType

    abstract override fun numberValue(): Number

    abstract override fun byteValue(): Byte

    abstract override fun shortValue(): Short

    abstract override fun intValue(): Int

    abstract override fun longValue(): Long

    abstract override fun floatValue(): Float

    abstract override fun doubleValue(): Double

    abstract override fun bigDecimalValue(): BigDecimal

    abstract override fun bigIntegerValue(): BigInteger

    abstract override fun canConvertToInt(): Boolean

    abstract override fun canConvertToLong(): Boolean

    /*
     *******************************************************************************************************************
     * General type coercions
     *******************************************************************************************************************
     */

    final override fun asInt(): Int {
        return intValue()
    }

    final override fun asInt(defaultValue: Int): Int {
        return intValue()
    }

    final override fun asLong(): Long {
        return longValue()
    }

    final override fun asLong(defaultValue: Long): Long {
        return longValue()
    }

    final override fun asDouble(): Double {
        return doubleValue()
    }

    final override fun asDouble(defaultValue: Double): Double {
        return doubleValue()
    }

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    /**
     * Convenience method for checking whether this node is a [FloatNode] or [DoubleNode] that contains "not-a-number"
     * (NaN) value.
     */
    open fun isNaN(): Boolean {
        return false
    }

}