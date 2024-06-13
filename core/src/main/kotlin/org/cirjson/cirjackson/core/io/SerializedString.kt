package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.SerializableString
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * String token that can lazily serialize String contained and then reuse that serialization later on. This is similar
 * to JDBC prepared statements, for example, in that instances should only be created when they are used more than use;
 * prime candidates are various serializers.
 *
 * Class is final for performance reasons and since this is not designed to be extensible or customizable
 * (customizations would occur in calling code)
 */
class SerializedString(override val value: String) : SerializableString {

    override val length: Int = value.length

    private val quotedUTF8 by lazy { CirJsonStringEncoder.quoteAsUTF8(value) }

    private val unquotedUTF8 by lazy { CirJsonStringEncoder.encodeAsUTF8(value) }

    private val quotedChars by lazy { CirJsonStringEncoder.quoteAsCharArray(value) }

    /**
     * Accessor for accessing value that has been quoted (escaped) using CirJSON quoting rules (using backslash-prefixed
     * codes) into a CharArray.
     */
    override fun asQuotedChars(): CharArray {
        return quotedChars
    }

    /**
     * Accessor for accessing value as is (without CirJSON quoting (ecaping)) encoded as UTF-8 byte array.
     */
    override fun asUnquotedUTF8(): ByteArray {
        return unquotedUTF8
    }

    /**
     * Accessor for accessing value that has been quoted (escaped) using CirJSON quoting rules (using backslash-prefixed
     * codes), and encoded using UTF-8 encoding into a byte array.
     */
    override fun asQuotedUTF8(): ByteArray {
        return quotedUTF8
    }

    override fun appendQuotedUTF8(buffer: ByteArray, offset: Int): Int {
        val length = quotedUTF8.size

        if (offset + length > buffer.size) {
            return -1
        }

        quotedUTF8.copyInto(buffer, offset)
        return length
    }

    override fun appendQuoted(buffer: CharArray, offset: Int): Int {
        val length = quotedChars.size

        if (offset + length > buffer.size) {
            return -1
        }

        quotedChars.copyInto(buffer, offset)
        return length
    }

    override fun appendUnquotedUTF8(buffer: ByteArray, offset: Int): Int {
        val length = unquotedUTF8.size

        if (offset + length > buffer.size) {
            return -1
        }

        unquotedUTF8.copyInto(buffer, offset)
        return length
    }

    override fun appendUnquoted(buffer: CharArray, offset: Int): Int {
        val length = value.length

        if (offset + length > buffer.size) {
            return -1
        }

        value.toCharArray(buffer, offset)
        return length
    }

    override fun writeQuotedUTF8(output: OutputStream): Int {
        val length = quotedUTF8.size
        output.write(quotedUTF8, 0, length)
        return length
    }

    override fun writeUnquotedUTF8(output: OutputStream): Int {
        val length = unquotedUTF8.size
        output.write(unquotedUTF8, 0, length)
        return length
    }

    override fun putQuotedUTF8(buffer: ByteBuffer): Int {
        val length = quotedUTF8.size

        if (length > buffer.remaining()) {
            return -1
        }

        buffer.put(quotedUTF8, 0, length)
        return length
    }

    override fun putUnquotedUTF8(buffer: ByteBuffer): Int {
        val length = unquotedUTF8.size

        if (length > buffer.remaining()) {
            return -1
        }

        buffer.put(unquotedUTF8, 0, length)
        return length
    }

    override fun toString(): String {
        return value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as SerializedString

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

}