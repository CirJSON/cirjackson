package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.UTF8Writer
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import org.cirjson.cirjackson.core.util.Other
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal

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

    /*
     *******************************************************************************************************************
     * Public API, write methods, textual
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        return writeName(name.value)
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    @Throws(CirJacksonException::class)
    override fun writeString(value: SerializableString): CirJsonGenerator {
        return writeString(value.value)
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String): CirJsonGenerator {
        verifyValueWrite(WRITE_RAW_VALUE)
        return writeRaw(text)
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator {
        verifyValueWrite(WRITE_RAW_VALUE)
        return writeRaw(text, offset, length)
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator {
        verifyValueWrite(WRITE_RAW_VALUE)
        return writeRaw(text, offset, length)
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(raw: SerializableString): CirJsonGenerator {
        verifyValueWrite(WRITE_RAW_VALUE)
        return writeRaw(raw)
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, POJOs, trees
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writePOJO(pojo: Any?): CirJsonGenerator {
        if (pojo == null) {
            writeNull()
        } else {
            objectWriteContext.writeValue(this, pojo)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(rootNode: TreeNode?): CirJsonGenerator {
        if (rootNode == null) {
            writeNull()
        } else {
            objectWriteContext.writeTree(this, rootNode)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, low-level output handling
     *******************************************************************************************************************
     */

    override fun close() {
        if (isClosed) {
            return
        }

        try {
            closeInput()
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        } finally {
            releaseBuffers()
            ioContext.close()
            isClosed = true
        }
    }

    /*
     *******************************************************************************************************************
     * Package methods for this, subclasses
     *******************************************************************************************************************
     */

    @Throws(IOException::class)
    protected abstract fun closeInput()

    /**
     * Method called to release any buffers generator may be holding, once generator is being closed.
     */
    protected abstract fun releaseBuffers()

    /**
     * Method called before trying to write a value (scalar or structured), to verify that this is legal in current
     * output state, as well as to output separators if and as necessary.
     *
     * @param typeMessage Additional message used for generating exception message if value output is NOT legal in
     * current generator output state.
     *
     * @throws CirJacksonException if there is a problem in trying to write a value
     */
    @Throws(CirJacksonException::class)
    protected abstract fun verifyValueWrite(typeMessage: String)

    /**
     * Overridable factory method called to instantiate an appropriate [PrettyPrinter] for case of "just use the default
     * one", when default pretty printer handling enabled.
     *
     * @return Instance of "default" pretty printer to use
     */
    protected open fun constructDefaultPrettyPrinter(): PrettyPrinter {
        return DefaultPrettyPrinter()
    }

    /**
     * Helper method used to serialize a [BigDecimal] as a String, for serialization, taking into account configuration
     * settings
     *
     * @param value BigDecimal value to convert to String
     *
     * @return String representation of `value`
     *
     * @throws CirJacksonException if there is a problem serializing value as String
     */
    @Throws(CirJacksonException::class)
    protected fun asString(value: BigDecimal): String {
        if (!StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN.isEnabledIn(streamWriteFeatures)) {
            return value.toString()
        }

        val scale = value.scale()

        return if (scale in -MAX_BIG_DECIMAL_SCALE..MAX_BIG_DECIMAL_SCALE) {
            value.toPlainString()
        } else {
            reportError("Attempt to write plain `BigDecimal` (see " +
                    "CirJsonGenerator.Feature.WRITE_BIG_DECIMAL_AS_PLAIN) with illegal scale (%$scale): needs to be " +
                    "between [-$MAX_BIG_DECIMAL_SCALE, $MAX_BIG_DECIMAL_SCALE]")
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods: UTF-8 related
     *******************************************************************************************************************
     */

    protected fun decodeSurrogate(surrogate1: Int, surrogate2: Int): Int {
        return if (surrogate2 in UTF8Writer.SURR2_FIRST..UTF8Writer.SURR2_LAST) {
            (surrogate1 shl 10) + surrogate2 + UTF8Writer.SURROGATE_BASE
        } else {
            reportError("Incomplete surrogate pair: first char 0x${
                surrogate1.toString(16).uppercase().padStart(4, '0')
            }, second 0x${surrogate2.toString(16).uppercase().padStart(4, '0')}")
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods: input parameter validation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun checkRangeBoundsForByteArray(data: ByteArray?, offset: Int, len: Int) {
        data ?: return reportArgumentError("Invalid `ByteArray` argument: `null`")
        checkRange(data.size, offset, len, "ByteArray")
    }

    @Throws(CirJacksonException::class)
    protected fun checkRangeBoundsForCharArray(data: CharArray?, offset: Int, len: Int) {
        data ?: return reportArgumentError("Invalid `CharArray` argument: `null`")
        checkRange(data.size, offset, len, "CharArray")
    }

    @Throws(CirJacksonException::class)
    protected fun checkRangeBoundsForString(data: String?, offset: Int, len: Int) {
        data ?: return reportArgumentError("Invalid `String` argument: `null`")
        checkRange(data.length, offset, len, "String")
    }

    @Throws(CirJacksonException::class)
    private fun checkRange(dataLength: Int, offset: Int, length: Int, type: String) {
        val end = offset + length

        // Note: we are checking that:
        //
        // !(offset < 0)
        // !(len < 0)
        // !((offset + len) < 0) // int overflow!
        // !((offset + len) > dataLen) == !((dataLen - (offset+len)) < 0)
        //
        // All can be optimized by doing bitwise OR and checking for negative:
        val anyNegs = offset or length or end or (dataLength - end)

        if (anyNegs < 0) {
            return reportArgumentError(
                    "Invalid 'offset' ($offset) and/or 'length' ($length) arguments for `$type` of length $dataLength")
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods: error reporting
     *******************************************************************************************************************
     */

    protected fun throwInternal() {
        Other.throwInternal()
    }

    companion object {

        const val WRITE_BINARY = "write a binary value"

        const val WRITE_BOOLEAN = "write a boolean value"

        const val WRITE_NULL = "write a null"

        const val WRITE_NUMBER = "write a number"

        const val WRITE_RAW = "write a raw (unencoded) value"

        const val WRITE_RAW_VALUE = "write raw value"

        const val WRITE_STRING = "write a string"

        /**
         * This value is the limit of scale allowed for serializing [BigDecimal] in "plain" (non-engineering) notation;
         * intent is to prevent asymmetric attack whereupon simple eng-notation with big scale is used to generate huge
         * "plain" serialization. See [core#315] for details.
         */
        const val MAX_BIG_DECIMAL_SCALE = 9999

        /**
         * Default set of [StreamWriteCapabilities][StreamWriteCapability] that may be used as basis for format-specific
         * readers (or as bogus instance if non-null set needs to be passed).
         */
        val DEFAULT_WRITE_CAPABILITIES = CirJacksonFeatureSet.fromDefaults(StreamWriteCapability.entries)

        /**
         * Default set of [StreamWriteCapabilities][StreamWriteCapability] for typical textual formats, to use either
         * as-is, or as a base with possible differences.
         */
        val DEFAULT_TEXTUAL_WRITE_CAPABILITIES =
                DEFAULT_WRITE_CAPABILITIES.with(StreamWriteCapability.CAN_WRITE_FORMATTED_NUMBERS)

        /**
         * Default set of [StreamWriteCapabilities][StreamWriteCapability] for typical binary formats, to use either
         * as-is, or as a base with possible differences.
         */
        val DEFAULT_BINARY_WRITE_CAPABILITIES =
                DEFAULT_WRITE_CAPABILITIES.with(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY)

    }

}