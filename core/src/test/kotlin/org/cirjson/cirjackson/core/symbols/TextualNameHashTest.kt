package org.cirjson.cirjackson.core.symbols

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TextualNameHashTest {

    @Test
    fun testMatch() {
        val matcher = SimpleNameMatcher.construct(null, listOf())
        assertEquals(PropertyNameMatcher.MATCH_UNKNOWN_NAME, matcher.matchByQuad(0))
        assertEquals(PropertyNameMatcher.MATCH_UNKNOWN_NAME, matcher.matchByQuad(0, 0))
        assertEquals(PropertyNameMatcher.MATCH_UNKNOWN_NAME, matcher.matchByQuad(0, 0, 0))
        assertEquals(PropertyNameMatcher.MATCH_UNKNOWN_NAME, matcher.matchByQuad(intArrayOf(0), 0))
    }

    @Test
    fun testSuffix1() {
        testSpillEfficiency(generate("", 99), 0, 0)
    }

    @Test
    fun testSuffix2() {
        testSpillEfficiency(generate("base", 39), 7, 1)
    }

    @Test
    fun testSuffix3() {
        testSpillEfficiency(generate("Of ", 139), 14, 6)
    }

    @Test
    fun testSuffix4() {
        testSpillEfficiency(generate("ACE-", 499), 71, 34)
    }

    @Test
    fun testSuffix5() {
        testSpillEfficiency(generate("sLarTiBartFast#", 3000), 441, 278)
    }

    @Test
    fun testPrefix1() {
        testSpillEfficiency(generate2("", 99), 0, 0)
    }

    @Test
    fun testPrefix2() {
        testSpillEfficiency(generate2("base", 39), 5, 0)
    }

    @Test
    fun testPrefix3() {
        testSpillEfficiency(generate2("Of ", 139), 15, 0)
    }

    @Test
    fun testPrefix4() {
        testSpillEfficiency(generate2("ACE-", 499), 54, 3)
    }

    @Test
    fun testPrefix5() {
        testSpillEfficiency(generate2("sLarTiBartFast#", 3000), 844, 232)
    }

    @Test
    fun testMisc11() {
        testSpillEfficiency(listOf("player", "uri", "title", "width", "height", "format", "duration", "size", "bitrate",
                "copyright", "persons"), 1, 0)
    }

    @Test
    fun testMisc5() {
        testSpillEfficiency(listOf("uri", "title", "width", "height", "size"), 2, 0)
    }

    @Test
    fun testMisc2() {
        testSpillEfficiency(listOf("content", "images"), 0, 0)
    }

    @Suppress("NAME_SHADOWING")
    private fun generate(base: String, count: Int): List<String> {
        var count = count
        val result = ArrayList<String>()

        while (--count >= 0) {
            val name = "$base$count"
            result.add(name.intern())
        }

        return result
    }

    @Suppress("NAME_SHADOWING", "SameParameterValue")
    private fun generate2(base: String, count: Int): List<String> {
        var count = count
        val result = ArrayList<String>()

        while (--count >= 0) {
            val name = "$count$base"
            result.add(name.intern())
        }

        return result
    }

    private fun testSpillEfficiency(names: List<String>, expectedSecondary: Int, expectedSpills: Int) {
        val matcher = SimpleNameMatcher.construct(null, names)
        val secondary = matcher.secondaryCount()
        val spills = matcher.spillCount()

        if (expectedSecondary != secondary || expectedSpills != spills) {
            fail("Expected $expectedSecondary secondary, $expectedSpills spills; got $secondary / $spills")
        }
    }

}