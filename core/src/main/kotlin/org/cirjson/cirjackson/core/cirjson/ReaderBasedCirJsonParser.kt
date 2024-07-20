package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.CharsToNameCanonicalizer
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

/**
 * This is a concrete implementation of [CirJsonParser], which is based on a [Reader] to handle low-level character
 * conversion tasks.
 */
open class ReaderBasedCirJsonParser : CirJsonParserBase {

    /**
     * Reader that can be used for reading more content, if one buffer from input source, but in some cases preloaded
     * buffer is handed to the parser.
     */
    protected var myReader: Reader? = null

    /**
     * Current buffer from which data is read; generally data is read into buffer from input source.
     */
    protected var myInputBuffer: CharArray

    /**
     * Flag that indicates whether the input buffer is recyclable (and needs to be returned to recycler once we are
     * done) or not.
     *
     * If it is not, it also means that parser can NOT modify underlying buffer.
     */
    protected var myIsBufferRecyclable: Boolean

    protected val mySymbols: CharsToNameCanonicalizer

    protected val myHashSeed: Int

    /**
     * Flag that indicates that the current token has not yet been fully processed, and needs to be finished for some
     * access (or skipped to obtain the next token)
     */
    protected var myIsTokenIncomplete = false

    /**
     * Value of [myInputPointer] at the time when the first character of name token was read. Used for calculating token
     * location when requested; combined with [myCurrentInputProcessed], may be updated appropriately as needed.
     */
    protected var myNameStartOffset = 0

    protected var myNameStartRow = 0

    protected var myNameStartColumn = 0

    protected var myIsBufferReleased = false

    /**
     * Constructor called when caller wants to provide input buffer directly (or needs to, in case of bootstrapping
     * having read some of the contents) and it may or may not be recyclable use standard recycle context.
     *
     * @param objectReadContext Object read context to use
     *
     * @param ioContext I/O context to use
     *
     * @param streamReadFeatures Standard stream read features enabled
     *
     * @param formatReadFeatures Format-specific read features enabled
     *
     * @param reader Reader used for reading actual content, if any; `null` if none
     *
     * @param symbols Name canonicalizer to use
     *
     * @param inputBuffer Input buffer to read initial content from (before Reader)
     *
     * @param start Pointer in `inputBuffer` that has the first content character to decode
     *
     * @param end Pointer past the last content character in `inputBuffer`
     *
     * @param bufferRecyclable Whether `inputBuffer` passed is managed by CirJackson core (and thereby needs recycling)
     */
    constructor(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
            formatReadFeatures: Int, reader: Reader?, symbols: CharsToNameCanonicalizer, inputBuffer: CharArray,
            start: Int, end: Int, bufferRecyclable: Boolean) : super(objectReadContext, ioContext, streamReadFeatures,
            formatReadFeatures) {
        myReader = reader
        myInputBuffer = inputBuffer
        myInputPointer = start
        myInputEnd = end
        myCurrentInputRowStart = start
        myCurrentInputProcessed = -start.toLong()
        mySymbols = symbols
        myHashSeed = symbols.hashSeed
        myIsBufferRecyclable = bufferRecyclable
    }

    /**
     * Constructor called when input comes as a [Reader], and buffer allocation can be done using default mechanism.
     *
     * @param objectReadContext Object read context to use
     *
     * @param ioContext I/O context to use
     *
     * @param streamReadFeatures Standard stream read features enabled
     *
     * @param formatReadFeatures Format-specific read features enabled
     *
     * @param reader Reader used for reading actual content, if any; `null` if none
     *
     * @param symbols Name canonicalizer to use
     */
    constructor(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
            formatReadFeatures: Int, reader: Reader?, symbols: CharsToNameCanonicalizer) : super(objectReadContext,
            ioContext, streamReadFeatures, formatReadFeatures) {
        myReader = reader
        myInputBuffer = ioContext.allocateTokenBuffer()
        myInputPointer = 0
        myInputEnd = 0
        mySymbols = symbols
        myHashSeed = symbols.hashSeed
        myIsBufferRecyclable = true
    }

    /*
     *******************************************************************************************************************
     * Configuration access
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
        return myReader
    }

    @Throws(CirJacksonException::class)
    protected fun getNextChar(eofMessage: String, forToken: CirJsonToken): Char {
        if (myInputPointer >= myInputEnd) {
            if (!loadMore()) {
                return reportInvalidEOF(eofMessage, forToken)
            }
        }

        return myInputBuffer[myInputPointer++]
    }

    override fun closeInput() {
        if (myReader != null) {
            if (myIOContext!!.isResourceManaged || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE)) {
                try {
                    myReader!!.close()
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            myReader = null
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

            if (!myIsBufferReleased) {
                myIsBufferReleased = true
                myInputBuffer = CharArray(0)
                myIOContext!!.releaseTokenBuffer(buffer)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Low-level access, supporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun loadMoreGuaranteed() {
        if (!loadMore()) {
            return reportInvalidEOF()
        }
    }

    @Throws(CirJacksonException::class)
    protected fun loadMore(): Boolean {
        if (myReader == null) {
            return false
        }

        val count = try {
            myReader!!.read(myInputBuffer, 0, myInputBuffer.size)
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
                finishString()
            }

            myTextBuffer.contentsAsString()
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
                    finishString()
                }

                myTextBuffer.contentsAsString()
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
                    finishString()
                }

                myTextBuffer.contentsAsString()
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                currentName
            }

            else -> {
                super.getValueAsString(null)
            }
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
            var ch: Char

            do {
                if (myInputPointer >= myInputEnd) {
                    loadMoreGuaranteed()
                }

                ch = myInputBuffer[myInputPointer++]
            } while (ch <= CODE_SPACE.toChar())

            var bits = base64Variant.decodeBase64Char(ch.code)

            if (bits < 0) {
                if (ch == '"') {
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

            ch = myInputBuffer[myInputPointer++]
            bits = base64Variant.decodeBase64Char(ch.code)

            if (bits < 0) {
                bits = decodeBase64Escape(base64Variant, ch, 1)
            }

            decodedData = decodedData shl 6 or bits

            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == '"') {
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

                    ch = myInputBuffer[myInputPointer++]

                    if (!base64Variant.usesPaddingChar(ch)) {
                        if (decodeBase64Escape(base64Variant, ch, 3) != Base64Variant.BASE64_VALUE_PADDING) {
                            return reportInvalidBase64Char(base64Variant, ch, 3,
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

            ch = myInputBuffer[myInputPointer++]
            bits = base64Variant.decodeBase64Char(ch.code)
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == '"') {
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
     * Public API, traversal
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
            return myCurrentToken
        }

        if (streamReadContext!!.isExpectingComma) {
            i = skipComma(i)

            if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                if (i or 0x20 == CODE_R_CURLY) {
                    closeScope(i)
                    return myCurrentToken
                }
            }
        }

        val isInObject = streamReadContext!!.isInObject

        if (isInObject) {
            updateNameLocation()
            val name = if (i == CODE_QUOTE) parseName() else handleOddName(i)
            streamReadContext!!.currentName = name
            myCurrentToken = CirJsonToken.PROPERTY_NAME
            i = skipColon()
        }

        updateLocation()

        val token = when (i) {
            '"'.code -> {
                myIsTokenIncomplete = true
                CirJsonToken.VALUE_STRING
            }

            '['.code -> {
                if (!isInObject) {
                    createChildArrayContext(tokenLineNumber, myTokenInputColumn)
                }

                CirJsonToken.START_ARRAY
            }

            '{'.code -> {
                if (!isInObject) {
                    createChildObjectContext(tokenLineNumber, myTokenInputColumn)
                }

                CirJsonToken.START_OBJECT
            }

            '}'.code -> {
                reportUnexpectedChar(i.toChar(), "expected a value")
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

            '-'.code -> {
                parseSignedNumber(true)
            }

            '+'.code -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    parseSignedNumber(false)
                } else {
                    handleOddValue(i)
                }
            }

            '.'.code -> {
                parseFloatThatStartsWithPeriod(false)
            }

            '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                parseUnsignedNumber(i)
            }

            else -> {
                handleOddValue(i)
            }
        }

        return if (isInObject) {
            myNextToken = token
            myCurrentToken
        } else {
            myCurrentToken = token
            token
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

    /*
     *******************************************************************************************************************
     * Public API, nextXXX() overrides
     *******************************************************************************************************************
     */

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
            i = skipComma(i)

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
            val nameChars = string.asQuotedChars()
            val length = nameChars.size

            if (myInputPointer + length + 4 < myInputEnd) {
                val end = myInputPointer + length

                if (myInputBuffer[end] == '"') {
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
            i = skipComma(i)

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
        val name = if (i == CODE_QUOTE) parseName() else handleOddName(i)
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
                    handleOddValue(i)
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
                handleOddValue(i)
            }
        }

        myNextToken = token
        return name
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
                    handleOddValue(i)
                }
            }

            '.'.code -> {
                parseFloatThatStartsWithPeriod(false)
            }

            '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                parseUnsignedNumber(i)
            }

            else -> {
                handleOddValue(i)
            }
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun isNextTokenNameMaybe(i: Int, nameToMatch: String): Boolean {
        var i = i
        val name = if (i == CODE_QUOTE) parseName() else handleOddName(i)
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
                    handleOddValue(i)
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
                handleOddValue(i)
            }
        }

        myNextToken = token
        return nameToMatch == name
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
                parseSignedNumber(true).also { myCurrentToken = it }
            }

            '.'.code -> {
                parseFloatThatStartsWithPeriod(false).also { myCurrentToken = it }
            }

            '0'.code, '1'.code, '2'.code, '3'.code, '4'.code, '5'.code, '6'.code, '7'.code, '8'.code, '9'.code -> {
                parseUnsignedNumber(i).also { myCurrentToken = it }
            }

            ','.code -> {
                if (!streamReadContext!!.isInRoot && formatReadFeatures and FEAT_MASK_ALLOW_MISSING != 0) {
                    --myInputPointer
                    CirJsonToken.VALUE_NULL.also { myCurrentToken = it }
                } else {
                    handleOddValue(i).also { myCurrentToken = it }
                }
            }

            else -> {
                handleOddValue(i).also { myCurrentToken = it }
            }
        }
    }

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
            if (myIsTokenIncomplete) {
                myIsTokenIncomplete = false
                finishString()
            }

            return myTextBuffer.contentsAsString()
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
    protected fun parseFloatThatStartsWithPeriod(neg: Boolean): CirJsonToken? {
        if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
            return handleOddValue('.'.code)
        }

        var startPointer = myInputPointer - 1

        if (neg) {
            --startPointer
        }

        return parseFloat(CODE_PERIOD, startPointer, myInputPointer, neg, 0)
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
    protected fun parseUnsignedNumber(code: Int): CirJsonToken? {
        var ch = code
        var pointer = myInputPointer
        val startPointer = pointer - 1
        val inputLength = myInputEnd

        if (ch == CODE_0) {
            return parseNumber(false, startPointer)
        }

        var integralLength = 1

        while (true) {
            if (pointer >= inputLength) {
                myInputPointer = startPointer
                return parseNumber(false, startPointer)
            }

            ch = myInputBuffer[pointer++].code

            if (ch !in CODE_0..CODE_9) {
                break
            }

            ++integralLength
        }

        if (ch == CODE_PERIOD || ch or 0x20 == CODE_E_LOWERCASE) {
            myInputPointer = pointer
            return parseFloat(ch, startPointer, pointer, false, integralLength)
        }

        --pointer
        myInputPointer = pointer

        if (streamReadContext!!.isInRoot) {
            verifyRootSpace(ch)
        }

        val length = pointer - startPointer
        myTextBuffer.resetWithShared(myInputBuffer, startPointer, length)
        return resetInt(false, integralLength)
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun parseFloat(code: Int, startPointer: Int, pointer: Int, negative: Boolean,
            integralLength: Int): CirJsonToken? {
        var ch = code
        var pointer = pointer
        val inputLength = myInputEnd
        var fractionLength = 0

        if (ch == '.'.code) {
            while (true) {
                if (pointer >= inputLength) {
                    return parseNumber(negative, startPointer)
                }

                ch = myInputBuffer[pointer++].code

                if (ch !in CODE_0..CODE_9) {
                    break
                }

                ++fractionLength
            }

            if (fractionLength == 0) {
                if (!isEnabled(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)) {
                    return reportUnexpectedNumberChar(ch.toChar(), "Decimal point not followed by a digit")
                }
            }
        }

        var exponentLength = 0

        if (ch or 0x20 == CODE_E_LOWERCASE) {
            if (pointer >= inputLength) {
                myInputPointer = startPointer
                return parseNumber(negative, startPointer)
            }

            ch = myInputBuffer[pointer++].code

            if (ch == CODE_MINUS || ch == CODE_PLUS) {
                if (pointer >= inputLength) {
                    myInputPointer = startPointer
                    return parseNumber(negative, startPointer)
                }

                ch = myInputBuffer[pointer++].code
            }

            while (ch in CODE_0..CODE_9) {
                ++exponentLength

                if (pointer >= inputLength) {
                    myInputPointer = startPointer
                    return parseNumber(negative, startPointer)
                }

                ch = myInputBuffer[pointer++].code
            }

            if (fractionLength == 0) {
                return reportUnexpectedNumberChar(ch.toChar(), "Exponent indicator not followed by a digit")
            }
        }

        myInputPointer = --pointer

        if (streamReadContext!!.isInRoot) {
            verifyRootSpace(ch)
        }

        val length = pointer - startPointer
        myTextBuffer.resetWithShared(myInputBuffer, startPointer, length)
        return resetFloat(false, integralLength, fractionLength, exponentLength)
    }

    @Throws(CirJacksonException::class)
    protected fun parseSignedNumber(negative: Boolean): CirJsonToken? {
        var pointer = myInputPointer
        val startPointer = if (negative) pointer - 1 else pointer
        val inputEnd = myInputEnd

        if (pointer >= inputEnd) {
            return parseNumber(negative, startPointer)
        }

        var ch = myInputBuffer[pointer++].code

        if (ch !in CODE_0..CODE_9) {
            myInputPointer = pointer

            return if (ch == CODE_PERIOD) {
                parseFloatThatStartsWithPeriod(negative)
            } else {
                handleInvalidNumberStart(ch, negative, true)
            }
        }

        if (ch == CODE_0) {
            return parseNumber(negative, startPointer)
        }

        var integralLength = 1

        while (true) {
            if (pointer >= inputEnd) {
                return parseNumber(negative, startPointer)
            }

            ch = myInputBuffer[pointer++].code

            if (ch !in CODE_0..CODE_9) {
                break
            }

            ++integralLength
        }

        if (ch == CODE_PERIOD || ch or 0x20 == CODE_E_LOWERCASE) {
            myInputPointer = pointer
            return parseFloat(ch, startPointer, pointer, negative, integralLength)
        }

        --pointer
        myInputPointer = pointer

        if (streamReadContext!!.isInRoot) {
            verifyRootSpace(ch)
        }

        val length = pointer - startPointer
        myTextBuffer.resetWithShared(myInputBuffer, startPointer, length)
        return resetInt(negative, integralLength)
    }

    /**
     * Method called to parse a number, when the primary parse method has failed to parse it, due to it being split on
     * buffer boundary. As a result code is very similar, except that it has to explicitly copy contents to the text
     * buffer instead of just sharing the main input buffer.
     *
     * @param negative Whether number being decoded is negative or not
     *
     * @param startPointer Offset in input buffer for the next character of content
     *
     * @return Type of token decoded, usually [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT]
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    private fun parseNumber(negative: Boolean, startPointer: Int): CirJsonToken? {
        myInputPointer = if (negative) startPointer + 1 else startPointer
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = 0

        if (negative) {
            outputBuffer[outputPointer++] = '-'
        }

        var integralLength = 0
        var c = if (myInputPointer < myInputEnd) {
            myInputBuffer[myInputPointer++]
        } else {
            getNextChar("No digit following sign", CirJsonToken.VALUE_NUMBER_INT)
        }

        if (c == '0') {
            c = verifyNoLeadingZeroes()
        }

        var eof = false

        while (c in '0'..'9') {
            ++integralLength

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c

            if (myInputPointer >= myInputEnd && !loadMore()) {
                c = CODE_NULL_CHAR.toChar()
                eof = true
                break
            }

            c = myInputBuffer[myInputPointer++]
        }

        if (integralLength == 0) {
            if (c != '.' || !isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                return handleInvalidNumberStart(c.code, negative)
            }
        }

        var fractionLength = -1

        if (c == '.') {
            fractionLength = 0

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c

            while (true) {
                if (myInputPointer >= myInputEnd && !loadMore()) {
                    eof = true
                    break
                }

                c = myInputBuffer[myInputPointer++]

                if (c !in '0'..'9') {
                    break
                }

                ++fractionLength

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c
            }

            if (fractionLength == 0) {
                if (!isEnabled(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)) {
                    return reportUnexpectedNumberChar(c, "Decimal point not followed by a digit")
                }
            }
        }

        var exponentLength = -1

        if (c.code or 0x20 == CODE_E_LOWERCASE) {
            exponentLength = 0

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c

            c = if (myInputPointer < myInputEnd) {
                myInputBuffer[myInputPointer++]
            } else {
                getNextChar("expected a digit for number exponent", CirJsonToken.VALUE_NUMBER_FLOAT)
            }

            if (c == '-' || c == '+') {
                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c

                c = if (myInputPointer < myInputEnd) {
                    myInputBuffer[myInputPointer++]
                } else {
                    getNextChar("expected a digit for number exponent", CirJsonToken.VALUE_NUMBER_FLOAT)
                }
            }

            while (c in '0'..'9') {
                ++exponentLength

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c

                if (myInputPointer >= myInputEnd && !loadMore()) {
                    eof = true
                    break
                }

                c = myInputBuffer[myInputPointer++]
            }

            if (exponentLength == 0) {
                return reportUnexpectedNumberChar(c, "Exponent indicator not followed by a digit")
            }
        }

        if (!eof) {
            --myInputPointer

            if (streamReadContext!!.isInRoot) {
                verifyRootSpace(c.code)
            }
        }

        myTextBuffer.currentSegmentSize = outputPointer

        return if (fractionLength < 0 && exponentLength < 0) {
            resetInt(negative, integralLength)
        } else {
            resetFloat(negative, integralLength, fractionLength, exponentLength)
        }
    }

    /**
     * Method called when we have seen one zero, and want to ensure it is not followed by another
     */
    @Throws(CirJacksonException::class)
    private fun verifyNoLeadingZeroes(): Char {
        if (myInputPointer < myInputEnd) {
            val ch = myInputBuffer[myInputPointer]

            if (ch !in '0'..'9') {
                return '0'
            }
        }

        return verifyNoLeadingZeroes2()
    }

    @Throws(CirJacksonException::class)
    private fun verifyNoLeadingZeroes2(): Char {
        if (myInputPointer >= myInputEnd && !loadMore()) {
            return '0'
        }

        var ch = myInputBuffer[myInputPointer]

        if (ch !in '0'..'9') {
            return '0'
        }

        if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)) {
            return reportInvalidNumber("Leading zeroes not allowed")
        }

        ++myInputPointer

        if (ch == '0') {
            while (myInputPointer < myInputEnd || loadMore()) {
                ch = myInputBuffer[myInputPointer]

                if (ch !in '0'..'9') {
                    return '0'
                }

                ++myInputPointer

                if (ch != '0') {
                    break
                }
            }
        }

        return ch
    }

    @Throws(CirJacksonException::class)
    protected fun handleInvalidNumberStart(code: Int, negative: Boolean): CirJsonToken? {
        return handleInvalidNumberStart(code, negative, false)
    }

    /**
     * Method called if expected numeric value (due to leading sign) does not look like a number
     */
    @Throws(CirJacksonException::class)
    protected fun handleInvalidNumberStart(code: Int, negative: Boolean, hasSign: Boolean): CirJsonToken? {
        var ch = code

        if (ch == 'I'.code) {
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOFInValue(CirJsonToken.VALUE_NUMBER_INT)
                }
            }

            ch = myInputBuffer[myInputPointer++].code

            if (ch == 'N'.code) {
                val match = if (negative) "-INF" else "+INF"
                matchToken(match, 3)

                return if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    resetAsNaN(match, if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY)
                } else {
                    reportError(
                            "Non-standard token '$match': enable `CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
                }
            } else if (ch == 'n'.code) {
                val match = if (negative) "-Infinity" else "+Infinity"
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
    protected fun parseName(): String? {
        var pointer = myInputPointer
        var hash = myHashSeed
        val codes = INPUT_CODE_LATIN1

        while (pointer < myInputEnd) {
            val ch = myInputBuffer[pointer].code

            if (ch < codes.size && codes[ch] != 0) {
                if (ch == '"'.code) {
                    val start = myInputPointer
                    myInputPointer = pointer + 1
                    return mySymbols.findSymbol(myInputBuffer, start, pointer - start, hash)
                }

                break
            }

            hash = hash * CharsToNameCanonicalizer.HASH_MULT + ch
            ++pointer
        }

        val start = myInputPointer
        myInputPointer = pointer
        return parseName(start, hash, CODE_QUOTE)
    }

    @Throws(CirJacksonException::class)
    protected fun parseName(startPointer: Int, hash: Int, endChar: Int): String? {
        var realHash = hash

        myTextBuffer.resetWithShared(myInputBuffer, startPointer, myInputPointer - startPointer)

        var outputBuffer = myTextBuffer.currentSegment
        var outputPointer = myTextBuffer.currentSegmentSize

        while (true) {
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF("in property name", CirJsonToken.PROPERTY_NAME)
                }
            }

            var c = myInputBuffer[myInputPointer++]
            val code = c.code

            if (code <= CODE_BACKSLASH) {
                if (code == CODE_BACKSLASH) {
                    c = decodeEscaped()
                } else if (code <= endChar) {
                    if (code == endChar) {
                        break
                    }

                    if (code < CODE_SPACE) {
                        throwUnquotedSpace(code, "name")
                    }
                }
            }

            realHash = realHash * CharsToNameCanonicalizer.HASH_MULT + code

            outputBuffer[outputPointer++] = c

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }
        }

        myTextBuffer.currentSegmentSize = outputPointer
        val textBuffer = myTextBuffer
        val buffer = textBuffer.textBuffer
        val start = textBuffer.textOffset
        val length = textBuffer.size
        return mySymbols.findSymbol(buffer, start, length, realHash)
    }

    /**
     * Method called when we see non-white space character other than double quote, when expecting an Object property
     * name. In standard mode will just throw an exception; but in non-standard modes may be able to parse name.
     *
     * @param i First not-yet-decoded character of possible "odd name" to decode
     *
     * @return Name decoded, if allowed and successful
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun handleOddName(i: Int): String? {
        if (i == '\''.code && isEnabled(CirJsonReadFeature.ALLOW_SINGLE_QUOTES)) {
            return parseApostropheName()
        }

        if (!isEnabled(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)) {
            return reportUnexpectedChar(i.toChar(), "was expecting double-quote to start property name")
        }

        val codes = INPUT_CODE_LATIN1_JS_NAMES
        val maxCode = codes.size

        val firstOk = if (i < maxCode) {
            codes[i] == 0
        } else {
            i.toChar().isJavaIdentifierPart()
        }

        if (!firstOk) {
            return reportUnexpectedChar(i.toChar(),
                    "was expecting either valid name character (for unquoted name) or double-quote (for quoted) to start property name")
        }

        var pointer = myInputPointer
        var hash = myHashSeed
        val inputLength = myInputEnd

        if (pointer < inputLength) {
            do {
                val c = myInputBuffer[pointer]
                val ch = c.code

                if (ch < maxCode && codes[ch] != 0 || !c.isJavaIdentifierPart()) {
                    val start = myInputPointer - 1
                    myInputPointer = pointer
                    return mySymbols.findSymbol(myInputBuffer, start, pointer - start, hash)
                }

                hash = hash * CharsToNameCanonicalizer.HASH_MULT + ch
                ++pointer
            } while (pointer < inputLength)
        }

        val start = myInputPointer - 1
        myInputPointer = pointer
        return handleOddName(start, hash, codes)
    }

    @Throws(CirJacksonException::class)
    protected fun parseApostropheName(): String? {
        var pointer = myInputPointer
        var hash = myHashSeed
        val inputLength = myInputEnd
        val codes = INPUT_CODE_LATIN1
        val maxCode = codes.size

        if (pointer < inputLength) {
            do {
                val ch = myInputBuffer[pointer].code

                if (ch == CODE_APOSTROPHE) {
                    val start = myInputPointer
                    myInputPointer = pointer + 1
                    return mySymbols.findSymbol(myInputBuffer, start, pointer - start, hash)
                }

                if (ch < maxCode && codes[ch] != 0) {
                    break
                }

                hash = hash * CharsToNameCanonicalizer.HASH_MULT + ch
                ++pointer
            } while (pointer < inputLength)
        }

        val start = myInputPointer
        myInputPointer = pointer
        return parseName(start, hash, '\''.code)
    }

    /**
     * Method for handling cases where first non-space character of an expected value token is not legal for standard
     * CirJSON content.
     *
     * @param i First undecoded character of possible "odd value" to decode
     *
     * @return Type of value decoded, if allowed and successful
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun handleOddValue(i: Int): CirJsonToken? {
        return when (i) {
            CODE_APOSTROPHE -> {
                if (isEnabled(CirJsonReadFeature.ALLOW_SINGLE_QUOTES)) {
                    handleApostrophe()
                } else if (i.toChar().isJavaIdentifierPart()) {
                    reportInvalidToken(i.toChar().toString(), validCirJsonTokenList())
                } else {
                    reportUnexpectedChar(i.toChar(), "expected a valid value ${validCirJsonValueList()}")
                }
            }

            CODE_R_BRACKET, CODE_COMMA -> {
                if (i == CODE_COMMA || streamReadContext!!.isInArray) {
                    if (!streamReadContext!!.isInRoot) {
                        if (formatReadFeatures and FEAT_MASK_ALLOW_MISSING != 0) {
                            --myInputPointer
                            return CirJsonToken.VALUE_NULL
                        }
                    }
                }

                if (i.toChar().isJavaIdentifierPart()) {
                    reportInvalidToken(i.toChar().toString(), validCirJsonTokenList())
                } else {
                    reportUnexpectedChar(i.toChar(), "expected a valid value ${validCirJsonValueList()}")
                }
            }

            'N'.code -> {
                matchToken("NaN", 1)

                if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    resetAsNaN("NaN", Double.NaN)
                } else {
                    reportError(
                            "Non-standard token 'NaN': enable `CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
                }
            }

            'I'.code -> {
                matchToken("Infinity", 1)

                if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    resetAsNaN("Infinity", Double.POSITIVE_INFINITY)
                } else {
                    reportError(
                            "Non-standard token 'Infinity': enable `CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
                }
            }

            '+'.code -> {
                if (myInputPointer >= myInputEnd) {
                    if (!loadMore()) {
                        return reportInvalidEOFInValue(CirJsonToken.PROPERTY_NAME)
                    }
                }

                handleInvalidNumberStart(myInputBuffer[myInputPointer++].code, negative = false, hasSign = true)
            }

            else -> {
                if (i.toChar().isJavaIdentifierPart()) {
                    reportInvalidToken(i.toChar().toString(), validCirJsonTokenList())
                } else {
                    reportUnexpectedChar(i.toChar(), "expected a valid value ${validCirJsonValueList()}")
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    protected fun handleApostrophe(): CirJsonToken? {
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        var outputPointer = myTextBuffer.currentSegmentSize

        while (true) {
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF(": was expecting closing quote for a string value",
                            CirJsonToken.VALUE_STRING)
                }
            }

            var c = myInputBuffer[myInputPointer++]
            val ch = c.code

            if (ch <= CODE_BACKSLASH) {
                if (ch == CODE_BACKSLASH) {
                    c = decodeEscaped()
                } else if (ch <= CODE_APOSTROPHE) {
                    if (ch == CODE_APOSTROPHE) {
                        break
                    }

                    if (ch < CODE_SPACE) {
                        throwUnquotedSpace(ch, "string value")
                    }
                }
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c
        }

        myTextBuffer.currentSegmentSize = outputPointer
        return CirJsonToken.VALUE_STRING
    }

    private fun handleOddName(startPointer: Int, hash: Int, codes: IntArray): String {
        var realHash = hash

        myTextBuffer.resetWithShared(myInputBuffer, startPointer, myInputPointer - startPointer)

        var outputBuffer = myTextBuffer.currentSegment
        var outputPointer = myTextBuffer.currentSegmentSize
        val maxCode = codes.size

        while (true) {
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    break
                }
            }

            val c = myInputBuffer[myInputPointer]
            val ch = c.code

            if (ch < maxCode && codes[ch] != 0 || !c.isJavaIdentifierPart()) {
                break
            }

            ++myInputPointer
            realHash = realHash * CharsToNameCanonicalizer.HASH_MULT + ch

            outputBuffer[outputPointer++] = c

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }
        }

        myTextBuffer.currentSegmentSize = outputPointer
        val textBuffer = myTextBuffer
        val buffer = textBuffer.textBuffer
        val start = textBuffer.textOffset
        val length = textBuffer.size
        return mySymbols.findSymbol(buffer, start, length, realHash)
    }

    @Throws(CirJacksonException::class)
    protected fun finishString() {
        var pointer = myInputPointer
        val inputLength = myInputEnd
        val codes = INPUT_CODE_LATIN1
        val maxCode = codes.size

        if (pointer < inputLength) {
            do {
                val ch = myInputBuffer[pointer].code

                if (ch < maxCode && codes[ch] != 0) {
                    if (ch == CODE_QUOTE) {
                        myTextBuffer.resetWithShared(myInputBuffer, myInputPointer, pointer - myInputPointer)
                    }

                    break
                }

                ++pointer
            } while (pointer < inputLength)
        }

        myTextBuffer.resetWithCopy(myInputBuffer, myInputPointer, pointer - myInputPointer)
        myInputPointer = pointer
        finishString2()
    }

    @Throws(CirJacksonException::class)
    protected fun finishString2() {
        var outputBuffer = myTextBuffer.currentSegment
        var outputPointer = myTextBuffer.currentSegmentSize
        val codes = INPUT_CODE_LATIN1
        val maxCode = codes.size

        while (true) {
            if (myInputPointer >= myInputEnd) {
                if (!loadMore()) {
                    return reportInvalidEOF(": was expecting closing quote for a string value",
                            CirJsonToken.VALUE_STRING)
                }
            }

            var c = myInputBuffer[myInputPointer++]
            val ch = c.code

            if (ch < maxCode && codes[ch] != 0) {
                if (ch == CODE_BACKSLASH) {
                    c = decodeEscaped()
                } else if (ch == CODE_QUOTE) {
                    break
                } else if (ch < CODE_SPACE) {
                    throwUnquotedSpace(ch, "string value")
                }
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c
        }

        myTextBuffer.currentSegmentSize = outputPointer
    }

    /**
     * Method called to skim through rest of unparsed String value, if it is not needed. This can be done a bit faster
     * if contents need not be stored for future access.
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun skipString() {
        myIsTokenIncomplete = false

        var inputPointer = myInputPointer
        var inputLength = myInputEnd
        val inputBuffer = myInputBuffer

        while (true) {
            if (inputPointer >= inputLength) {
                myInputPointer = inputPointer

                if (!loadMore()) {
                    return reportInvalidEOF(": was expecting closing quote for a string value",
                            CirJsonToken.VALUE_STRING)
                }

                inputPointer = myInputPointer
                inputLength = myInputEnd
            }

            val ch = inputBuffer[inputPointer++].code

            if (ch <= CODE_BACKSLASH) {
                if (ch == CODE_BACKSLASH) {
                    myInputPointer = inputPointer
                    decodeEscaped()
                    inputPointer = myInputPointer
                    inputLength = myInputEnd
                } else if (ch <= CODE_APOSTROPHE) {
                    if (ch == CODE_QUOTE) {
                        myInputPointer = inputPointer
                        break
                    }

                    if (ch < CODE_SPACE) {
                        myInputPointer = inputPointer
                        throwUnquotedSpace(ch, "string value")
                    }
                }
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun skipCR() {
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
    private fun skipColonFast(pointer: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipComma(i: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipAfterComma(): Int {
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
    private fun skipComment(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipCComment(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipYAMLComment(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipLine() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun decodeEscaped(): Char {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchTrue() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchFalse() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchNull() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun matchToken(matchString: String, i: Int) {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun matchToken2(matchString: String, i: Int) {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun checkMatchEnd(matchString: String, i: Int, c: Int) {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Binary access
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
        TODO("Not yet implemented")
    }

    override fun currentLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun currentLocationMinusOne(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    private fun updateLocation() {
        TODO("Not yet implemented")
    }

    private fun updateNameLocation() {
        TODO("Not yet implemented")
    }

    override val objectId: Any?
        get() = null

    override val typeId: Any?
        get() = null

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

        while (myInputPointer < myInputEnd) {
            val c = myInputBuffer[myInputPointer++]

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

    companion object {

        private val FEAT_MASK_TRAILING_COMMA = CirJsonReadFeature.ALLOW_TRAILING_COMMA.mask

        private val FEAT_MASK_ALLOW_MISSING = CirJsonReadFeature.ALLOW_MISSING_VALUES.mask

        /**
         * Latin1 encoding is not supported, but we do use 8-bit subset for pre-processing task, to simplify first pass,
         * keep it fast.
         */
        private val INPUT_CODE_LATIN1 = CharTypes.inputCodeLatin1

        private val INPUT_CODE_LATIN1_JS_NAMES = CharTypes.inputCodeLatin1JsNames

    }

}