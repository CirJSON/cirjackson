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
        delegate.writeStartArray()
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        delegate.writeStartArray(currentValue)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        delegate.writeStartArray(currentValue, size)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(): CirJsonGenerator {
        delegate.writeEndArray()
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(): CirJsonGenerator {
        delegate.writeStartObject()
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        delegate.writeStartObject(currentValue)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        delegate.writeStartObject(currentValue, size)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(): CirJsonGenerator {
        delegate.writeEndObject()
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeObjectId(referenced: Any): CirJsonGenerator {
        delegate.writeObjectId(referenced)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeArrayId(referenced: Any): CirJsonGenerator {
        delegate.writeArrayId(referenced)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: String): CirJsonGenerator {
        delegate.writeName(name)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        delegate.writeName(name)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writePropertyId(id: Long): CirJsonGenerator {
        delegate.writePropertyId(id)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: IntArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeArray(array, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: LongArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeArray(array, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: DoubleArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeArray(array, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeArray(array: Array<String>, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeArray(array, offset, length)
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, text/String values
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeString(value: String?): CirJsonGenerator {
        delegate.writeString(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        delegate.writeString(reader, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeString(buffer, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(value: SerializableString): CirJsonGenerator {
        delegate.writeString(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeRawUTF8String(buffer, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeUTF8String(buffer, offset, length)
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, binary/raw content
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String): CirJsonGenerator {
        delegate.writeRaw(text)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeRaw(text, offset, length)
        return this
    }

    override fun writeRaw(raw: SerializableString): CirJsonGenerator {
        delegate.writeRaw(raw)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        delegate.writeRaw(char)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeRaw(buffer, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String): CirJsonGenerator {
        delegate.writeRawValue(text)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeRawValue(text, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeRawValue(text, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        delegate.writeBinary(variant, data, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        return delegate.writeBinary(variant, data, dataLength)
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, other value types
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Short): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Int): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Long): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigInteger?): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Double): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Float): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigDecimal?): CirJsonGenerator {
        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValue: String?): CirJsonGenerator {
        delegate.writeNumber(encodedValue)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        delegate.writeBoolean(state)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNull(): CirJsonGenerator {
        delegate.writeNull()
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, convenience property-write methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeOmittedProperty(propertyName: String): CirJsonGenerator {
        delegate.writeOmittedProperty(propertyName)
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, Native Ids
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeEmbeddedObject(obj: Any?): CirJsonGenerator {
        delegate.writeEmbeddedObject(obj)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeTypeId(id: Any): CirJsonGenerator {
        delegate.writeTypeId(id)
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, serializing objects
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writePOJO(pojo: Any?): CirJsonGenerator {
        if (myIsDelegateHandlingCopyMethods) {
            delegate.writePOJO(pojo)
            return this
        }

        if (pojo == null) {
            writeNull()
        } else {
            objectWriteContext.writeValue(this, pojo)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(rootNode: TreeNode?): CirJsonGenerator {
        if (myIsDelegateHandlingCopyMethods) {
            delegate.writeTree(rootNode)
            return this
        }

        if (rootNode == null) {
            writeNull()
        } else {
            objectWriteContext.writeTree(this, rootNode)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, copy-through methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun copyCurrentEvent(parser: CirJsonParser) {
        if (myIsDelegateHandlingCopyMethods) {
            delegate.copyCurrentEvent(parser)
        } else {
            super.copyCurrentEvent(parser)
        }
    }

    @Throws(CirJacksonException::class)
    override fun copyCurrentStructure(parser: CirJsonParser) {
        if (myIsDelegateHandlingCopyMethods) {
            delegate.copyCurrentStructure(parser)
        } else {
            super.copyCurrentStructure(parser)
        }
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