package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.IOContext

/**
 * This base class implements part of API that a CirJSON generator exposes to applications, adds shared internal methods
 * that subclasses can use and adds some abstract methods subclasses must implement.
 */
abstract class GeneratorBase(override val objectWriteContext: ObjectWriteContext, val ioContext: IOContext,
        streamWriteFeatures: Int) : CirJsonGenerator() {

    override var streamWriteFeatures: Int = streamWriteFeatures
        protected set

    override val streamWriteConstraints: StreamWriteConstraints = ioContext.streamWriteConstraints

    /**
     * Flag that indicates whether generator is closed or not. Gets set when it is closed by an explicit call ([close]).
     */
    override var isClosed = false
        protected set

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    override fun isEnabled(feature: StreamWriteFeature): Boolean {
        return streamWriteFeatures and feature.mask != 0
    }

    override fun configure(feature: StreamWriteFeature, state: Boolean): CirJsonGenerator {
        streamWriteFeatures = if (state) {
            streamWriteFeatures or feature.mask
        } else {
            streamWriteFeatures and feature.mask.inv()
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, structural
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        return writeStartArray(currentValue)
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        return writeStartObject(currentValue)
    }

    companion object {

        const val WRITE_BINARY = "write a binary value"

        const val WRITE_BOOLEAN = "write a boolean value"

        const val WRITE_NULL = "write a null"

        const val WRITE_NUMBER = "write a number"

        const val WRITE_RAW = "write a raw (unencoded) value"

        const val WRITE_STRING = "write a string"

    }

}