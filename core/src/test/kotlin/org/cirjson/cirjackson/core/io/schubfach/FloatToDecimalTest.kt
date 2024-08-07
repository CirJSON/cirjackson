package org.cirjson.cirjackson.core.io.schubfach

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatToDecimalTest {

    @Test
    fun testExtremeValues() {
        FloatToDecimalChecker.toDec(Float.NEGATIVE_INFINITY)
        FloatToDecimalChecker.toDec(-Float.MAX_VALUE)
        FloatToDecimalChecker.toDec(-java.lang.Float.MIN_NORMAL)
        FloatToDecimalChecker.toDec(-Float.MIN_VALUE)
        FloatToDecimalChecker.toDec(-0.0f)
        FloatToDecimalChecker.toDec(0.0f)
        FloatToDecimalChecker.toDec(Float.MIN_VALUE)
        FloatToDecimalChecker.toDec(java.lang.Float.MIN_NORMAL)
        FloatToDecimalChecker.toDec(Float.MAX_VALUE)
        FloatToDecimalChecker.toDec(Float.POSITIVE_INFINITY)
        FloatToDecimalChecker.toDec(Float.NaN)

        FloatToDecimalChecker.toDec(Float.fromBits(0x7FC0_0001))
        FloatToDecimalChecker.toDec(Float.fromBits(0x7F80_0001))
        FloatToDecimalChecker.toDec(Float.fromBits(HardHolder.INT1))
        FloatToDecimalChecker.toDec(Float.fromBits(HardHolder.INT2))

        for (c in 1L..<FloatToDecimalChecker.C_TINY) {
            FloatToDecimalChecker.toDec(c * Float.MIN_VALUE)
        }
    }

    @Test
    fun testPowersOf10() {
        for (e in FloatToDecimalChecker.E_MIN..FloatToDecimalChecker.E_MAX) {
            FloatToDecimalChecker.toDec("1e$e".toFloat())
        }
    }

    @Test
    fun testPowersOf2() {
        var v = Float.MIN_VALUE

        while (v <= Float.MAX_VALUE) {
            FloatToDecimalChecker.toDec(v)
            v *= 2
        }
    }

    @Test
    fun testSomeAnomalies() {
        for (s in FloatAnomalies) {
            FloatToDecimalChecker.toDec(s.toFloat())
        }
    }

    @Test
    fun testPaxson() {
        for (i in FloatPaxsonSignificands.indices) {
            FloatToDecimalChecker.toDec(StrictMath.scalb(FloatPaxsonSignificands[i], FloatPaxsonExponents[i]))
        }
    }

    @Test
    fun testIntegers() {
        for (i in IntProgression.fromClosedRange(0, (1 shl FloatToDecimalChecker.P - 1) - 1, 3)) {
            FloatToDecimalChecker.toDec(i.toFloat())
        }
    }

    @Test
    fun testRandomNumbers() {
        FloatToDecimalChecker.randomNumberTests(1_000_000, Random.Default)
    }

    @Test
    fun testConstants() {
        assertEquals(FloatToDecimal.P, FloatToDecimalChecker.P, "P")
        assertEquals(FloatToDecimalChecker.C_MIN, FloatToDecimalChecker.C_MIN.toFloat().toInt(), "C_MIN")
        assertEquals(FloatToDecimalChecker.C_MAX, FloatToDecimalChecker.C_MAX.toFloat().toInt(), "C_MAX")
        assertEquals(Float.MIN_VALUE, FloatToDecimalChecker.MIN_VALUE, "MIN_VALUE")
        assertEquals(java.lang.Float.MIN_NORMAL, FloatToDecimalChecker.MIN_NORMAL, "MIN_NORMAL")
        assertEquals(Float.MAX_VALUE, FloatToDecimalChecker.MAX_VALUE, "MAX_VALUE")

        assertEquals(FloatToDecimal.Q_MIN, FloatToDecimalChecker.Q_MIN, "Q_MIN")
        assertEquals(FloatToDecimal.Q_MAX, FloatToDecimalChecker.Q_MAX, "Q_MAX")

        assertEquals(FloatToDecimal.K_MIN, FloatToDecimalChecker.K_MIN, "K_MIN")
        assertEquals(FloatToDecimal.K_MAX, FloatToDecimalChecker.K_MAX, "K_MAX")
        assertEquals(FloatToDecimal.H, FloatToDecimalChecker.H, "H")

        assertEquals(FloatToDecimal.E_MIN, FloatToDecimalChecker.E_MIN, "E_MIN")
        assertEquals(FloatToDecimal.E_MAX, FloatToDecimalChecker.E_MAX, "E_MAX")
        assertEquals(FloatToDecimal.C_TINY.toLong(), FloatToDecimalChecker.C_TINY, "C_TINY")
    }

}