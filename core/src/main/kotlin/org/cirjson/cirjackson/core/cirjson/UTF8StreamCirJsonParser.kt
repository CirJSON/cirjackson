package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.extensions.growBy
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import kotlin.math.min

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

    /**
     * Accessor of [mySymbols] for tests
     */
    internal val symbols = mySymbols

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
    override fun releaseBuffered(output: OutputStream): Int {
        val count = myInputEnd - myInputPointer

        if (count < 1) {
            return 0
        }

        val originalPointer = myInputPointer
        myInputPointer += count

        try {
            output.write(myInputBuffer, originalPointer, count)
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
        streamReadConstraints().validateDocumentLength(myCurrentInputProcessed)

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
                    val name = myStreamReadContext.currentName!!
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
                currentName()
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
                currentName()
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
                myStreamReadContext.currentName
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
                myStreamReadContext.currentName!!.length
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

            if (myInputPointer >= myInputEnd) {
                loadMoreGuaranteed()
            }

            ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
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
            val token = if (i == CODE_R_CURLY) CirJsonToken.END_OBJECT else CirJsonToken.END_ARRAY
            return token.also { myCurrentToken = it }
        }

        if (myStreamReadContext.isExpectingComma) {
            if (i != CODE_COMMA) {
                return reportUnexpectedChar(i.toChar(),
                        "was expecting comma to separate ${myStreamReadContext.typeDescription} entries")
            }

            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return myCurrentToken
                }
            }
        }

        if (!myStreamReadContext.isInObject) {
            updateLocation()
            return nextTokenNotInObject(i)
        }

        updateNameLocation()
        val name = parseName(i)
        myStreamReadContext.currentName = name
        myCurrentToken = if (myCurrentToken == CirJsonToken.START_OBJECT) {
            if (name != idName) {
                return reportInvalidToken(name!!, "Expected property name '$idName', received '$name'")
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME
        } else {
            CirJsonToken.PROPERTY_NAME
        }
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

        if (myStreamReadContext.isExpectingComma) {
            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return null
                }
            }
        }

        if (!myStreamReadContext.isInObject) {
            updateLocation()
            nextTokenNotInObject(i)
            return null
        }

        updateNameLocation()
        val name = parseName(i)
        myStreamReadContext.currentName = name
        myCurrentToken = if (myCurrentToken == CirJsonToken.START_OBJECT) {
            if (name != idName) {
                return reportInvalidToken(name!!, "Expected property name '$idName', received '$name'")
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME
        } else {
            CirJsonToken.PROPERTY_NAME
        }
        i = skipColon()
        updateLocation()

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

        if (myStreamReadContext.isExpectingComma) {
            i = skipWhitespace()

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return false
                }
            }
        }

        if (!myStreamReadContext.isInObject) {
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
                            myStreamReadContext.currentName = string.value
                            isNextTokenNameYes(skipColonFast(pointer + 1))
                            return true
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

        if (myStreamReadContext.isExpectingComma) {
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

        if (!myStreamReadContext.isInObject) {
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

        myStreamReadContext.currentName = name
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
        myStreamReadContext.currentName = name
        myCurrentToken = if (myCurrentToken == CirJsonToken.START_OBJECT) {
            if (name != idName) {
                return reportInvalidToken(name!!, "Expected property name '$idName', received '$name'")
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME
        } else {
            CirJsonToken.PROPERTY_NAME
        }
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
    @Suppress("NAME_SHADOWING")
    protected fun matchName(matcher: PropertyNameMatcher, i: Int): Int {
        var i = i

        if (i != CODE_QUOTE) {
            return -1
        }

        var quadPointer = myInputPointer

        if (quadPointer + 13 > myInputEnd) {
            return -1
        }

        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1

        var q = input[quadPointer++].toInt() and 0xFF

        if (codes[q] != 0) {
            return if (q == CODE_QUOTE) {
                matcher.matchName("")
            } else {
                -1
            }
        }

        i = input[quadPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            if (i != CODE_QUOTE) {
                return -1
            }
        } else {
            q = q shl 8 or i
            i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                if (i != CODE_QUOTE) {
                    return -1
                }
            } else {
                q = q shl 8 or i
                i = input[quadPointer++].toInt() and 0xFF

                if (codes[i] != 0) {
                    if (i != CODE_QUOTE) {
                        return -1
                    }
                } else {
                    q = q shl 8 or i
                    i = input[quadPointer++].toInt() and 0xFF

                    if (codes[i] == 0) {
                        myQuad1 = q
                        return matchMediumName(matcher, quadPointer, i)
                    }

                    if (i != CODE_QUOTE) {
                        return -1
                    }
                }
            }
        }

        myQuadPointer = quadPointer
        return matcher.matchByQuad(q)
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun matchMediumName(matcher: PropertyNameMatcher, quadPointer: Int, quad2: Int): Int {
        var quadPointer = quadPointer
        var q2 = quad2
        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1

        var i = input[quadPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            if (i != CODE_QUOTE) {
                return -1
            }
        } else {
            q2 = q2 shl 8 or i
            i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                if (i != CODE_QUOTE) {
                    return -1
                }
            } else {
                q2 = q2 shl 8 or i
                i = input[quadPointer++].toInt() and 0xFF

                if (codes[i] != 0) {
                    if (i != CODE_QUOTE) {
                        return -1
                    }
                } else {
                    q2 = q2 shl 8 or i
                    i = input[quadPointer++].toInt() and 0xFF

                    if (codes[i] == 0) {
                        return matchMediumName(matcher, quadPointer, i, q2)
                    }

                    if (i != CODE_QUOTE) {
                        return -1
                    }
                }
            }
        }

        myQuadPointer = quadPointer
        return matcher.matchByQuad(myQuad1, q2)
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun matchMediumName(matcher: PropertyNameMatcher, quadPointer: Int, quad3: Int, q2: Int): Int {
        var q3 = quad3
        var quadPointer = quadPointer
        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1

        var i = input[quadPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            if (i != CODE_QUOTE) {
                return -1
            }
        } else {
            q3 = q3 shl 8 or i
            i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                if (i != CODE_QUOTE) {
                    return -1
                }
            } else {
                q3 = q3 shl 8 or i
                i = input[quadPointer++].toInt() and 0xFF

                if (codes[i] != 0) {
                    if (i != CODE_QUOTE) {
                        return -1
                    }
                } else {
                    q3 = q3 shl 8 or i
                    i = input[quadPointer++].toInt() and 0xFF

                    if (codes[i] == 0) {
                        myQuadBuffer[0] = myQuad1
                        myQuadBuffer[1] = q2
                        myQuadBuffer[2] = q3
                        return matchLongName(matcher, quadPointer, i)
                    }

                    if (i != CODE_QUOTE) {
                        return -1
                    }
                }
            }
        }

        myQuadPointer = quadPointer
        return matcher.matchByQuad(myQuad1, q2, q3)
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun matchLongName(matcher: PropertyNameMatcher, quadPointer: Int, quad: Int): Int {
        var q = quad
        var quadPointer = quadPointer
        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1
        var quadLength = 3

        while (quadPointer + 4 <= myInputEnd) {
            var i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    myQuadPointer = quadPointer
                    matcher.matchByQuad(myQuadBuffer, quadLength)
                } else {
                    -1
                }
            }

            q = q shl 8 or i
            i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                if (i != CODE_QUOTE) {
                    return -1
                }

                break
            }

            q = q shl 8 or i
            i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                if (i != CODE_QUOTE) {
                    return -1
                }

                break
            }

            q = q shl 8 or i
            i = input[quadPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                if (i != CODE_QUOTE) {
                    return -1
                }

                break
            }

            if (quadLength >= myQuadBuffer.size) {
                myQuadBuffer = myQuadBuffer.growBy(quadLength)
            }

            myQuadBuffer[quadLength++] = q
            q = i
        }

        return -1
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

    /*
     *******************************************************************************************************************
     * Internal methods, number parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
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
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected open fun parseUnsignedNumber(code: Int): CirJsonToken? {
        var c = code
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()

        if (c == CODE_0) {
            c = verifyNoLeadingZeroes()
        }

        outputBuffer[0] = c.toChar()
        var integralLength = 1
        var outputPointer = 1
        val end = min(myInputEnd, myInputPointer + outputBuffer.size - 1)

        while (true) {
            if (myInputPointer >= end) {
                return parseNumber(outputBuffer, outputPointer, false, integralLength)
            }

            c = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (c !in CODE_0..CODE_9) {
                break
            }

            ++integralLength
            outputBuffer[outputPointer++] = c.toChar()
        }

        if (c == CODE_PERIOD || c or 0x20 == CODE_E_LOWERCASE) {
            return parseFloat(outputBuffer, outputPointer, c, false, integralLength)
        }

        --myInputPointer
        myTextBuffer.currentSegmentSize = outputPointer

        if (myStreamReadContext.isInRoot) {
            verifyRootSpace(c)
        }

        return resetInt(false, integralLength)
    }

    @Throws(CirJacksonException::class)
    private fun parseSignedNumber(negative: Boolean): CirJsonToken? {
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = 0

        if (negative) {
            outputBuffer[outputPointer++] = '-'
        }

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        var c = myInputBuffer[myInputPointer++].toInt() and 0xFF

        if (c <= CODE_0) {
            if (c != CODE_0) {
                return if (c == CODE_PERIOD) {
                    parseFloatThatStartsWithPeriod(negative)
                } else {
                    handleInvalidNumberStart(c, negative, true)
                }
            }

            c = verifyNoLeadingZeroes()
        } else if (c > CODE_9) {
            return handleInvalidNumberStart(c, negative, true)
        }

        outputBuffer[outputPointer++] = c.toChar()
        var integralLength = 1
        val end = min(myInputEnd, myInputPointer + outputBuffer.size - outputPointer)

        while (true) {
            if (myInputPointer >= end) {
                return parseNumber(outputBuffer, outputPointer, negative, integralLength)
            }

            c = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (c !in CODE_0..CODE_9) {
                break
            }

            ++integralLength
            outputBuffer[outputPointer++] = c.toChar()
        }

        if (c == CODE_PERIOD || c or 0x20 == CODE_E_LOWERCASE) {
            return parseFloat(outputBuffer, outputPointer, c, negative, integralLength)
        }

        --myInputPointer
        myTextBuffer.currentSegmentSize = outputPointer

        if (myStreamReadContext.isInRoot) {
            verifyRootSpace(c)
        }

        return resetInt(negative, integralLength)
    }

    /**
     * Method called to handle parsing when input is split across buffer boundary (or output is longer than segment used
     * to store it)
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun parseNumber(outputBuffer: CharArray, outputPointer: Int, negative: Boolean,
            integralLength: Int): CirJsonToken? {
        var outputBuffer = outputBuffer
        var outputPointer = outputPointer
        var integralLength = integralLength

        while (true) {
            if (myInputPointer >= myInputEnd && !loadMore()) {
                myTextBuffer.currentSegmentSize = outputPointer
                return resetInt(negative, integralLength)
            }

            val c = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (c !in CODE_0..CODE_9) {
                if (c == CODE_PERIOD || c or 0x20 == CODE_E_LOWERCASE) {
                    return parseFloat(outputBuffer, outputPointer, c, negative, integralLength)
                }

                break
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            ++integralLength
            outputBuffer[outputPointer++] = c.toChar()
        }

        --myInputPointer
        myTextBuffer.currentSegmentSize = outputPointer

        if (myStreamReadContext.isInRoot) {
            verifyRootSpace(myInputBuffer[myInputPointer].toInt() and 0xFF)
        }

        return resetInt(negative, integralLength)
    }

    /**
     * Method called when we have seen one zero, and want to ensure it is not followed by another
     */
    @Throws(CirJacksonException::class)
    private fun verifyNoLeadingZeroes(): Int {
        if (myInputPointer >= myInputEnd && !loadMore()) {
            return CODE_0
        }

        var ch = myInputBuffer[myInputPointer].toInt() and 0xFF

        if (ch !in CODE_0..CODE_9) {
            return CODE_0
        }

        if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)) {
            return reportInvalidNumber("Leading zeroes not allowed")
        }

        ++myInputPointer

        if (ch == CODE_0) {
            while (myInputPointer < myInputEnd || loadMore()) {
                ch = myInputBuffer[myInputPointer].toInt() and 0xFF

                if (ch !in CODE_0..CODE_9) {
                    return CODE_0
                }

                ++myInputPointer

                if (ch != CODE_0) {
                    break
                }
            }
        }

        return ch
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun parseFloat(outputBuffer: CharArray, outputPointer: Int, code: Int, negative: Boolean,
            integralLength: Int): CirJsonToken? {
        var outputBuffer = outputBuffer
        var outputPointer = outputPointer
        var c = code
        var fractionLength = 0
        var eof = false

        if (c == CODE_PERIOD) {
            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()

            while (true) {
                if (myInputPointer >= myInputEnd && !loadMore()) {
                    eof = true
                    break
                }

                c = myInputBuffer[myInputPointer++].toInt() and 0xFF

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

            if (myInputPointer >= myInputEnd) {
                loadMoreGuaranteed()
            }

            c = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (c == CODE_MINUS || c == CODE_PLUS) {
                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c.toChar()

                if (myInputPointer >= myInputEnd) {
                    loadMoreGuaranteed()
                }

                c = myInputBuffer[myInputPointer++].toInt() and 0xFF
            }

            while (c in CODE_0..CODE_9) {
                ++exponentLength

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c.toChar()

                if (myInputPointer >= myInputEnd && !loadMore()) {
                    eof = true
                    break
                }

                c = myInputBuffer[myInputPointer++].toInt() and 0xFF
            }

            if (exponentLength == 0) {
                return reportUnexpectedNumberChar(c.toChar(), "Exponent indicator not followed by a digit")
            }
        }

        if (!eof) {
            --myInputPointer

            if (myStreamReadContext.isInRoot) {
                verifyRootSpace(c)
            }
        }

        myTextBuffer.currentSegmentSize = outputPointer
        return resetFloat(negative, integralLength, fractionLength, exponentLength)
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
        var i = code

        if (i != CODE_QUOTE) {
            return handleOddName(i)
        }

        if (myInputPointer + 13 > myInputEnd) {
            return slowParseName()
        }

        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1

        var q = input[myInputPointer++].toInt() and 0xFF

        return if (codes[q] == 0) {
            i = input[myInputPointer++].toInt() and 0xFF
            if (codes[i] == 0) {
                q = q shl 8 or i
                i = input[myInputPointer++].toInt() and 0xFF

                if (codes[i] == 0) {
                    q = q shl 8 or i
                    i = input[myInputPointer++].toInt() and 0xFF

                    if (codes[i] == 0) {
                        q = q shl 8 or i
                        i = input[myInputPointer++].toInt() and 0xFF

                        if (codes[i] == 0) {
                            myQuad1 = q
                            parseMediumName(i)
                        } else if (i == CODE_QUOTE) {
                            findName(q, 4)
                        } else {
                            parseName(q, i, 4)
                        }
                    } else if (i == CODE_QUOTE) {
                        findName(q, 3)
                    } else {
                        parseName(q, i, 3)
                    }
                } else if (i == CODE_QUOTE) {
                    findName(q, 2)
                } else {
                    parseName(q, i, 2)
                }
            } else if (i == CODE_QUOTE) {
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

    @Throws(CirJacksonException::class)
    protected fun parseMediumName(quad2: Int): String? {
        var q2 = quad2
        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1

        var i = input[myInputPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 1)
            } else {
                parseName(myQuad1, q2, i, 1)
            }
        }

        q2 = q2 shl 8 or i
        i = input[myInputPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 2)
            } else {
                parseName(myQuad1, q2, i, 2)
            }
        }

        q2 = q2 shl 8 or i
        i = input[myInputPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, 3)
            } else {
                parseName(myQuad1, q2, i, 3)
            }
        }

        q2 = q2 shl 8 or i
        i = input[myInputPointer++].toInt() and 0xFF

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

    @Throws(CirJacksonException::class)
    protected fun parseMediumName(quad3: Int, q2: Int): String? {
        var q3 = quad3
        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1

        var i = input[myInputPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 1)
            } else {
                parseName(myQuad1, q2, q3, i, 1)
            }
        }

        q3 = q3 shl 8 or i
        i = input[myInputPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 2)
            } else {
                parseName(myQuad1, q2, q3, i, 2)
            }
        }

        q3 = q3 shl 8 or i
        i = input[myInputPointer++].toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                findName(myQuad1, q2, q3, 3)
            } else {
                parseName(myQuad1, q2, q3, i, 3)
            }
        }

        q3 = q3 shl 8 or i
        i = input[myInputPointer++].toInt() and 0xFF

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

    @Throws(CirJacksonException::class)
    protected fun parseLongName(quad: Int, q2: Int, q3: Int): String? {
        var q = quad

        myQuadBuffer[0] = myQuad1
        myQuadBuffer[1] = q2
        myQuadBuffer[2] = q3

        val input = myInputBuffer
        val codes = INPUT_CODE_LATIN1
        var quadLength = 3

        while (myInputPointer + 4 <= myInputEnd) {
            var i = input[myInputPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 1)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 1)
                }
            }

            q = q shl 8 or i
            i = input[myInputPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 2)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 2)
                }
            }

            q = q shl 8 or i
            i = input[myInputPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 3)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 3)
                }
            }

            q = q shl 8 or i
            i = input[myInputPointer++].toInt() and 0xFF

            if (codes[i] != 0) {
                return if (i == CODE_QUOTE) {
                    findName(myQuadBuffer, quadLength, q, 4)
                } else {
                    parseEscapedName(myQuadBuffer, quadLength, q, i, 4)
                }
            }

            if (quadLength >= myQuadBuffer.size) {
                myQuadBuffer = growNameDecodeBuffer(myQuadBuffer, quadLength)
            }

            myQuadBuffer[quadLength++] = q
            q = i
        }

        return parseEscapedName(myQuadBuffer, quadLength, 0, q, 0)
    }

    /**
     * Method called when not even first 8 bytes are guaranteed to come consecutively. Happens rarely, so this is
     * offlined; plus we'll also do full checks for escaping etc.
     */
    @Throws(CirJacksonException::class)
    protected open fun slowParseName(): String? {
        if (myInputPointer >= myInputEnd) {
            if (!loadMore()) {
                return reportInvalidEOF(": was expecting closing '\"' for name", CirJsonToken.PROPERTY_NAME)
            }
        }

        val i = myInputBuffer[myInputPointer++].toInt() and 0xFF

        return if (i != CODE_QUOTE) {
            parseEscapedName(myQuadBuffer, 0, 0, i, 0)
        } else {
            ""
        }
    }

    @Throws(CirJacksonException::class)
    private fun parseName(q1: Int, code: Int, lastQuadBytes: Int): String? {
        return parseEscapedName(myQuadBuffer, 0, q1, code, lastQuadBytes)
    }

    @Throws(CirJacksonException::class)
    private fun parseName(q1: Int, q2: Int, code: Int, lastQuadBytes: Int): String? {
        myQuadBuffer[0] = q1
        return parseEscapedName(myQuadBuffer, 1, q2, code, lastQuadBytes)
    }

    @Throws(CirJacksonException::class)
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
    @Throws(CirJacksonException::class)
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

            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF("in property name", CirJsonToken.PROPERTY_NAME)
                }
            }

            ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
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
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems (invalid name)
     */
    @Throws(CirJacksonException::class)
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

        while (true) {
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

            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF("in property name", CirJsonToken.PROPERTY_NAME)
                }
            }

            ch = myInputBuffer[myInputPointer].toInt() and 0xFF

            if (codes[ch] != 0) {
                break
            }

            ++myInputPointer
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
     * Parsing to support apostrophe-quoted names. Plenty of duplicated code; main reason being to try to avoid slowing
     * down fast path for valid CirJSON -- more alternatives, more code, generally a bit slower execution.
     */
    @Throws(CirJacksonException::class)
    protected open fun parseApostropheName(): String? {
        if (myInputPointer >= myInputEnd) {
            if (!loadMore()) {
                return reportInvalidEOF(": was expecting closing ''' for name", CirJsonToken.PROPERTY_NAME)
            }
        }

        var ch = myInputBuffer[myInputPointer++].toInt() and 0xFF

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

            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF("in property name", CirJsonToken.PROPERTY_NAME)
                }
            }

            ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
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

    @Throws(CirJacksonException::class)
    private fun findName(quad1: Int, lastQuadBytes: Int): String? {
        val q1 = padLastQuad(quad1, lastQuadBytes)

        val name = mySymbols.findName(q1)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1
        return addName(myQuadBuffer, 1, lastQuadBytes)
    }

    @Throws(CirJacksonException::class)
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

    @Throws(CirJacksonException::class)
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

    @Throws(CirJacksonException::class)
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
    @Throws(CirJacksonException::class)
    private fun addName(quads: IntArray, quadLength: Int, lastQuadBytes: Int): String? {
        val byteLength = (quadLength shl 2) - 4 + lastQuadBytes
        streamReadConstraints().validateNameLength(byteLength)

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
                ch2 = ch2 shr (3 - byteIndex shl 3)
                ++index

                if (ch2 and 0xC0 != 0x080) {
                    return reportInvalidOther(ch2)
                }

                ch = ch shl 6 or (ch2 and 0x3F)

                if (needed > 1) {
                    ch2 = quads[index shr 2]
                    byteIndex = index and 3
                    ch2 = ch2 shr (3 - byteIndex shl 3)
                    ++index

                    if (ch2 and 0xC0 != 0x080) {
                        return reportInvalidOther(ch2)
                    }

                    ch = ch shl 6 or (ch2 and 0x3F)

                    if (needed > 2) {
                        ch2 = quads[index shr 2]
                        byteIndex = index and 3
                        ch2 = ch2 shr (3 - byteIndex shl 3)
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

                    charBuffer[charIndex++] = ((ch shr 10) + 0xD800).toChar()
                    ch = ch and 0x03FF or 0xDC00
                }
            }

            if (charIndex >= charBuffer.size) {
                charBuffer = myTextBuffer.expandCurrentSegment()
            }

            charBuffer[charIndex++] = ch.toChar()
        }

        val baseName = String(charBuffer, 0, charIndex)

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
        var pointer = myInputPointer

        if (pointer >= myInputEnd) {
            loadMoreGuaranteed()
            pointer = myInputPointer
        }

        var outputPointer = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()

        val max = min(myInputEnd, pointer + outputBuffer.size)
        val inputBuffer = myInputBuffer

        while (pointer < max) {
            val c = inputBuffer[pointer].toInt() and 0xFF

            if (INPUT_CODE_UTF8[c] != 0) {
                if (c == CODE_QUOTE) {
                    myInputPointer = pointer + 1
                    myTextBuffer.currentSegmentSize = outputPointer
                    return
                }

                break
            }

            ++pointer
            outputBuffer[outputPointer++] = c.toChar()
        }

        myInputPointer = pointer
        finishString(outputBuffer, outputPointer)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishAndReturnString(): String? {
        var pointer = myInputPointer

        if (pointer >= myInputEnd) {
            loadMoreGuaranteed()
            pointer = myInputPointer
        }

        var outputPointer = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()

        val max = min(myInputEnd, pointer + outputBuffer.size)
        val inputBuffer = myInputBuffer

        while (pointer < max) {
            val c = inputBuffer[pointer].toInt() and 0xFF

            if (INPUT_CODE_UTF8[c] != 0) {
                if (c == CODE_QUOTE) {
                    myInputPointer = pointer + 1
                    return myTextBuffer.setCurrentAndReturn(outputPointer)
                }

                break
            }

            ++pointer
            outputBuffer[outputPointer++] = c.toChar()
        }

        myInputPointer = pointer
        finishString(outputBuffer, outputPointer)
        return myTextBuffer.contentsAsString()
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun finishString(outputBuffer: CharArray, outputPointer: Int) {
        var outputBuffer = outputBuffer
        var outputPointer = outputPointer

        var c: Int

        val codes = INPUT_CODE_UTF8
        val inputBuffer = myInputBuffer

        while (true) {
            asciiLoop@ while (true) {
                var pointer = myInputPointer

                if (pointer >= myInputEnd) {
                    loadMoreGuaranteed()
                    pointer = myInputPointer
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                val max = min(myInputEnd, pointer + outputBuffer.size - outputPointer)

                while (pointer < max) {
                    c = inputBuffer[pointer++].toInt() and 0xFF

                    if (codes[c] != 0) {
                        myInputPointer = pointer
                        break@asciiLoop
                    }

                    outputBuffer[outputPointer++] = c.toChar()
                }

                myInputPointer = pointer
            }

            if (c == CODE_QUOTE) {
                break
            }

            c = when (codes[c]) {
                1 -> decodeEscaped().code

                2 -> decodeUTF8V2(c)

                3 -> if (myInputEnd - myInputPointer >= 2) {
                    decodeUTF8V3Fast(c)
                } else {
                    decodeUTF8V3(c)
                }

                4 -> {
                    c = decodeUTF8V4(c)
                    outputBuffer[outputPointer++] = (c shr 10 or 0xD800).toChar()

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = myTextBuffer.finishCurrentSegment()
                        outputPointer = 0
                    }

                    c and 0x03FF or 0xDC00
                }

                else -> if (c < CODE_SPACE) {
                    throwUnquotedSpace(c, "string value")
                    c
                } else {
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
        myIsTokenIncomplete = false

        val codes = INPUT_CODE_UTF8
        val inputBuffer = myInputBuffer

        while (true) {
            var c: Int

            asciiLoop@ while (true) {
                var pointer = myInputPointer
                var max = myInputEnd

                if (pointer >= max) {
                    loadMoreGuaranteed()
                    pointer = myInputPointer
                    max = myInputEnd
                }

                while (pointer < max) {
                    c = inputBuffer[pointer++].toInt() and 0xFF

                    if (codes[c] != 0) {
                        myInputPointer = pointer
                        break@asciiLoop
                    }
                }

                myInputPointer = pointer
            }

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
     * @param code First undecoded character of possible "odd value" to decode
     *
     * @return Type of value decoded, if allowed and successful
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnexpectedValue(code: Int): CirJsonToken? {
        val ch = code.toChar()

        when (ch) {
            ']', ',', '}' -> {
                if (ch == ']' && !myStreamReadContext.isInArray) {
                    return if (ch.isJavaIdentifierStart()) {
                        reportInvalidToken(ch.toString(), validCirJsonTokenList())
                    } else {
                        reportUnexpectedChar(ch, "expected a valid value ${validCirJsonValueList()}")
                    }
                }

                if (ch != '}' && !myStreamReadContext.isInRoot) {
                    if (myStreamReadContext.isInArray && myStreamReadContext.currentIndex == 0) {
                        return reportUnexpectedChar(ch, "expected VALUE_STRING")
                    }

                    if (formatReadFeatures and FEAT_MASK_ALLOW_MISSING != 0) {
                        --myInputPointer
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
                if (myInputPointer >= myInputEnd) {
                    if (!loadMore()) {
                        return reportInvalidEOFInValue(CirJsonToken.VALUE_NUMBER_INT)
                    }
                }

                return handleInvalidNumberStart(myInputBuffer[myInputPointer++].toInt() and 0xFF, negative = false,
                        hasSign = true)
            }
        }

        return if (ch.isJavaIdentifierStart()) {
            reportInvalidToken(ch.toString(), validCirJsonTokenList())
        } else {
            reportUnexpectedChar(ch, "expected a valid value ${validCirJsonValueList()}")
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun handleApostrophe(): CirJsonToken? {
        var c: Int
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = 0

        val codes = INPUT_CODE_UTF8
        val inputBuffer = myInputBuffer

        mainLoop@ while (true) {
            asciiLoop@ while (true) {
                if (myInputPointer >= myInputEnd) {
                    loadMoreGuaranteed()
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                val max = min(myInputEnd, myInputPointer + outputBuffer.size - outputPointer)

                while (myInputPointer < max) {
                    c = inputBuffer[myInputPointer++].toInt() and 0xFF

                    if (c == CODE_APOSTROPHE) {
                        break@mainLoop
                    }

                    if (codes[c] != 0 && c != CODE_QUOTE) {
                        break@asciiLoop
                    }

                    outputBuffer[outputPointer++] = c.toChar()
                }
            }

            c = when (codes[c]) {
                1 -> decodeEscaped().code

                2 -> decodeUTF8V2(c)

                3 -> if (myInputEnd - myInputPointer >= 2) {
                    decodeUTF8V3Fast(c)
                } else {
                    decodeUTF8V3(c)
                }

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

    /*
     *******************************************************************************************************************
     * Internal methods, well-known token decoding
     *******************************************************************************************************************
     */

    /**
     * Method called if expected numeric value (due to leading sign) does not look like a number
     */
    @Throws(CirJacksonException::class)
    protected open fun handleInvalidNumberStart(code: Int, negative: Boolean): CirJsonToken? {
        return handleInvalidNumberStart(code, negative, false)
    }

    @Throws(CirJacksonException::class)
    protected open fun handleInvalidNumberStart(code: Int, negative: Boolean, hasSign: Boolean): CirJsonToken? {
        var ch = code

        if (ch == 'I'.code) {
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOFInValue(CirJsonToken.VALUE_NUMBER_FLOAT)
                }
            }

            ch = myInputBuffer[myInputPointer++].toInt()

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

    @Throws(CirJacksonException::class)
    protected fun matchTrue() {
        var pointer = myInputPointer

        if (pointer + 3 < myInputEnd) {
            val buffer = myInputBuffer

            if (buffer[pointer++].toInt() == 'r'.code && buffer[pointer++].toInt() == 'u'.code &&
                    buffer[pointer++].toInt() == 'e'.code) {
                val ch = buffer[pointer].toInt() and 0xFF

                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    myInputPointer = pointer
                    return
                }
            }
        }

        matchToken2("true", 1)
    }

    @Throws(CirJacksonException::class)
    protected fun matchFalse() {
        var pointer = myInputPointer

        if (pointer + 4 < myInputEnd) {
            val buffer = myInputBuffer

            if (buffer[pointer++].toInt() == 'a'.code && buffer[pointer++].toInt() == 'l'.code &&
                    buffer[pointer++].toInt() == 's'.code && buffer[pointer++].toInt() == 'e'.code) {
                val ch = buffer[pointer].toInt() and 0xFF

                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    myInputPointer = pointer
                    return
                }
            }
        }

        matchToken2("false", 1)
    }

    @Throws(CirJacksonException::class)
    protected fun matchNull() {
        var pointer = myInputPointer

        if (pointer + 3 < myInputEnd) {
            val buffer = myInputBuffer

            if (buffer[pointer++].toInt() == 'u'.code && buffer[pointer++].toInt() == 'l'.code &&
                    buffer[pointer++].toInt() == 'l'.code) {
                val ch = buffer[pointer].toInt() and 0xFF

                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    myInputPointer = pointer
                    return
                }
            }
        }

        matchToken2("null", 1)
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun matchToken(matchString: String, i: Int) {
        var i = i
        val length = matchString.length

        if (myInputPointer + length >= myInputEnd) {
            matchToken2(matchString, i)
            return
        }

        do {
            if (myInputBuffer[myInputPointer].toInt() != matchString[i].code) {
                return reportInvalidToken(matchString.substring(0, i))
            }

            ++myInputPointer
        } while (++i < length)

        val ch = myInputBuffer[myInputPointer].toInt() and 0xFF

        if (ch >= CODE_0 && ch != CODE_R_BRACKET && ch != CODE_R_CURLY) {
            checkMatchEnd(matchString, i, ch)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun matchToken2(matchString: String, i: Int) {
        var i = i
        val length = matchString.length

        do {
            if (myInputPointer >= myInputEnd && !loadMore() ||
                    myInputBuffer[myInputPointer].toInt() != matchString[i].code) {
                return reportInvalidToken(matchString.substring(0, i))
            }

            ++myInputPointer
        } while (++i < length)

        if (myInputPointer >= myInputEnd && !loadMore()) {
            return
        }

        val ch = myInputBuffer[myInputPointer].toInt() and 0xFF

        if (ch >= CODE_0 && ch != CODE_R_BRACKET && ch != CODE_R_CURLY) {
            checkMatchEnd(matchString, i, ch)
        }
    }

    @Throws(CirJacksonException::class)
    private fun checkMatchEnd(matchString: String, i: Int, code: Int) {
        val c = decodeCharForError(code).toChar()

        if (c.isJavaIdentifierPart()) {
            return reportInvalidToken(matchString.substring(0, i))
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, whitespace skipping, escape/unescape
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun skipWhitespace(): Int {
        while (myInputPointer < myInputEnd) {
            val i = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    --myInputPointer
                    skipWhitespace2()
                } else {
                    i
                }
            }

            if (i != CODE_SPACE) {
                if (i == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (i == CODE_CR) {
                    skipCR()
                } else if (i != CODE_TAB) {
                    return reportInvalidSpace(i)
                }
            }
        }

        return skipWhitespace2()
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespace2(): Int {
        while (myInputPointer < myInputEnd || loadMore()) {
            val i = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (i > CODE_SPACE) {
                if (i == CODE_SLASH) {
                    skipComment()
                    continue
                }

                if (i == CODE_HASH) {
                    if (skipYAMLComment()) {
                        continue
                    }
                }

                return i
            }

            if (i != CODE_SPACE) {
                if (i == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (i == CODE_CR) {
                    skipCR()
                } else if (i != CODE_TAB) {
                    return reportInvalidSpace(i)
                }
            }
        }

        throw constructReadException(
                "Unexpected end-of-input within/between ${myStreamReadContext.typeDescription} entries")
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespaceOrEnd(): Int {
        if (myInputPointer >= myInputEnd) {
            if (!loadMore()) {
                return eofAsNextChar()
            }
        }

        var i = myInputBuffer[myInputPointer++].toInt() and 0xFF

        if (i > CODE_SPACE) {
            return if (i == CODE_SLASH || i == CODE_HASH) {
                --myInputPointer
                skipWhitespaceOrEnd2()
            } else {
                i
            }
        }

        if (i != CODE_SPACE) {
            if (i == CODE_LF) {
                ++myCurrentInputRow
                myCurrentInputRowStart = myInputPointer
            } else if (i == CODE_CR) {
                skipCR()
            } else if (i != CODE_TAB) {
                return reportInvalidSpace(i)
            }
        }

        while (myInputPointer < myInputEnd) {
            i = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    --myInputPointer
                    skipWhitespaceOrEnd2()
                } else {
                    i
                }
            }

            if (i != CODE_SPACE) {
                if (i == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (i == CODE_CR) {
                    skipCR()
                } else if (i != CODE_TAB) {
                    return reportInvalidSpace(i)
                }
            }
        }

        return skipWhitespaceOrEnd2()
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespaceOrEnd2(): Int {
        while (myInputPointer < myInputEnd || loadMore()) {
            val i = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (i > CODE_SPACE) {
                if (i == CODE_SLASH) {
                    skipComment()
                    continue
                }

                if (i == CODE_HASH) {
                    if (skipYAMLComment()) {
                        continue
                    }
                }

                return i
            }

            if (i != CODE_SPACE) {
                if (i == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (i == CODE_CR) {
                    skipCR()
                } else if (i != CODE_TAB) {
                    return reportInvalidSpace(i)
                }
            }
        }

        return eofAsNextChar()
    }

    @Throws(CirJacksonException::class)
    private fun skipColon(): Int {
        if (myInputPointer + 4 >= myInputEnd) {
            return skipColon(false)
        }

        var i = myInputBuffer[myInputPointer].toInt()

        if (i == CODE_COLON) {
            i = myInputBuffer[++myInputPointer].toInt()

            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    skipColon(true)
                } else {
                    ++myInputPointer
                    i
                }
            }

            if (i == CODE_SPACE || i == CODE_TAB) {
                i = myInputBuffer[++myInputPointer].toInt()

                if (i > CODE_SPACE) {
                    return if (i == CODE_SLASH || i == CODE_HASH) {
                        skipColon(true)
                    } else {
                        ++myInputPointer
                        i
                    }
                }
            }

            return skipColon(true)
        }

        if (i == CODE_SPACE || i == CODE_TAB) {
            i = myInputBuffer[++myInputPointer].toInt()
        }

        if (i == CODE_COLON) {
            i = myInputBuffer[++myInputPointer].toInt()

            if (i > CODE_SPACE) {
                return if (i == CODE_SLASH || i == CODE_HASH) {
                    skipColon(true)
                } else {
                    ++myInputPointer
                    i
                }
            }

            if (i == CODE_SPACE || i == CODE_TAB) {
                i = myInputBuffer[++myInputPointer].toInt()

                if (i > CODE_SPACE) {
                    return if (i == CODE_SLASH || i == CODE_HASH) {
                        skipColon(true)
                    } else {
                        ++myInputPointer
                        i
                    }
                }
            }

            return skipColon(true)
        }

        return skipColon(false)
    }

    @Throws(CirJacksonException::class)
    private fun skipColon(gotColon: Boolean): Int {
        var realGotColon = gotColon

        while (myInputPointer < myInputEnd || loadMore()) {
            val i = myInputBuffer[myInputPointer++].toInt() and 0xFF

            if (i > CODE_SPACE) {
                if (i == CODE_SLASH) {
                    skipComment()
                    continue
                }

                if (i == CODE_HASH) {
                    if (skipYAMLComment()) {
                        continue
                    }
                }

                if (realGotColon) {
                    return i
                }

                if (i != CODE_COLON) {
                    return reportUnexpectedChar(i.toChar(), "was expecting a colon to separate property name and value")
                }

                realGotColon = true
            } else if (i != CODE_SPACE) {
                if (i == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (i == CODE_CR) {
                    skipCR()
                } else if (i != CODE_TAB) {
                    return reportInvalidSpace(i)
                }
            }
        }

        return reportInvalidEOF("within/between ${myStreamReadContext.typeDescription} entries", null)
    }

    @Throws(CirJacksonException::class)
    private fun skipComment() {
        if (!isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS)) {
            return reportUnexpectedChar('/',
                    "maybe a (non-standard) comment? (not recognized as one since Feature 'ALLOW_COMMENTS' not enabled for parser)")
        }

        if (myInputPointer >= myInputEnd && !loadMore()) {
            return reportInvalidEOF("in a comment", null)
        }

        when (val c = myInputBuffer[myInputPointer++].toInt() and 0xFF) {
            CODE_SLASH -> skipLine()

            CODE_ASTERISK -> skipCComment()

            else -> return reportUnexpectedChar(c.toChar(), "was expecting either '*' or '/' for a comment")
        }
    }

    @Throws(CirJacksonException::class)
    private fun skipCComment() {
        val codes = INPUT_CODE_COMMENT

        while (myInputPointer < myInputEnd || loadMore()) {
            val i = myInputBuffer[myInputPointer++].toInt() and 0xFF
            val code = codes[i]

            if (code == 0) {
                continue
            }

            when (code) {
                CODE_ASTERISK -> {
                    if (myInputPointer >= myInputEnd && !loadMore()) {
                        break
                    }

                    if (myInputBuffer[myInputPointer].toInt() == CODE_SLASH) {
                        ++myInputPointer
                        return
                    }
                }

                CODE_LF -> {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                }

                CODE_CR -> skipCR()

                2 -> skipUTF8V2()

                3 -> skipUTF8V3()

                4 -> skipUTF8V4()

                else -> return reportInvalidChar(i)
            }
        }

        return reportInvalidEOF("in a comment", null)
    }

    @Throws(CirJacksonException::class)
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
    @Throws(CirJacksonException::class)
    private fun skipLine() {
        val codes = INPUT_CODE_COMMENT

        while (myInputPointer < myInputEnd || loadMore()) {
            val i = myInputBuffer[myInputPointer++].toInt() and 0xFF
            val code = codes[i]

            if (code == 0) {
                continue
            }

            when (code) {
                CODE_LF -> {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                    return
                }

                CODE_CR -> {
                    skipCR()
                    return
                }

                CODE_ASTERISK -> {}

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
        if (myInputPointer >= myInputEnd) {
            if (!loadMore()) {
                return reportInvalidEOF("in character escape sequence", CirJsonToken.VALUE_STRING)
            }
        }

        when (val c = myInputBuffer[myInputPointer++].toInt()) {
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
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF("in character escape sequence", CirJsonToken.VALUE_STRING)
                }
            }

            val ch = myInputBuffer[myInputPointer++].toInt()
            val digit = CharTypes.charToHex(ch.toChar())

            if (digit < 0) {
                return reportUnexpectedChar((ch and 0xFF).toChar(),
                        "expected a hex-digit for character escape sequence")
            }

            value = value shl 4 or digit
        }

        return value.toChar()
    }

    @Throws(CirJacksonException::class)
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

            var d = nextByte()

            if (d and 0xC0 != 0x080) {
                return reportInvalidOther(d and 0xFF)
            }

            c = c shl 6 or (d and 0x3F)

            if (needed > 1) {
                d = nextByte()

                if (d and 0xC0 != 0x080) {
                    return reportInvalidOther(d and 0xFF)
                }

                c = c shl 6 or (d and 0x3F)

                if (needed > 2) {
                    d = nextByte()

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

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V2(c: Int): Int {
        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        val d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        return c and 0x1F shl 6 or (d and 0x3F)
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V3(code1: Int): Int {
        var c1 = code1

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        c1 = c1 and 0x0F
        var d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        c1 = c1 shl 6 or (d and 0x3F)

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        return c1 shl 6 or (d and 0x3F)
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V3Fast(code1: Int): Int {
        var c1 = code1
        c1 = c1 and 0x0F
        var d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        c1 = c1 shl 6 or (d and 0x3F)
        d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        return c1 shl 6 or (d and 0x3F)
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V4(code: Int): Int {
        var c = code

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        var d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        c = c and 0x07 shl 6 or (d and 0x3F)

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        c = c shl 6 or (d and 0x3F)

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        d = myInputBuffer[myInputPointer++].toInt()

        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        return (c shl 6 or (d and 0x3F)) - 0x10000
    }

    @Throws(CirJacksonException::class)
    private fun skipUTF8V2() {
        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        val c = myInputBuffer[myInputPointer++].toInt()

        if (c and 0xC0 != 0x080) {
            return reportInvalidOther(c and 0xFF, myInputPointer)
        }
    }

    @Throws(CirJacksonException::class)
    private fun skipUTF8V3() {
        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        var c = myInputBuffer[myInputPointer++].toInt()

        if (c and 0xC0 != 0x080) {
            return reportInvalidOther(c and 0xFF, myInputPointer)
        }

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        c = myInputBuffer[myInputPointer++].toInt()

        if (c and 0xC0 != 0x080) {
            return reportInvalidOther(c and 0xFF, myInputPointer)
        }
    }

    @Throws(CirJacksonException::class)
    private fun skipUTF8V4() {
        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        var c = myInputBuffer[myInputPointer++].toInt()

        if (c and 0xC0 != 0x080) {
            return reportInvalidOther(c and 0xFF, myInputPointer)
        }

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        c = myInputBuffer[myInputPointer++].toInt()

        if (c and 0xC0 != 0x080) {
            return reportInvalidOther(c and 0xFF, myInputPointer)
        }

        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        c = myInputBuffer[myInputPointer++].toInt()

        if (c and 0xC0 != 0x080) {
            return reportInvalidOther(c and 0xFF, myInputPointer)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, input loading
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun skipCR() {
        if (myInputPointer < myInputEnd || loadMore()) {
            if (myInputBuffer[myInputPointer] == CODE_LF.toByte()) {
                ++myInputPointer
            }
        }

        ++myCurrentInputRow
        myCurrentInputRowStart = myInputPointer
    }

    @Throws(CirJacksonException::class)
    private fun nextByte(): Int {
        if (myInputPointer >= myInputEnd) {
            loadMoreGuaranteed()
        }

        return myInputBuffer[myInputPointer++].toInt() and 0xFF
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
        val builder = byteArrayBuilder

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
                    return builder.toByteArray()
                }

                bits = decodeBase64Escape(base64Variant, ch, 0)

                if (bits < 0) {
                    continue
                }
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

            if (myInputPointer >= myInputEnd) {
                loadMoreGuaranteed()
            }

            ch = myInputBuffer[myInputPointer++].toInt() and 0xFF
            bits = base64Variant.decodeBase64Char(ch)

            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == CODE_QUOTE) {
                        decodedData = decodedData shr 4
                        builder.append(decodedData)

                        if (base64Variant.isRequiringPaddingOnRead) {
                            --myInputPointer
                            return handleBase64MissingPadding(base64Variant)
                        }

                        return builder.toByteArray()
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
                    builder.append(decodedData)
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
                        builder.appendTwoBytes(decodedData)

                        if (base64Variant.isRequiringPaddingOnRead) {
                            --myInputPointer
                            return handleBase64MissingPadding(base64Variant)
                        }

                        return builder.toByteArray()
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
     * Internal methods, location updating
     *******************************************************************************************************************
     */

    override fun currentTokenLocation(): CirJsonLocation {
        return if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            val total = myCurrentInputProcessed + myNameStartOffset - 1
            CirJsonLocation(contentReference(), total, -1L, myNameStartRow, myNameStartColumn)
        } else {
            CirJsonLocation(contentReference(), tokenCharacterOffset - 1, -1L, tokenLineNumber, myTokenInputColumn)
        }
    }

    override fun currentLocation(): CirJsonLocation {
        val column = myInputPointer - myCurrentInputRowStart + 1
        return CirJsonLocation(contentReference(), myCurrentInputProcessed + myInputPointer, -1L, myCurrentInputRow,
                column)
    }

    override fun currentLocationMinusOne(): CirJsonLocation {
        val previousInputPointer = myInputPointer - 1
        val column = previousInputPointer - myCurrentInputRowStart + 1
        return CirJsonLocation(contentReference(), myCurrentInputProcessed + previousInputPointer, -1L,
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
     * Internal methods, other
     *******************************************************************************************************************
     */

    @Throws(StreamReadException::class)
    private fun closeScope(i: Int) {
        if (i == CODE_R_BRACKET) {
            updateLocation()

            if (!myStreamReadContext.isInArray) {
                reportMismatchedEndMarker(i.toChar(), '}')
            }

            myStreamReadContext = myStreamReadContext.clearAndGetParent()!!
            myCurrentToken = CirJsonToken.END_ARRAY
        }

        if (i != CODE_R_CURLY) {
            return
        }

        updateLocation()

        if (!myStreamReadContext.isInObject) {
            reportMismatchedEndMarker(i.toChar(), ']')
        }

        myStreamReadContext = myStreamReadContext.clearAndGetParent()!!
        myCurrentToken = CirJsonToken.END_OBJECT
    }

    override val objectId: Any?
        get() = null

    override val typeId: Any?
        get() = null

    /*
     *******************************************************************************************************************
     * Internal methods, error reporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidToken(matchedPart: String): T {
        return reportInvalidToken(matchedPart, validCirJsonTokenList())
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidToken(matchedPart: String, message: String): T {
        val stringBuilder = StringBuilder(matchedPart)

        while (myInputPointer < myInputEnd || loadMore()) {
            val i = myInputBuffer[myInputPointer++]
            val c = decodeCharForError(i.toInt()).toChar()

            if (!c.isJavaIdentifierPart()) {
                break
            }

            stringBuilder.append(c)

            if (stringBuilder.length >= myIOContext!!.errorReportConfiguration.maxErrorTokenLength) {
                stringBuilder.append("...")
                break
            }
        }

        throw constructReadException("Unrecognized token '$stringBuilder': was expecting $message")
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

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidOther(mask: Int, pointer: Int): T {
        myInputPointer = pointer
        return reportInvalidOther(mask)
    }

    companion object {

        private val FEAT_MASK_TRAILING_COMMA = CirJsonReadFeature.ALLOW_TRAILING_COMMA.mask

        private val FEAT_MASK_ALLOW_MISSING = CirJsonReadFeature.ALLOW_MISSING_VALUES.mask

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