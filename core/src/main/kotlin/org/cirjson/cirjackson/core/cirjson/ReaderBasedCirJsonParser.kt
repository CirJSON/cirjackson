package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.CharsToNameCanonicalizer
import java.io.Reader

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

    override fun currentTokenLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun closeInput() {
        TODO("Not yet implemented")
    }

    override fun currentLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun streamReadInputSource(): Any? {
        TODO("Not yet implemented")
    }

    override fun nextToken(): CirJsonToken? {
        TODO("Not yet implemented")
    }

    override val text: String?
        get() = TODO("Not yet implemented")

    override val textCharacters: CharArray?
        get() = TODO("Not yet implemented")

    override val textLength: Int
        get() = TODO("Not yet implemented")

    override val textOffset: Int
        get() = TODO("Not yet implemented")

    override val objectId: Any?
        get() = TODO("Not yet implemented")

    override val typeId: Any?
        get() = TODO("Not yet implemented")

}