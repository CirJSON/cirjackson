package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class OutputStreamInitTest : TestBase() {

    @Test
    fun testForFile() {
        val factory = FailingCirJsonFactory()

        try {
            val generator =
                    factory.createGenerator(ObjectWriteContext.empty(), File("/tmp/test.cirjson"), CirJsonEncoding.UTF8)
            generator.writeString("foo")
            factory.lastStream!!.startFailing()
            generator.close()
            fail("Should not pass")
        } catch (e: Exception) {
            verifyException(e, "Will not read")
        }

        assertNotNull(factory.lastStream)
        assertTrue(factory.lastStream!!.isClosed)
    }

    private class FailingOutputStream : OutputStream() {

        var isClosed = false

        var writes = 0

        var isFailing = false

        fun startFailing() {
            isFailing = true
        }

        override fun write(b: Int) {
            ++writes

            if (isFailing) {
                throw IOException("Will not read, ever!")
            }
        }

        override fun close() {
            super.close()
            isClosed = true
        }

    }

    private class FailingCirJsonFactory : CirJsonFactory() {

        var lastStream: FailingOutputStream? = null

        override fun fileOutputStream(file: File): OutputStream {
            return FailingOutputStream().also { lastStream = it }
        }

    }

}