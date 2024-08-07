package org.cirjson.cirjackson.core.io.schubfach

import java.math.BigDecimal
import kotlin.random.Random

class FloatToDecimalChecker private constructor(private var v: Float, s: String) : ToDecimalChecker(s) {

    private val myOriginalBits = v.toRawBits()

    override fun toBigDecimal(): BigDecimal {
        return BigDecimal(v.toDouble())
    }

    override fun recovers(bigDecimal: BigDecimal): Boolean {
        return bigDecimal.toFloat() == v
    }

    override fun recovers(string: String): Boolean {
        return string.toFloat() == v
    }

    override fun hexBits(): String {
        return String.format("0x%01X__%02X__%02X_%04X", myOriginalBits ushr 31 and 0x1, myOriginalBits ushr 23 and 0xFF,
                myOriginalBits ushr 16 and 0x7F, myOriginalBits and 0xFFFF)
    }

    override fun minExp(): Int {
        return E_MIN
    }

    override fun maxExp(): Int {
        return E_MAX
    }

    override fun maxLen10(): Int {
        return H
    }

    override fun negate() {
        v = -v
    }

    override val isZero: Boolean
        get() = v == 0.0f

    override val isInfinity: Boolean
        get() = v == Float.POSITIVE_INFINITY

    override val isNegative: Boolean
        get() = myOriginalBits < 0

    override val isNaN: Boolean
        get() = v.isNaN()

    companion object {

        val P = 3.0f.toRawBits().countTrailingZeroBits() + 2

        private val W = Float.SIZE_BITS - P

        val Q_MIN = (-1 shl W - 1) - P + 3

        val Q_MAX = (1 shl W - 1) - P

        val C_MIN = 1 shl P - 1

        val C_MAX = (1 shl P) - 1

        val K_MIN = MathUtils.flog10pow2(Q_MIN)

        val K_MAX = MathUtils.flog10pow2(Q_MAX)

        val H = MathUtils.flog10pow2(P) + 2

        val MIN_VALUE = StrictMath.scalb(1.0f, Q_MIN)

        val MIN_NORMAL = StrictMath.scalb(C_MIN.toFloat(), Q_MIN)

        val MAX_VALUE = StrictMath.scalb(C_MAX.toFloat(), Q_MAX)

        val E_MIN = e(MIN_VALUE.toDouble())

        val E_MAX = e(MAX_VALUE.toDouble())

        val C_TINY = cTiny(Q_MIN, K_MIN)

        fun toDec(v: Float) {
            val s = FloatToDecimal.toString(v)
            FloatToDecimalChecker(v, s).validate()
        }

        /**
         * Random floats over the whole range
         */
        fun randomNumberTests(randomCount: Int, random: Random) {
            for (i in 1..randomCount) {
                toDec(Float.fromBits(random.nextInt()))
            }
        }

    }

}