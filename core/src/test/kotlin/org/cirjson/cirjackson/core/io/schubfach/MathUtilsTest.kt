package org.cirjson.cirjackson.core.io.schubfach

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MathUtilsTest {

    @Test
    fun testG() {
        for (k in MathUtils.K_MIN..MathUtils.K_MAX) {
            testG(k, MathUtils.g1(k), MathUtils.g0(k))
        }
    }

    private fun testG(k: Int, g1: Long, g0: Long) {
        assertTrue(g1 shl 1 < 0 && g1 >= 0 && g0 >= 0, "g")
        val g = BigInteger.valueOf(g1).shiftLeft(63).or(BigInteger.valueOf(g0))
        assertTrue(g.signum() > 0 && g.bitLength() == 126, "g")
        val r = MathUtils.flog2pow10(-k) - 125

        if (k <= 0 && r < 0) {
            assertEquals(g.subtract(BigInteger.ONE), BigInteger.TEN.pow(-k).shiftLeft(-r), "g")
            return
        }

        if (k > 0 && r < 0) {
            val pow5 = BigInteger.TEN.pow(k)
            val mhs = BigInteger.ONE.shiftLeft(-r)
            val rhs = g.multiply(pow5)
            assertTrue(rhs.subtract(pow5) <= mhs && mhs < rhs, "g")
            return
        }

        if (k <= 0) {
            val mhs = BigInteger.TEN.pow(-k)
            assertTrue(g.subtract(BigInteger.ONE).shiftLeft(r) <= mhs && mhs < g.shiftLeft(r), "g")
            return
        }

        fail("g")
    }

    @Test
    fun testFlog10threeQuartersPow2() {
        assertEquals(0, MathUtils.flog10threeQuartersPow2(1), "flog10threeQuartersPow2")

        var e = 0
        var k0 = MathUtils.flog10threeQuartersPow2(e)
        assertTrue(k0 < 0, "flog10threeQuartersPow2")
        var l = THREE.multiply(BigInteger.TEN.pow(-k0 - 1))
        var u = l.multiply(BigInteger.TEN)

        while (true) {
            assertTrue(2 - e in l.bitLength()..<u.bitLength(), "flog10threeQuartersPow2")
            --e

            if (e < Q_MIN) {
                break
            }

            val kp = MathUtils.flog10threeQuartersPow2(e)
            assertTrue(kp <= k0, "flog10threeQuartersPow2")

            if (kp < k0) {
                assertEquals(1, k0 - kp, "flog10threeQuartersPow2")
                k0 = kp
                l = u
                u = u.multiply(BigInteger.TEN)
            }
        }

        e = 2
        k0 = MathUtils.flog10threeQuartersPow2(e)
        assertTrue(k0 >= 0, "flog10threeQuartersPow2")
        val l10 = BigInteger.TEN.pow(k0)
        var u10 = l10.multiply(BigInteger.TEN)
        l = l10.divide(THREE)
        u = u10.divide(THREE)

        while (true) {
            assertTrue(e - 2 in l.bitLength()..<u.bitLength(), "flog10threeQuartersPow2")
            ++e

            if (e > Q_MAX) {
                break
            }

            val kp = MathUtils.flog10threeQuartersPow2(e)
            assertTrue(kp >= k0, "flog10threeQuartersPow2")

            if (kp > k0) {
                assertEquals(1, kp - k0, "flog10threeQuartersPow2")
                k0 = kp
                u10 = u10.multiply(BigInteger.TEN)
                l = u
                u = u10.divide(THREE)
            }
        }
    }

    @Test
    fun testFlog10pow2() {
        assertEquals(0, MathUtils.flog10pow2(0), "flog10threeQuartersPow2")

        var e = -1
        var k = MathUtils.flog10pow2(e)
        assertTrue(k < 0, "flog10pow2")
        var l = BigInteger.TEN.pow(-k - 1)
        var u = l.multiply(BigInteger.TEN)

        while (true) {
            assertTrue(-e in l.bitLength()..<u.bitLength(), "flog10pow2")
            --e

            if (e < Q_MIN) {
                break
            }

            val kp = MathUtils.flog10pow2(e)
            assertTrue(kp <= k, "flog10pow2")

            if (kp < k) {
                assertEquals(1, k - kp, "flog10pow2")
                k = kp
                l = u
                u = u.multiply(BigInteger.TEN)
            }
        }

        e = 1
        k = MathUtils.flog10pow2(e)
        assertTrue(k >= 0, "flog10pow2")
        l = BigInteger.TEN.pow(k)
        u = l.multiply(BigInteger.TEN)

        while (true) {
            assertTrue(e in l.bitLength()..<u.bitLength(), "flog10pow2")
            ++e

            if (e > Q_MAX) {
                break
            }

            val kp = MathUtils.flog10pow2(e)
            assertTrue(kp >= k, "flog10pow2")

            if (kp > k) {
                assertEquals(1, kp - k, "flog10pow2")
                k = kp
                l = u
                u = u.multiply(BigInteger.TEN)
            }
        }
    }

    @Test
    fun testFlog2pow10() {
        assertEquals(0, MathUtils.flog2pow10(0), "flog2pow10")

        var e = -1
        var k0 = MathUtils.flog2pow10(e)
        assertTrue(k0 <= -4, "flog2pow10")
        var l = BigInteger.TEN

        while (true) {
            assertEquals(-k0, l.bitLength(), "flog2pow10")
            --e

            if (e < Q_MIN) {
                break
            }

            k0 = MathUtils.flog2pow10(e)
            l = l.multiply(BigInteger.TEN)
        }

        e = 1
        k0 = MathUtils.flog2pow10(e)
        assertTrue(k0 >= 3, "flog2pow10")
        l = BigInteger.TEN

        while (true) {
            assertEquals(k0 + 1, l.bitLength(), "flog2pow10")
            ++e

            if (e > Q_MAX) {
                break
            }

            k0 = MathUtils.flog2pow10(e)
            l = l.multiply(BigInteger.TEN)
        }
    }

    @Test
    fun testPow10() {
        var pow = 1L

        for (e in 0..H) {
            assertEquals(pow, MathUtils.pow10(e), "pow10")
            pow *= 10L
        }
    }

    @Test
    fun testBinaryConstants() {
        assertEquals(C_MIN, C_MIN.toDouble().toLong(), "C_MIN")
        assertEquals(C_MAX, C_MAX.toDouble().toLong(), "C_MAX")
        assertEquals(Double.MIN_VALUE, StrictMath.scalb(1.0, Q_MIN), "MIN_VALUE")
        assertEquals(MIN_NORMAL, StrictMath.scalb(C_MIN.toDouble(), Q_MIN), "MIN_NORMAL")
        assertEquals(Double.MAX_VALUE, StrictMath.scalb(C_MAX.toDouble(), Q_MAX), "MAX_VALUE")
    }

    @Test
    fun testDecimalConstants() {
        assertEquals(MathUtils.K_MIN, K_MIN, "K_MIN")
        assertEquals(MathUtils.K_MAX, K_MAX, "K_MAX")
        assertEquals(MathUtils.H, H, "H")
    }

    companion object {

        private val THREE = BigInteger.valueOf(3)

        private val P = 3.0.toRawBits().countTrailingZeroBits() + 2

        private val W = Double.SIZE_BITS - P

        private val Q_MIN = (-1 shl W - 1) - P + 3

        private val Q_MAX = (1 shl W - 1) - P

        private val C_MIN = 1L shl P - 1

        private val C_MAX = (1L shl P) - 1

        private val K_MIN = MathUtils.flog10pow2(Q_MIN)

        private val K_MAX = MathUtils.flog10pow2(Q_MAX)

        private val H = MathUtils.flog10pow2(P) + 2

        private val MIN_NORMAL = Double.fromBits(0x0010000000000000L)

    }

}