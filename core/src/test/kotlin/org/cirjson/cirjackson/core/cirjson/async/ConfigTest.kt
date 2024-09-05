package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testFactoryDefaults() {
        assertTrue(factory.isParsingAsyncPossible)
    }

    @Test
    fun testAsyncParerDefaults() {
        for (mode in ALL_ASYNC_MODES) {
            asyncParerDefaults(mode)
        }
    }

    private fun asyncParerDefaults(mode: Int) {
        val doc = utf8Bytes("[\"root\",true,false]")
        val wrapper = createAsync(factory, mode, 99, doc, 0)
        val parser = wrapper.parser
        assertTrue(parser.isParsingAsyncPossible)
        assertNull(parser.streamReadInputSource())
        assertEquals(-1, parser.releaseBuffered(StringWriter()))
        assertEquals(0, parser.releaseBuffered(ByteArrayOutputStream()))

        assertToken(CirJsonToken.START_ARRAY, wrapper.nextToken())
        assertEquals(18, parser.releaseBuffered(ByteArrayOutputStream()))
        wrapper.close()
        parser.close()
    }

}