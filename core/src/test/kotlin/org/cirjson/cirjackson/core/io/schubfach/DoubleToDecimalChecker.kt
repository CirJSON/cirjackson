package org.cirjson.cirjackson.core.io.schubfach

import java.math.BigDecimal
import kotlin.random.Random

class DoubleToDecimalChecker private constructor(private var v: Double, s: String) : ToDecimalChecker(s) {

    private val myOriginalBits = v.toRawBits()

    override fun toBigDecimal(): BigDecimal {
        return BigDecimal(v)
    }

    override fun recovers(bigDecimal: BigDecimal): Boolean {
        return bigDecimal.toDouble() == v
    }

    override fun recovers(string: String): Boolean {
        return string.toDouble() == v
    }

    override fun hexBits(): String {
        return String.format("0x%01X__%03X__%01X_%04X_%04X_%04X", (myOriginalBits ushr 63 and 0x1L).toInt(),
                (myOriginalBits ushr 52 and 0x7FFL).toInt(), (myOriginalBits ushr 48 and 0xFL).toInt(),
                (myOriginalBits ushr 32 and 0xFFFFL).toInt(), (myOriginalBits ushr 16 and 0xFFFFL).toInt(),
                (myOriginalBits and 0xFFFFL).toInt())
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
        get() = v == 0.0

    override val isInfinity: Boolean
        get() = v == Double.POSITIVE_INFINITY

    override val isNegative: Boolean
        get() = myOriginalBits < 0

    override val isNaN: Boolean
        get() = v.isNaN()

    companion object {

        val P = 3.0.toRawBits().countTrailingZeroBits() + 2

        private val W = Double.SIZE_BITS - P

        val Q_MIN = (-1 shl W - 1) - P + 3

        val Q_MAX = (1 shl W - 1) - P

        val C_MIN = 1L shl P - 1

        val C_MAX = (1L shl P) - 1

        val K_MIN = MathUtils.flog10pow2(Q_MIN)

        val K_MAX = MathUtils.flog10pow2(Q_MAX)

        val H = MathUtils.flog10pow2(P) + 2

        val MIN_VALUE = StrictMath.scalb(1.0, Q_MIN)

        val MIN_NORMAL = StrictMath.scalb(C_MIN.toDouble(), Q_MIN)

        val MAX_VALUE = StrictMath.scalb(C_MAX.toDouble(), Q_MAX)

        val E_MIN = e(MIN_VALUE)

        val E_MAX = e(MAX_VALUE)

        val C_TINY = cTiny(Q_MIN, K_MIN)

        fun toDec(v: Double) {
            val s = DoubleToDecimal.toString(v)
            DoubleToDecimalChecker(v, s).validate()
        }

        fun randomNumberTests(randomCount: Int, random: Random) {
            testRandom(randomCount, random)
            testRandomUnit(randomCount, random)
            testRandomMilli(randomCount, random)
            testRandomMicro(randomCount, random)
        }

        /**
         * Random doubles over the whole range
         */
        private fun testRandom(randomCount: Int, random: Random) {
            for (i in 1..randomCount) {
                toDec(Double.fromBits(random.nextLong()))
            }
        }

        /**
         * Random doubles over the integer range [0, 2^52).
         * These are all exact doubles and exercise the fast path (except 0).
         */
        private fun testRandomUnit(randomCount: Int, random: Random) {
            for (i in 1..randomCount) {
                toDec((random.nextLong() and (1L shl P - 1)).toDouble())
            }
        }

        /**
         * Random doubles over the range [0, 10^15) as "multiples" of 1e-3
         */
        private fun testRandomMilli(randomCount: Int, random: Random) {
            for (i in 1..randomCount) {
                toDec((random.nextLong().mod(1_000_000_000_000_000_000L) / 1e3))
            }
        }

        /**
         * Random doubles over the range [0, 10^15) as "multiples" of 1e-6
         */
        private fun testRandomMicro(randomCount: Int, random: Random) {
            for (i in 1..randomCount) {
                toDec(((random.nextLong() and 0x7FFF_FFFF_FFFF_FFFFL) / 1e6))
            }
        }

    }

}