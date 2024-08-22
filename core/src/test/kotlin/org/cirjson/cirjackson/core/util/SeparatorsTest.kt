package org.cirjson.cirjackson.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SeparatorsTest {

    @Test
    fun testWithArrayValueSeparatorWithDigit() {
        val separators = Separators('5', '5', '5')

        var separators2 = separators.withArrayElementSeparator('5')

        assertEquals('5', separators2.objectEntrySeparator)
        assertEquals('5', separators2.objectNameValueSeparator)
        assertEquals('5', separators2.arrayElementSeparator)
        assertSame(separators, separators2)

        separators2 = separators.withArrayElementSeparator('6')

        assertEquals('5', separators2.objectEntrySeparator)
        assertEquals('5', separators2.objectNameValueSeparator)
        assertEquals('6', separators2.arrayElementSeparator)
        assertNotSame(separators, separators2)
    }

    @Test
    fun testWithObjectEntrySeparator() {
        val separators = Separators('5', '5', '5')

        var separators2 = separators.withObjectEntrySeparator('5')

        assertEquals('5', separators2.objectEntrySeparator)
        assertEquals('5', separators2.objectNameValueSeparator)
        assertEquals('5', separators2.arrayElementSeparator)
        assertSame(separators, separators2)

        separators2 = separators.withObjectEntrySeparator('!')

        assertEquals('!', separators2.objectEntrySeparator)
        assertEquals('5', separators2.objectNameValueSeparator)
        assertEquals('5', separators2.arrayElementSeparator)
        assertNotSame(separators, separators2)
    }

    @Test
    fun testWithObjectFieldValueSeparatorWithDigit() {
        val separators = Separators('5', '5', '5')

        var separators2 = separators.withObjectNameValueSeparator('5')

        assertEquals('5', separators2.objectEntrySeparator)
        assertEquals('5', separators2.objectNameValueSeparator)
        assertEquals('5', separators2.arrayElementSeparator)
        assertSame(separators, separators2)

        separators2 = separators.withObjectNameValueSeparator('6')

        assertEquals('5', separators2.objectEntrySeparator)
        assertEquals('6', separators2.objectNameValueSeparator)
        assertEquals('5', separators2.arrayElementSeparator)
        assertNotSame(separators, separators2)
    }

}