package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.CirJsonToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AsyncPointerFromContext : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testPointerWithAsyncParser() {
        val doc = utf8Bytes(apostropheToQuote(
                "{'__cirJsonId__':'root','a':123,'array':['root/a',1,2,['root/a/2',3],5,{'__cirJsonId__':'root/a/4','obInArray':4}],'ob':{'__cirJsonId__':'root/ob','first':['root/ob/first',false,true],'second':{'__cirJsonId__':'root/ob/second','sub':37}},'b':true}"))
        for (mode in ALL_ASYNC_MODES) {
            pointerWithAsyncParser(mode, 0, doc, 1000)
            pointerWithAsyncParser(mode, 0, doc, 99)
            pointerWithAsyncParser(mode, 0, doc, 7)
            pointerWithAsyncParser(mode, 0, doc, 5)
            pointerWithAsyncParser(mode, 0, doc, 3)
            pointerWithAsyncParser(mode, 0, doc, 2)
            pointerWithAsyncParser(mode, 0, doc, 1)

            pointerWithAsyncParser(mode, 1, doc, 99)
            pointerWithAsyncParser(mode, 1, doc, 5)
            pointerWithAsyncParser(mode, 1, doc, 3)
            pointerWithAsyncParser(mode, 1, doc, 1)
        }
    }

    private fun pointerWithAsyncParser(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertSame(EMPTY_POINTER, parser.parsingContext!!.pathAsPointer())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertSame(EMPTY_POINTER, parser.parsingContext!!.pathAsPointer())

        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/a", parser.parsingContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/a", parser.parsingContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/array", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/array", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/array", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/0", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/1", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/array/2", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/array/2", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/2/0", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/array/2", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/3", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/array/4", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/array/4/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/array/4/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/array/4/obInArray", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/array/4/obInArray", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/array/4", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/array", parser.parsingContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/ob", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/ob/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/first", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals("/ob/first", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/ob/first", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals("/ob/first/0", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals("/ob/first/1", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals("/ob/first", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/second", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/ob/second", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/second/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("/ob/second/__cirJsonId__", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/ob/second/sub", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("/ob/second/sub", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/ob/second", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("/ob", parser.parsingContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("/b", parser.parsingContext!!.pathAsPointer().toString())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals("/b", parser.parsingContext!!.pathAsPointer().toString())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertSame(EMPTY_POINTER, parser.parsingContext!!.pathAsPointer())
        assertNull(parser.nextToken())
        parser.close()
    }

    companion object {

        private val EMPTY_POINTER = CirJsonPointer.empty()

    }

}