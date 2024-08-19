package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("ControlFlowWithEmptyBody")
class TestByteBasedSymbols : TestBase() {

    @Test
    fun testSharedSymbols() {
        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            sharedSymbols(mode)
        }
    }

    private fun sharedSymbols(mode: Int) {
        val factory = CirJsonFactory()

        val doc = "{ \"__cirJsonId__\" : \"root\", \"a\" : 1, \"x\" : [ \"array\" ] }"
        val parser = createParser(factory, mode, doc)

        while (parser.nextToken() != CirJsonToken.START_ARRAY) {
        }

        val doc1 = createDoc(true)
        val doc2 = createDoc(false)

        for (x in 0..<2) {
            val parser1 = createParser(factory, mode, doc1)
            val parser2 = createParser(factory, mode, doc2)

            assertToken(CirJsonToken.START_OBJECT, parser1.nextToken())
            assertToken(CirJsonToken.START_OBJECT, parser2.nextToken())

            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser1.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser2.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser1.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser2.nextToken())

            val length = PROPERTY_NAMES.size

            for (i in 0..<length) {
                assertToken(CirJsonToken.PROPERTY_NAME, parser1.nextToken())
                assertToken(CirJsonToken.PROPERTY_NAME, parser2.nextToken())
                assertEquals(PROPERTY_NAMES[i], parser1.currentName)
                assertEquals(PROPERTY_NAMES[length - i - 1], parser2.currentName)
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser1.nextToken())
                assertToken(CirJsonToken.VALUE_NUMBER_INT, parser2.nextToken())
                assertEquals(i, parser1.intValue)
                assertEquals(i, parser2.intValue)
            }

            assertToken(CirJsonToken.END_OBJECT, parser1.nextToken())
            assertToken(CirJsonToken.END_OBJECT, parser2.nextToken())

            parser1.close()
            parser2.close()
        }

        parser.close()
    }

    @Test
    fun testAuxMethodsWithNewSymbolTable() {
        val canonicalizer = ByteQuadsCanonicalizer.createRoot().makeChild(TokenStreamFactory.Feature.collectDefaults())
        assertNull(canonicalizer.findName(A_BYTES))
        assertNull(canonicalizer.findName(A_BYTES, B_BYTES))

        canonicalizer.addName("AAAA", intArrayOf(A_BYTES), 1)
        val name1 = canonicalizer.findName(A_BYTES)
        assertEquals("AAAA", name1)
        canonicalizer.addName("AAAABBBB", intArrayOf(A_BYTES, B_BYTES), 2)
        val name2 = canonicalizer.findName(A_BYTES, B_BYTES)
        assertEquals("AAAABBBB", name2)
        assertNotNull(name2)

        assertNotNull(canonicalizer.toString())
    }

    @Test
    fun testPerName() {
        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            perName(mode)
        }
    }

    private fun perName(mode: Int) {
        val factory = MyFactory()
        val parser = createParser(factory, mode, createPerNameDoc())

        while (parser.nextToken() != null) {
        }

        parser.close()
    }

    @Test
    fun testQuadsCollision() {
        val random = Random(42)
        val root = ByteQuadsCanonicalizer.createRoot()
        var canon = root.makeChild(TokenStreamFactory.Feature.collectDefaults())

        val numberCollisions = 25
        val collisions = IntArray(numberCollisions)

        var maybe = random.nextInt()
        var hash = canon.calculateHash(maybe)
        val target = hash and 2048 - 1 shl 2

        var i = 0

        while (i < numberCollisions) {
            maybe = random.nextInt()
            hash = canon.calculateHash(maybe)
            val offset = hash and 2048 - 1 shl 2

            if (offset == target) {
                collisions[i++] = maybe
            }
        }

        for (i in 0..<22) {
            canon.addName(i.toString(), collisions[i])
        }

        canon.release()

        canon = root.makeChild(TokenStreamFactory.Feature.collectDefaults())

        canon.addName("22", collisions[22])
    }

    private fun createDoc(add: Boolean): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("{ ")
        stringBuilder.append("\"__cirJsonId__\" : \"root\"")

        val length = PROPERTY_NAMES.size

        for (i in 0..<length) {
            stringBuilder.append(" , \"")
            stringBuilder.append(if (add) PROPERTY_NAMES[i] else PROPERTY_NAMES[length - i - 1])
            stringBuilder.append("\" : ")
            stringBuilder.append(i)
        }

        stringBuilder.append(" }")
        return stringBuilder.toString()
    }

    private fun createPerNameDoc(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("{\n")
        stringBuilder.append("    \"__cirJsonId__\" : \"root\"")
        stringBuilder.append(",\n    \"expectedGCPerPosition\": null")

        for (i in 0..<60) {
            stringBuilder.append(",\n    \"").append(i).append("\": null")
        }

        stringBuilder.append("\n}")
        return stringBuilder.toString()
    }

    private class MyFactory : CirJsonFactory() {

        override val myByteSymbolCanonicalizer: ByteQuadsCanonicalizer = ByteQuadsCanonicalizer.createRoot(-523743345)

    }

    companion object {

        private val PROPERTY_NAMES = arrayOf("a", "b", "c", "x", "y", "b13", "abcdefg", "a123", "a0", "b0", "c0", "d0",
                "e0", "f0", "g0", "h0", "x2", "aa", "ba", "ab", "b31", "___x", "aX", "xxx", "a2", "b2", "c2", "d2",
                "e2", "f2", "g2", "h2", "a3", "b3", "c3", "d3", "e3", "f3", "g3", "h3", "a1", "b1", "c1", "d1", "e1",
                "f1", "g1", "h1")

        private const val A_BYTES = 0x41414141

        private const val B_BYTES = 0x42424242

    }

}