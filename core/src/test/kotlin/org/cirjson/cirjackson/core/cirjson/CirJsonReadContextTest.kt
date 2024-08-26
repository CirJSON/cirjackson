package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.ContentReference
import kotlin.test.*

class CirJsonReadContextTest : TestBase() {

    @Test
    fun testSetCurrentNameTwice() {
        val name = "duplicatedField"
        val duplicateDetector = DuplicateDetector.rootDetector(null as CirJsonGenerator?)
        val cirJsonReadContext = CirJsonReadContext(null, 0, duplicateDetector, 2441, 2441, 2441)
        cirJsonReadContext.currentName = name

        try {
            cirJsonReadContext.currentName = name
            fail("Should have failed")
        } catch (e: StreamReadException) {
            verifyException(e, "Duplicate Object property \"$name\"")
            verifyException(e, name)
        }
    }

    @Test
    fun testSetCurrentName() {
        val cirJsonReadContext = CirJsonReadContext.createRootContext(0, 0, null)
        cirJsonReadContext.currentName = "abc"
        assertEquals("abc", cirJsonReadContext.currentName)
        cirJsonReadContext.currentName = null
        assertNull(cirJsonReadContext.currentName)
    }

    @Test
    fun testReset() {
        val duplicateDetector = DuplicateDetector.rootDetector(null as CirJsonGenerator?)
        val cirJsonReadContext = CirJsonReadContext.createRootContext(duplicateDetector)
        val source = ContentReference.unknown()

        assertTrue(cirJsonReadContext.isInRoot)
        assertEquals(1, cirJsonReadContext.startLocation(source).lineNumber)
        assertEquals(0, cirJsonReadContext.startLocation(source).columnNumber)

        cirJsonReadContext.reset(200, 500, 200)
        assertEquals("?", cirJsonReadContext.typeDescription)
        assertEquals(500, cirJsonReadContext.startLocation(source).lineNumber)
        assertEquals(200, cirJsonReadContext.startLocation(source).columnNumber)
    }

}