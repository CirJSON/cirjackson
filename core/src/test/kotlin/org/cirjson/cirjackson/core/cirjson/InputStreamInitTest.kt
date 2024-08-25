package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class InputStreamInitTest : TestBase() {

    @Test
    fun testForFile() {
        val factory = FailingCirJsonFactory()

        try {
            factory.createParser(ObjectReadContext.empty(), File("/tmp/test.cirjson"))
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Will not read")
        }

        assertNotNull(factory.lastStream)
        assertTrue(factory.lastStream!!.isClosed)
    }

    @Test
    fun testForURL() {
        val factory = FailingCirJsonFactory()

        try {
            factory.createParser(ObjectReadContext.empty(), URL("http://localhost:80/"))
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Will not read")
        }

        assertNotNull(factory.lastStream)
        assertTrue(factory.lastStream!!.isClosed)
    }

    private class FailingInputStream : InputStream() {

        var isClosed = false

        override fun read(): Int {
            throw IOException("Will not read, ever!")
        }

        override fun close() {
            super.close()
            isClosed = true
        }

    }

    private class FailingCirJsonFactory : CirJsonFactory() {

        var lastStream: FailingInputStream? = null

        override fun fileInputStream(file: File): InputStream {
            return FailingInputStream().also { lastStream = it }
        }

        override fun optimizedStreamFromURL(url: URL): InputStream {
            return FailingInputStream().also { lastStream = it }
        }

    }

}