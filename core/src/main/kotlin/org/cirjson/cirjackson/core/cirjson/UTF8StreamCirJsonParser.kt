package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.extensions.write
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer

/**
 * This is a concrete implementation of [CirJsonParser], which is based on a [InputStream] as the input source.
 *
 * @constructor Constructor called when caller wants to provide input buffer directly (or needs to, in case of
 * bootstrapping having read some of the contents) and it may or may not be recyclable use standard recycle context.
 *
 * @param objectReadContext Object read context to use
 *
 * @param ioContext I/O context to use
 *
 * @param streamReadFeatures Standard stream read features enabled
 *
 * @param formatReadFeatures Format-specific read features enabled
 *
 * @param myInputStream InputStream used for reading actual content, if any; `null` if none
 *
 * @param mySymbols Name canonicalizer to use
 *
 * @param myInputBuffer Input buffer to read initial content from (before Reader)
 *
 * @param start Pointer in `inputBuffer` that has the first content character to decode
 *
 * @param end Pointer past the last content character in `inputBuffer`
 *
 * @param bytesPreProcessed Number of bytes that have been consumed already (by bootstrapping)
 *
 * @param myIsBufferRecyclable Whether `inputBuffer` passed is managed by CirJackson core (and thereby needs recycling)
 *
 * @property mySymbols Symbol table that contains property names encountered so far.
 *
 * @property myInputBuffer Current buffer from which data is read; generally data is read into buffer from input source,
 * but in some cases preloaded buffer is handed to the parser.
 *
 * @property myIsBufferRecyclable Flag that indicates whether the input buffer is recyclable (and needs to be returned
 * to recycler once we are done) or not.
 *
 * If it is not, it also means that parser can NOT modify underlying buffer.
 */
open class UTF8StreamCirJsonParser(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
        formatReadFeatures: Int, protected var myInputStream: InputStream?,
        protected val mySymbols: ByteQuadsCanonicalizer, protected var myInputBuffer: ByteArray, start: Int, end: Int,
        bytesPreProcessed: Int, protected var myIsBufferRecyclable: Boolean) :
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

    /**
     * Value of [myInputPointer] at the time when the first character of name token was read. Used for calculating token
     * location when requested; combined with [myCurrentInputProcessed], may be updated appropriately as needed.
     */
    protected var myNameStartOffset = 0

    protected var myNameStartRow = 0

    protected var myNameStartColumn = 0

    init {
        myInputPointer = start
        myInputEnd = end
        myCurrentInputRowStart = start - bytesPreProcessed
        myCurrentInputProcessed = (bytesPreProcessed - start).toLong()
    }

    /*
     *******************************************************************************************************************
     * Overrides, life-cycle
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun releaseBuffered(writer: Writer): Int {
        val count = myInputEnd - myInputPointer

        if (count < 1) {
            return 0
        }

        val originalPointer = myInputPointer
        myInputPointer += count

        try {
            writer.write(myInputBuffer, originalPointer, count)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return count
    }

    override fun streamReadInputSource(): Any? {
        return myInputStream
    }

    /*
     *******************************************************************************************************************
     * Overrides, low-level reading
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun loadMoreGuaranteed() {
        if (!loadMore()) {
            return reportInvalidEOF()
        }
    }

    protected fun loadMore(): Boolean {
        if (myInputStream == null) {
            return false
        }

        val count = try {
            myInputStream!!.read(myInputBuffer, 0, myInputBuffer.size)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        val bufferSize = myInputEnd
        myCurrentInputProcessed += bufferSize
        myCurrentInputRowStart -= bufferSize
        streamReadConstraints.validateDocumentLength(myCurrentInputProcessed)

        myInputPointer = 0

        if (count > 0) {
            myNameStartOffset -= bufferSize
            myInputPointer = 0
            myInputEnd = count
            return true
        }

        myInputEnd = 0
        closeInput()

        if (count == 0) {
            return reportBadReader(myInputBuffer.size)
        }

        return false
    }

    override fun closeInput() {
        if (myInputStream != null) {
            if (myIOContext!!.isResourceManaged || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE)) {
                try {
                    myInputStream!!.close()
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            myInputStream = null
        }
    }

    /**
     * Method called to release internal buffers owned by the base reader. This may be called along with [closeInput]
     * (for example, when explicitly closing this reader instance), or separately (if need be).
     */
    override fun releaseBuffers() {
        super.releaseBuffers()

        mySymbols.release()

        if (myIsBufferRecyclable) {
            val buffer = myInputBuffer

            if (myInputBuffer !== NO_BYTES) {
                myInputBuffer = NO_BYTES
                myIOContext!!.releaseReadIOBuffer(buffer)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, data access
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    final override val text: String?
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
    final override val valueAsString: String?
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
    final override fun getValueAsString(defaultValue: String?): String? {
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
                super.getValueAsString(null)
            }
        }
    }

    @get:Throws(CirJacksonException::class)
    override val valueAsInt: Int
        get() = if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT || myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
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
        return if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT || myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
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
    final override val textCharacters: CharArray?
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
    final override val textLength: Int
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
    final override val textOffset: Int
        get() = when (val id = myCurrentToken?.id) {
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
        if (myCurrentToken == CirJsonToken.VALUE_EMBEDDED_OBJECT && myBinaryValue != null) {
            return myBinaryValue!!
        }

        if (myCurrentToken != CirJsonToken.VALUE_STRING) {
            return reportError("Current token ($myCurrentToken) not VALUE_STRING or VALUE_EMBEDDED_OBJECT, can not " +
                    "access as binary")
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
        } finally {
            myIOContext.releaseBase64Buffer(buffer)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun readBinary(base64Variant: Base64Variant, output: OutputStream, buffer: ByteArray): Int {
        var outputPointer = 0
        val outputEnd = buffer.size - 3
        var outputCount = 0

        while (true) {
            var ch: Int

            do {
                if (myInputPointer >= myInputEnd) {
                    loadMoreGuaranteed()
                }

                ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
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

            if (myInputPointer >= myInputEnd) {
                loadMoreGuaranteed()
            }

            ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                bits = decodeBase64Escape(base64Variant, ch, 1)
            }

            decodedData = decodedData shl 6 or bits

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
                    if (myInputPointer >= myInputEnd) {
                        loadMoreGuaranteed()
                    }

                    ch = myInputBuffer[myInputPointer++].toInt() and 0xFF

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

            if (myInputPointer >= myInputEnd) {
                loadMoreGuaranteed()
            }

            ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
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

            try {
                output.write(buffer, 0, outputPointer)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }
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
        if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            return nextAfterName()
        }

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

        if (i or 0x20 == CODE_R_CURLY) {
            closeScope(i)
            val token = if (i == CODE_R_CURLY) CirJsonToken.END_ARRAY else CirJsonToken.END_OBJECT
            return token.also { myCurrentToken = it }
        }

        if (streamReadContext!!.isExpectingComma) {
            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return myCurrentToken
                }
            }
        }

        if (!streamReadContext!!.isInObject) {
            updateLocation()
            return nextTokenNotInObject(i)
        }

        updateNameLocation()
        val name = parseName(i)
        streamReadContext!!.currentName = name
        myCurrentToken = CirJsonToken.PROPERTY_NAME
        i = skipColon()
        updateLocation()

        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            myNextToken = CirJsonToken.VALUE_STRING
            return myCurrentToken
        }

        val token = when (i) {
            '-'.code -> {
                parseSignedNumber(true)
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false)
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
                matchTrue()
                CirJsonToken.VALUE_TRUE
            }

            'f'.code -> {
                matchFalse()
                CirJsonToken.VALUE_FALSE
            }

            'n'.code -> {
                matchNull()
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

    @Throws(CirJacksonException::class)
    private fun nextTokenNotInObject(i: Int): CirJsonToken? {
        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            return CirJsonToken.VALUE_STRING.also { myCurrentToken = it }
        }

        return when (i) {
            '['.code -> {
                createChildArrayContext(tokenLineNumber, myTokenInputColumn)
                CirJsonToken.START_ARRAY.also { myCurrentToken = it }
            }

            '{'.code -> {
                createChildObjectContext(tokenLineNumber, myTokenInputColumn)
                CirJsonToken.START_OBJECT.also { myCurrentToken = it }
            }

            't'.code -> {
                matchTrue()
                CirJsonToken.VALUE_TRUE.also { myCurrentToken = it }
            }

            'f'.code -> {
                matchFalse()
                CirJsonToken.VALUE_FALSE.also { myCurrentToken = it }
            }

            'n'.code -> {
                matchNull()
                CirJsonToken.VALUE_NULL.also { myCurrentToken = it }
            }

            '-'.code -> {
                parseSignedNumber(true).also { myCurrentToken = it }
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false).also { myCurrentToken = it }
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

    @Throws(CirJacksonException::class)
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
        myNumberTypesValid = NUMBER_UNKNOWN

        if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            nextAfterName()
            return null
        }

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

        if (i or 0x20 == CODE_R_CURLY) {
            closeScope(i)
            return null
        }

        if (streamReadContext!!.isExpectingComma) {
            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return null
                }
            }
        }

        if (!streamReadContext!!.isInObject) {
            updateLocation()
            nextTokenNotInObject(i)
            return null
        }

        updateNameLocation()
        val name = parseName(i)
        streamReadContext!!.currentName = name
        myCurrentToken = CirJsonToken.PROPERTY_NAME
        i = skipColon()

        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            myNextToken = CirJsonToken.VALUE_STRING
            return name
        }

        val token = when (i) {
            '-'.code -> {
                parseSignedNumber(true)
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false)
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
                matchTrue()
                CirJsonToken.VALUE_TRUE
            }

            'f'.code -> {
                matchFalse()
                CirJsonToken.VALUE_FALSE
            }

            'n'.code -> {
                matchNull()
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
    override fun nextName(string: SerializableString): Boolean {
        myNumberTypesValid = NUMBER_UNKNOWN

        if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            nextAfterName()
            return false
        }

        if (myIsTokenIncomplete) {
            skipString()
        }

        var i = skipWhitespaceOrEnd()

        if (i < 0) {
            close()
            myCurrentToken = null
            return false
        }

        myBinaryValue = null

        if (i or 0x20 == CODE_R_CURLY) {
            closeScope(i)
            return false
        }

        if (streamReadContext!!.isExpectingComma) {
            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return false
                }
            }
        }

        if (!streamReadContext!!.isInObject) {
            updateLocation()
            nextTokenNotInObject(i)
            return false
        }

        updateNameLocation()

        if (i == CODE_QUOTE) {
            val nameChars = string.asQuotedUTF8()
            val length = nameChars.size

            if (myInputPointer + length + 4 < myInputEnd) {
                val end = myInputPointer + length

                if (myInputBuffer[end].toInt() == CODE_QUOTE) {
                    var offset = 0
                    var pointer = myInputPointer

                    while (true) {
                        if (pointer == end) {
                            streamReadContext!!.currentName = string.value
                            isNextTokenNameYes(skipColonFast(pointer + 1))
                        }

                        if (nameChars[offset++] != myInputBuffer[pointer++]) {
                            break
                        }
                    }
                }
            }
        }

        return isNextTokenNameMaybe(i, string.value)
    }

    @Throws(CirJacksonException::class)
    override fun nextNameMatch(matcher: PropertyNameMatcher): Int {
        myNumberTypesValid = NUMBER_UNKNOWN

        if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            nextAfterName()
            return PropertyNameMatcher.MATCH_ODD_TOKEN
        }

        if (myIsTokenIncomplete) {
            skipString()
        }

        var i = skipWhitespaceOrEnd()

        if (i < 0) {
            close()
            myCurrentToken = null
            return PropertyNameMatcher.MATCH_ODD_TOKEN
        }

        myBinaryValue = null

        if (i or 0x20 == CODE_R_CURLY) {
            closeScope(i)
            return if (i == CODE_R_CURLY) PropertyNameMatcher.MATCH_END_OBJECT else PropertyNameMatcher.MATCH_ODD_TOKEN
        }

        if (streamReadContext!!.isExpectingComma) {
            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return if (i == CODE_R_CURLY) {
                        PropertyNameMatcher.MATCH_END_OBJECT
                    } else {
                        PropertyNameMatcher.MATCH_ODD_TOKEN
                    }
                }
            }
        }

        if (!streamReadContext!!.isInObject) {
            updateLocation()
            nextTokenNotInObject(i)
            return PropertyNameMatcher.MATCH_ODD_TOKEN
        }

        updateNameLocation()
        val name: String?
        var match = matchName(matcher, i)

        if (match >= 0) {
            myInputPointer = myQuadPointer
            name = matcher.nameLookup!![match]
        } else {
            name = parseName(i)!!
            match = matcher.matchName(name)
        }

        streamReadContext!!.currentName = name
        myCurrentToken = CirJsonToken.PROPERTY_NAME
        i = skipColon()

        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            myNextToken = CirJsonToken.VALUE_STRING
            return match
        }

        val token = when (i) {
            '-'.code -> {
                parseSignedNumber(true)
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false)
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
                matchTrue()
                CirJsonToken.VALUE_TRUE
            }

            'f'.code -> {
                matchFalse()
                CirJsonToken.VALUE_FALSE
            }

            'n'.code -> {
                matchNull()
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
        return match
    }

    @Throws(CirJacksonException::class)
    private fun skipColonFast(pointer: Int): Int {
        var realPointer = pointer
        var i = myInputBuffer[realPointer++].toInt()

        if (i == CODE_COLON) {
            i = myInputBuffer[realPointer++].toInt()

            if (i > CODE_SPACE) {
                if (i != CODE_SLASH && i != CODE_HASH) {
                    myInputPointer = realPointer
                    return i
                }
            } else if (i == CODE_SPACE || i == CODE_TAB) {
                i = myInputBuffer[realPointer++].toInt()

                if (i > CODE_SPACE) {
                    if (i != CODE_SLASH && i != CODE_HASH) {
                        myInputPointer = realPointer
                        return i
                    }
                }
            }

            myInputPointer = realPointer - 1
            return skipColon(true)
        }

        if (i == CODE_SPACE || i == CODE_TAB) {
            i = myInputBuffer[realPointer++].toInt()
        }

        val gotColon = i == CODE_COLON

        if (gotColon) {
            i = myInputBuffer[realPointer++].toInt()

            if (i > CODE_SPACE) {
                if (i != CODE_SLASH && i != CODE_HASH) {
                    myInputPointer = realPointer
                    return i
                }
            } else if (i == CODE_SPACE || i == CODE_TAB) {
                i = myInputBuffer[realPointer++].toInt()

                if (i > CODE_SPACE) {
                    if (i != CODE_SLASH && i != CODE_HASH) {
                        myInputPointer = realPointer
                        return i
                    }
                }
            }
        }

        myInputPointer = realPointer - 1
        return skipColon(gotColon)
    }

    @Throws(CirJacksonException::class)
    private fun isNextTokenNameYes(i: Int) {
        myCurrentToken = CirJsonToken.PROPERTY_NAME
        updateLocation()

        myNextToken = when (i) {
            '"'.code -> {
                myIsTokenIncomplete = true
                CirJsonToken.VALUE_STRING
            }

            '['.code -> {
                CirJsonToken.START_ARRAY
            }

            '{'.code -> {
                CirJsonToken.START_OBJECT
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

            '-'.code -> {
                parseSignedNumber(true)
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false)
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

            else -> {
                handleUnexpectedValue(i)
            }
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun isNextTokenNameMaybe(i: Int, nameToMatch: String): Boolean {
        var i = i
        val name = parseName(i)
        streamReadContext!!.currentName = name
        myCurrentToken = CirJsonToken.PROPERTY_NAME
        i = skipColon()
        updateNameLocation()

        if (i == CODE_QUOTE) {
            myIsTokenIncomplete = true
            myNextToken = CirJsonToken.VALUE_STRING
            return nameToMatch == name
        }

        val token = when (i) {
            '-'.code -> {
                parseSignedNumber(true)
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false)
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
                matchTrue()
                CirJsonToken.VALUE_TRUE
            }

            'f'.code -> {
                matchFalse()
                CirJsonToken.VALUE_FALSE
            }

            'n'.code -> {
                matchNull()
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
        return nameToMatch == name
    }

    @Throws(CirJacksonException::class)
    protected fun matchName(matcher: PropertyNameMatcher, i: Int): Int {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Public API, traversal, nextXXXValue() variants
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    final override fun nextTextValue(): String? {
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
    final override fun nextIntValue(defaultValue: Int): Int {
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
    final override fun nextLongValue(defaultValue: Long): Long {
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
    final override fun nextBooleanValue(): Boolean? {
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

    @Throws(CirJacksonException::class)
    protected fun parseFloatThatStartsWithPeriod(negative: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
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
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected open fun parseUnsignedNumber(code: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun parseSignedNumber(negative: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called to handle parsing when input is split across buffer boundary (or output is longer than segment used
     * to store it)
     */
    @Throws(CirJacksonException::class)
    private fun parseNumber(outputBuffer: CharArray, outputPointer: Int, negative: Boolean,
            integralPartLength: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called when we have seen one zero, and want to ensure it is not followed by another
     */
    @Throws(CirJacksonException::class)
    private fun verifyNoLeadingZeroes(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun parseFloat(outputBuffer: CharArray, outputPointer: Int, negative: Boolean,
            integralPartLength: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called to ensure that a root-value is followed by a space token.
     *
     * NOTE: caller MUST ensure there is at least one character available; and that input pointer is AT given char (not
     * past)
     *
     * @param code First character of likely white space to skip
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    private fun verifyRootSpace(code: Int) {
        ++myInputPointer

        when (code) {
            ' '.code, '\t'.code -> {}

            '\r'.code -> {
                --myInputPointer
            }

            '\n'.code -> {
                ++myCurrentInputRow
                myCurrentInputRowStart = myInputPointer
            }

            else -> {
                reportMissingRootWhiteSpace(code.toChar())
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, secondary parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun parseName(code: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseMediumName(quad2: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseMediumName(quad3: Int, q2: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseLongName(quad: Int, q2: Int, q3: Int): String? {
        TODO("Not yet implemented")
    }

    /**
     * Method called when not even first 8 bytes are guaranteed to come consecutively. Happens rarely, so this is
     * offlined; plus we'll also do full checks for escaping etc.
     */
    @Throws(CirJacksonException::class)
    protected open fun slowParseName(): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun parseName(q1: Int, code: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun parseName(q1: Int, q2: Int, code: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun parseName(q1: Int, q2: Int, q3: Int, code: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseEscapedName(quads: IntArray, quadLength: Int, currentQuad: Int, ch: Int,
            currentQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    /**
     * Method called when we see non-white space character other than double quote, when expecting a property name. In
     * standard mode will just throw an exception; but in non-standard modes may be able to parse name.
     *
     * @param code First undecoded character of possible "odd name" to decode
     *
     * @return Name decoded, if allowed and successful
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems (invalid name)
     */
    @Throws(CirJacksonException::class)
    protected open fun handleOddName(code: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun parseApostropheName(): String? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, symbol (name) handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun findName(quad1: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun findName(q1: Int, quad2: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun findName(q1: Int, q2: Int, quad3: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun findName(quads: IntArray, quadLength: Int, lastQuad: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun addName(quads: IntArray, quadLength: Int, lastQuadBytes: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun finishString() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun finishAndReturnString(): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun finishString(outputBuffer: CharArray, outputPointer: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Method called to skim through rest of unparsed String value, if it is not needed. This can be done a bit faster
     * if contents need not be stored for future access.
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems (invalid String value)
     */
    @Throws(CirJacksonException::class)
    protected open fun skipString() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun handleUnexpectedValue(code: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun handleApostrophe(): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, well-known token decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun handleInvalidNumberStart(code: Int, negative: Boolean, hasSign: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun matchTrue() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun matchFalse() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun matchNull() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun matchToken(matchString: String, i: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchToken2(matchString: String, i: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun checkMatchEnd(matchString: String, i: Int, code: Int) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, whitespace skipping, escape/unescape
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun skipWhitespace(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespace2(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespaceOrEnd(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespaceOrEnd2(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipColon(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipColon(gotColon: Boolean): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipComment() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipCComment() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipYAMLComment(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Method for skipping contents of an input line; usually for CPP and YAML style comments.
     */
    @Throws(CirJacksonException::class)
    private fun skipLine() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun decodeEscaped(): Char {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun decodeCharForError(firstByte: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, UTF8 decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V2(c: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V3(code1: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V3Fast(code1: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V4(code: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipUTF8V2(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipUTF8V3(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipUTF8V4(): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, input loading
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun skipCR() {
        if (myInputPointer < myInputEnd || loadMore()) {
            ++myInputPointer
        }

        ++myCurrentInputRow
        myCurrentInputRowStart = myInputPointer
    }

    @Throws(CirJacksonException::class)
    private fun nextByte(): Int {
        TODO("Not yet implemented")
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
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun decodeBase64(base64Variant: Base64Variant): ByteArray {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, location updating
     *******************************************************************************************************************
     */

    override fun currentTokenLocation(): CirJsonLocation {
        return if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            val total = myCurrentInputProcessed + myNameStartOffset - 1
            CirJsonLocation(contentReference(), -1L, total, myNameStartRow, myNameStartColumn)
        } else {
            CirJsonLocation(contentReference(), -1L, tokenCharacterOffset - 1, myNameStartRow, myNameStartColumn)
        }
    }

    override fun currentLocation(): CirJsonLocation {
        val column = myInputPointer - myCurrentInputRowStart + 1
        return CirJsonLocation(contentReference(), -1L, myCurrentInputProcessed + myInputPointer, myCurrentInputRow,
                column)
    }

    override fun currentLocationMinusOne(): CirJsonLocation {
        val previousInputPointer = myInputPointer - 1
        val column = previousInputPointer - myCurrentInputRowStart + 1
        return CirJsonLocation(contentReference(), -1L, myCurrentInputProcessed + previousInputPointer,
                myCurrentInputRow, column)
    }

    private fun updateLocation() {
        val pointer = myInputPointer
        tokenCharacterOffset = myCurrentInputProcessed + pointer
        tokenLineNumber = myCurrentInputRow
        myTokenInputColumn = pointer - myCurrentInputRowStart
    }

    private fun updateNameLocation() {
        val pointer = myInputPointer
        myNameStartOffset = pointer
        myNameStartRow = myCurrentInputRow
        myNameStartColumn = pointer - myCurrentInputRowStart
    }

    /*
     *******************************************************************************************************************
     * Internal methods, location updating
     *******************************************************************************************************************
     */

    @Throws(StreamReadException::class)
    private fun closeScope(i: Int) {
        if (i == CODE_R_BRACKET) {
            updateLocation()

            if (!streamReadContext!!.isInArray) {
                reportMismatchedEndMarker(i.toChar(), '}')
            }

            streamReadContext = streamReadContext!!.clearAndGetParent()
            myCurrentToken = CirJsonToken.END_ARRAY
        }

        if (i != CODE_R_CURLY) {
            return
        }

        updateLocation()

        if (!streamReadContext!!.isInObject) {
            reportMismatchedEndMarker(i.toChar(), ']')
        }

        streamReadContext = streamReadContext!!.clearAndGetParent()
        myCurrentToken = CirJsonToken.END_OBJECT
    }

    override val objectId: Any?
        get() = null

    override val typeId: Any?
        get() = null

    companion object {

        private val FEAT_MASK_TRAILING_COMMA = CirJsonReadFeature.ALLOW_TRAILING_COMMA.mask

        private val FEAT_MASK_ALLOW_MISSING = CirJsonReadFeature.ALLOW_MISSING_VALUES.mask

        /**
         * This is the main input-code lookup table, fetched eagerly
         */
        private val INPUT_CODE_UTF8 = CharTypes.inputCodeUtf8

        /**
         * Latin1 encoding is not supported, but we do use 8-bit subset for pre-processing task, to simplify first pass,
         * keep it fast.
         */
        private val INPUT_CODE_LATIN1 = CharTypes.inputCodeLatin1

        private val INPUT_CODE_LATIN1_JS_NAMES = CharTypes.inputCodeLatin1JsNames

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