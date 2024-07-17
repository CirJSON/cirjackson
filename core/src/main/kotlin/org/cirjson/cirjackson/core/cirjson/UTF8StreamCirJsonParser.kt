package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import java.io.InputStream

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