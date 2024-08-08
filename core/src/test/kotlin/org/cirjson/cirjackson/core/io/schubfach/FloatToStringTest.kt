package org.cirjson.cirjackson.core.io.schubfach

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("FloatingPointLiteralPrecision")
class FloatToStringTest {

    @Test
    fun testSimpleCases() {
        assertFloatToStringEquals("0.0", 0.0f)
        assertFloatToStringEquals("-0.0", Float.fromBits(0x80000000.toInt()))
        assertFloatToStringEquals("1.0", 1.0f)
        assertFloatToStringEquals("-1.0", -1f)
        assertFloatToStringEquals("NaN", Float.NaN)
        assertFloatToStringEquals("Infinity", Float.POSITIVE_INFINITY)
        assertFloatToStringEquals("-Infinity", Float.NEGATIVE_INFINITY)
    }

    @Test
    fun testSwitchToSubnormal() {
        assertFloatToStringEquals("1.1754944E-38", Float.fromBits(0x00800000))
    }

    /**
     * Floating point values in the range 1.0E-3 <= x < 1.0E7 have to be printed
     * without exponent. This test checks the values at those boundaries.
     */
    @Test
    fun boundaryConditions() {
        // x = 1.0E7
        assertFloatToStringEquals("1.0E7", 1.0E7f)
        // x < 1.0E7
        assertFloatToStringEquals("9999999.0", 9999999.0f)
        // x = 1.0E-3
        assertFloatToStringEquals("0.001", 0.001f)
        // x < 1.0E-3
        assertFloatToStringEquals("9.999999E-4", 0.0009999999f)
    }

    @Test
    fun minAndMax() {
        assertFloatToStringEquals("3.4028235E38", Float.fromBits(0x7f7fffff))
        assertFloatToStringEquals("1.4E-45", Float.fromBits(0x00000001))
    }

    @Test
    fun roundingModeEven() {
        assertFloatToStringEquals("3.355445E7", 3.3554448E7f)
        assertFloatToStringEquals("9.0E9", 8.999999E9f)
        assertFloatToStringEquals("3.436672E10", 3.4366717E10f)
    }

    @Test
    fun roundingEvenIfTied() {
        assertFloatToStringEquals("0.33007812", 0.33007812f)
    }

    @Test
    fun looksLikePow5() {
        // These are all floating point numbers where the mantissa is a power of 5,
        // and the exponent is in the range such that q = 10.
        assertFloatToStringEquals("6.7108864E17", Float.fromBits(0x5D1502F9))
        assertFloatToStringEquals("1.3421773E18", Float.fromBits(0x5D9502F9))
        assertFloatToStringEquals("2.6843546E18", Float.fromBits(0x5E1502F9))
    }

    @Test
    fun regressionTest() {
        assertFloatToStringEquals("4.7223665E21", 4.7223665E21f)
        assertFloatToStringEquals("8388608.0", 8388608.0f)
        assertFloatToStringEquals("1.6777216E7", 1.6777216E7f)
        assertFloatToStringEquals("3.3554436E7", 3.3554436E7f)
        assertFloatToStringEquals("6.7131496E7", 6.7131496E7f)
        assertFloatToStringEquals("1.9310392E-38", 1.9310392E-38f)
        assertFloatToStringEquals("-2.47E-43", -2.47E-43f)
        assertFloatToStringEquals("1.993244E-38", 1.993244E-38f)
        assertFloatToStringEquals("4103.9004", 4103.9003f)
        assertFloatToStringEquals("5.3399997E9", 5.3399997E9f)
        assertFloatToStringEquals("6.0898E-39", 6.0898E-39f)
        assertFloatToStringEquals("0.0010310042", 0.0010310042f)
        assertFloatToStringEquals("2.882326E17", 2.8823261E17f)
        assertFloatToStringEquals("7.038531E-26", 7.038531E-26f)
        assertFloatToStringEquals("9.223404E17", 9.2234038E17f)
        assertFloatToStringEquals("6.710887E7", 6.7108872E7f)
        assertFloatToStringEquals("9.8E-45", 1.0E-44f)
        assertFloatToStringEquals("2.816025E14", 2.816025E14f)
        assertFloatToStringEquals("9.223372E18", 9.223372E18f)
        assertFloatToStringEquals("1.5846086E29", 1.5846085E29f)
        assertFloatToStringEquals("1.1811161E19", 1.1811161E19f)
        assertFloatToStringEquals("5.368709E18", 5.368709E18f)
        assertFloatToStringEquals("4.6143166E18", 4.6143165E18f)
        assertFloatToStringEquals("0.007812537", 0.007812537f)
        assertFloatToStringEquals("1.4E-45", 1.4E-45f)
        assertFloatToStringEquals("1.18697725E20", 1.18697724E20f)
        assertFloatToStringEquals("1.00014165E-36", 1.00014165E-36f)
        assertFloatToStringEquals("200.0", 200f)
        assertFloatToStringEquals("3.3554432E7", 3.3554432E7f)
    }

    private fun assertFloatToStringEquals(expected: String, v: Float) {
        val actual = FloatToDecimal.toString(v)
        assertEquals(expected, actual)
    }

}