package org.cirjson.cirjackson.core.support

import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class MockDataInput(private val myInput: InputStream) : DataInput {

    constructor(data: ByteArray) : this(ByteArrayInputStream(data))

    constructor(utf8Data: String) : this(utf8Data.toByteArray(StandardCharsets.UTF_8))

    override fun skipBytes(n: Int): Int {
        return myInput.skip(n.toLong()).toInt()
    }

    override fun readByte(): Byte {
        val ch = myInput.read()

        if (ch < 0) {
            throw EOFException("End-of-input for readByte()")
        }

        return ch.toByte()
    }

    override fun readFully(b: ByteArray) {
        throw UnsupportedOperationException()
    }

    override fun readFully(b: ByteArray, off: Int, len: Int) {
        throw UnsupportedOperationException()
    }

    override fun readBoolean(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun readUnsignedByte(): Int {
        val ch = myInput.read()

        if (ch < 0) {
            throw EOFException("End-of-input for readByte()")
        }

        return ch
    }

    override fun readShort(): Short {
        throw UnsupportedOperationException()
    }

    override fun readUnsignedShort(): Int {
        throw UnsupportedOperationException()
    }

    override fun readChar(): Char {
        throw UnsupportedOperationException()
    }

    override fun readInt(): Int {
        throw UnsupportedOperationException()
    }

    override fun readLong(): Long {
        throw UnsupportedOperationException()
    }

    override fun readFloat(): Float {
        throw UnsupportedOperationException()
    }

    override fun readDouble(): Double {
        throw UnsupportedOperationException()
    }

    override fun readLine(): String {
        throw UnsupportedOperationException()
    }

    override fun readUTF(): String {
        throw UnsupportedOperationException()
    }

}