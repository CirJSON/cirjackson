package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import java.io.DataInput
import java.io.OutputStream

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
class UTF8DataInputCirJsonParser(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
        formatReadFeatures: Int, protected var myInputData: DataInput, protected val mySymbols: ByteQuadsCanonicalizer,
        protected var myNextByte: Int) :
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

    override fun currentTokenLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun currentLocation(): CirJsonLocation {
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

    companion object {

        /**
         * This is the main input-code lookup table, fetched eagerly
         */
        private val INPUT_CODE_UTF8 = CharTypes.inputCodeUtf8

        private val INPUT_CODE_COMMENT = CharTypes.inputCodeComment

        /**
         * Latin1 encoding is not supported, but we do use 8-bit subset for pre-processing task, to simplify first pass,
         * keep it fast.
         */
        private val INPUT_CODE_LATIN1 = CharTypes.inputCodeLatin1

    }

}