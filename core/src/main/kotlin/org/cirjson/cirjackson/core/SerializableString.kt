package org.cirjson.cirjackson.core

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Interface that defines how CirJackson package can interact with efficient pre-serialized or lazily-serialized and
 * reused String representations. Typically, implementations store possible serialized version(s) so that serialization
 * of String can be done more efficiently, especially when used multiple times.
 *
 * Note that "quoted" in methods means quoting of 'special' characters using CirJSON backlash notation (and not use of
 * actual double quotes).
 *
 * @see org.cirjson.cirjackson.core.io.SerializedString
 */
interface SerializableString {

    /**
     * Returns unquoted String that this object represents (and offers serialized forms for)
     */
    val value: String

    /**
     * Returns length of the (unquoted) String as characters. Functionally equivalent to:
     * ```
     * value.length;
     * ```
     *
     * @return Length of the String in characters
     */
    val length: Int

    /**
     * Returns CirJSON quoted form of the String, as character array. Result can be embedded as-is in textual CirJSON as
     * property name or CirJSON String.
     *
     * @return CirJSON quoted form of the String as `CharArray`
     */
    fun asQuotedChars(): CharArray

    /**
     * Returns UTF-8 encoded version of unquoted String. Functionally equivalent to (but more efficient than):
     * ```
     * value.toByteArray(StandardCharsets.UTF_8)
     * ```
     *
     * @return UTF-8 encoded version of String, without any escaping
     */
    fun asUnquotedUTF8(): ByteArray

    /**
     * Returns UTF-8 encoded version of CirJSON-quoted String. Functionally equivalent to (but more efficient than):
     * ```
     * String(asQuotedChars()).toByteArray(StandardCharsets.UTF_8)
     * ```
     *
     * @return UTF-8 encoded version of CirJSON-escaped String
     */
    fun asQuotedUTF8(): ByteArray

    /**
     * Method that will append quoted UTF-8 bytes of this String into given buffer, if there is enough room; if not,
     * returns -1. Functionally equivalent to:
     * ```
     * val bytes = str.asQuotedUTF8()
     * bytes.copyInto(buffer, offset, 0, bytes.size)
     * return bytes.size
     * ```
     *
     * @param buffer Buffer to append JSON-escaped String into
     *
     * @param offset Offset in `buffer` to append String at
     *
     * @return Number of bytes appended, if successful, otherwise -1
     */
    fun appendQuotedUTF8(buffer: ByteArray, offset: Int): Int

    /**
     * Method that will append quoted characters of this String into given buffer. Functionally equivalent to:
     * ```
     * val chars = str.asQuotedChars()
     * chars.copyInto(buffer, offset, 0, chars.size)
     * return chars.size
     * ```
     *
     * @param buffer Buffer to append CirJSON-escaped String into
     *
     * @param offset Offset in `buffer` to append String at
     *
     * @return Number of characters appended, if successful, otherwise -1
     */
    fun appendQuoted(buffer: CharArray, offset: Int): Int

    /**
     * Method that will append unquoted ('raw') UTF-8 bytes of this String into given buffer. Functionally equivalent
     * to:
     * ```
     * val bytes = str.asUnquotedCharsUTF8()
     * bytes.copyInto(buffer, offset, 0, bytes.size)
     * return bytes.size
     * ```
     *
     * @param buffer Buffer to append literal (unescaped) String into
     *
     * @param offset Offset in `buffer` to append String at
     *
     * @return Number of bytes appended, if successful, otherwise -1
     */
    fun appendUnquotedUTF8(buffer: ByteArray, offset: Int): Int

    /**
     * Method that will append unquoted characters of this String into given buffer. Functionally equivalent to:
     * ```
     * val chars = str.value.toCharArray()
     * chars.copyInto(buffer, offset, 0, chars.size)
     * return chars.size
     * ```
     *
     * @param buffer Buffer to append literal (unescaped) String into
     *
     * @param offset Offset in `buffer` to append String at
     *
     * @return Number of characters appended, if successful, otherwise -1
     */
    fun appendUnquoted(buffer: CharArray, offset: Int): Int

    /**
     * Method for writing CirJSON-escaped UTF-8 encoded String value using given [OutputStream].
     *
     * @param output [OutputStream] to write String into
     *
     * @return Number of bytes written
     *
     * @throws IOException if underlying stream write fails
     */
    @Throws(IOException::class)
    fun writeQuotedUTF8(output: OutputStream): Int

    /**
     * Method for writing unescaped UTF-8 encoded String value using given [OutputStream].
     *
     * @param output [OutputStream] to write String into
     *
     * @return Number of bytes written
     *
     * @throws IOException if underlying stream write fails
     */
    @Throws(IOException::class)
    fun writeUnquotedUTF8(output: OutputStream): Int

    /**
     * Method for appending CirJSON-escaped UTF-8 encoded String value into given [ByteBuffer], if it fits.
     *
     * @param buffer [ByteBuffer] to append String into
     *
     * @return Number of bytes put, if contents fit, otherwise -1
     *
     * @throws IOException if underlying buffer append operation fails
     */
    @Throws(IOException::class)
    fun putQuotedUTF8(buffer: ByteBuffer): Int

    /**
     * Method for appending unquoted ('raw') UTF-8 encoded String value into given [ByteBuffer], if it fits.
     *
     * @param buffer [ByteBuffer] to append String into
     *
     * @return Number of bytes put, if contents fit, otherwise -1
     *
     * @throws IOException if underlying buffer append operation fails
     */
    @Throws(IOException::class)
    fun putUnquotedUTF8(buffer: ByteBuffer): Int

}