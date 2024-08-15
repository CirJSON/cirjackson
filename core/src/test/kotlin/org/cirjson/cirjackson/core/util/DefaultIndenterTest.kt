package org.cirjson.cirjackson.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DefaultIndenterTest {

    @Test
    fun testWithLinefeed() {
        val indenter = DefaultIndenter()
        val indenter2 = indenter.withLinefeed("-XG'#x")
        val indenter3 = indenter2.withLinefeed("-XG'#x")

        assertEquals("-XG'#x", indenter3.eol)
        assertNotSame(indenter3, indenter)
        assertSame(indenter3, indenter2)
    }

    @Test
    fun testWithIndent() {
        val indenter = DefaultIndenter()
        val indenter2 = indenter.withIndent("9Qh/6,~n")
        val indenter3 = indenter2.withIndent("9Qh/6,~n")

        assertEquals("9Qh/6,~n", indenter3.indent)
        assertNotSame(indenter3, indenter)
        assertSame(indenter3, indenter2)
    }

}