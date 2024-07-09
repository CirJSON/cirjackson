package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.util.Named
import java.util.*
import kotlin.test.Test
import kotlin.test.fail

class PropertyNameMatcherTest : TestBase() {

    @Test
    fun testSmallMatching() {
        testMatching("single")
        testMatching("1", "2a")
        testMatching("first", "Second", "third")
        testMatching(null, "b", null)
    }

    @Test
    fun testMediumMatching() {
        testMatching("a", "bcd", "Fittipaldi", "goober")
        testMatching("a", "bcd", null, "goober")
        // important: non-null size still small, but full size big(ger)
        testMatching("a", null, null, "goober", "xyz")

        // then bit larger
        testMatching("foo", "bar", "foobar", "fubar", "bizzbah", "grimagnoefwemp")

        testMatching("a", "b", "c", "d", "E", "f", "G", "h")
        testMatching("a", "b", null, "d", "E", "f", "G", null)
    }

    @Test
    fun testLargeMatching() {
        testMatching(generate("base", 39))
        testMatching(generate("Of ", 139))
        testMatching(generate("ACE-", 499))
    }

    private fun generate(base: String, count: Int): List<String> {
        val result = mutableListOf<String>()

        for (i in count downTo 0) {
            val name = "$base$i"
            result.add(name.intern())
        }

        return result
    }

    private fun testMatching(vararg names: String?) {
        testMatching(names.toList())
    }

    private fun testMatching(names: List<String?>) {
        testCaseSensitive(names)
        testCaseInsensitive(names)
    }

    private fun testCaseSensitive(names: List<String?>) {
        val matcher = SimpleNameMatcher.construct(null, names)

        for ((index, name) in names.withIndex()) {
            name ?: continue
            expectAnyMatch(matcher, names, index)
            expectAnyMatch(matcher, names, index, name)
            expectNonMatch(matcher, "${name}FOOBAR")
        }
    }

    private fun expectAnyMatch(matcher: PropertyNameMatcher, names: List<String?>, index: Int) {
        val name = names[index] ?: return
        expectAnyMatch(matcher, names, index, name)
    }

    private fun expectAnyMatch(matcher: PropertyNameMatcher, names: List<String?>, index: Int, name: String?) {
        name ?: return
        val match = matcher.matchName(name)

        if (match != index) {
            fail("Should have any-matched #$index (of ${names.size}) for '$name', did not, got: $match")
        }
    }

    private fun expectNonMatch(matcher: PropertyNameMatcher, name: String?) {
        name ?: return

        val match = matcher.matchName(name)

        if (match != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            fail("Should NOT have any-matched '$name'; did match with index #$match")
        }

        expectInternedNonMatch(matcher, name)
    }

    private fun expectInternedNonMatch(matcher: PropertyNameMatcher, name: String?) {
        name ?: return

        val match = matcher.matchName(name)

        if (match != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            fail("Should NOT have any-matched '$name'; did match with index #$match")
        }
    }

    private fun testCaseInsensitive(names: List<String?>) {
        val locale = Locale("en", "US")
        val matcher = SimpleNameMatcher.constructCaseInsensitive(locale, named(names), true)

        for ((index, name) in names.withIndex()) {
            name ?: continue
            expectAnyMatch(matcher, names, index)
            expectAnyMatch(matcher, names, index, name)
            expectAnyMatch(matcher, names, index, name.lowercase(locale))
            expectAnyMatch(matcher, names, index, name.uppercase(locale))
            expectNonMatch(matcher, "${name}FOOBAR")
        }
    }

    private fun named(names: List<String?>) = names.map { Named.fromString(it) }

}