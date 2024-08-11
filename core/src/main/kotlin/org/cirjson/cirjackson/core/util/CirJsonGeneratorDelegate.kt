package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.CharacterEscapes
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger

/**
 * @param delegate Underlying generator to delegate calls to
 *
 * @param isDelegateHandlingCopyMethods Flag assigned to `myIsDelegateHandlingCopyMethods` and which defines whether
 * copy methods are handled locally (false), or delegated to configured
 */
open class CirJsonGeneratorDelegate(delegate: CirJsonGenerator, isDelegateHandlingCopyMethods: Boolean) :
        CirJsonGenerator() {

    /**
     * Underlying generator that calls are delegated to
     */
    var delegate = delegate
        protected set

    /**
     * Whether copy methods ([copyCurrentEvent], [copyCurrentStructure], [writeTree] and [writePOJO]) are to be called
     * (`true`), or handled by this object (`false`).
     */
    protected var myIsDelegateHandlingCopyMethods = isDelegateHandlingCopyMethods

    constructor(delegate: CirJsonGenerator) : this(delegate, true)

    override fun currentValue(): Any? {
        return delegate.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        delegate.assignCurrentValue(value)
    }

    /*
     *******************************************************************************************************************
     * Public API, metadata
     *******************************************************************************************************************
     */

    override val schema: FormatSchema?
        get() = delegate.schema

    override fun version(): Version {
        return delegate.version()
    }

    override val streamWriteOutputTarget: Any?
        get() = delegate.streamWriteOutputTarget

    override val streamWriteOutputBuffered: Int
        get() = delegate.streamWriteOutputBuffered

    /*
     *******************************************************************************************************************
     * Public API, capability introspection
     *******************************************************************************************************************
     */

    override val isAbleWriteTypeId: Boolean
        get() = delegate.isAbleWriteTypeId

    override val isAbleOmitProperties: Boolean
        get() = delegate.isAbleOmitProperties

    override val streamWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>
        get() = delegate.streamWriteCapabilities

    /*
     *******************************************************************************************************************
     * Public API, configuration
     *******************************************************************************************************************
     */

    override fun isEnabled(feature: StreamWriteFeature): Boolean {
        return delegate.isEnabled(feature)
    }

    override val streamWriteFeatures: Int
        get() = delegate.streamWriteFeatures

    override fun configure(feature: StreamWriteFeature, state: Boolean): CirJsonGenerator {
        delegate.configure(feature, state)
        return this
    }

    /*
     *******************************************************************************************************************
     * Configuring generator
     *******************************************************************************************************************
     */

    override val highestNonEscapedChar: Int
        get() = delegate.highestNonEscapedChar

    override var characterEscapes: CharacterEscapes?
        get() = delegate.characterEscapes
        set(value) {
            super.characterEscapes = value
        }

    /*
     *******************************************************************************************************************
     * Public API, write methods, structural
     *******************************************************************************************************************
     */

    override val idName: String
        get() = delegate.idName

    @Throws(CirJacksonException::class)
    override fun getID(target: Any, isArray: Boolean): String {
        return delegate.getID(target, isArray)
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeObjectId(referenced: Any): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeArrayId(referenced: Any): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writePropertyId(id: Long): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: IntArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: LongArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: DoubleArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: Array<String>, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, text/String values
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeString(value: String?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(value: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, binary/raw content
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, other value types
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Short): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Long): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigInteger?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Double): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Float): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigDecimal?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValue: String?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNull(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, convenience property-write methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeOmittedProperty(propertyName: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, Native Ids
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeEmbeddedObject(obj: Any?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeTypeId(id: Any): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, serializing objects
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writePOJO(pojo: Any?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(rootNode: TreeNode?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, copy-through methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun copyCurrentEvent(parser: CirJsonParser) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun copyCurrentStructure(parser: CirJsonParser) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, context access
     *******************************************************************************************************************
     */

    override val streamWriteContext: TokenStreamContext
        get() = delegate.streamWriteContext

    override val objectWriteContext: ObjectWriteContext
        get() = delegate.objectWriteContext

    /*
     *******************************************************************************************************************
     * Public API, buffer handling
     *******************************************************************************************************************
     */

    override fun close() {
        delegate.close()
    }

    override fun flush() {
        delegate.flush()
    }

    /*
     *******************************************************************************************************************
     * Closeable implementation
     *******************************************************************************************************************
     */

    override val isClosed: Boolean
        get() = delegate.isClosed

}