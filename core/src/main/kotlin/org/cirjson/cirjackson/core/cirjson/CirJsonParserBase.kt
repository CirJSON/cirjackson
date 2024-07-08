package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.base.ParserBase
import org.cirjson.cirjackson.core.io.IOContext

/**
 * Another intermediate base class, only used by actual CirJSON-backed parser implementations.
 *
 * @property formatReadFeatures Bit flag for [CirJsonReadFeature]s that are enabled.
 */
abstract class CirJsonParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
        protected var formatReadFeatures: Int) : ParserBase(objectReadContext, ioContext, streamReadFeatures) {

    /**
     * Secondary token related to the next token after current one; used if its type is known. This may be value token
     * that follows [CirJsonToken.PROPERTY_NAME], for example.
     */
    protected var myNextToken: CirJsonToken? = null

    companion object {

        private val NO_CHARS = charArrayOf()

    }

}