package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.FromStringDeserializer
import org.cirjson.cirjackson.databind.exception.InvalidFormatException
import java.util.*

open class UUIDDeserializer : FromStringDeserializer<UUID>(UUID::class) {

    @Throws(CirJacksonException::class)
    override fun deserialize(value: String, context: DeserializationContext): UUID? {
        if (value.length != 36) {
            return if (value.length == 24) {
                val id = convertFromUrlSafe(value)
                val bytes = Base64Variants.defaultVariant.decode(id)
                fromBytes(bytes, context)
            } else if (value.length == 22) {
                val id = convertToUrlSafe(value)
                val bytes = Base64Variants.MODIFIED_FOR_URL.decode(id)
                fromBytes(bytes, context)
            } else {
                badFormat(value, context)
            }
        }

        if (value[8] != '-' || value[13] != '-' || value[18] != '-' || value[23] != '-') {
            badFormat(value, context)
        }

        var l1 = intFromChars(value, 0, context).toLong()
        l1 = l1 shl 32
        var l2 = shortFromChars(value, 9, context).toLong() shl 16
        l2 = l2 or shortFromChars(value, 14, context).toLong()
        val hi = l1 + l2

        l1 = shortFromChars(value, 19, context).toLong() shl 16 or shortFromChars(value, 24, context).toLong()
        l1 = l1 shl 32
        l2 = intFromChars(value, 28, context).toLong()
        l2 = l2 shl 32 ushr 32
        val lo = l1 or l2

        return UUID(hi, lo)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeEmbedded(obj: Any, context: DeserializationContext): UUID? {
        return if (obj !is ByteArray) {
            super.deserializeEmbedded(obj, context)
        } else {
            fromBytes(obj, context)
        }
    }

    @Throws(CirJacksonException::class)
    private fun badFormat(uuidString: String, context: DeserializationContext): UUID? {
        return context.handleWeirdStringValue(handledType(), uuidString,
                "UUID has to be represented by standard 36-char representation") as UUID?
    }

    protected open fun intFromChars(string: String, index: Int, context: DeserializationContext): Int {
        return (byteFromChars(string, index, context) shl 24) + (byteFromChars(string, index + 2,
                context) shl 16) + (byteFromChars(string, index + 4, context) shl 8) + byteFromChars(string, index + 6,
                context)
    }

    protected open fun shortFromChars(string: String, index: Int, context: DeserializationContext): Int {
        return (byteFromChars(string, index, context) shl 8) + byteFromChars(string, index + 2, context)
    }

    protected open fun byteFromChars(string: String, index: Int, context: DeserializationContext): Int {
        val c1 = string[index]
        val codeC1 = c1.code
        val c2 = string[index + 1]
        val codeC2 = c2.code

        if (codeC1 <= 127 && codeC2 <= 127) {
            val hex = (HEX_DIGITS[codeC1] shl 4) or HEX_DIGITS[codeC2]

            if (hex >= 0) {
                return hex
            }
        }

        if (codeC1 >= 127 || HEX_DIGITS[codeC1] == -1) {
            badChar(string, index, context, c1)
        } else {
            badChar(string, index + 1, context, c2)
        }
    }

    protected open fun badChar(uuidString: String, index: Int, context: DeserializationContext, char: Char): Nothing {
        throw context.weirdStringException(uuidString, handledType(),
                "Non-hex character '$char' (value 0x${char.code.toString(16)}), not valid for UUID String")
    }

    private fun fromBytes(bytes: ByteArray, context: DeserializationContext): UUID {
        if (bytes.size != 16) {
            throw InvalidFormatException.from(context.parser,
                    "Can only construct UUIDs from ByteArray[16]; got ${bytes.size} bytes", bytes, handledType())
        }

        return UUID(long(bytes, 0), long(bytes, 8))
    }

    private fun convertToUrlSafe(base64: String): String {
        return base64.replace('+', '-').replace('/', '_')
    }

    private fun convertFromUrlSafe(base64: String): String {
        return base64.replace('-', '+').replace('_', '/')
    }

    companion object {

        val HEX_DIGITS = Array(127) { -1 }.apply {
            for (i in 0..<10) {
                this['0'.code + i] = i
            }

            for (i in 0..<6) {
                this['a'.code + i] = 10 + i
                this['A'.code + i] = 10 + i
            }
        }

        private fun long(bytes: ByteArray, offset: Int): Long {
            val l1 = int(bytes, offset).toLong() shl 32
            val l2 = int(bytes, offset + 4).toLong() shl 32 ushr 32
            return l1 or l2
        }

        private fun int(bytes: ByteArray, offset: Int): Int {
            return (bytes[offset].toInt() shl 24) or (bytes[offset + 1].toInt() and 0xFF shl 16) or
                    (bytes[offset + 2].toInt() and 0xFF shl 8) or (bytes[offset + 3].toInt() and 0xFF)
        }

    }

}