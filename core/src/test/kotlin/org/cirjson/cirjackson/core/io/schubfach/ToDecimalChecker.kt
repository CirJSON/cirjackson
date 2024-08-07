package org.cirjson.cirjackson.core.io.schubfach

import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.floor
import kotlin.math.log10
import kotlin.test.assertTrue

/**
 * A checker for the specification. It just relies on straightforward use of (expensive) BigDecimal arithmetic, not
 * optimized at all.
 */
abstract class ToDecimalChecker protected constructor(private val s: String) {

    private var c = 0L

    private var q = 0

    private var len10 = 0

    protected abstract fun toBigDecimal(): BigDecimal

    protected abstract fun recovers(bigDecimal: BigDecimal): Boolean

    protected abstract fun recovers(string: String): Boolean

    protected abstract fun hexBits(): String

    protected abstract fun minExp(): Int

    protected abstract fun maxExp(): Int

    protected abstract fun maxLen10(): Int

    protected abstract fun negate()

    protected abstract val isZero: Boolean

    protected abstract val isInfinity: Boolean

    protected abstract val isNegative: Boolean

    protected abstract val isNaN: Boolean

    protected fun validate() {
        val message =
                "toString applied to the bits ${hexBits()} returns \"$s\", which is not correct according to the specification."
        assertTrue(isOk, message)
    }

    /**
     * Returns whether [t] syntactically meets the expected output of toString. It is restricted to finite positive
     * outputs. It is an unusually long method but rather straightforward, too. Many conditionals could be merged, but
     * KISS here.
     */
    private fun parse(t: String): Boolean {
        val reader = StringReader(t)
        var ch = reader.read()
        var i = 0

        while (ch == '0'.code) {
            ++i
            ch = reader.read()
        }

        var p = i

        while (ch in '0'.code..'9'.code) {
            c = 10L * c + (ch - '0'.code)

            if (c < 0L) {
                return false
            }

            ++len10
            ++p
            ch = reader.read()
        }

        var fz = p

        if (ch == '.'.code) {
            ++fz
            ch = reader.read()
        }

        var f = fz

        while (ch == '0'.code) {
            c *= 10L

            if (c < 0L) {
                return false
            }

            ++len10
            ++f
            ch = reader.read()
        }

        if (c == 0L) {
            len10 = 0
        }

        var x = f

        while (ch in '0'.code..'9'.code) {
            c = 10L * c + (ch - '0'.code)

            if (c < 0L) {
                return false
            }

            ++len10
            ++x
            ch = reader.read()
        }

        var g = x

        if (ch == 'E'.code) {
            ++g
            ch = reader.read()
        }

        var ez = g

        if (ch == '-'.code) {
            ++ez
            ch = reader.read()
        }

        var e = ez

        while (ch == '0'.code) {
            ++e
            ch = reader.read()
        }

        var z = e

        while (ch in '0'.code..'9'.code) {
            q = 10 * q + (ch - '0'.code)

            if (q < 0) {
                return false
            }

            ++z
            ch = reader.read()
        }

        if (z != t.length) {
            return false
        }

        if (p == 0) {
            return false
        }

        if (fz == p) {
            return false
        }

        if (x == fz) {
            return false
        }

        if (f == x && f - fz > 1) {
            return false
        }

        if (x == z) {
            if (i > 1) {
                return false
            }

            if (i == 1 && f - fz > 2) {
                return false
            }

            if (p > 7) {
                return false
            }

            q = fz - x

            return true
        }

        if (i != 0 || p != 1) {
            return false
        }

        if (x == g) {
            return false
        }

        if (ez == z) {
            return false
        }

        if (ez != e) {
            return false
        }

        if (g != ez) {
            q = -q
        }

        if (q in -3..<7) {
            return false
        }

        q += fz - x

        return true
    }

    private val isOk
        get() = checkIsOk()

    private fun checkIsOk(): Boolean {
        if (isNaN) {
            return s == "NaN"
        }

        var t = s

        if (isNegative) {
            if (s.isEmpty() || s[0] != '-') {
                return false
            }

            negate()
            t = s.substring(1)
        }

        if (isInfinity) {
            return t == "Infinity"
        }

        if (isZero) {
            return t == "0.0"
        }

        if (!parse(t)) {
            return false
        }

        if (len10 < 2) {
            c *= 10
            q -= 1
            len10 += 1
        }

        if (len10 !in 2..maxLen10()) {
            return false
        }

        if (q + len10 !in minExp()..maxExp()) {
            return false
        }

        try {
            if (!recovers(t)) {
                return false
            }
        } catch (e: NumberFormatException) {
            return false
        }

        while (len10 > 2 && c.mod(10L) == 0L) {
            c /= 10L
            q += 1
            len10 -= 1
        }

        if (len10 > 2) {
            if (recovers(BigDecimal.valueOf(c / 10L, -q - 1))) {
                return false
            }

            if (recovers(BigDecimal.valueOf(c / 10L + 1L, -q - 1))) {
                return false
            }
        }

        val decimalProcessor = if (c == 10L) {
            BigDecimal.valueOf(99L, 1 - q)
        } else {
            BigDecimal.valueOf(c - 1, -q)
        }

        if (recovers(decimalProcessor)) {
            val bv = toBigDecimal()
            val deltaV = bv.subtract(BigDecimal.valueOf(c, -q))

            if (deltaV.signum() >= 0) {
                return true
            }

            val delta = decimalProcessor.subtract(bv)

            if (deltaV.signum() >= 0) {
                return false
            }

            val cmp = deltaV.compareTo(delta)
            return cmp > 0 || cmp == 0 && c and 0x1L == 0L
        }

        val decimalSuccessor = BigDecimal.valueOf(c + 1, -q)

        if (recovers(decimalSuccessor)) {
            val bv = toBigDecimal()
            val deltaV = bv.subtract(BigDecimal.valueOf(c, -q))

            if (deltaV.signum() <= 0) {
                return true
            }

            val delta = decimalSuccessor.subtract(bv)

            if (deltaV.signum() <= 0) {
                return false
            }

            val cmp = deltaV.compareTo(delta)
            return cmp < 0 || cmp == 0 && c and 0x1L == 0L
        }

        return true
    }

    companion object {

        fun e(v: Double): Int {
            var e = floor(log10(v)).toInt() + 1
            val vp = BigDecimal(v)
            var low = BigDecimal(BigInteger.ONE, 1 - e)

            while (low > vp) {
                low = BigDecimal(BigInteger.ONE, 1 - --e)
            }

            var high = BigDecimal(BigInteger.ONE, -e)

            while (vp >= high) {
                high = BigDecimal(BigInteger.ONE, -++e)
            }

            return e
        }

        fun cTiny(qMin: Int, kMin: Int): Long {
            val qr = BigInteger.ONE.shiftLeft(-qMin).divideAndRemainder(BigInteger.TEN.pow(-kMin - 1))
            val cTiny = if (qr[1].signum() <= 0) qr[0] else qr[0].add(BigInteger.ONE)
            assertTrue(cTiny.bitLength() < Long.SIZE_BITS, "C_TINY")
            return cTiny.toLong()
        }

    }

}