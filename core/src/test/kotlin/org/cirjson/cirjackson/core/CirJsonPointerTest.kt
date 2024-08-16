package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.*

class CirJsonPointerTest : TestBase() {

    @Test
    fun testSimplePath() {
        val input = "/Image/15/name"

        var pointer: CirJsonPointer? = CirJsonPointer.compile(input)
        assertFalse(pointer!!.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("Image", pointer.matchingProperty)
        assertEquals("/Image/15", pointer.head!!.toString())
        assertEquals(input, pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(15, pointer.matchingIndex)
        assertEquals("15", pointer.matchingProperty)
        assertEquals("/15", pointer.head!!.toString())
        assertEquals("/15/name", pointer.toString())

        assertEquals("", pointer.head!!.head!!.toString())
        assertNull(pointer.head!!.head!!.head)

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("name", pointer.matchingProperty)
        assertEquals("/name", pointer.toString())
        assertEquals("", pointer.head!!.toString())
        assertSame(EMPTY_POINTER, pointer.head)

        pointer = pointer.tail
        assertTrue(pointer!!.isMatching)
        assertNull(pointer.tail)
        assertNull(pointer.head)
        assertNull(pointer.matchingProperty)
        assertEquals(-1, pointer.matchingIndex)
    }

    @Test
    fun testSimplePathLonger() {
        val input = "/a/b/c/d/e/f/0"
        val pointer = CirJsonPointer.compile(input)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("a", pointer.matchingProperty)
        assertEquals("/a/b/c/d/e/f", pointer.head!!.toString())
        assertEquals("/b/c/d/e/f/0", pointer.tail!!.toString())
        assertEquals("/0", pointer.last!!.toString())
        assertEquals(input, pointer.toString())
    }

    @Test
    fun testSimpleTail() {
        val input = "/root/leaf"
        val pointer = CirJsonPointer.compile(input)

        assertEquals("/leaf", pointer.tail!!.toString())
        assertEquals("", pointer.tail!!.tail!!.toString())
    }

    @Test
    fun testWonkyNumber() {
        val pointer = CirJsonPointer.compile("/1e0")
        assertFalse(pointer.isMatching)
    }

    @Test
    fun testZeroIndex() {
        var pointer = CirJsonPointer.compile("/0")
        assertEquals(0, pointer.matchingIndex)
        pointer = CirJsonPointer.compile("/00")
        assertEquals(-1, pointer.matchingIndex)
    }

    @Test
    fun testLast() {
        var input = "/Image/name"

        var pointer = CirJsonPointer.compile(input)
        var leaf = pointer.last!!

        assertEquals("/name", leaf.toString())
        assertEquals("name", leaf.matchingProperty)

        input = "/Image/15/name"

        pointer = CirJsonPointer.compile(input)
        leaf = pointer.last!!

        assertEquals("/name", leaf.toString())
        assertEquals("name", leaf.matchingProperty)
    }

    @Test
    fun testEmptyPointer() {
        assertSame(EMPTY_POINTER, CirJsonPointer.compile(""))
        assertEquals("", EMPTY_POINTER.toString())

        assertFalse(EMPTY_POINTER.isMaybeMatchingProperty)
        assertFalse(EMPTY_POINTER.isMaybeMatchingElement)
        assertEquals(-1, EMPTY_POINTER.matchingIndex)
        assertNull(EMPTY_POINTER.matchingProperty)
    }

    @Test
    fun testPointerWithEmptyPropertyName() {
        val pointer = CirJsonPointer.compile("/")
        assertNotNull(pointer)
        assertNotSame(EMPTY_POINTER, pointer)

        assertEquals("/", pointer.toString())
        assertTrue(pointer.isMaybeMatchingProperty)
        assertFalse(pointer.isMaybeMatchingElement)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("", pointer.matchingProperty)
        assertTrue(pointer.matchesProperty(""))
        assertFalse(pointer.matchesElement(0))
        assertFalse(pointer.matchesElement(-1))
        assertFalse(pointer.matchesProperty("1"))
    }

    @Test
    fun testEquality() {
        assertNotEquals(CirJsonPointer.empty(), CirJsonPointer.compile("/"))

        assertEquals(CirJsonPointer.compile("/foo/3"), CirJsonPointer.compile("/foo/3"))
        assertNotEquals(CirJsonPointer.empty(), CirJsonPointer.compile("/12"))
        assertNotEquals(CirJsonPointer.compile("/12"), CirJsonPointer.empty())

        assertEquals(CirJsonPointer.compile("/a/b/c").tail, CirJsonPointer.compile("/foo/b/c").tail)

        val pointer = CirJsonPointer.compile("/abc/def")
        val tail = CirJsonPointer.compile("/def")
        assertEquals(pointer.tail, tail)
        assertEquals(tail, pointer.tail)

        assertNotEquals("/" as Any, CirJsonPointer.empty())
    }

    @Test
    fun testProperties() {
        assertTrue(CirJsonPointer.compile("/foo").isMaybeMatchingProperty)
        assertFalse(CirJsonPointer.compile("/foo").isMaybeMatchingElement)

        assertTrue(CirJsonPointer.compile("/12").isMaybeMatchingElement)
        assertTrue(CirJsonPointer.compile("/12").isMaybeMatchingProperty)
    }

    @Test
    fun testAppend() {
        val input = "/Image/15/name"
        val stringToAppend = "/extension"

        val pointer = CirJsonPointer.compile(input)
        val toAppend = CirJsonPointer.compile(stringToAppend)

        val appended = pointer.append(toAppend)

        assertEquals("extension", appended.last!!.matchingProperty)

        assertEquals("/Image/15/name/extension", appended.toString())
    }

    @Test
    fun testAppendWithFinalSlash() {
        val input = "/Image/15/name/"
        val stringToAppend = "/extension"

        val pointer = CirJsonPointer.compile(input)
        val toAppend = CirJsonPointer.compile(stringToAppend)

        val appended = pointer.append(toAppend)

        assertEquals("extension", appended.last!!.matchingProperty)

        assertEquals("/Image/15/name//extension", appended.toString())
    }

    @Test
    fun testAppendProperty() {
        val input = "/Image/15/name"
        val stringToAppendNoSlash = "extension"
        val stringToAppendWithSlash = "/extension~"

        val pointer = CirJsonPointer.compile(input)
        val appendedNoSlash = pointer.appendProperty(stringToAppendNoSlash)
        val appendedWithSlash = pointer.appendProperty(stringToAppendWithSlash)

        assertEquals(stringToAppendNoSlash, appendedNoSlash.last!!.matchingProperty)
        assertEquals("/Image/15/name/extension", appendedNoSlash.toString())

        assertEquals(stringToAppendWithSlash, appendedWithSlash.last!!.matchingProperty)
        assertEquals("/Image/15/name/~1extension~0", appendedWithSlash.toString())
    }

    @Test
    fun testAppendPropertyEmpty() {
        val base = "/Image/72/src"

        val basePointer = CirJsonPointer.compile(base)

        assertSame(basePointer, basePointer.appendProperty(null))

        val sub = basePointer.appendProperty("")
        assertNotSame(basePointer, sub)

        assertEquals("$base/", sub.toString())
    }

    @Test
    fun testAppendIndex() {
        val input = "/Image/15/name"
        val index = 12

        val pointer = CirJsonPointer.compile(input)
        val appended = pointer.appendIndex(index)

        assertEquals(12, appended.last!!.matchingIndex)
    }

    @Test
    fun testQuotedPath() {
        val input = "/w~1out/til~0de/~1ab"

        var pointer: CirJsonPointer? = CirJsonPointer.compile(input)
        assertFalse(pointer!!.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("w/out", pointer.matchingProperty)
        assertEquals("/w~1out/til~0de", pointer.head!!.toString())
        assertEquals(input, pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("til~de", pointer.matchingProperty)
        assertEquals("/til~0de", pointer.head!!.toString())
        assertEquals("/til~0de/~1ab", pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals("/ab", pointer.matchingProperty)
        assertEquals("", pointer.head!!.toString())
        assertEquals("/~1ab", pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertTrue(pointer.isMatching)
        assertNull(pointer.tail)
    }

    @Test
    fun testLongNumbers() {
        val longId = 1L + Int.MAX_VALUE

        val input = "/User/$longId"

        var pointer: CirJsonPointer? = CirJsonPointer.compile(input)
        assertEquals("User", pointer!!.matchingProperty)
        assertEquals(input, pointer.toString())

        pointer = pointer.tail
        assertNotNull(pointer)
        assertFalse(pointer.isMatching)
        assertEquals(-1, pointer.matchingIndex)
        assertEquals(longId.toString(), pointer.matchingProperty)

        pointer = pointer.tail
        assertNotNull(pointer)
        assertTrue(pointer.isMatching)
        assertNull(pointer.tail)
    }

    @Test
    fun testAppendWithTail() {
        val original = CirJsonPointer.compile("/a1/b/c")
        val tailPointer = original.tail!!
        assertEquals("/b/c", tailPointer.toString())

        val other = CirJsonPointer.compile("/a2")
        assertEquals("/a2", other.toString())

        assertEquals("/a2/b/c", other.append(tailPointer).toString())

        assertEquals("/b/c/a2", tailPointer.append(other).toString())

        assertEquals("/b/c/xyz", tailPointer.appendProperty("xyz").toString())
    }

    @Test
    fun testDeepCirJsonPointer() {
        val maxDepth = 120_000
        val input = generateDeepString(maxDepth)
        val factory = CirJsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Int.MAX_VALUE).build()).build()
        val parser = createParser(factory, MODE_READER, input)

        try {
            while (true) {
                parser.nextToken()
            }
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end")
            val parsingContext = parser.streamReadContext!!
            val pointer = parsingContext.pathAsPointer()
            val pointerString = pointer.toString()
            val expected = String(CharArray(maxDepth - 1)).replace("\u0000", "/0")
            assertEquals(expected, pointerString)
        }

        parser.close()
    }

    @Suppress("SameParameterValue")
    private fun generateDeepString(maxDepth: Int): String {
        val stringBuilder = StringBuilder(maxDepth * 10)

        for (i in 0..<maxDepth) {
            stringBuilder.append("[\"$i\",")
        }

        return stringBuilder.toString().removeSuffix(",")
    }

    @Test
    fun testViaParser() {
        val simple = apostropheToQuote(
                "{'__cirJsonId__':'root','a':123,'array':['root/a',1,2,['root/a/2',3],5,{'__cirJsonId__':'root/a/4','obInArray':4}],'ob':{'__cirJsonId__':'root/ob','first':['root/ob/first',false,true],'second':{'__cirJsonId__':'root/ob/second','sub':37}},'b':true}")

        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            viaParser(createParser(mode, simple))
        }
    }

    private fun viaParser(parser: CirJsonParser) {
        assertSame(EMPTY_POINTER, parser.streamReadContext!!.pathAsPointer())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertSame(EMPTY_POINTER, parser.streamReadContext!!.pathAsPointer())

        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/a", parser.streamReadContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/a", parser.streamReadContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/array", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/array", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/array", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/0", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/1", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/array/2", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/array/2", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/2/0", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/array/2", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/3", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/array/4", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/array/4/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/array/4/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/array/4/obInArray", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/4/obInArray", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/array/4", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/array", parser.streamReadContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/ob", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/ob/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/first", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/ob/first", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/ob/first", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals("/ob/first/0", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals("/ob/first/1", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/ob/first", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/second", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/ob/second", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/second/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/ob/second/__cirJsonId__", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/second/sub", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/ob/second/sub", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/ob/second", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/ob", parser.streamReadContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/b", parser.streamReadContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals("/b", parser.streamReadContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertSame(EMPTY_POINTER, parser.streamReadContext!!.pathAsPointer())

        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testViaGenerator() {
        for (mode in ALL_GENERATOR_MODES) {
            viaGenerator(createGenerator(mode))
        }
    }

    private fun viaGenerator(generator: CirJsonGenerator) {
        assertSame(EMPTY_POINTER, generator.streamWriteContext.pathAsPointer())

        generator.writeStartArray()
        assertSame(EMPTY_POINTER, generator.streamWriteContext.pathAsPointer())
        generator.writeArrayId(listOf<String>())
        assertSame(EMPTY_POINTER, generator.streamWriteContext.pathAsPointer())
        generator.writeBoolean(true)
        assertEquals("/0", generator.streamWriteContext.pathAsPointer().toString())

        generator.writeStartObject()
        assertEquals("/1", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeObjectId(Any())
        assertEquals("/1/__cirJsonId__", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeName("x")
        assertEquals("/1/x", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeString("foo")
        assertEquals("/1/x", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeName("stats")
        assertEquals("/1/stats", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeStartObject()
        assertEquals("/1/stats", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeObjectId(Any())
        assertEquals("/1/stats/__cirJsonId__", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeName("rate")
        assertEquals("/1/stats/rate", generator.streamWriteContext.pathAsPointer().toString())
        generator.writeNumber(13)
        generator.writeEndObject()
        assertEquals("/1/stats", generator.streamWriteContext.pathAsPointer().toString())

        generator.writeEndObject()
        assertEquals("/1", generator.streamWriteContext.pathAsPointer().toString())

        generator.writeEndArray()
        assertSame(EMPTY_POINTER, generator.streamWriteContext.pathAsPointer())

        generator.close()
        (generator.streamWriteOutputTarget as AutoCloseable).close()
    }

    @Test
    fun testParserWithRoot() {
        val cirJson = apostropheToQuote(
                "{'__cirJsonId__':'0','a':1,'b':3}\n{'__cirJsonId__':'1','a':5,'c':['1/c',1,2]}\n['2',1,2]\n")

        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            parserWithRoot(createParser(mode, cirJson))
        }
    }

    private fun parserWithRoot(parser: CirJsonParser) {
        assertSame(EMPTY_POINTER, parser.streamReadContext!!.pathAsPointer(true))

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/0", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/0/__cirJsonId__", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/0/__cirJsonId__", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/0/a", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/0/a", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/0/b", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/0/b", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/0", parser.streamReadContext!!.pathAsPointer(true).toString())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/1", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/1/__cirJsonId__", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/1/__cirJsonId__", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/1/a", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/1/a", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/1/c", parser.streamReadContext!!.pathAsPointer(true).toString())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/1/c", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/1/c", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/1/c/0", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/1/c/1", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/1/c", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/1", parser.streamReadContext!!.pathAsPointer(true).toString())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/2", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/2", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/2/0", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/2/1", parser.streamReadContext!!.pathAsPointer(true).toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/2", parser.streamReadContext!!.pathAsPointer(true).toString())

        assertNull(parser.nextToken())

        assertEquals("/2", parser.streamReadContext!!.pathAsPointer(true).toString())

        parser.close()
    }

    @Test
    fun testGeneratorWithRoot() {
        for (mode in ALL_GENERATOR_MODES) {
            generatorWithRoot(createGenerator(mode))
        }
    }

    private fun generatorWithRoot(generator: CirJsonGenerator) {
        assertSame(EMPTY_POINTER, generator.streamWriteContext.pathAsPointer(true))

        generator.writeStartArray()
        assertEquals("/0", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeArrayId(listOf<String>())
        assertEquals("/0", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeBoolean(true)
        assertEquals("/0/0", generator.streamWriteContext.pathAsPointer(true).toString())

        generator.writeStartObject()
        assertEquals("/0/1", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeObjectId(Any())
        assertEquals("/0/1/__cirJsonId__", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeName("x")
        assertEquals("/0/1/x", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeString("foo")
        assertEquals("/0/1/x", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeEndObject()
        assertEquals("/0/1", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeEndArray()
        assertEquals("/0", generator.streamWriteContext.pathAsPointer(true).toString())

        generator.writeBoolean(true)
        assertEquals("/1", generator.streamWriteContext.pathAsPointer(true).toString())

        generator.writeStartArray()
        assertEquals("/2", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeArrayId(listOf<String>())
        assertEquals("/2", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeString("foo")
        assertEquals("/2/0", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeString("bar")
        assertEquals("/2/1", generator.streamWriteContext.pathAsPointer(true).toString())
        generator.writeEndArray()
        assertEquals("/2", generator.streamWriteContext.pathAsPointer(true).toString())

        assertEquals("/2", generator.streamWriteContext.pathAsPointer(true).toString())

        generator.close()
        (generator.streamWriteOutputTarget as AutoCloseable).close()
    }

    @Test
    fun testCirJsonPointerParseTailSimple() {
        cirJsonPointerTest(generatePath(false))
    }

    @Test
    fun testCirJsonPointerParseTailWithQuoted() {
        cirJsonPointerTest(generatePath(true))
    }

    private fun cirJsonPointerTest(pathExpression: String) {
        val pointer = CirJsonPointer.compile(pathExpression)
        assertNotNull(pointer)
        assertEquals(pathExpression, pointer.toString())

        var current: CirJsonPointer? = pointer

        while (current!!.tail.also { current = it } != null) {
            val actual = current!!.toString()
            val expected = pathExpression.substring(pathExpression.length - actual.length)
            assertEquals(expected, actual)
        }
    }

    private fun generatePath(escaped: Boolean): String {
        val stringBuilder = StringBuilder(4 * TOO_DEEP_PATH)

        for (i in 0..<TOO_DEEP_PATH) {
            stringBuilder.append('/').append(('a'.code + i.mod(25)).toChar()).append(i)

            if (escaped) {
                when (i and 7) {
                    1 -> stringBuilder.append("~0x")
                    4 -> stringBuilder.append("~1y")
                }
            }
        }

        return stringBuilder.toString()
    }

    companion object {

        private const val TOO_DEEP_PATH = 25_000

        private val EMPTY_POINTER = CirJsonPointer.empty()

    }

}