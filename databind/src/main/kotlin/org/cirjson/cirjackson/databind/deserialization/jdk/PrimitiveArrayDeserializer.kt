package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsConstantProvider
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsFailProvider
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern
import org.cirjson.cirjackson.databind.util.componentType
import kotlin.reflect.KClass

/**
 * Container for deserializers used for instantiating "primitive arrays", arrays that contain non-object kotlin
 * primitive types.
 */
abstract class PrimitiveArrayDeserializer<T : Any> : StandardDeserializer<T> {

    /**
     * Handler we need for dealing with `null` values as elements
     */
    protected val myNullProvider: NullValueProvider?

    @Transient
    private var myEmptyValue: T? = null

    /**
     * Specific override for this instance (from proper, or global per-type overrides) to indicate whether single value
     * may be taken to mean an unwrapped one-element array or not. If `null`, left to global defaults.
     */
    protected val myUnwrapSingle: Boolean?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(valueClass: KClass<T>) : super(valueClass) {
        myNullProvider = null
        myUnwrapSingle = null
    }

    protected constructor(base: PrimitiveArrayDeserializer<*>, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(base.myValueClass) {
        myNullProvider = nullValueProvider
        myUnwrapSingle = unwrapSingle
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val unwrapSingle =
                findFormatFeature(context, property, myValueClass, CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        val nullStyle = findContentNullStyle(context, property)

        val nullValueProvider = when (nullStyle) {
            Nulls.SKIP -> NullsConstantProvider.skipper()

            Nulls.FAIL -> property?.let { NullsFailProvider.constructForProperty(it, it.type.contentType) }
                    ?: NullsFailProvider.constructForRootValue(context.constructType(myValueClass.componentType))

            else -> null
        }

        if (myUnwrapSingle == unwrapSingle && myNullProvider === nullValueProvider) {
            return this
        }

        return withResolved(nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to implement
     *******************************************************************************************************************
     */

    protected abstract fun withResolved(nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*>

    protected abstract fun constructEmpty(): T

    @Throws(CirJacksonException::class)
    protected abstract fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): T?

    protected abstract fun concatenate(oldValue: T, newValue: T): T

    protected abstract fun sizeOf(value: T): Int

    /*
     *******************************************************************************************************************
     * Default implementations
     *******************************************************************************************************************
     */

    override fun logicalType(): LogicalType {
        return LogicalType.ARRAY
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean {
        return true
    }

    override val emptyAccessPattern: AccessPattern
        get() = AccessPattern.CONSTANT

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return myEmptyValue ?: constructEmpty().also { myEmptyValue = it }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromArray(parser, context)
    }

    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: T): T? {
        val newValue = deserialize(parser, context)
        val size = sizeOf(intoValue)

        if (size == 0) {
            return newValue
        }

        return concatenate(intoValue, newValue!!)
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun handleNonArray(parser: CirJsonParser, context: DeserializationContext): T? {
        if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
            return deserializeFromString(parser, context)
        }

        val canWarp = myUnwrapSingle ?: context.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

        if (!canWarp) {
            return context.handleUnexpectedToken(getValueType(context), parser) as T?
        }

        return handleSingleElementUnwrapped(parser, context)
    }

    /*
     *******************************************************************************************************************
     * Actual deserializers
     *******************************************************************************************************************
     */

    @CirJacksonStandardImplementation
    internal class CharArrayDeserializer : PrimitiveArrayDeserializer<CharArray>(CharArray::class) {

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return this
        }

        override fun constructEmpty(): CharArray {
            return CharArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): CharArray? {
            var token = parser.currentToken()

            if (token == CirJsonToken.VALUE_STRING) {
                val buffer = parser.textCharacters!!
                val offset = parser.textOffset
                val length = parser.textLength
                return buffer.copyOfRange(offset, offset + length)
            } else if (parser.isExpectedStartArrayToken) {
                val stringBuilder = StringBuilder(64)

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    val string = if (token == CirJsonToken.VALUE_STRING) {
                        parser.text!!
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (myNullProvider != null) {
                            myNullProvider.getNullValue(context)
                            continue
                        }

                        verifyNullForPrimitive(context)
                        "\u0000"
                    } else {
                        val charSequence = context.handleUnexpectedToken(getValueType(context), parser) as CharSequence
                        charSequence.toString()
                    }

                    if (string.length != 1) {
                        return context.reportInputMismatch(this,
                                "Cannot convert a CirJSON String of length ${string.length} into a char element of CharArray")
                    }

                    stringBuilder.append(string[0])
                }

                return stringBuilder.toString().toCharArray()
            } else if (token != CirJsonToken.VALUE_EMBEDDED_OBJECT) {
                return context.handleUnexpectedToken(getValueType(context), parser) as CharArray?
            }

            return when (val obj = parser.embeddedObject) {
                null -> null
                is CharArray -> obj
                is String -> obj.toCharArray()
                is ByteArray -> Base64Variants.defaultVariant.encode(obj, false).toCharArray()
                else -> context.handleUnexpectedToken(getValueType(context), parser) as CharArray?
            }
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): CharArray? {
            return context.handleUnexpectedToken(getValueType(context), parser) as CharArray?
        }

        override fun concatenate(oldValue: CharArray, newValue: CharArray): CharArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: CharArray): Int {
            return value.size
        }

    }

    @CirJacksonStandardImplementation
    internal class BooleanArrayDeserializer : PrimitiveArrayDeserializer<BooleanArray> {

        constructor() : super(BooleanArray::class)

        constructor(base: BooleanArrayDeserializer, nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?) : super(base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return BooleanArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): BooleanArray {
            return BooleanArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): BooleanArray? {
            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.booleanBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                var token: CirJsonToken? = null

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    val value = if (token == CirJsonToken.VALUE_TRUE) {
                        true
                    } else if (token == CirJsonToken.VALUE_FALSE) {
                        false
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (myNullProvider != null) {
                            myNullProvider.getNullValue(context)
                            continue
                        }

                        verifyNullForPrimitive(context)
                        false
                    } else {
                        parseBooleanPrimitive(parser, context)
                    }

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser,
                context: DeserializationContext): BooleanArray {
            return booleanArrayOf(parseBooleanPrimitive(parser, context))
        }

        override fun concatenate(oldValue: BooleanArray, newValue: BooleanArray): BooleanArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: BooleanArray): Int {
            return value.size
        }

    }

    /**
     * When dealing with byte arrays we have one more alternative (compared to int/long/shorts): base64 encoded data.
     */
    @CirJacksonStandardImplementation
    internal class ByteArrayDeserializer : PrimitiveArrayDeserializer<ByteArray> {

        constructor() : super(ByteArray::class)

        constructor(base: ByteArrayDeserializer, nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?) : super(
                base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return ByteArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): ByteArray {
            return ByteArray(0)
        }

        override fun logicalType(): LogicalType {
            return LogicalType.BINARY
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): ByteArray? {
            var token = parser.currentToken()

            if (token == CirJsonToken.VALUE_STRING) {
                try {
                    return parser.getBinaryValue(context.base64Variant)
                } catch (e: StreamReadException) {
                    val message = e.originalMessage!!

                    if ("base64" in message) {
                        return context.handleWeirdStringValue(ByteArray::class, parser.text!!, message) as ByteArray?
                    }
                } catch (e: DatabindException) {
                    val message = e.originalMessage!!

                    if ("base64" in message) {
                        return context.handleWeirdStringValue(ByteArray::class, parser.text!!, message) as ByteArray?
                    }
                }
            } else if (token == CirJsonToken.VALUE_EMBEDDED_OBJECT) {
                val obj = parser.embeddedObject ?: return null

                if (obj is ByteArray) {
                    return obj
                }
            }

            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.byteBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    val value = if (token == CirJsonToken.VALUE_NUMBER_INT) {
                        parser.byteValue
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (myNullProvider != null) {
                            myNullProvider.getNullValue(context)
                            continue
                        }

                        verifyNullForPrimitive(context)
                        0.toByte()
                    } else {
                        parseBytePrimitive(parser, context)
                    }

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): ByteArray? {
            val token = parser.currentToken()

            val value = when (token) {
                CirJsonToken.VALUE_NUMBER_INT -> {
                    parser.byteValue
                }

                CirJsonToken.VALUE_NULL -> {
                    return if (myNullProvider != null) {
                        myNullProvider.getNullValue(context)
                        getEmptyValue(context) as ByteArray?
                    } else {
                        verifyNullForPrimitive(context)
                        null
                    }
                }

                else -> {
                    val number = context.handleUnexpectedToken(getValueType(context), parser) as Number
                    number.toByte()
                }
            }

            return byteArrayOf(value)
        }

        override fun concatenate(oldValue: ByteArray, newValue: ByteArray): ByteArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: ByteArray): Int {
            return value.size
        }

    }

    @CirJacksonStandardImplementation
    internal class ShortArrayDeserializer : PrimitiveArrayDeserializer<ShortArray> {

        constructor() : super(ShortArray::class)

        constructor(base: ShortArrayDeserializer, nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?) : super(base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return ShortArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): ShortArray {
            return ShortArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): ShortArray? {
            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.shortBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                var token: CirJsonToken? = null

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    val value = if (token == CirJsonToken.VALUE_NULL) {
                        if (myNullProvider != null) {
                            myNullProvider.getNullValue(context)
                            continue
                        }

                        verifyNullForPrimitive(context)
                        0.toShort()
                    } else {
                        parseShortPrimitive(parser, context)
                    }

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): ShortArray {
            return shortArrayOf(parseShortPrimitive(parser, context))
        }

        override fun concatenate(oldValue: ShortArray, newValue: ShortArray): ShortArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: ShortArray): Int {
            return value.size
        }

    }

    @CirJacksonStandardImplementation
    internal class IntArrayDeserializer : PrimitiveArrayDeserializer<IntArray> {

        constructor() : super(IntArray::class)

        constructor(base: IntArrayDeserializer, nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?) : super(
                base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return IntArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): IntArray {
            return IntArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): IntArray? {
            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.intBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                var token: CirJsonToken? = null

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    val value = if (token == CirJsonToken.VALUE_NUMBER_INT) {
                        parser.intValue
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (myNullProvider != null) {
                            myNullProvider.getNullValue(context)
                            continue
                        }

                        verifyNullForPrimitive(context)
                        0
                    } else {
                        parseIntPrimitive(parser, context)
                    }

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): IntArray {
            return intArrayOf(parseIntPrimitive(parser, context))
        }

        override fun concatenate(oldValue: IntArray, newValue: IntArray): IntArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: IntArray): Int {
            return value.size
        }

        companion object {

            val INSTANCE = IntArrayDeserializer()

        }

    }

    @CirJacksonStandardImplementation
    internal class LongArrayDeserializer : PrimitiveArrayDeserializer<LongArray> {

        constructor() : super(LongArray::class)

        constructor(base: LongArrayDeserializer, nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?) : super(
                base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return LongArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): LongArray {
            return LongArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): LongArray? {
            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.longBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                var token: CirJsonToken? = null

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    val value = if (token == CirJsonToken.VALUE_NUMBER_INT) {
                        parser.longValue
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (myNullProvider != null) {
                            myNullProvider.getNullValue(context)
                            continue
                        }

                        verifyNullForPrimitive(context)
                        0L
                    } else {
                        parseLongPrimitive(parser, context)
                    }

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): LongArray {
            return longArrayOf(parseLongPrimitive(parser, context))
        }

        override fun concatenate(oldValue: LongArray, newValue: LongArray): LongArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: LongArray): Int {
            return value.size
        }

        companion object {

            val INSTANCE = LongArrayDeserializer()

        }

    }

    @CirJacksonStandardImplementation
    internal class FloatArrayDeserializer : PrimitiveArrayDeserializer<FloatArray> {

        constructor() : super(FloatArray::class)

        constructor(base: FloatArrayDeserializer, nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?) : super(base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return FloatArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): FloatArray {
            return FloatArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): FloatArray? {
            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.floatBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                var token: CirJsonToken? = null

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    if (token == CirJsonToken.VALUE_NULL && myNullProvider != null) {
                        myNullProvider.getNullValue(context)
                        continue
                    }

                    val value = parseFloatPrimitive(parser, context)

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): FloatArray {
            return floatArrayOf(parseFloatPrimitive(parser, context))
        }

        override fun concatenate(oldValue: FloatArray, newValue: FloatArray): FloatArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: FloatArray): Int {
            return value.size
        }

    }

    @CirJacksonStandardImplementation
    internal class DoubleArrayDeserializer : PrimitiveArrayDeserializer<DoubleArray> {

        constructor() : super(DoubleArray::class)

        constructor(base: DoubleArrayDeserializer, nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?) : super(base, nullValueProvider, unwrapSingle)

        override fun withResolved(nullValueProvider: NullValueProvider?,
                unwrapSingle: Boolean?): PrimitiveArrayDeserializer<*> {
            return DoubleArrayDeserializer(this, nullValueProvider, unwrapSingle)
        }

        override fun constructEmpty(): DoubleArray {
            return DoubleArray(0)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): DoubleArray? {
            if (!parser.isExpectedStartArrayToken) {
                return handleNonArray(parser, context)
            }

            val builder = context.arrayBuilders.doubleBuilder
            var chunk = builder.resetAndStart()
            var index = 0

            try {
                var token: CirJsonToken? = null

                while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                    if (token == CirJsonToken.VALUE_NULL && myNullProvider != null) {
                        myNullProvider.getNullValue(context)
                        continue
                    }

                    val value = parseDoublePrimitive(parser, context)

                    if (index >= chunk.size) {
                        chunk = builder.appendCompletedChunk(chunk, index)
                        index = 0
                    }

                    chunk[index++] = value
                }
            } catch (e: Exception) {
                throw CirJacksonException.wrapWithPath(e, chunk, builder.bufferedSize() + index)
            }

            return builder.completeAndClearBuffer(chunk, index)
        }

        @Throws(CirJacksonException::class)
        override fun handleSingleElementUnwrapped(parser: CirJsonParser, context: DeserializationContext): DoubleArray {
            return doubleArrayOf(parseDoublePrimitive(parser, context))
        }

        override fun concatenate(oldValue: DoubleArray, newValue: DoubleArray): DoubleArray {
            val size1 = oldValue.size
            val size2 = newValue.size
            val result = oldValue.copyOf(size1 + size2)
            newValue.copyInto(result, size1)
            return result
        }

        override fun sizeOf(value: DoubleArray): Int {
            return value.size
        }

    }

    companion object {

        fun forType(rawType: KClass<*>): ValueDeserializer<*> {
            return when (rawType) {
                Int::class -> IntArrayDeserializer.INSTANCE
                Long::class -> LongArrayDeserializer.INSTANCE
                Byte::class -> ByteArrayDeserializer()
                Short::class -> ShortArrayDeserializer()
                Float::class -> FloatArrayDeserializer()
                Double::class -> DoubleArrayDeserializer()
                Boolean::class -> BooleanArrayDeserializer()
                Char::class -> CharArrayDeserializer()
                else -> throw IllegalArgumentException("Unknown primitive array element type: $rawType")
            }
        }

    }

}