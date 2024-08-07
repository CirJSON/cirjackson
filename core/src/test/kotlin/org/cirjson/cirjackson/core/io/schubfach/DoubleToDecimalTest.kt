package org.cirjson.cirjackson.core.io.schubfach

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DoubleToDecimalTest {

    @Test
    fun testExtremeValues() {
        DoubleToDecimalChecker.toDec(Double.NEGATIVE_INFINITY)
        DoubleToDecimalChecker.toDec(-Double.MAX_VALUE)
        DoubleToDecimalChecker.toDec(-java.lang.Double.MIN_NORMAL)
        DoubleToDecimalChecker.toDec(-Double.MIN_VALUE)
        DoubleToDecimalChecker.toDec(-0.0)
        DoubleToDecimalChecker.toDec(0.0)
        DoubleToDecimalChecker.toDec(Double.MIN_VALUE)
        DoubleToDecimalChecker.toDec(java.lang.Double.MIN_NORMAL)
        DoubleToDecimalChecker.toDec(Double.MAX_VALUE)
        DoubleToDecimalChecker.toDec(Double.POSITIVE_INFINITY)
        DoubleToDecimalChecker.toDec(Double.NaN)

        DoubleToDecimalChecker.toDec(Double.fromBits(0x7FF8_0000_0000_0001L))
        DoubleToDecimalChecker.toDec(Double.fromBits(0x7FF0_0000_0000_0001L))
        DoubleToDecimalChecker.toDec(Double.fromBits(HardHolder.LONG1))
        DoubleToDecimalChecker.toDec(Double.fromBits(HardHolder.LONG2))

        for (c in 1L..<DoubleToDecimalChecker.C_TINY) {
            DoubleToDecimalChecker.toDec(c * Double.MIN_VALUE)
        }
    }

    @Test
    fun testPowersOf10() {
        for (e in DoubleToDecimalChecker.E_MIN..DoubleToDecimalChecker.E_MAX) {
            DoubleToDecimalChecker.toDec("1e$e".toDouble())
        }
    }

    @Test
    fun testPowersOf2() {
        var v = Double.MIN_VALUE

        while (v <= Double.MAX_VALUE) {
            DoubleToDecimalChecker.toDec(v)
            v *= 2
        }
    }

    @Test
    fun testSomeAnomalies() {
        for (s in DoubleAnomalies) {
            DoubleToDecimalChecker.toDec(s.toDouble())
        }
    }

    @Test
    fun testPaxson() {
        for (i in DoublePaxsonSignificands.indices) {
            DoubleToDecimalChecker.toDec(StrictMath.scalb(DoublePaxsonSignificands[i], DoublePaxsonExponents[i]))
        }
    }

    @Test
    fun testLongs() {
        for (i in 10_000..<100_000) {
            DoubleToDecimalChecker.toDec(i * 1e15)
        }
    }

    @Test
    fun testIntegers() {
        for (i in 0..1_000_000) {
            DoubleToDecimalChecker.toDec(i.toDouble())
        }
    }

    @Test
    fun testConstants() {
        assertEquals(DoubleToDecimal.P, DoubleToDecimalChecker.P, "P")
        assertEquals(DoubleToDecimalChecker.C_MIN, DoubleToDecimalChecker.C_MIN.toDouble().toLong(), "C_MIN")
        assertEquals(DoubleToDecimalChecker.C_MAX, DoubleToDecimalChecker.C_MAX.toDouble().toLong(), "C_MAX")
        assertEquals(Double.MIN_VALUE, DoubleToDecimalChecker.MIN_VALUE, "MIN_VALUE")
        assertEquals(java.lang.Double.MIN_NORMAL, DoubleToDecimalChecker.MIN_NORMAL, "MIN_NORMAL")
        assertEquals(Double.MAX_VALUE, DoubleToDecimalChecker.MAX_VALUE, "MAX_VALUE")

        assertEquals(DoubleToDecimal.Q_MIN, DoubleToDecimalChecker.Q_MIN, "Q_MIN")
        assertEquals(DoubleToDecimal.Q_MAX, DoubleToDecimalChecker.Q_MAX, "Q_MAX")

        assertEquals(DoubleToDecimal.K_MIN, DoubleToDecimalChecker.K_MIN, "K_MIN")
        assertEquals(DoubleToDecimal.K_MAX, DoubleToDecimalChecker.K_MAX, "K_MAX")
        assertEquals(DoubleToDecimal.H, DoubleToDecimalChecker.H, "H")

        assertEquals(DoubleToDecimal.E_MIN, DoubleToDecimalChecker.E_MIN, "E_MIN")
        assertEquals(DoubleToDecimal.E_MAX, DoubleToDecimalChecker.E_MAX, "E_MAX")
        assertEquals(DoubleToDecimal.C_TINY, DoubleToDecimalChecker.C_TINY, "C_TINY")
    }

    @Test
    fun testHardValues() {
        for (v in HardHolder.hard0()) {
            DoubleToDecimalChecker.toDec(v)
        }

        for (v in HardHolder.hard1()) {
            DoubleToDecimalChecker.toDec(v)
        }

        for (v in HardHolder.hard2()) {
            DoubleToDecimalChecker.toDec(v)
        }

        for (v in HardHolder.hard3()) {
            DoubleToDecimalChecker.toDec(v)
        }
    }

    @Test
    fun testRandomNumbers() {
        DoubleToDecimalChecker.randomNumberTests(250_000, Random.Default)
    }

}