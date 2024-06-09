package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.io.ContentReference

/**
 * Object that encapsulates Location information used for reporting parsing (or potentially generation) errors, as well
 * as current location within input streams.
 *
 * NOTE: users should be careful if using [equals] implementation as it may or may not compare underlying "content
 * reference" for equality. Instead, it would make sense to explicitly implementing equality checks using specific
 * criteria caller desires.
 *
 * @property byteOffset Byte offset within underlying stream, reader or writer, if available; `-1` if not.
 *
 * @property charOffset Character offset within underlying stream, reader or writer, if available; `-1` if not.
 *
 * @property lineNumber Access for getting line number of this location (1-based), if available; `-1` if not. Note that
 * line number is typically not available for binary formats.
 *
 * @property columnNumber Access for getting column offset of this location (1-based), if available; `-1` if not. Note
 * that column position is typically not available for binary formats.
 *
 * Note: this returns an offset that is in units of input, so for `byte`-based input sources (like
 * [java.io.InputStream]) this does not take into account multibyte characters: one logical character can be 1, 2 or 3
 * bytes long. To calculate column position in characters either `char`-based input source (like [java.io.Reader]) needs
 * to be used, or content needs to be explicitly decoded.
 */
class CirJsonLocation(contentReference: ContentReference?, val byteOffset: Long, val charOffset: Long,
        val lineNumber: Int, val columnNumber: Int) {

    /**
     * Accessor for information about the original input source content is being read from. Returned reference is never
     * `null` but may not contain useful information.
     */
    val contentReference = contentReference ?: ContentReference.unknown()

    /**
     * Accessor for getting a textual description of source reference (Object returned by [contentReference]), as
     * included in description returned by [toString].
     *
     * Note: implementation will simply call [ContentReference.buildSourceDescription])
     */
    val sourceDescription: String by lazy {
        this.contentReference.buildSourceDescription()
    }

    constructor(contentReference: ContentReference?, charOffset: Long, lineNumber: Int, columnNumber: Int) : this(
            contentReference, -1L, charOffset, lineNumber, columnNumber)

    /**
     * Accessor for a brief summary of Location offsets (line number, column position, or byte offset, if available).
     */
    val offsetDescription: String
        get() = appendOffsetDescription(StringBuilder(40)).toString()

    fun appendOffsetDescription(stringBuilder: StringBuilder): StringBuilder {
        if (contentReference.isContentTextual) {
            stringBuilder.append("line: ")

            if (lineNumber >= 0) {
                stringBuilder.append(lineNumber)
            } else {
                stringBuilder.append("UNKNOWN")
            }

            stringBuilder.append(", column: ")

            if (columnNumber >= 0) {
                stringBuilder.append(columnNumber)
            } else {
                stringBuilder.append("UNKNOWN")
            }
        } else {
            stringBuilder.append("byte offset: #")

            if (byteOffset >= 0) {
                stringBuilder.append(byteOffset)
            } else {
                stringBuilder.append("UNKNOWN")
            }
        }

        return stringBuilder
    }

    fun toString(stringBuilder: StringBuilder): StringBuilder {
        if (this === NA) {
            return stringBuilder.append(NO_LOCATION_DESCRIPTION)
        }

        stringBuilder.apply {
            append("[Source: ")
            append(sourceDescription)
            append("; ")
        }

        return appendOffsetDescription(stringBuilder).append(']')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is CirJsonLocation) {
            return false
        }

        if (contentReference != other.contentReference) {
            return false
        }

        return lineNumber == other.lineNumber && columnNumber == other.columnNumber && charOffset == other.charOffset
                && byteOffset == other.byteOffset
    }

    override fun hashCode(): Int {
        var hash = contentReference.hashCode()
        hash = hash xor lineNumber
        hash += columnNumber
        hash = hash xor charOffset.hashCode()
        hash += byteOffset.hashCode()
        return hash
    }

    override fun toString(): String {
        if (this == NA) {
            return NO_LOCATION_DESCRIPTION
        }

        val srcDesc = sourceDescription

        val sb = StringBuilder().apply {
            append("[Source: ")
            append(srcDesc)
            append("; ")
        }
        return appendOffsetDescription(sb).append(']').toString()
    }

    companion object {

        private const val NO_LOCATION_DESCRIPTION = "[No location information]"

        /**
         * Shared immutable "N/A location" that can be returned to indicate that no location information is available.
         */
        val NA = CirJsonLocation(ContentReference.unknown(), -1L, -1L, -1, -1)

    }

}