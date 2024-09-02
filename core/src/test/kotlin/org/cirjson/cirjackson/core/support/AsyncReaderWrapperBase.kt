package org.cirjson.cirjackson.core.support

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import java.io.StringWriter

abstract class AsyncReaderWrapperBase(val parser: CirJsonParser) : AutoCloseable {

    fun currentToken(): CirJsonToken? {
        return parser.currentToken()
    }

    fun currentText(): String? {
        return parser.text
    }

    fun currentTextViaCharacters(): String {
        val chars = parser.textCharacters!!
        val offset = parser.textOffset
        val length = parser.textLength
        return String(chars, offset, length)
    }

    fun currentTextViaWriter(): String {
        val stringWriter = StringWriter()
        val length = parser.getText(stringWriter)
        val string = stringWriter.toString()

        if (length != string.length) {
            throw IllegalStateException("Reader.getText(Writer) returned $length, but wrote ${string.length} chars")
        }

        return string
    }

    abstract fun nextToken(): CirJsonToken?

    val currentName
        get() = parser.currentName

    val parsingContext
        get() = parser.streamReadContext

    val intValue
        get() = parser.intValue

    val longValue
        get() = parser.longValue

    val bigIntegerValue
        get() = parser.bigIntegerValue

    val floatValue
        get() = parser.floatValue

    val doubleValue
        get() = parser.doubleValue

    val bigDecimalValue
        get() = parser.bigDecimalValue

    val binaryValue
        get() = parser.binaryValue

    val numberValue
        get() = parser.numberValue

    val numberValueDeferred
        get() = parser.numberValueDeferred

    val numberType
        get() = parser.numberType

    override fun close() {
        parser.close()
    }

    val isClosed
        get() = parser.isClosed

}