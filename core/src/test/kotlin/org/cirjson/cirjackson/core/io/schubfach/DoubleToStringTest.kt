package org.cirjson.cirjackson.core.io.schubfach

import kotlin.test.Test
import kotlin.test.assertEquals

class DoubleToStringTest {

    @Test
    fun testSimpleCases() {
        assertDoubleToStringEquals("0.0", 0.0)
        assertDoubleToStringEquals("-0.0", Double.fromBits(HardHolder.DOUBLE_NEGATIVE_ZERO_BITS))
        assertDoubleToStringEquals("1.0", 1.0)
        assertDoubleToStringEquals("-1.0", -1.0)
        assertDoubleToStringEquals("NaN", Double.NaN)
        assertDoubleToStringEquals("Infinity", Double.POSITIVE_INFINITY)
        assertDoubleToStringEquals("-Infinity", Double.NEGATIVE_INFINITY)
    }

    @Test
    fun testSwitchToSubnormal() {
        assertDoubleToStringEquals("2.2250738585072014E-308", Double.fromBits(0x0010000000000000L))
    }

    /**
     * Floating point values in the range 1.0E-3 <= x < 1.0E7 have to be printed without exponent. This test checks the
     * values at those boundaries.
     */
    @Test
    fun testBoundaryConditions() {
        assertDoubleToStringEquals("1.0E7", 1.0E7)
        // x < 1.0E7
        assertDoubleToStringEquals("9999999.999999998", 9999999.999999998)
        // x = 1.0E-3
        assertDoubleToStringEquals("0.001", 0.001)
        // x < 1.0E-3
        assertDoubleToStringEquals("9.999999999999998E-4", 0.0009999999999999998)
    }

    @Test
    fun testMinAndMax() {
        assertDoubleToStringEquals("1.7976931348623157E308", Double.fromBits(0x7FEFFFFFFFFFFFFFL))
        assertDoubleToStringEquals("4.9E-324", Double.fromBits(1L))
    }

    @Test
    fun testRoundingModeEven() {
        assertDoubleToStringEquals("-2.109808898695963E16", -2.109808898695963E16)
    }

    @Test
    fun testRegressionTest() {
        assertDoubleToStringEquals("4.940656E-318", 4.940656E-318)
        assertDoubleToStringEquals("1.18575755E-316", 1.18575755E-316)
        assertDoubleToStringEquals("2.989102097996E-312", 2.989102097996E-312)
        assertDoubleToStringEquals("9.0608011534336E15", 9.0608011534336E15)
        assertDoubleToStringEquals("4.708356024711512E18", 4.708356024711512E18)
        assertDoubleToStringEquals("9.409340012568248E18", 9.409340012568248E18)
        // This number naively requires 65 bit for the intermediate results if we reduce the lookup
        // table by half. This checks that we don't lose any information in that case.
        assertDoubleToStringEquals("1.8531501765868567E21", 1.8531501765868567E21)
        assertDoubleToStringEquals("-3.347727380279489E33", -3.347727380279489E33)
        // Discovered by Andriy Plokhotnyuk, see #29.
        assertDoubleToStringEquals("1.9430376160308388E16", 1.9430376160308388E16)
        assertDoubleToStringEquals("-6.9741824662760956E19", -6.9741824662760956E19)
        assertDoubleToStringEquals("4.3816050601147837E18", 4.3816050601147837E18)
    }

    private fun assertDoubleToStringEquals(expected: String, v: Double) {
        val actual = DoubleToDecimal.toString(v)
        assertEquals(expected, actual)
    }

}