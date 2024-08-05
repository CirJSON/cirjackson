package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class BinaryNameMatcherTest : TestBase() {

    /*
     *******************************************************************************************************************
     * Constructors
     *******************************************************************************************************************
     */

    private fun construct(names: List<String>): BinaryNameMatcher {
        val matcher = BinaryNameMatcher.construct(names)
        assertNotNull(matcher.toString())
        return matcher
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

    /*
     *******************************************************************************************************************
     * Base tests
     *******************************************************************************************************************
     */

    @Test
    fun testSmallMatching() {
        testMatching("single")
        testMatching("1", "2a")
        testMatching("first", "secondLong", "3rd")
    }

    @Test
    fun testMediumMatching() {
        testMatching("a", "bcd", "Fittipaldi", "goober")
        testMatching("foo", "bar", "foobar", "fubar", "bizzbah", "grimagnoefwemp")
        testMatching("a", "b", "c", "d", "E", "f", "G", "h")
        testMatching("a", "b", "d", "E", "f", "G")
        testMatching("areaNames", "audienceSubCategoryNames", "blockNames", "seatCategoryNames", "subTopicNames",
                "subjectNames", "topicNames", "topicSubTopics", "venueNames", "events", "performances")
    }

    @Test
    fun testLargeMatching() {
        testMatching(generate("base", 39))
        testMatching(generate("Of ", 139))
        testMatching(generate("ACE-", 499))
        testMatching(generate2("-longer", 999))
        testMatching(generate("sLarTiBartFast-", 3000))
    }

    private fun testMatching(vararg names: String) {
        testMatching(names.toList())
    }

    private fun testMatching(names: List<String>) {
        val matcher = construct(names)

        for ((i, name) in names.withIndex()) {
            expectMatch(matcher, names, i)
            expectNonMatch(matcher, "${name}FOOBAR")
        }
    }

    private fun expectMatch(matcher: BinaryNameMatcher, names: List<String>, index: Int) {
        val name = names[index]
        expectMatch(matcher, names, index, name)
    }

    private fun expectMatch(matcher: BinaryNameMatcher, names: List<String>, index: Int, name: String) {
        var match = match(matcher, name)

        if (match != index) {
            fail("Should have found '$name' (index #$index of total of ${names.size}), didn't: got $match")
        }

        match = matcher.matchName(name)

        if (match != index) {
            fail("Should have found '$name' (index #$index of total of ${names.size}) via String lookup: instead got $match")
        }
    }

    private fun expectNonMatch(matcher: BinaryNameMatcher, name: String) {
        val match = match(matcher, name)

        if (match != -1) {
            fail("Should NOT have any-matched '$name'; did match as: $match")
        }
    }

    private fun match(matcher: BinaryNameMatcher, name: String): Int {
        val quads = BinaryNameMatcher.quads(name)

        return when (quads.size) {
            1 -> matcher.matchByQuad(quads[0])
            2 -> matcher.matchByQuad(quads[0], quads[1])
            3 -> matcher.matchByQuad(quads[0], quads[1], quads[2])
            else -> matcher.matchByQuad(quads, quads.size)
        }
    }

    /*
     *******************************************************************************************************************
     * Hashing tests
     *******************************************************************************************************************
     */

    @Test
    fun testSuffix1() {
        testSpillEfficiency(generate("", 99), 77, 16, 6, 0)
    }

    @Test
    fun testSuffix2() {
        testSpillEfficiency(generate("base", 39), 33, 6, 0, 0)
    }

    @Test
    fun testSuffix3() {
        testSpillEfficiency(generate("Of ", 139), 122, 16, 1, 0)
    }

    @Test
    fun testSuffix4() {
        testSpillEfficiency(generate("ACE-", 499), 422, 66, 11, 0)
    }

    @Test
    fun testSuffix5() {
        testSpillEfficiency(generate("sLarTiBartFast#", 3000), 1128, 886, 786, 200)
    }

    @Test
    fun testPrefix1() {
        testSpillEfficiency(generate2("", 99), 77, 16, 6, 0)
    }

    @Test
    fun testPrefix2() {
        testSpillEfficiency(generate2("base", 39), 29, 8, 2, 0)
    }

    @Test
    fun testPrefix3() {
        testSpillEfficiency(generate2("Of ", 139), 116, 16, 7, 0)
    }

    @Test
    fun testPrefix4() {
        testSpillEfficiency(generate2("ACE-", 499), 384, 92, 23, 0)
    }

    @Test
    fun testMisc11() {
        testSpillEfficiency(listOf("player", "uri", "title", "width", "height", "format", "duration", "size", "bitrate",
                "copyright", "persons"), 11, 0, 0, 0)
    }

    @Test
    fun testMisc5() {
        testSpillEfficiency(listOf("uri", "title", "width", "height", "size"), 5, 0, 0, 0)
    }

    @Test
    fun testMisc2() {
        testSpillEfficiency(listOf("content", "images"), 2, 0, 0, 0)
    }

    private fun testSpillEfficiency(names: List<String>, primary: Int, secondary: Int, tertiary: Int,
            expectedSpills: Int) {
        val matcher = construct(names)
        assertEquals(names.size, matcher.totalCount())
        assertEquals(primary, matcher.primaryQuadCount(), "Primary count not matching")
        assertEquals(secondary, matcher.secondaryQuadCount(), "Secondary count not matching")
        assertEquals(tertiary, matcher.tertiaryQuadCount(), "Tertiary count not matching")
        assertEquals(expectedSpills, matcher.spilloverQuadCount(), "Spill count not matching")
    }

}