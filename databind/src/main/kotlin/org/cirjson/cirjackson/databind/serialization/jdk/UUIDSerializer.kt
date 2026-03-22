package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.StreamWriteCapability
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonValueFormat
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import java.util.*

/**
 * Specialized [ValueSerializer] to output [UUIDs][UUID]. Beyond optimized access and writing of textual representation
 * (which is the default handling in most cases), it will alternatively allow serialization using raw binary output (as
 * 16-byte block) if underlying data format has efficient means to access that.
 *
 * @property myAsBinary Configuration setting that indicates if serialization as binary (native or Base64-encoded) has
 * been forced; `null` means "use default heuristic"
 */
open class UUIDSerializer protected constructor(protected val myAsBinary: Boolean?) :
        StandardScalarSerializer<UUID>(UUID::class) {

    constructor() : this(null)

    override fun isEmpty(provider: SerializerProvider, value: UUID?): Boolean {
        return value!!.leastSignificantBits == 0L && value.mostSignificantBits == 0L
    }

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val format = findFormatOverrides(provider, property, handledType()!!)
        val shape = format.shape

        val asBinary = when (shape) {
            CirJsonFormat.Shape.BINARY -> true
            CirJsonFormat.Shape.STRING -> false
            else -> null
        }

        if (myAsBinary == asBinary) {
            return this
        }

        return UUIDSerializer(asBinary)
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: UUID, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (writeAsBinary(serializers)) {
            generator.writeBinary(asBytes(value))
            return
        }

        val chars = CharArray(36)
        val msb = value.mostSignificantBits
        appendInt((msb shr 32).toInt(), chars, 0)
        chars[8] = '-'
        val int = msb.toInt()
        appendShort(int ushr 16, chars, 9)
        chars[13] = '-'
        appendShort(int, chars, 14)
        chars[18] = '-'

        val lsb = value.leastSignificantBits
        appendShort((lsb ushr 48).toInt(), chars, 19)
        chars[23] = '-'
        appendShort((lsb ushr 32).toInt(), chars, 24)
        appendInt(lsb.toInt(), chars, 28)

        generator.writeString(chars, 0, 36)
    }

    protected open fun writeAsBinary(context: SerializerProvider): Boolean {
        return myAsBinary ?: context.isEnabled(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitStringFormat(visitor, typeHint, CirJsonValueFormat.UUID)
    }

    companion object {

        val HEX_CHARS = "0123456789abcdef".toCharArray()

        private fun appendInt(bits: Int, chars: CharArray, offset: Int) {
            appendShort(bits shr 16, chars, offset)
            appendShort(bits, chars, offset + 4)
        }

        private fun appendShort(bits: Int, chars: CharArray, offset: Int) {
            var i = offset
            chars[i] = HEX_CHARS[(bits shr 12) and 0xF]
            chars[++i] = HEX_CHARS[(bits shr 8) and 0xF]
            chars[++i] = HEX_CHARS[(bits shr 4) and 0xF]
            chars[++i] = HEX_CHARS[bits and 0xF]
        }

        private fun asBytes(uuid: UUID): ByteArray {
            val buffer = ByteArray(16)
            val hi = uuid.mostSignificantBits
            val lo = uuid.leastSignificantBits
            appendInt((hi shr 32).toInt(), buffer, 0)
            appendInt(hi.toInt(), buffer, 4)
            appendInt((lo shr 32).toInt(), buffer, 8)
            appendInt(lo.toInt(), buffer, 12)
            return buffer
        }

        private fun appendInt(bits: Int, buffer: ByteArray, offset: Int) {
            var i = offset
            buffer[i] = (bits shr 24).toByte()
            buffer[++i] = (bits shr 16).toByte()
            buffer[++i] = (bits shr 8).toByte()
            buffer[++i] = bits.toByte()
        }

    }

}