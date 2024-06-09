package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.ErrorReportConfiguration
import org.cirjson.cirjackson.core.util.MutablePair
import org.cirjson.cirjackson.core.util.mutableTo
import kotlin.math.min

/**
 * Abstraction that encloses information about content being processed -- input source or output target, streaming or
 * not -- for the purpose of including pertinent information in location (see
 * [org.cirjson.cirjackson.core.CirJsonLocation]) objections, most commonly to be printed out as part of `Exception`
 * messages.
 *
 * @property isContentTextual Marker flag to indicate whether included content is textual or not: this is taken to mean,
 * by default, that a snippet of content may be displayed for exception messages.
 *
 * @property rawContent Reference to the actual underlying content.
 *
 * @property contentOffset For static content, indicates offset from the beginning of static array. `-1` if not in use.
 *
 * @property contentLength For static content, indicates length of content in the static array. `-1` if not in use.
 */
open class ContentReference protected constructor(val isContentTextual: Boolean, val rawContent: Any?,
        val contentOffset: Int, val contentLength: Int, errorReportConfiguration: ErrorReportConfiguration) {

    /**
     * Internal accessor, overridable, used for checking length (in units in which content is counted, either bytes or
     * chars) to use for truncation (so as not to include full content for humongous sources or targets)
     *
     * @see ErrorReportConfiguration.maxRawContentLength
     */
    protected open val maxRawContentLength = errorReportConfiguration.maxRawContentLength

    protected constructor(hasTextualContent: Boolean, rawContent: Any?,
            errorReportConfiguration: ErrorReportConfiguration) : this(hasTextualContent, rawContent, -1, -1,
            errorReportConfiguration)

    /**
     * Method for constructing a "source description" when content represented by this reference is read.
     *
     * @return Description constructed
     */
    fun buildSourceDescription(): String {
        return appendSourceDescription(StringBuilder(200)).toString()
    }

    /**
     * Method for appending a "source description" when content represented by this reference is read.
     *
     * @param stringBuilder StringBuilder to append description to
     *
     * @return StringBuilder passed as argument (for call chaining)
     */
    fun appendSourceDescription(stringBuilder: StringBuilder): StringBuilder {
        val srcRef = rawContent ?: return stringBuilder.apply {
            if (this@ContentReference === REDACTED_CONTENT) {
                append("REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled)")
            } else {
                append("UNKNOWN")
            }
        }

        val typeName = getTypeName(srcRef)
        stringBuilder.apply {
            append('(')
            append(typeName)
            append(')')
        }

        if (isContentTextual) {
            var unitStr = " chars"

            val maxLength = maxRawContentLength
            val offsets = contentOffset mutableTo contentLength

            val trimmed = when (srcRef) {
                is CharSequence -> truncate(srcRef, offsets, maxLength)

                is ByteArray -> truncate(srcRef, offsets, maxLength).also { unitStr = " bytes" }

                is CharArray -> truncate(srcRef, offsets, maxLength)

                else -> null
            }

            if (trimmed != null) {
                append(stringBuilder, trimmed)

                if (offsets.second > maxLength) {
                    stringBuilder.append("[truncated ").append(offsets.second - maxLength).append(']')
                }
            }
        } else {
            if (srcRef !is ByteArray) {
                return stringBuilder
            }

            var length = contentLength

            if (length < 0) {
                length = srcRef.size
            }

            stringBuilder.append('[').append(length).append(" bytes]")
        }

        return stringBuilder
    }

    protected fun truncate(charSequence: CharSequence, offsets: MutablePair<Int, Int>, maxLength: Int): String {
        truncateOffsets(offsets, charSequence.length)
        val start = offsets.first
        val length = min(offsets.second, maxLength)
        return charSequence.subSequence(start, start + length).toString()
    }

    protected fun truncate(bytes: ByteArray, offsets: MutablePair<Int, Int>, maxLength: Int): String {
        truncateOffsets(offsets, bytes.size)
        val start = offsets.first
        val length = min(offsets.second, maxLength)
        return String(bytes, start, length, Charsets.UTF_8)
    }

    protected fun truncate(chars: CharArray, offsets: MutablePair<Int, Int>, maxLength: Int): String {
        truncateOffsets(offsets, chars.size)
        val start = offsets.first
        val length = min(offsets.second, maxLength)
        return String(chars, start, length)
    }

    protected fun truncateOffsets(offsets: MutablePair<Int, Int>, actualLength: Int) {
        var start = offsets.first
        start = if (start < 0) 0 else if (start >= actualLength) actualLength else start
        offsets.first = start

        val length = offsets.second
        val maxLength = actualLength - start

        if (length < 0 || length > maxLength) {
            offsets.second = maxLength
        }
    }

    protected fun append(stringBuilder: StringBuilder, content: String): Int {
        stringBuilder.apply {
            append('"')

            for (char in content) {
                if (!char.isISOControl() || !appendEscaped(stringBuilder, char)) {
                    append(char)
                }
            }

            append('"')
        }

        return content.length
    }

    protected fun appendEscaped(stringBuilder: StringBuilder, controlChar: Char): Boolean {
        if (controlChar == '\r' || controlChar == '\n') {
            return false
        }

        stringBuilder.apply {
            append("\\u")
            append(CharTypes.hexToChar((controlChar.code shr 12) and 0xF))
            append(CharTypes.hexToChar((controlChar.code shr 8) and 0xF))
            append(CharTypes.hexToChar((controlChar.code shr 4) and 0xF))
            append(CharTypes.hexToChar(controlChar.code and 0xF))
        }

        return true
    }

    companion object {

        /**
         * Constant that may be used when source/target content is not known (or not exposed). Assumed to contain
         * Binary content, meaning that no content snippets will be included.
         */
        val UNKNOWN_CONTENT = ContentReference(false, null, ErrorReportConfiguration.defaults())

        /**
         * As content is redacted by default, a marker reference for slightly different description from "unknown" is
         * used, to indicate explicit removal of source/content reference (as opposed to it missing from not being
         * available or so)
         */
        val REDACTED_CONTENT = ContentReference(false, null, ErrorReportConfiguration.defaults())

        /**
         * Accessor for getting a placeholder for cases where actual content is not known (or is not something that
         * system wants to expose).
         *
         * @return Placeholder "unknown" (or "empty") instance to use instead of `null` reference
         */
        fun unknown() = UNKNOWN_CONTENT

        private fun getTypeName(srcRef: Any): String {
            val srcType = if (srcRef is Class<*>) srcRef else srcRef.javaClass
            val typeName = srcType.name

            return when {
                typeName.startsWith("java.") || typeName.startsWith("kotlin.") -> srcType.simpleName

                srcRef is ByteArray -> "ByteArray"

                srcRef is CharArray -> "CharArray"

                else -> typeName
            }
        }

        fun construct(isContentTextual: Boolean, rawContent: Any?, contentOffset: Int, contentLength: Int,
                errorReportConfiguration: ErrorReportConfiguration): ContentReference {
            return ContentReference(isContentTextual, rawContent, contentOffset, contentLength,
                    errorReportConfiguration)
        }

        fun construct(isContentTextual: Boolean, rawContent: Any?,
                errorReportConfiguration: ErrorReportConfiguration): ContentReference {
            return ContentReference(isContentTextual, rawContent, errorReportConfiguration)
        }

    }

}