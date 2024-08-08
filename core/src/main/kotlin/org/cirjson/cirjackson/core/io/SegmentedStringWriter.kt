package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.util.BufferRecycler
import org.cirjson.cirjackson.core.util.TextBuffer
import java.io.StringWriter
import java.io.Writer

/**
 * Efficient alternative to [StringWriter], based on using segmented internal buffer. Initial input buffer is also
 * recyclable.
 *
 * This class is most useful when serializing CirJSON content as a String: if so, instance of this class can be given as
 * the writer to `CirJsonGenerator`.
 */
class SegmentedStringWriter(bufferRecycler: BufferRecycler?) : Writer(), BufferRecycler.Gettable {

    private val myBuffer = TextBuffer(bufferRecycler)

    /*
     *******************************************************************************************************************
     * BufferRecycler.Gettable implementation
     *******************************************************************************************************************
     */

    override fun bufferRecycler(): BufferRecycler? {
        return myBuffer.bufferRecycler
    }

    /*
     *******************************************************************************************************************
     * Writer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun append(csq: CharSequence): Writer {
        val string = csq.toString()
        myBuffer.append(string, 0, string.length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun append(csq: CharSequence, start: Int, end: Int): Writer {
        val string = csq.subSequence(start, end).toString()
        myBuffer.append(string, 0, string.length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun append(c: Char): Writer {
        write(c.code)
        return this
    }

    override fun close() {
        // no-op
    }

    override fun flush() {
        // no-op
    }

    @Throws(CirJacksonException::class)
    override fun write(cbuf: CharArray) {
        myBuffer.append(cbuf, 0, cbuf.size)
    }

    @Throws(CirJacksonException::class)
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        myBuffer.append(cbuf, off, len)
    }

    @Throws(CirJacksonException::class)
    override fun write(c: Int) {
        myBuffer.append(c.toChar())
    }

    @Throws(CirJacksonException::class)
    override fun write(str: String) {
        myBuffer.append(str, 0, str.length)
    }

    @Throws(CirJacksonException::class)
    override fun write(str: String, off: Int, len: Int) {
        myBuffer.append(str, off, len)
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    val contentAndClear
        get() = myBuffer.contentsAsString().also { myBuffer.releaseBuffers() }

}