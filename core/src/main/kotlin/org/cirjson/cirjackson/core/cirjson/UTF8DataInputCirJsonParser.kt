package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.extensions.growBy
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import java.io.*

/**
 * This is a concrete implementation of [CirJsonParser], which is based on a [DataInput] as the input source.
 *
 * Due to limitations in look-ahead (basically there's none), as well as overhead of reading content mostly
 * byte-by-byte, there are some minor differences from regular streaming parsing. Specifically:
 *
 * * Input location offsets not being tracked, as offsets would need to be updated for each read from all over the
 * place. If caller wants this information, it has to track this with [DataInput]. This also affects column number, so
 * the only location information available is the row (line) number (but even that is approximate in case of two-byte
 * linefeeds -- it should work with single CR or LF tho)
 *
 * * No white space validation: checks are simplified NOT to check for control characters.
 *
 * @property mySymbols Symbol table that contains property names encountered so far.
 *
 * @property myNextByte Sometimes we need buffering for just a single byte we read but have to "push back"
 */
open class UTF8DataInputCirJsonParser(objectReadContext: ObjectReadContext, ioContext: IOContext,
        streamReadFeatures: Int, formatReadFeatures: Int, protected var myInputData: DataInput,
        protected val mySymbols: ByteQuadsCanonicalizer, protected var myNextByte: Int) :
        CirJsonParserBase(objectReadContext, ioContext, streamReadFeatures, formatReadFeatures) {

    /**
     * Temporary buffer used for name parsing.
     */
    protected var myQuadBuffer = IntArray(16)

    /**
     * Flag that indicates that the current token has not yet been fully processed, and needs to be finished for some
     * access (or skipped to obtain the next token)
     */
    protected var myIsTokenIncomplete = false

    /**
     * Temporary storage for partially parsed name bytes.
     */
    private var myQuad1 = 0

    /**
     * Temporary input pointer
     */
    private var myQuadPointer = 0

    /*
     *******************************************************************************************************************
     * Overrides for life-cycle
     *******************************************************************************************************************
     */

    override fun releaseBuffered(output: OutputStream): Int {
        return 0
    }

    override fun streamReadInputSource(): Any {
        return myInputData
    }

    /*
     *******************************************************************************************************************
     * Overrides, low-level reading
     *******************************************************************************************************************
     */

    override fun closeInput() {
        // no-op
    }

    /**
     * Method called to release internal buffers owned by the base reader. This may be called along with [closeInput]
     * (for example, when explicitly closing this reader instance), or separately (if need be).
     */
    override fun releaseBuffers() {
        super.releaseBuffers()
        mySymbols.release()
    }

    /*
     *******************************************************************************************************************
     * Public API, data access
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    override val text: String?
        get() = if (myCurrentToken == CirJsonToken.VALUE_STRING) {
            if (myIsTokenIncomplete) {
                myIsTokenIncomplete = false
                finishAndReturnString()
            } else {
                myTextBuffer.contentsAsString()
            }
        } else {
            getText(myCurrentToken)
        }

    @Throws(CirJacksonException::class)
    override fun getText(writer: Writer): Int {
        val token = myCurrentToken ?: return 0

        return try {
            when (token) {
                CirJsonToken.VALUE_STRING -> {
                    if (myIsTokenIncomplete) {
                        myIsTokenIncomplete = false
                        finishString()
                    }

                    myTextBuffer.contentsToWriter(writer)
                }

                CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                    val name = streamReadContext!!.currentName!!
                    writer.write(name)
                    name.length
                }

                else -> {
                    if (token.isNumeric) {
                        myTextBuffer.contentsToWriter(writer)
                    } else {
                        val c = token.charArrayRepresentation!!
                        writer.write(c)
                        c.size
                    }
                }
            }
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @get:Throws(CirJacksonException::class)
    override val valueAsString: String?
        get() = when (myCurrentToken) {
            CirJsonToken.VALUE_STRING -> {
                if (myIsTokenIncomplete) {
                    myIsTokenIncomplete = false
                    finishAndReturnString()
                } else {
                    myTextBuffer.contentsAsString()
                }
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                currentName
            }

            else -> {
                super.getValueAsString(null)
            }
        }

    @Throws(CirJacksonException::class)
    override fun getValueAsString(defaultValue: String?): String? {
        return when (myCurrentToken) {
            CirJsonToken.VALUE_STRING -> {
                if (myIsTokenIncomplete) {
                    myIsTokenIncomplete = false
                    finishAndReturnString()
                } else {
                    myTextBuffer.contentsAsString()
                }
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                currentName
            }

            else -> {
                super.getValueAsString(defaultValue)
            }
        }
    }

    @get:Throws(CirJacksonException::class)
    override val valueAsInt: Int
        get() = if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT ||
                myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
            if (myNumberTypesValid and NUMBER_INT == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseIntValue()
                } else if (myNumberTypesValid and NUMBER_INT == 0) {
                    convertNumberToInt()
                    myNumberInt
                } else {
                    myNumberInt
                }
            } else {
                myNumberInt
            }
        } else {
            super.getValueAsInt(0)
        }

    @Throws(CirJacksonException::class)
    override fun getValueAsInt(defaultValue: Int): Int {
        return if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT ||
                myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
            if (myNumberTypesValid and NUMBER_INT == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseIntValue()
                } else if (myNumberTypesValid and NUMBER_INT == 0) {
                    convertNumberToInt()
                    myNumberInt
                } else {
                    myNumberInt
                }
            } else {
                myNumberInt
            }
        } else {
            super.getValueAsInt(defaultValue)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun getText(token: CirJsonToken?): String? {
        token ?: return null

        return when (token.id) {
            CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME -> {
                streamReadContext!!.currentName
            }

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                myTextBuffer.contentsAsString()
            }

            else -> {
                token.token
            }
        }
    }

    @get:Throws(CirJacksonException::class)
    override val textCharacters: CharArray?
        get() = when (val id = myCurrentToken?.id) {
            null -> null

            CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME -> {
                currentNameInBuffer()
            }

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                if (id == CirJsonTokenId.ID_STRING) {
                    if (myIsTokenIncomplete) {
                        myIsTokenIncomplete = false
                        finishString()
                    }
                }

                myTextBuffer.textBuffer
            }

            else -> {
                myCurrentToken!!.charArrayRepresentation
            }
        }

    @get:Throws(CirJacksonException::class)
    override val textLength: Int
        get() = when (val id = myCurrentToken?.id) {
            null -> 0

            CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME -> {
                streamReadContext!!.currentName!!.length
            }

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                if (id == CirJsonTokenId.ID_STRING) {
                    if (myIsTokenIncomplete) {
                        myIsTokenIncomplete = false
                        finishString()
                    }
                }

                myTextBuffer.size
            }

            else -> {
                myCurrentToken!!.charArrayRepresentation!!.size
            }
        }

    @get:Throws(CirJacksonException::class)
    override val textOffset: Int
        get() = when (val id = myCurrentToken?.id) {
            null -> 0

            CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME -> {
                0
            }

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                if (id == CirJsonTokenId.ID_STRING) {
                    if (myIsTokenIncomplete) {
                        myIsTokenIncomplete = false
                        finishString()
                    }
                }

                myTextBuffer.textOffset
            }

            else -> 0
        }

    @Throws(CirJacksonException::class)
    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray {
        if (myCurrentToken != CirJsonToken.VALUE_STRING && (myCurrentToken != CirJsonToken.VALUE_EMBEDDED_OBJECT ||
                        myBinaryValue == null)) {
            return reportError(
                    "Current token ($myCurrentToken) not VALUE_STRING or VALUE_EMBEDDED_OBJECT, can not access as binary")
        }

        if (myIsTokenIncomplete) {
            try {
                myBinaryValue = decodeBase64(base64Variant)
            } catch (e: IllegalArgumentException) {
                throw constructReadException("Failed to decode VALUE_STRING as base64 ($base64Variant): ${e.message}")
            }

            myIsTokenIncomplete = false
        } else if (myBinaryValue == null) {
            val builder = byteArrayBuilder
            decodeBase64(text!!, builder, base64Variant)
            myBinaryValue = builder.toByteArray()
        }

        return myBinaryValue!!
    }

    @Throws(CirJacksonException::class)
    override fun readBinaryValue(base64Variant: Base64Variant, output: OutputStream): Int {
        if (!myIsTokenIncomplete || myCurrentToken != CirJsonToken.VALUE_STRING) {
            val b = getBinaryValue(base64Variant)

            try {
                output.write(b)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            return b.size
        }

        val buffer = myIOContext!!.allocateBase64Buffer()

        try {
            return readBinary(base64Variant, output, buffer)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        } finally {
            myIOContext.releaseBase64Buffer(buffer)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    protected open fun readBinary(base64Variant: Base64Variant, output: OutputStream, buffer: ByteArray): Int {
        var outputPointer = 0
        val outputEnd = buffer.size - 3
        var outputCount = 0

        while (true) {
            var ch: Int

            do {
                ch = myInputData.readUnsignedByte()
            } while (ch <= CODE_SPACE)

            var bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (ch == CODE_QUOTE) {
                    break
                }

                bits = decodeBase64Escape(base64Variant, ch, 0)

                if (bits < 0) {
                    continue
                }
            }

            if (outputPointer > outputEnd) {
                outputCount += outputPointer

                try {
                    output.write(buffer, 0, outputPointer)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                outputPointer = 0
            }

            var decodedData = bits

            ch = myInputData.readUnsignedByte()
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                bits = decodeBase64Escape(base64Variant, ch, 1)
            }

            decodedData = decodedData shl 6 or bits
            ch = myInputData.readUnsignedByte()
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == CODE_QUOTE) {
                        decodedData = decodedData shr 4
                        buffer[outputPointer++] = decodedData.toByte()

                        if (base64Variant.isRequiringPaddingOnRead) {
                            --myInputPointer
                            return handleBase64MissingPadding(base64Variant)
                        }

                        break
                    }

                    bits = decodeBase64Escape(base64Variant, ch, 2)
                }

                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    ch = myInputData.readUnsignedByte()

                    if (!base64Variant.usesPaddingChar(ch)) {
                        if (decodeBase64Escape(base64Variant, ch, 3) != Base64Variant.BASE64_VALUE_PADDING) {
                            return reportInvalidBase64Char(base64Variant, ch.toChar(), 3,
                                    "expected padding character '${base64Variant.paddingChar}'")
                        }
                    }

                    decodedData = decodedData shr 4
                    buffer[outputPointer++] = decodedData.toByte()
                    continue
                }
            }

            decodedData = decodedData shl 6 or bits
            ch = myInputData.readUnsignedByte()
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == CODE_QUOTE) {
                        decodedData = decodedData shr 2
                        buffer[outputPointer++] = (decodedData shr 8).toByte()
                        buffer[outputPointer++] = decodedData.toByte()

                        if (base64Variant.isRequiringPaddingOnRead) {
                            --myInputPointer
                            return handleBase64MissingPadding(base64Variant)
                        }

                        break
                    }

                    bits = decodeBase64Escape(base64Variant, ch, 3)
                }

                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    decodedData = decodedData shr 2
                    buffer[outputPointer++] = (decodedData shr 8).toByte()
                    buffer[outputPointer++] = decodedData.toByte()
                    continue
                }
            }

            decodedData = decodedData shl 6 or bits
            buffer[outputPointer++] = (decodedData shr 16).toByte()
            buffer[outputPointer++] = (decodedData shr 8).toByte()
            buffer[outputPointer++] = decodedData.toByte()
        }
        myIsTokenIncomplete = false

        if (outputPointer > 0) {
            outputCount += outputPointer
            output.write(buffer, 0, outputPointer)
        }

        return outputCount
    }

    /*
     *******************************************************************************************************************
     * Public API, traversal, basic
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextToken(): CirJsonToken? {
        if (isClosed) {
            return null
        }

        return try {
            if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                    myCurrentToken == CirJsonToken.PROPERTY_NAME) {
                nextAfterName()
            } else {
                nextTokenInternal()
            }
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun nextTokenInternal(): CirJsonToken? {
        myNumberTypesValid = NUMBER_UNKNOWN

        if (myIsTokenIncomplete) {
            skipString()
        }

        var i = skipWhitespaceOrEnd()

        if (i < 0) {
            close()
            myCurrentToken = null
            return null
        }

        myBinaryValue = null
        tokenLineNumber = myCurrentInputRow

        if (i or 0x20 == CODE_R_CURLY) {
            closeScope(i)
            return myCurrentToken
        }

        if (streamReadContext!!.isExpectingComma) {
            if (i != CODE_COMMA) {
                return reportUnexpectedChar(i.toChar(),
                        "was expecting comma to separate ${streamReadContext!!.typeDescription} entries")
            }

            i = skipWhitespace()
            tokenLineNumber = myCurrentInputRow

            if (isEnabled(CirJsonReadFeature.ALLOW_TRAILING_COMMA)) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return myCurrentToken
                }
            }
        }

        if (!streamReadContext!!.isInObject) {
            return nextTokenNotInObject(i)
        }

        val name = parseName(i)
        streamReadContext!!.currentName = name
        myCurrentToken = if (myCurrentToken == CirJsonToken.START_OBJECT) {
            if (name != idName) {
                return reportInvalidToken(i, "Expected property name '$idName', received '$name'")
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME
        } else {
            CirJsonToken.PROPERTY_NAME
        }

        i = skipColon()

        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            myNextToken = CirJsonToken.VALUE_STRING
            return myCurrentToken
        }

        val token = when (i) {
            '-'.code -> {
                parseNegNumber()
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parsePosNumber()
                } else {
                    handleUnexpectedValue(i)
                }
            }

            '.'.code -> {
                parseFloatThatStartsWithPeriod(false)
            }

            '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                parseUnsignedNumber(i)
            }

            't'.code -> {
                matchToken("true", 1)
                CirJsonToken.VALUE_TRUE
            }

            'f'.code -> {
                matchToken("false", 1)
                CirJsonToken.VALUE_FALSE
            }

            'n'.code -> {
                matchToken("null", 1)
                CirJsonToken.VALUE_NULL
            }

            '['.code -> {
                CirJsonToken.START_ARRAY
            }

            '{'.code -> {
                CirJsonToken.START_OBJECT
            }

            else -> {
                handleUnexpectedValue(i)
            }
        }

        myNextToken = token
        return myCurrentToken
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun nextTokenNotInObject(i: Int): CirJsonToken? {
        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            return CirJsonToken.VALUE_STRING.also { myCurrentToken = it }
        }

        return when (i) {
            '['.code -> {
                streamReadContext = streamReadContext!!.createChildArrayContext(tokenLineNumber, myTokenInputColumn)
                CirJsonToken.START_ARRAY.also { myCurrentToken = it }
            }

            '{'.code -> {
                streamReadContext = streamReadContext!!.createChildObjectContext(tokenLineNumber, myTokenInputColumn)
                CirJsonToken.START_OBJECT.also { myCurrentToken = it }
            }

            't'.code -> {
                matchToken("true", 1)
                CirJsonToken.VALUE_TRUE.also { myCurrentToken = it }
            }

            'f'.code -> {
                matchToken("false", 1)
                CirJsonToken.VALUE_FALSE.also { myCurrentToken = it }
            }

            'n'.code -> {
                matchToken("null", 1)
                CirJsonToken.VALUE_NULL.also { myCurrentToken = it }
            }

            '-'.code -> {
                parseNegNumber().also { myCurrentToken = it }
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parsePosNumber().also { myCurrentToken = it }
                } else {
                    handleUnexpectedValue(i).also { myCurrentToken = it }
                }
            }

            '.'.code -> {
                parseFloatThatStartsWithPeriod(false).also { myCurrentToken = it }
            }

            '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                parseUnsignedNumber(i).also { myCurrentToken = it }
            }

            else -> {
                handleUnexpectedValue(i).also { myCurrentToken = it }
            }
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun nextAfterName(): CirJsonToken? {
        myIsNameCopied = false
        val token = myNextToken
        myNextToken = null

        if (token == CirJsonToken.START_ARRAY) {
            createChildArrayContext(tokenLineNumber, myTokenInputColumn)
        } else if (token == CirJsonToken.START_OBJECT) {
            createChildObjectContext(tokenLineNumber, myTokenInputColumn)
        }

        return token.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    override fun finishToken() {
        if (myIsTokenIncomplete) {
            myIsTokenIncomplete = false
            finishString()
        }
    }

    @Throws(CirJacksonException::class)
    override fun nextName(): String? {
        return try {
            nextNameInternal()
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun nextNameInternal(): String? {
        myNumberTypesValid = NUMBER_UNKNOWN

        if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            nextAfterName()
            return null
        }

        if (myIsTokenIncomplete) {
            skipString()
        }

        var i = skipWhitespaceOrEnd()
        myBinaryValue = null
        tokenLineNumber = myCurrentInputRow

        if (i or 0x20 == CODE_R_CURLY) {
            closeScope(i)
            return null
        }

        if (streamReadContext!!.isExpectingComma) {
            if (i != CODE_COMMA) {
                return reportUnexpectedChar(i.toChar(),
                        "was expecting comma to separate ${streamReadContext!!.typeDescription} entries")
            }

            i = skipWhitespace()

            if (isEnabled(CirJsonReadFeature.ALLOW_TRAILING_COMMA)) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return null
                }
            }
        }

        if (!streamReadContext!!.isInObject) {
            nextTokenNotInObject(i)
            return null
        }

        val name = parseName(i)
        streamReadContext!!.currentName = name
        myCurrentToken = if (myCurrentToken == CirJsonToken.START_OBJECT) {
            if (name != idName) {
                return reportInvalidToken(i, "Expected property name '$idName', received '$name'")
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME
        } else {
            CirJsonToken.PROPERTY_NAME
        }

        i = skipColon()

        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            myNextToken = CirJsonToken.VALUE_STRING
            return name
        }

        val token = when (i) {
            '-'.code -> {
                parseNegNumber()
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parsePosNumber()
                } else {
                    handleUnexpectedValue(i)
                }
            }

            '.'.code -> {
                parseFloatThatStartsWithPeriod(false)
            }

            '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                parseUnsignedNumber(i)
            }

            't'.code -> {
                matchToken("true", 1)
                CirJsonToken.VALUE_TRUE
            }

            'f'.code -> {
                matchToken("false", 1)
                CirJsonToken.VALUE_FALSE
            }

            'n'.code -> {
                matchToken("null", 1)
                CirJsonToken.VALUE_NULL
            }

            '['.code -> {
                CirJsonToken.START_ARRAY
            }

            '{'.code -> {
                CirJsonToken.START_OBJECT
            }

            else -> {
                handleUnexpectedValue(i)
            }
        }

        myNextToken = token
        return name
    }

    @Throws(CirJacksonException::class)
    override fun nextTextValue(): String? {
        if (myCurrentToken != CirJsonToken.PROPERTY_NAME && myCurrentToken != CirJsonToken.CIRJSON_ID_PROPERTY_NAME) {
            return if (nextToken() == CirJsonToken.VALUE_STRING) text else null
        }

        myIsNameCopied = false
        val token = myNextToken
        myNextToken = null
        myCurrentToken = token

        if (token == CirJsonToken.VALUE_STRING) {
            return if (myIsTokenIncomplete) {
                myIsTokenIncomplete = false
                finishAndReturnString()
            } else {
                myTextBuffer.contentsAsString()
            }
        }

        if (token == CirJsonToken.START_ARRAY) {
            createChildArrayContext(tokenLineNumber, myTokenInputColumn)
        } else if (token == CirJsonToken.START_OBJECT) {
            createChildObjectContext(tokenLineNumber, myTokenInputColumn)
        }

        return null
    }

    @Throws(CirJacksonException::class)
    override fun nextIntValue(defaultValue: Int): Int {
        if (myCurrentToken != CirJsonToken.PROPERTY_NAME) {
            return if (nextToken() == CirJsonToken.VALUE_NUMBER_INT) intValue else defaultValue
        }

        myIsNameCopied = false
        val token = myNextToken
        myNextToken = null
        myCurrentToken = token

        if (token == CirJsonToken.VALUE_NUMBER_INT) {
            return intValue
        }

        if (token == CirJsonToken.START_ARRAY) {
            createChildArrayContext(tokenLineNumber, myTokenInputColumn)
        } else if (token == CirJsonToken.START_OBJECT) {
            createChildObjectContext(tokenLineNumber, myTokenInputColumn)
        }

        return defaultValue
    }

    @Throws(CirJacksonException::class)
    override fun nextLongValue(defaultValue: Long): Long {
        if (myCurrentToken != CirJsonToken.PROPERTY_NAME) {
            return if (nextToken() == CirJsonToken.VALUE_NUMBER_INT) longValue else defaultValue
        }

        myIsNameCopied = false
        val token = myNextToken
        myNextToken = null
        myCurrentToken = token

        if (token == CirJsonToken.VALUE_NUMBER_INT) {
            return longValue
        }

        if (token == CirJsonToken.START_ARRAY) {
            createChildArrayContext(tokenLineNumber, myTokenInputColumn)
        } else if (token == CirJsonToken.START_OBJECT) {
            createChildObjectContext(tokenLineNumber, myTokenInputColumn)
        }

        return defaultValue
    }

    @Throws(CirJacksonException::class)
    override fun nextBooleanValue(): Boolean? {
        if (myCurrentToken != CirJsonToken.PROPERTY_NAME) {
            return when (nextToken()?.id) {
                CirJsonTokenId.ID_TRUE -> true
                CirJsonTokenId.ID_FALSE -> false
                else -> null
            }
        }

        myIsNameCopied = false
        val token = myNextToken
        myNextToken = null
        myCurrentToken = token

        if (token == CirJsonToken.VALUE_TRUE) {
            return true
        }

        if (token == CirJsonToken.VALUE_FALSE) {
            return false
        }

        if (token == CirJsonToken.START_ARRAY) {
            createChildArrayContext(tokenLineNumber, myTokenInputColumn)
        } else if (token == CirJsonToken.START_OBJECT) {
            createChildObjectContext(tokenLineNumber, myTokenInputColumn)
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Internal methods, number parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class, IOException::class)
    protected fun parseFloatThatStartsWithPeriod(negative: Boolean): CirJsonToken? {
        if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
            return handleUnexpectedValue(CODE_PERIOD)
        }

        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = 0

        if (negative) {
            outputBuffer[outputPointer++] = '-'
        }

        return parseFloat(outputBuffer, outputPointer, CODE_PERIOD, negative, 0)
    }

    /**
     * Initial parsing method for number values. It needs to be able to parse enough input to be able to determine
     * whether the value is to be considered a simple integer value, or a more generic decimal value: latter of which
     * needs to be expressed as a floating point number. The basic rule is that if the number has no fractional or
     * exponential part, it is an integer; otherwise a floating point number.
     *
     * Because much of input has to be processed in any case, no partial parsing is done: all input text will be stored
     * for further processing. However, actual numeric value conversion will be deferred, since it is usually the most
     * complicated and costliest part of processing.
     *
     * @param code The first non-null digit character of the number to parse
     *
     * @return Type of token decoded, usually [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT]
     *
     * @throws IOException for low-level I/O
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected open fun parseUnsignedNumber(code: Int): CirJsonToken? {
        var c = code
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()

        var outputPointer = if (c == CODE_0) {
            c = handleLeadingZeroes()

            if (c in CODE_0..CODE_9) {
                0
            } else if (c or 0x20 == 'x'.code) {
                return handleInvalidNumberStart(c, false)
            } else {
                outputBuffer[0] = '0'
                1
            }
        } else {
            outputBuffer[0] = c.toChar()
            c = myInputData.readUnsignedByte()
            1
        }

        var integralLength = outputPointer

        while (c in CODE_0..CODE_9) {
            ++integralLength

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()
            c = myInputData.readUnsignedByte()
        }

        if (c == CODE_PERIOD || c or 0x20 == CODE_E_LOWERCASE) {
            return parseFloat(outputBuffer, outputPointer, c, false, integralLength)
        }

        myTextBuffer.currentSegmentSize = outputPointer
        myNextByte = c

        if (streamReadContext!!.isInRoot) {
            verifyRootSpace()
        }

        return resetInt(false, integralLength)
    }

    @Throws(CirJacksonException::class, IOException::class)
    protected fun parsePosNumber(): CirJsonToken? {
        return parseSignedNumber(false)
    }

    @Throws(CirJacksonException::class, IOException::class)
    protected fun parseNegNumber(): CirJsonToken? {
        return parseSignedNumber(true)
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun parseSignedNumber(negative: Boolean): CirJsonToken? {
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = 0

        if (negative) {
            outputBuffer[outputPointer++] = '-'
        }

        var c = myInputData.readUnsignedByte()
        outputBuffer[outputPointer++] = c.toChar()

        if (c <= CODE_0) {
            if (c != CODE_0) {
                return if (c == CODE_PERIOD) {
                    parseFloatThatStartsWithPeriod(negative)
                } else {
                    handleInvalidNumberStart(c, negative, true)
                }
            }

            c = handleLeadingZeroes()
        } else if (c > CODE_9) {
            return handleInvalidNumberStart(c, negative, true)
        } else {
            c = myInputData.readUnsignedByte()
        }

        var integralLength = 1

        while (c in CODE_0..CODE_9) {
            ++integralLength

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()
            c = myInputData.readUnsignedByte()
        }

        if (c == CODE_PERIOD || c or 0x20 == CODE_E_LOWERCASE) {
            return parseFloat(outputBuffer, outputPointer, c, negative, integralLength)
        }

        myTextBuffer.currentSegmentSize = outputPointer
        myNextByte = c

        if (streamReadContext!!.isInRoot) {
            verifyRootSpace()
        }

        return resetInt(negative, integralLength)
    }

    /**
     * Method called when we have seen one zero, and want to ensure it is not followed by another, or, if leading zeroes
     * allowed, skipped redundant ones.
     *
     * @return Character immediately following zeroes
     *
     * @throws IOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class, IOException::class)
    private fun handleLeadingZeroes(): Int {
        var ch = myInputData.readUnsignedByte()

        if (ch !in CODE_0..CODE_9) {
            return ch
        }

        if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)) {
            return reportInvalidNumber("Leading zeroes not allowed")
        }

        while (ch == CODE_0) {
            ch = myInputData.readUnsignedByte()
        }

        return ch
    }

    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    private fun parseFloat(outputBuffer: CharArray, outputPointer: Int, code: Int, negative: Boolean,
            integralLength: Int): CirJsonToken? {
        var outputBuffer = outputBuffer
        var outputPointer = outputPointer
        var c = code
        var fractionLength = 0

        if (c == CODE_PERIOD) {
            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()

            while (true) {
                c = myInputData.readUnsignedByte()

                if (c !in CODE_0..CODE_9) {
                    break
                }

                ++fractionLength

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c.toChar()
            }

            if (fractionLength == 0) {
                if (!isEnabled(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)) {
                    return reportUnexpectedNumberChar(c.toChar(), "Decimal point not followed by a digit")
                }
            }
        }

        var exponentLength = 0

        if (c or 0x20 == CODE_E_LOWERCASE) {
            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()
            c = myInputData.readUnsignedByte()

            if (c == CODE_MINUS || c == CODE_PLUS) {
                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c.toChar()
                c = myInputData.readUnsignedByte()
            }

            while (c in CODE_0..CODE_9) {
                ++exponentLength

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c.toChar()
                c = myInputData.readUnsignedByte()
            }

            if (exponentLength == 0) {
                return reportUnexpectedNumberChar(c.toChar(), "Exponent indicator not followed by a digit")
            }
        }

        myNextByte = c

        if (streamReadContext!!.isInRoot) {
            verifyRootSpace()
        }

        myTextBuffer.currentSegmentSize = outputPointer
        return resetFloat(negative, integralLength, fractionLength, exponentLength)
    }

    /**
     * Method called to ensure that a root-value is followed by a space token, if possible.
     *
     * NOTE: with [DataInput] source, not really feasible, up-front. If we did want, we could rearrange things to
     * require space before next read, but initially let's just do nothing.
     */
    @Throws(CirJacksonException::class)
    private fun verifyRootSpace() {
        val ch = myNextByte

        if (ch > CODE_SPACE) {
            return reportMissingRootWhiteSpace(ch.toChar())
        }

        myNextByte = -1

        if (ch == CODE_CR || ch == CODE_LF) {
            ++myCurrentInputRow
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, secondary parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    protected fun parseName(i: Int): String? {
        var i = i

        if (i != CODE_QUOTE) {
            return handleOddName(i)
        }

        val codes = INPUT_CODE_LATIN1

        var q = myInputData.readUnsignedByte()

        return if (codes[q] == 0) {
            i = myInputData.readUnsignedByte()
            if (codes[i] == 0) {
                q = q shl 8 or i
                i = myInputData.readUnsignedByte()

                if (codes[i] == 0) {
                    q = q shl 8 or i
                    i = myInputData.readUnsignedByte()

                    if (codes[i] == 0) {
                        q = q shl 8 or i
                        i = myInputData.readUnsignedByte()

                        if (codes[i] == 0) {
                            myQuad1 = q
                            parseMediumName(i)
                        } else if (q == CODE_QUOTE) {
                            findName(q, 4)
                        } else {
                            parseName(q, i, 4)
                        }
                    } else if (q == CODE_QUOTE) {
                        findName(q, 3)
                    } else {
                        parseName(q, i, 3)
                    }
                } else if (q == CODE_QUOTE) {
                    findName(q, 2)
                } else {
                    parseName(q, i, 2)
                }
            } else if (q == CODE_QUOTE) {
                findName(q, 1)
            } else {
                parseName(q, i, 1)
            }
        } else if (q == CODE_QUOTE) {
            ""
        } else {
            parseName(0, q, 0)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun parseMediumName(quad2: Int): String? {
        var q2 = quad2
        val codes = INPUT_CODE_LATIN1

        var i = myInputData.readUnsignedByte()
        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 1)
            } else {
                parseName(myQuad1, q2, i, 1)
            }
        }

        q2 = q2 shl 8 or i
        i = myInputData.readUnsignedByte()

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 2)
            } else {
                parseName(myQuad1, q2, i, 2)
            }
        }

        q2 = q2 shl 8 or i
        i = myInputData.readUnsignedByte()

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 3)
            } else {
                parseName(myQuad1, q2, i, 3)
            }
        }

        q2 = q2 shl 8 or i
        i = myInputData.readUnsignedByte()

        return if (codes[i] != 0) {
            if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 4)
            } else {
                parseName(myQuad1, q2, i, 4)
            }
        } else {
            parseMediumName(i, q2)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun parseMediumName(quad3: Int, q2: Int): String? {
        var q3 = quad3
        val codes = INPUT_CODE_LATIN1

        var i = myInputData.readUnsignedByte()

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 1)
            } else {
                parseName(myQuad1, q2, q3, i, 1)
            }
        }

        q3 = q3 shl 8 or i
        i = myInputData.readUnsignedByte()

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 2)
            } else {
                parseName(myQuad1, q2, q3, i, 2)
            }
        }

        q3 = q3 shl 8 or i
        i = myInputData.readUnsignedByte()

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 3)
            } else {
                parseName(myQuad1, q2, q3, i, 3)
            }
        }

        q3 = q3 shl 8 or i
        i = myInputData.readUnsignedByte()

        return if (codes[i] != 0) {
            if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 4)
            } else {
                parseName(myQuad1, q2, q3, i, 4)
            }
        } else {
            parseLongName(i, q2, q3)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    fun parseLongName(quad: Int, q2: Int, q3: Int): String? {
        var q = quad

        myQuadBuffer[0] = myQuad1
        myQuadBuffer[1] = q2
        myQuadBuffer[2] = q3

        val codes = INPUT_CODE_LATIN1
        var quadLength = 3

        while (true) {
            var i = myInputData.readUnsignedByte()

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 1)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 1)
                }
            }

            q = q shl 8 or i
            i = myInputData.readUnsignedByte()

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 2)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 2)
                }
            }

            q = q shl 8 or i
            i = myInputData.readUnsignedByte()

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 3)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 3)
                }
            }

            q = q shl 8 or i
            i = myInputData.readUnsignedByte()

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 4)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 4)
                }
            }

            if (quadLength >= myQuadBuffer.size) {
                myQuadBuffer = myQuadBuffer.growBy(quadLength)
            }

            myQuadBuffer[quadLength++] = q
            q = i
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun parseName(q1: Int, code: Int, lastQuadBytes: Int): String? {
        return parseEscapedName(myQuadBuffer, 0, q1, code, lastQuadBytes)
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun parseName(q1: Int, q2: Int, code: Int, lastQuadBytes: Int): String? {
        myQuadBuffer[0] = q1
        return parseEscapedName(myQuadBuffer, 1, q2, code, lastQuadBytes)
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun parseName(q1: Int, q2: Int, q3: Int, code: Int, lastQuadBytes: Int): String? {
        myQuadBuffer[0] = q1
        myQuadBuffer[1] = q2
        return parseEscapedName(myQuadBuffer, 2, q3, code, lastQuadBytes)
    }

    /**
     * Slower parsing method which is generally branched to when an escape sequence is detected (or alternatively for
     * long names, one crossing input buffer boundary). Needs to be able to handle more exceptional cases, gets slower,
     * and hence is offlined to a separate method.
     */
    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    protected fun parseEscapedName(quads: IntArray, quadLength: Int, currentQuad: Int, code: Int,
            currentQuadBytes: Int): String? {
        var quads = quads
        var quadLength = quadLength
        var currentQuad = currentQuad
        var ch = code
        var currentQuadBytes = currentQuadBytes

        while (true) {
            if (INPUT_CODE_LATIN1[ch] != 0) {
                if (ch == CODE_QUOTE) {
                    break
                }

                if (ch != CODE_BACKSLASH) {
                    throwUnquotedSpace(ch, "name")
                } else {
                    ch = decodeEscaped().code
                }

                if (ch > 127) {
                    if (currentQuadBytes >= 4) {
                        if (quadLength >= quads.size) {
                            quads = growNameDecodeBuffer(quads, quads.size)
                            myQuadBuffer = quads
                        }

                        quads[quadLength++] = currentQuad
                        currentQuad = 0
                        currentQuadBytes = 0
                    }

                    if (ch < 0x800) {
                        currentQuad = currentQuad shl 8 or (ch shr 6) or 0xC0
                    } else {
                        currentQuad = currentQuad shl 8 or (ch shr 12) or 0xE0
                        ++currentQuadBytes

                        if (currentQuadBytes >= 4) {
                            if (quadLength >= quads.size) {
                                quads = growNameDecodeBuffer(quads, quads.size)
                                myQuadBuffer = quads
                            }

                            quads[quadLength++] = currentQuad
                            currentQuad = 0
                            currentQuadBytes = 0
                        }

                        currentQuad = currentQuad shl 8 or (ch shr 6 and 0x3F) or 0x80
                    }

                    ++currentQuadBytes
                    ch = ch and 0x3F or 0x80
                }
            }

            if (currentQuadBytes < 4) {
                ++currentQuadBytes
                currentQuad = currentQuad shl 8 or ch
            } else {
                if (quadLength >= quads.size) {
                    quads = growNameDecodeBuffer(quads, quads.size)
                    myQuadBuffer = quads
                }

                quads[quadLength++] = currentQuad
                currentQuad = ch
                currentQuadBytes = 1
            }

            ch = myInputData.readUnsignedByte()
        }

        if (currentQuadBytes > 0) {
            if (quadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            quads[quadLength++] = padLastQuad(currentQuad, currentQuadBytes)
        }

        return mySymbols.findName(quads, quadLength) ?: addName(quads, quadLength, currentQuadBytes)
    }

    /**
     * Method called when we see non-white space character other than double quote, when expecting a property name. In
     * standard mode will just throw an exception; but in non-standard modes may be able to parse name.
     *
     * @param code First undecoded character of possible "odd name" to decode
     *
     * @return Name decoded, if allowed and successful
     *
     * @throws IOException for low-level I/O problem
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected open fun handleOddName(code: Int): String? {
        var ch = code

        if (ch == CODE_APOSTROPHE && isEnabled(CirJsonReadFeature.ALLOW_SINGLE_QUOTES)) {
            return parseApostropheName()
        }

        if (!isEnabled(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)) {
            val c = decodeCharForError(ch).toChar()
            return reportUnexpectedChar(c, "was expecting double-quote to start property name")
        }

        val codes = INPUT_CODE_UTF8_JS_NAMES

        if (codes[ch] != 0) {
            return reportUnexpectedChar(ch.toChar(),
                    "was expecting either valid name character (for unquoted name) or double-quote (for quoted) to start property name")
        }

        var quads = myQuadBuffer
        var quadLength = 0
        var currentQuad = 0
        var currentQuadBytes = 0

        do {
            if (currentQuadBytes < 4) {
                ++currentQuadBytes
                currentQuad = currentQuad shl 8 or ch
            } else {
                if (quadLength >= quads.size) {
                    quads = growNameDecodeBuffer(quads, quads.size)
                    myQuadBuffer = quads
                }

                quads[quadLength++] = currentQuad
                currentQuad = ch
                currentQuadBytes = 1
            }

            ch = myInputData.readUnsignedByte()
        } while (codes[ch] == 0)

        myNextByte = ch

        if (currentQuadBytes > 0) {
            if (quadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            quads[quadLength++] = padLastQuad(currentQuad, currentQuadBytes)
        }

        return mySymbols.findName(quads, quadLength) ?: addName(quads, quadLength, currentQuadBytes)
    }

    /**
     * Parsing to support apostrophe-quoted names. Plenty of duplicated code; main reason being to try to avoid slowing
     * down fast path for valid CirJSON -- more alternatives, more code, generally a bit slower execution.
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected open fun parseApostropheName(): String? {
        var ch = myInputData.readUnsignedByte()

        if (ch == CODE_APOSTROPHE) {
            return ""
        }

        var quads = myQuadBuffer
        var quadLength = 0
        var currentQuad = 0
        var currentQuadBytes = 0

        while (ch != CODE_APOSTROPHE) {
            if (INPUT_CODE_LATIN1[ch] != 0 && ch != CODE_QUOTE) {
                if (ch != CODE_BACKSLASH) {
                    throwUnquotedSpace(ch, "name")
                } else {
                    ch = decodeEscaped().code
                }

                if (ch > 127) {
                    if (currentQuadBytes >= 4) {
                        if (quadLength >= quads.size) {
                            quads = growNameDecodeBuffer(quads, quads.size)
                            myQuadBuffer = quads
                        }

                        quads[quadLength++] = currentQuad
                        currentQuad = 0
                        currentQuadBytes = 0
                    }

                    if (ch < 0x800) {
                        currentQuad = currentQuad shl 8 or (ch shr 6) or 0xC0
                    } else {
                        currentQuad = currentQuad shl 8 or (ch shr 12) or 0xE0
                        ++currentQuadBytes

                        if (currentQuadBytes >= 4) {
                            if (quadLength >= quads.size) {
                                quads = growNameDecodeBuffer(quads, quads.size)
                                myQuadBuffer = quads
                            }

                            quads[quadLength++] = currentQuad
                            currentQuad = 0
                            currentQuadBytes = 0
                        }

                        currentQuad = currentQuad shl 8 or (ch shr 6 and 0x3F) or 0x80
                    }

                    ++currentQuadBytes
                    ch = ch and 0x3F or 0x80
                }
            }

            if (currentQuadBytes < 4) {
                ++currentQuadBytes
                currentQuad = currentQuad shl 8 or ch
            } else {
                if (quadLength >= quads.size) {
                    quads = growNameDecodeBuffer(quads, quads.size)
                    myQuadBuffer = quads
                }

                quads[quadLength++] = currentQuad
                currentQuad = ch
                currentQuadBytes = 1
            }

            ch = myInputData.readUnsignedByte()
        }

        if (currentQuadBytes > 0) {
            if (quadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            quads[quadLength++] = padLastQuad(currentQuad, currentQuadBytes)
        }

        return mySymbols.findName(quads, quadLength) ?: addName(quads, quadLength, currentQuadBytes)
    }

    /*
     *******************************************************************************************************************
     * Internal methods, symbol (name) handling
     *******************************************************************************************************************
     */

    @Throws(StreamReadException::class)
    private fun findName(quad1: Int, lastQuadBytes: Int): String? {
        val q1 = padLastQuad(quad1, lastQuadBytes)

        val name = mySymbols.findName(q1)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1
        return addName(myQuadBuffer, 1, lastQuadBytes)
    }

    @Throws(StreamReadException::class)
    private fun findName(q1: Int, quad2: Int, lastQuadBytes: Int): String? {
        val q2 = padLastQuad(quad2, lastQuadBytes)

        val name = mySymbols.findName(q1, q2)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1
        myQuadBuffer[1] = q2
        return addName(myQuadBuffer, 2, lastQuadBytes)
    }

    @Throws(StreamReadException::class)
    private fun findName(q1: Int, q2: Int, quad3: Int, lastQuadBytes: Int): String? {
        val q3 = padLastQuad(quad3, lastQuadBytes)

        val name = mySymbols.findName(q1, q2, q3)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1
        myQuadBuffer[1] = q2
        myQuadBuffer[2] = padLastQuad(q3, lastQuadBytes)
        return addName(myQuadBuffer, 3, lastQuadBytes)
    }

    @Throws(StreamReadException::class)
    private fun findName(quads: IntArray, quadLength: Int, lastQuad: Int, lastQuadBytes: Int): String? {
        var realQuads = quads
        var realQuadLength = quadLength

        if (realQuadLength >= realQuads.size) {
            realQuads = growNameDecodeBuffer(realQuads, realQuads.size)
            myQuadBuffer = realQuads
        }

        realQuads[realQuadLength++] = padLastQuad(lastQuad, lastQuadBytes)

        return mySymbols.findName(realQuads, realQuadLength) ?: addName(realQuads, realQuadLength, lastQuadBytes)
    }

    /**
     * This is the main workhorse method used when we take a symbol table miss. It needs to demultiplex individual
     * bytes, decode multibyte chars (if any), and then construct Name instance and add it to the symbol table.
     */
    @Throws(StreamReadException::class)
    private fun addName(quads: IntArray, quadLength: Int, lastQuadBytes: Int): String? {
        val byteLength = (quadLength shl 2) - 4 + lastQuadBytes

        val lastQuad: Int

        if (lastQuadBytes < 4) {
            lastQuad = quads[quadLength - 1]
            quads[quadLength - 1] = lastQuad shl (4 - lastQuadBytes shl 3)
        } else {
            lastQuad = 0
        }

        var charBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var charIndex = 0
        var index = 0

        while (index < byteLength) {
            var ch = quads[index shr 2]
            var byteIndex = index and 3
            ch = ch shr (3 - byteIndex shl 3) and 0xFF
            ++index

            if (ch > 127) {
                val needed = when {
                    ch and 0xE0 == 0xC0 -> {
                        ch = ch and 0x1F
                        1
                    }

                    ch and 0xF0 == 0xE0 -> {
                        ch = ch and 0x0F
                        2
                    }

                    ch and 0xF8 == 0xF0 -> {
                        ch = ch and 0x07
                        3
                    }

                    else -> {
                        reportInvalidInitial(ch)
                    }
                }

                if (index + needed > byteLength) {
                    return reportInvalidEOF("in property name", CirJsonToken.PROPERTY_NAME)
                }

                var ch2 = quads[index shr 2]
                byteIndex = index and 3
                ch2 = ch2 shr (3 - byteIndex shl 3) and 0xFF
                ++index

                if (ch2 and 0xC0 != 0x080) {
                    return reportInvalidOther(ch2)
                }

                ch = ch shl 6 or (ch2 and 0x3F)

                if (needed > 1) {
                    ch2 = quads[index shr 2]
                    byteIndex = index and 3
                    ch2 = ch2 shr (3 - byteIndex shl 3) and 0xFF
                    ++index

                    if (ch2 and 0xC0 != 0x080) {
                        return reportInvalidOther(ch2)
                    }

                    ch = ch shl 6 or (ch2 and 0x3F)

                    if (needed > 2) {
                        ch2 = quads[index shr 2]
                        byteIndex = index and 3
                        ch2 = ch2 shr (3 - byteIndex shl 3) and 0xFF
                        ++index

                        if (ch2 and 0xC0 != 0x080) {
                            return reportInvalidOther(ch2)
                        }

                        ch = ch shl 6 or (ch2 and 0x3F)
                    }
                }

                if (needed > 2) {
                    ch -= 0x10000

                    if (charIndex >= charBuffer.size) {
                        charBuffer = myTextBuffer.expandCurrentSegment()
                    }

                    charBuffer[charIndex++] = ((ch shr 10) + 0x0800).toChar()
                    ch = ch and 0x03FF or 0xDC00
                }
            }

            if (charIndex >= charBuffer.size) {
                charBuffer = myTextBuffer.expandCurrentSegment()
            }

            charBuffer[charIndex++] = ch.toChar()
        }

        val baseName = String(charBuffer, 0, charIndex)

        if (!mySymbols.isCanonicalizing) {
            return baseName
        }

        if (lastQuadBytes < 4) {
            quads[quadLength - 1] = lastQuad
        }

        return mySymbols.addName(baseName, quads, quadLength)
    }

    /*
     *******************************************************************************************************************
     * Internal methods, String value parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun finishString() {
        var outputPointer = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        val outputEnd = outputBuffer.size

        try {
            do {
                val c = myInputData.readUnsignedByte()

                if (INPUT_CODE_UTF8[c] != 0) {
                    if (c == CODE_QUOTE) {
                        myTextBuffer.currentSegmentSize = outputPointer
                    } else {
                        finishString(outputBuffer, outputPointer, c)
                    }

                    return
                }

                outputBuffer[outputPointer++] = c.toChar()
            } while (outputPointer < outputEnd)

            finishString(outputBuffer, outputPointer, myInputData.readUnsignedByte())
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class)
    private fun finishAndReturnString(): String {
        var outputPointer = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        val outputEnd = outputBuffer.size

        try {
            do {
                val c = myInputData.readUnsignedByte()

                if (INPUT_CODE_UTF8[c] != 0) {
                    return if (c == CODE_QUOTE) {
                        myTextBuffer.setCurrentAndReturn(outputPointer)
                    } else {
                        finishString(outputBuffer, outputPointer, c)
                        myTextBuffer.contentsAsString()
                    }
                }

                outputBuffer[outputPointer++] = c.toChar()
            } while (outputPointer < outputEnd)

            finishString(outputBuffer, outputPointer, myInputData.readUnsignedByte())
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return myTextBuffer.contentsAsString()
    }

    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    private fun finishString(outputBuffer: CharArray, outputPointer: Int, code: Int) {
        var outputBuffer = outputBuffer
        var outputPointer = outputPointer
        var c = code

        val codes = INPUT_CODE_UTF8
        var outputEnd = outputBuffer.size

        while (true) {
            while (codes[c] == 0) {
                if (outputPointer >= outputEnd) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                    outputEnd = outputBuffer.size
                }

                outputBuffer[outputPointer++] = c.toChar()
                c = myInputData.readUnsignedByte()
            }

            if (c == CODE_QUOTE) {
                break
            }

            when (codes[c]) {
                1 -> {
                    c = decodeEscaped().code
                }

                2 -> {
                    c = decodeUTF8V2(c)
                }

                3 -> {
                    c = decodeUTF8V3(c)
                }

                4 -> {
                    c = decodeUTF8V4(c)

                    if (outputPointer >= outputEnd) {
                        outputBuffer = myTextBuffer.finishCurrentSegment()
                        outputPointer = 0
                        outputEnd = outputBuffer.size
                    }

                    outputBuffer[outputPointer++] = (c shr 10 or 0xD800).toChar()
                    c = c and 0x3FF or 0xDC00
                }

                else -> {
                    if (c < CODE_SPACE) {
                        throwUnquotedSpace(c, "string value")
                    } else {
                        return reportInvalidChar(c)
                    }
                }
            }

            if (outputPointer >= outputEnd) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
                outputEnd = outputBuffer.size
            }

            outputBuffer[outputPointer++] = c.toChar()

            c = myInputData.readUnsignedByte()
        }

        myTextBuffer.currentSegmentSize = outputPointer
    }

    /**
     * Method called to skim through rest of unparsed String value, if it is not needed. This can be done a bit faster
     * if contents need not be stored for future access.
     *
     * @throws IOException for low-level I/O problem
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected open fun skipString() {
        myIsTokenIncomplete = false

        val codes = INPUT_CODE_UTF8

        while (true) {
            var c: Int

            do {
                c = myInputData.readUnsignedByte()
            } while (codes[c] == 0)

            if (c == CODE_QUOTE) {
                break
            }

            when (codes[c]) {
                1 -> decodeEscaped()

                2 -> skipUTF8V2()

                3 -> skipUTF8V3()

                4 -> skipUTF8V4()

                else -> if (c < CODE_SPACE) {
                    throwUnquotedSpace(c, "string value")
                } else {
                    return reportInvalidChar(c)
                }
            }
        }
    }

    /**
     * Method for handling cases where first non-space character of an expected value token is not legal for standard
     * CirJSON content.
     *
     * @param code First undecoded character of unexpected (but possibly ultimate accepted) value
     *
     * @return Token that was successfully decoded (if successful)
     *
     * @throws IOException for low-level I/O
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected open fun handleUnexpectedValue(code: Int): CirJsonToken? {
        val ch = code.toChar()

        when (ch) {
            ']', ',', '}' -> {
                if (ch == ']' && !streamReadContext!!.isInArray) {
                    return if (ch.isJavaIdentifierStart()) {
                        reportInvalidToken(code, ch.toString(), validCirJsonTokenList())
                    } else {
                        reportUnexpectedChar(ch, "expected a valid value ${validCirJsonValueList()}")
                    }
                }

                if (ch != '}' && !streamReadContext!!.isInRoot) {
                    if (streamReadContext!!.isInArray && streamReadContext!!.currentIndex == 0) {
                        return reportUnexpectedChar(ch, "expected VALUE_STRING")
                    }

                    if (isEnabled(CirJsonReadFeature.ALLOW_MISSING_VALUES)) {
                        myNextByte = code
                        return CirJsonToken.VALUE_NULL
                    }
                }

                return reportUnexpectedChar(ch, "expected a value")
            }

            '\'' -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_SINGLE_QUOTES)) {
                    return handleApostrophe()
                }
            }

            'N' -> {
                matchToken("NaN", 1)

                return if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    resetAsNaN("NaN", Double.NaN)
                } else {
                    reportError(
                            "Non-standard token 'NaN': enable `CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
                }
            }

            'I' -> {
                matchToken("Infinity", 1)

                return if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    resetAsNaN("Infinity", Double.POSITIVE_INFINITY)
                } else {
                    reportError(
                            "Non-standard token 'Infinity': enable `CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
                }
            }

            '+' -> {
                return handleInvalidNumberStart(myInputData.readUnsignedByte(), negative = false, hasSign = true)
            }
        }

        return if (ch.isJavaIdentifierStart()) {
            reportInvalidToken(code, ch.toString(), validCirJsonTokenList())
        } else {
            reportUnexpectedChar(ch, "expected a valid value ${validCirJsonValueList()}")
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    protected open fun handleApostrophe(): CirJsonToken? {
        var c: Int
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = 0

        val codes = INPUT_CODE_UTF8

        mainLoop@ while (true) {
            asciiLoop@ while (true) {
                var outputEnd = outputBuffer.size

                if (outputPointer >= outputEnd) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                    outputEnd = outputBuffer.size
                }

                do {
                    c = myInputData.readUnsignedByte()

                    if (c == CODE_APOSTROPHE) {
                        break@mainLoop
                    }

                    if (codes[c] != 0 && c != CODE_QUOTE) {
                        break@asciiLoop
                    }

                    outputBuffer[outputPointer++] = c.toChar()
                } while (outputPointer < outputEnd)
            }

            c = when (codes[c]) {
                1 -> decodeEscaped().code

                2 -> decodeUTF8V2(c)

                3 -> decodeUTF8V3(c)

                4 -> {
                    c = decodeUTF8V4(c)
                    outputBuffer[outputPointer++] = (c shr 10 or 0xD800).toChar()

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = myTextBuffer.finishCurrentSegment()
                        outputPointer = 0
                    }

                    c and 0x03FF or 0xDC00
                }

                else -> {
                    if (c < CODE_SPACE) {
                        throwUnquotedSpace(c, "string value")
                    }

                    reportInvalidChar(c)
                }
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()
        }

        myTextBuffer.currentSegmentSize = outputPointer
        return CirJsonToken.VALUE_STRING
    }

    /**
     * Method called if expected numeric value (due to leading sign) does not look like a number
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected open fun handleInvalidNumberStart(code: Int, negative: Boolean): CirJsonToken? {
        return handleInvalidNumberStart(code, negative, false)
    }

    @Throws(CirJacksonException::class, IOException::class)
    protected open fun handleInvalidNumberStart(code: Int, negative: Boolean, hasSign: Boolean): CirJsonToken? {
        var ch = code

        if (ch == 'I'.code) {
            ch = myInputData.readUnsignedByte()

            val match = if (ch == 'N'.code) {
                if (negative) {
                    "-INF"
                } else {
                    "+INF"
                }
            } else if (ch == 'n'.code) {
                if (negative) {
                    "-Infinity"
                } else {
                    "+Infinity"
                }
            } else {
                null
            }

            if (match != null) {
                matchToken(match, 3)

                return if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    resetAsNaN(match, if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY)
                } else {
                    reportError(
                            "Non-standard token '$match': enable `CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
                }
            }
        }

        return if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS) && hasSign && !negative) {
            reportUnexpectedNumberChar('+',
                    "CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
        } else {
            val message = if (negative) {
                "expected digit (0-9) to follow minus sign, for valid numeric value"
            } else {
                "expected digit (0-9) for valid numeric value"
            }
            reportUnexpectedNumberChar(ch.toChar(), message)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    protected fun matchToken(matchString: String, i: Int) {
        var i = i

        val length = matchString.length

        do {
            val ch = myInputData.readUnsignedByte()

            if (ch != matchString[i].code) {
                return reportInvalidToken(ch, matchString.substring(0, i))
            }
        } while (++i < length)

        val ch = myInputData.readUnsignedByte()

        if (ch >= CODE_0 && ch != CODE_R_BRACKET && ch != CODE_R_CURLY) {
            checkMatchEnd(matchString, i, ch)
        }

        myNextByte = ch
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun checkMatchEnd(matchString: String, i: Int, code: Int) {
        val ch = decodeCharForError(code)
        val c = ch.toChar()

        if (c.isJavaIdentifierPart()) {
            return reportInvalidToken(ch, matchString.substring(0, i))
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, whitespace skipping, escape/unescape
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipWhitespace(): Int {
        var i = myNextByte

        if (i < 0) {
            i = myInputData.readUnsignedByte()
        } else {
            myNextByte = -1
        }

        while (true) {
            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    skipWhitespaceComment(i)
                } else {
                    i
                }
            }

            if (i == CODE_LF) {
                ++myCurrentInputRow
            } else if (i == CODE_CR) {
                skipCR()

                if (myNextByte != -1) {
                    i = myNextByte
                    myNextByte = -1
                    continue
                }
            }

            i = myInputData.readUnsignedByte()
        }
    }

    /**
     * Alternative to [skipWhitespace] that handles possible [EOFException] caused by trying to read past the end of the
     * [DataInput].
     */
    @Throws(CirJacksonException::class, IOException::class)
    private fun skipWhitespaceOrEnd(): Int {
        var i = myNextByte

        if (i < 0) {
            try {
                i = myInputData.readUnsignedByte()
            } catch (e: EOFException) {
                return eofAsNextChar()
            }
        } else {
            myNextByte = -1
        }

        while (true) {
            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    skipWhitespaceComment(i)
                } else {
                    i
                }
            }

            try {
                if (i == CODE_LF) {
                    ++myCurrentInputRow
                } else if (i == CODE_CR) {
                    skipCR()

                    if (myNextByte != -1) {
                        i = myNextByte
                        myNextByte = -1
                        continue
                    }
                }

                i = myInputData.readUnsignedByte()
            } catch (e: EOFException) {
                return eofAsNextChar()
            }
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipCR() {
        myNextByte = myInputData.readUnsignedByte()

        if (myNextByte == CODE_LF) {
            myNextByte = -1
        }

        ++myCurrentInputRow
    }

    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    private fun skipWhitespaceComment(i: Int): Int {
        var i = i

        while (true) {
            if (i > CODE_SPACE) {
                if (i == CODE_SLASH) {
                    skipComment()
                } else if (i == CODE_HASH) {
                    if (!skipYAMLComment()) {
                        return i
                    }
                } else {
                    return i
                }
            }

            if (i == CODE_CR || i == CODE_LF) {
                ++myCurrentInputRow
            }

            i = myInputData.readUnsignedByte()
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipColon(): Int {
        var i = myNextByte

        if (i < 0) {
            i = myInputData.readUnsignedByte()
        } else {
            myNextByte = -1
        }

        if (i == CODE_COLON) {
            i = myInputData.readUnsignedByte()

            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    skipColon(i, true)
                } else {
                    i
                }
            }

            if (i == CODE_SPACE || i == CODE_TAB) {
                i = myInputData.readUnsignedByte()

                if (i > CODE_SPACE) {
                    return if (i == CODE_SLASH || i == CODE_HASH) {
                        skipColon(i, true)
                    } else {
                        i
                    }
                }
            }

            return skipColon(i, true)
        }

        if (i == CODE_SPACE || i == CODE_TAB) {
            i = myInputData.readUnsignedByte()
        }

        if (i == CODE_COLON) {
            i = myInputData.readUnsignedByte()

            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    skipColon(i, true)
                } else {
                    i
                }
            }

            if (i == CODE_SPACE || i == CODE_TAB) {
                i = myInputData.readUnsignedByte()

                if (i > CODE_SPACE) {
                    return if (i == CODE_SLASH || i == CODE_HASH) {
                        skipColon(i, true)
                    } else {
                        i
                    }
                }
            }

            return skipColon(i, true)
        }

        return skipColon(i, false)
    }

    @Throws(CirJacksonException::class, IOException::class)
    @Suppress("NAME_SHADOWING")
    private fun skipColon(i: Int, gotColon: Boolean): Int {
        var i = i
        var gotColon = gotColon

        while (true) {
            if (i > CODE_SPACE) {
                if (i == CODE_SLASH) {
                    skipComment()
                    i = myInputData.readUnsignedByte()
                    continue
                }

                if (i == CODE_HASH) {
                    if (skipYAMLComment()) {
                        i = myInputData.readUnsignedByte()
                        continue
                    }
                }

                if (gotColon) {
                    return i
                }

                if (i != CODE_COLON) {
                    return reportUnexpectedChar(i.toChar(), "was expecting a colon to separate property name and value")
                }

                gotColon = true
            }

            if (i == CODE_CR || i == CODE_LF) {
                ++myCurrentInputRow
            }

            i = myInputData.readUnsignedByte()
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipComment() {
        if (!isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS)) {
            return reportUnexpectedChar('/',
                    "maybe a (non-standard) comment? (not recognized as one since Feature 'ALLOW_COMMENTS' not enabled for parser)")
        }

        when (val c = myInputData.readUnsignedByte()) {
            CODE_SLASH -> skipLine()

            CODE_ASTERISK -> skipCComment()

            else -> return reportUnexpectedChar(c.toChar(), "was expecting either '*' or '/' for a comment")
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipCComment() {
        val codes = INPUT_CODE_COMMENT
        var i = myInputData.readUnsignedByte()

        while (true) {
            when (val code = codes[i]) {
                0 -> {}

                CODE_ASTERISK -> {
                    i = myInputData.readUnsignedByte()

                    if (i == CODE_SLASH) {
                        return
                    }

                    continue
                }

                CODE_LF, CODE_CR -> {
                    ++myCurrentInputRow
                }

                2 -> skipUTF8V2()

                3 -> skipUTF8V3()

                4 -> skipUTF8V4()

                else -> {
                    if (code < 0) {
                        return reportInvalidChar(i)
                    }
                }
            }

            i = myInputData.readUnsignedByte()
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipYAMLComment(): Boolean {
        return if (isEnabled(CirJsonReadFeature.ALLOW_YAML_COMMENTS)) {
            skipLine()
            true
        } else {
            false
        }
    }

    /**
     * Method for skipping contents of an input line; usually for CPP and YAML style comments.
     */
    @Throws(CirJacksonException::class, IOException::class)
    private fun skipLine() {
        val codes = INPUT_CODE_COMMENT

        while (true) {
            val i = myInputData.readUnsignedByte()

            when (val code = codes[i]) {
                0 -> {}

                CODE_LF, CODE_CR -> {
                    ++myCurrentInputRow
                    return
                }

                2 -> skipUTF8V2()

                3 -> skipUTF8V3()

                4 -> skipUTF8V4()

                else -> {
                    if (code < 0) {
                        return reportInvalidChar(i)
                    }
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    override fun decodeEscaped(): Char {
        return try {
            decodeEscapedInternal()
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun decodeEscapedInternal(): Char {
        when (val c = myInputData.readUnsignedByte()) {
            'b'.code -> return '\b'
            't'.code -> return '\t'
            'n'.code -> return '\n'
            'r'.code -> return '\r'
            'f'.code -> return '\u000c'
            '"'.code, '/'.code, '\\'.code -> return c.toChar()
            'u'.code -> {}
            else -> return handleUnrecognizedCharacterEscape(decodeCharForError(c).toChar())
        }

        var value = 0

        for (i in 0..<4) {
            val ch = myInputData.readUnsignedByte()
            val digit = CharTypes.charToHex(ch.toChar())

            if (digit < 0) {
                return reportUnexpectedChar(ch.toChar(), "expected a hex-digit for character escape sequence")
            }

            value = value shl 4 or digit
        }

        return value.toChar()
    }

    @Throws(CirJacksonException::class, IOException::class)
    protected open fun decodeCharForError(firstByte: Int): Int {
        var c = firstByte and 0xFF

        if (c > 0x7F) {
            val needed = when {
                c and 0xE0 == 0xC0 -> {
                    c = c and 0x1F
                    1
                }

                c and 0xF0 == 0xE0 -> {
                    c = c and 0x0F
                    2
                }

                c and 0xF8 == 0xF0 -> {
                    c = c and 0x07
                    3
                }

                else -> {
                    return reportInvalidInitial(c and 0xFF)
                }
            }

            var d = myInputData.readUnsignedByte()

            if (d and 0xC0 != 0x080) {
                return reportInvalidOther(d and 0xFF)
            }

            c = c shl 6 or (d and 0x3F)

            if (needed > 1) {
                d = myInputData.readUnsignedByte()

                if (d and 0xC0 != 0x080) {
                    return reportInvalidOther(d and 0xFF)
                }

                c = c shl 6 or (d and 0x3F)

                if (needed > 2) {
                    d = myInputData.readUnsignedByte()

                    if (d and 0xC0 != 0x080) {
                        return reportInvalidOther(d and 0xFF)
                    }

                    c = c shl 6 or (d and 0x3F)
                }
            }
        }

        return c
    }

    /*
     *******************************************************************************************************************
     * Internal methods, UTF8 decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class, IOException::class)
    private fun decodeUTF8V2(c: Int): Int {
        val d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        return c and 0x1F shl 6 or (d and 0x3F)
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun decodeUTF8V3(code1: Int): Int {
        var c1 = code1 and 0x0F

        var d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        c1 = c1 shl 6 or (d and 0x3F)
        d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        return c1 shl 6 or (d and 0x3F)
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun decodeUTF8V4(code: Int): Int {
        var c = code
        var d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        c = c and 0x07 shl 6 or (d and 0x3F)
        d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        c = c shl 6 or (d and 0x3F)
        d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        return (c shl 6 or (d and 0x3F)) - 0x10000
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipUTF8V2() {
        val d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipUTF8V3() {
        var d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }
    }

    @Throws(CirJacksonException::class, IOException::class)
    private fun skipUTF8V4() {
        var d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }

        d = myInputData.readUnsignedByte()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, binary access
     *******************************************************************************************************************
     */

    /**
     * Efficient handling for incremental parsing of base64-encoded textual content.
     *
     * @param base64Variant Type of base64 encoding expected in context
     *
     * @return Fully decoded value of base64 content
     *
     * @throws IOException for low-level I/O problem
     */
    @Throws(CirJacksonException::class, IOException::class)
    protected fun decodeBase64(base64Variant: Base64Variant): ByteArray {
        val builder = byteArrayBuilder

        while (true) {
            var ch: Int

            do {
                ch = myInputData.readUnsignedByte()
            } while (ch <= CODE_SPACE)

            var bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (ch == CODE_QUOTE) {
                    return builder.toByteArray()
                }

                bits = decodeBase64Escape(base64Variant, ch, 0)

                if (bits < 0) {
                    continue
                }
            }

            var decodedData = bits
            ch = myInputData.readUnsignedByte()
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                bits = decodeBase64Escape(base64Variant, ch, 1)
            }

            decodedData = decodedData shl 6 or bits
            ch = myInputData.readUnsignedByte()
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == CODE_QUOTE) {
                        decodedData = decodedData shr 4
                        builder.append(decodedData)

                        return if (base64Variant.isRequiringPaddingOnRead) {
                            handleBase64MissingPadding(base64Variant)
                        } else {
                            builder.toByteArray()
                        }
                    }

                    bits = decodeBase64Escape(base64Variant, ch, 2)
                }

                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    ch = myInputData.readUnsignedByte()

                    if (!base64Variant.usesPaddingChar(ch)) {
                        if (decodeBase64Escape(base64Variant, ch, 3) != Base64Variant.BASE64_VALUE_PADDING) {
                            return reportInvalidBase64Char(base64Variant, ch.toChar(), 3,
                                    "expected padding character '${base64Variant.paddingChar}'")
                        }
                    }

                    decodedData = decodedData shr 4
                    builder.append(decodedData)
                    continue
                }
            }

            decodedData = decodedData shl 6 or bits
            ch = myInputData.readUnsignedByte()
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == CODE_QUOTE) {
                        decodedData = decodedData shr 2
                        builder.appendTwoBytes(decodedData)

                        return if (base64Variant.isRequiringPaddingOnRead) {
                            handleBase64MissingPadding(base64Variant)
                        } else {
                            builder.toByteArray()
                        }
                    }

                    bits = decodeBase64Escape(base64Variant, ch, 3)
                }

                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    decodedData = decodedData shr 2
                    builder.appendTwoBytes(decodedData)
                    continue
                }
            }

            decodedData = decodedData shl 6 or bits
            builder.appendThreeBytes(decodedData)
        }
    }

    /*
     *******************************************************************************************************************
     * Improved location updating
     *******************************************************************************************************************
     */

    override fun currentTokenLocation(): CirJsonLocation {
        return CirJsonLocation(contentReference(), -1L, -1L, tokenLineNumber, -1)
    }

    override fun currentLocation(): CirJsonLocation {
        return CirJsonLocation(contentReference(), -1L, -1L, myCurrentInputRow, -1)
    }

    override fun currentLocationMinusOne(): CirJsonLocation {
        return currentLocation()
    }

    override val objectId: Any?
        get() = null

    override val typeId: Any?
        get() = null

    /*
     *******************************************************************************************************************
     * Internal methods, other
     *******************************************************************************************************************
     */

    @Throws(StreamReadException::class)
    private fun closeScope(i: Int) {
        if (i == CODE_R_BRACKET) {
            if (!streamReadContext!!.isInArray) {
                reportMismatchedEndMarker(i.toChar(), '}')
            }

            streamReadContext = streamReadContext!!.clearAndGetParent()
            myCurrentToken = CirJsonToken.END_ARRAY
        }

        if (i != CODE_R_CURLY) {
            return
        }

        if (!streamReadContext!!.isInObject) {
            reportMismatchedEndMarker(i.toChar(), ']')
        }

        streamReadContext = streamReadContext!!.clearAndGetParent()
        myCurrentToken = CirJsonToken.END_OBJECT
    }

    /*
     *******************************************************************************************************************
     * Internal methods, error reporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidToken(code: Int, matchedPart: String): T {
        return reportInvalidToken(code, matchedPart, validCirJsonTokenList())
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidToken(code: Int, matchedPart: String, message: String): T {
        var ch = if (code.toChar().toString() != matchedPart) {
            code
        } else {
            myInputData.readUnsignedByte()
        }

        val stringBuilder = StringBuilder(matchedPart)

        try {
            while (true) {
                val c = decodeCharForError(ch).toChar()

                if (!c.isJavaIdentifierPart()) {
                    break
                }

                stringBuilder.append(c)

                if (stringBuilder.length >= myIOContext!!.errorReportConfiguration.maxErrorTokenLength) {
                    stringBuilder.append("...")
                    break
                }

                ch = myInputData.readUnsignedByte()
            }
        } catch (_: IOException) {
        }

        return reportError("Unrecognized token '$stringBuilder': was expecting $message")
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidChar(code: Int): T {
        return if (code < 0) {
            reportInvalidSpace(code)
        } else {
            reportInvalidInitial(code)
        }
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidInitial(mask: Int): T {
        return reportError("Invalid UTF-8 start byte 0x${mask.toString(16)}")
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidOther(mask: Int): T {
        return reportError("Invalid UTF-8 middle byte 0x${mask.toString(16)}")
    }

    companion object {

        /**
         * This is the main input-code lookup table, fetched eagerly
         */
        private val INPUT_CODE_UTF8 = CharTypes.inputCodeUtf8

        private val INPUT_CODE_UTF8_JS_NAMES = CharTypes.inputCodeUtf8JsNames

        private val INPUT_CODE_COMMENT = CharTypes.inputCodeComment

        /**
         * Latin1 encoding is not supported, but we do use 8-bit subset for pre-processing task, to simplify first pass,
         * keep it fast.
         */
        private val INPUT_CODE_LATIN1 = CharTypes.inputCodeLatin1

        /**
         * Helper method needed for masking of `0x00` character
         */
        private fun padLastQuad(q: Int, bytes: Int): Int {
            return if (bytes != 4) {
                -1 shl (bytes shl 3) or q
            } else {
                q
            }
        }

    }

}