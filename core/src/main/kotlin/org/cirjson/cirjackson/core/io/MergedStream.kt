package org.cirjson.cirjackson.core.io

import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Simple [InputStream] implementation that is used to "unwind" some data previously read from an input stream; so that
 * as long as some of that data remains, it's returned; but as long as it's read, we'll just use data from the
 * underlying original stream. This is similar to [java.io.PushbackInputStream], but here there's only one implicit
 * pushback, when instance is constructed.
 */
class MergedStream(private val myContext: IOContext?, private val myInput: InputStream,
        private var myBuffer: ByteArray?, private var myPointer: Int, private var myEnd: Int) : InputStream() {

    @Throws(IOException::class)
    override fun available(): Int {
        return if (myBuffer != null) {
            myEnd - myPointer
        } else {
            myInput.available()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        free()
        myInput.close()
    }

    @Synchronized
    override fun mark(readlimit: Int) {
        if (myBuffer == null) {
            myInput.mark(readlimit)
        }
    }

    override fun markSupported(): Boolean {
        return myBuffer == null && myInput.markSupported()
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return if (myBuffer != null) {
            val c = myBuffer!![myPointer++].toInt() and 0xFF

            if (myPointer >= myEnd) {
                free()
            }

            c
        } else {
            myInput.read()
        }
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return if (myBuffer != null) {
            val avail = myEnd - myPointer
            val length = min(len, avail)

            myBuffer!!.copyInto(b, off, myPointer, myPointer + length)
            myPointer += length

            if (myPointer >= myEnd) {
                free()
            }

            length
        } else {
            myInput.read(b, off, len)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        if (myBuffer == null) {
            myInput.reset()
        }
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        var n = n
        var count = 0L

        if (myBuffer != null) {
            val amount = myEnd - myPointer

            if (n < amount) {
                myPointer += n.toInt()
                return n
            }

            free()
            count += amount
            n -= amount
        }

        if (n > 0) {
            count += myInput.skip(n)
        }

        return count
    }

    private fun free() {
        val buffer = myBuffer

        if (buffer != null) {
            myBuffer = null
            myContext?.releaseReadIOBuffer(buffer)
        }
    }

}