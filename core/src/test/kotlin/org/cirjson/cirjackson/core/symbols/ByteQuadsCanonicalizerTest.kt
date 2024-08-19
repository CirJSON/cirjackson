package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.*

class ByteQuadsCanonicalizerTest : TestBase() {

    @Test
    fun testMultiplyByFourFifths() {
        var i = 0

        while (i >= 0) {
            val expected = (i * 0.80).toInt()
            val actual = ByteQuadsCanonicalizer.multiplyByFourFifths(i)

            if (expected != actual) {
                fail("Input for 80% of $i differs: expected=$expected, actual=$actual")
            }

            i += 7
        }
    }

    @Test
    fun testBasicPlaceholderLookups() {
        val root = ByteQuadsCanonicalizer.createRoot(137)
        assertEquals(137, root.hashSeed)

        assertEquals(0, root.size)
        assertFalse(root.isCanonicalizing)

        val placeholder = root.makeChildOrPlaceholder(0)

        assertEquals(-1, placeholder.size)
        assertFalse(placeholder.isCanonicalizing)

        val quads = calcQuads("abcd1234efgh5678")

        assertNull(placeholder.findName(quads[0]))
        assertNull(placeholder.findName(quads[0], quads[1]))
        assertNull(placeholder.findName(quads[0], quads[1], quads[2]))
        assertNull(placeholder.findName(quads, quads.size))
    }

    @Test
    fun testBasicPlaceholderAddFails() {
        val root = ByteQuadsCanonicalizer.createRoot(137)
        val placeholder = root.makeChildOrPlaceholder(0)

        val quads = calcQuads("abcd1234efgh5678")

        try {
            placeholder.addName("abcd", placeholder.calculateHash(quads[0]))
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Cannot add names to Placeholder")
        }

        try {
            placeholder.addName("abcd1234", placeholder.calculateHash(quads[0], quads[1]))
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Cannot add names to Placeholder")
        }

        try {
            placeholder.addName("abcd1234efgh", placeholder.calculateHash(quads[0], quads[1], quads[2]))
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Cannot add names to Placeholder")
        }

        try {
            placeholder.addName("abcd1234efgh5678", placeholder.calculateHash(quads, quads.size))
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Cannot add names to Placeholder")
        }

        assertEquals(-1, placeholder.size)
        assertFalse(placeholder.isCanonicalizing)

        placeholder.release()

        assertEquals(0, root.size)
        assertFalse(root.isCanonicalizing)
    }

}