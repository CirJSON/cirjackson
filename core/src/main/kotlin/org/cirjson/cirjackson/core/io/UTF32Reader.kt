package org.cirjson.cirjackson.core.io

import java.io.InputStream
import java.io.Reader

/**
 * Simple UTF-32/UCS-4 decoder.
 *
 * @property myIsAutoClosed Whether underlying [InputStream] (if any) should be closed when this [Reader] is closed or
 * not.
 */
open class UTF32Reader(protected val myIOContext: IOContext, protected var myInput: InputStream?,
        private val myIsAutoClosed: Boolean, protected var myBuffer: ByteArray?, protected var myPointer: Int,
        protected var myLength: Int, protected val myIsBigEndian: Boolean) : Reader() {

    /**
     * Although input is fine with full Unicode set, Kotlin still uses 16-bit chars, so we may have to split high-order
     * chars into surrogate pairs.
     */
    protected var mySurrogate = NO_CHAR

    /**
     * Total read character count; used for error reporting purposes
     */
    protected var myCharCount = 0

    /**
     * Total read byte count; used for error reporting purposes
     */
    protected var myByteCount = 0

    protected val myIsManagedBuffers = myInput != null

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        TODO("Not yet implemented")
    }

    companion object {

        val NO_CHAR = '\u0000'

    }

}