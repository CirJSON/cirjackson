package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.*

class DuplicateDetectorTest : TestBase() {

    @Test
    fun testNullAsFirst() {
        val duplicateDetector = DuplicateDetector.rootDetector(null as CirJsonGenerator?)
        assertNull(duplicateDetector.findLocation())
        assertFalse(duplicateDetector.isDuplicate(null))
        assertFalse(duplicateDetector.isDuplicate(null))
    }

    @Test
    fun testChild() {
        val source = createParser(MODE_READER, "123")
        val duplicateDetector = DuplicateDetector.rootDetector(source)
        setupDetector(duplicateDetector)
        assertSame(source, duplicateDetector.source)
        assertEquals(source.currentLocation(), duplicateDetector.findLocation())

        val child = duplicateDetector.child()
        assertTrue(duplicateDetector.isDuplicate("foo"))
        assertTrue(duplicateDetector.isDuplicate("bar"))
        assertTrue(duplicateDetector.isDuplicate("foobar"))
        assertSame(source, child.source)
        assertEquals(source.currentLocation(), child.findLocation())
    }

    @Test
    fun testReset() {
        val duplicateDetector = DuplicateDetector.rootDetector(null as CirJsonGenerator?)
        assertFalse(duplicateDetector.isDuplicate("foo"))
        assertTrue(duplicateDetector.isDuplicate("foo"))
        assertFalse(duplicateDetector.isDuplicate("bar"))
        assertTrue(duplicateDetector.isDuplicate("bar"))
        assertFalse(duplicateDetector.isDuplicate("foobar"))
        assertTrue(duplicateDetector.isDuplicate("foobar"))
    }

    private fun setupDetector(duplicateDetector: DuplicateDetector) {
        assertFalse(duplicateDetector.isDuplicate("foo"))
        assertFalse(duplicateDetector.isDuplicate("bar"))
        assertFalse(duplicateDetector.isDuplicate("foobar"))
    }

}