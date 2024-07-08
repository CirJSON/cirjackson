package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.ParserBase
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet

/**
 * Another intermediate base class, only used by actual CirJSON-backed parser implementations.
 *
 * @property formatReadFeatures Bit flag for [CirJsonReadFeature]s that are enabled.
 */
abstract class CirJsonParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
        protected var formatReadFeatures: Int) : ParserBase(objectReadContext, ioContext, streamReadFeatures) {

    override var streamReadContext: CirJsonReadContext? = CirJsonReadContext.createRootContext(
            if (StreamReadFeature.STRICT_DUPLICATE_DETECTION.isEnabledIn(streamReadFeatures)) {
                DuplicateDetector.rootDetector(this)
            } else {
                null
            })
        protected set

    /**
     * Secondary token related to the next token after current one; used if its type is known. This may be value token
     * that follows [CirJsonToken.PROPERTY_NAME], for example.
     */
    protected var myNextToken: CirJsonToken? = null

    /**
     * Temporary buffer that is needed if an Object property name is accessed using [textCharacters] method (instead of
     * String returning alternatives)
     */
    private var myNameCopyBuffer = NO_CHARS

    /**
     * Flag set to indicate whether the Object property name is available from the name copy buffer or not (in addition
     * to its String representation being available via read context)
     */
    protected var myIsNameCopied = false

    /*
     *******************************************************************************************************************
     * Versioned, capabilities, config
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    override val streamReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>
        get() = DEFAULT_READ_CAPABILITIES

    companion object {

        private val NO_CHARS = charArrayOf()

    }

}