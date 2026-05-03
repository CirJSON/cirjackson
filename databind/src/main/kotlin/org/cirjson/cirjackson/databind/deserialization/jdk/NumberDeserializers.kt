package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.deserialization.standard.NullifyingDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern
import org.cirjson.cirjackson.databind.util.className
import org.cirjson.cirjackson.databind.util.isPrimitive
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

/**
 * Container class for deserializers that handle core primitive (and matching wrapper) types, as well as standard "big"
 * numeric types. Note that this includes types such as [Boolean] and [Char] which are not strictly numeric, but are
 * part of primitive/wrapper types.
 */
object NumberDeserializers {

    private val ourClassNames =
            hashSetOf(Boolean::class.qualifiedName!!, Byte::class.qualifiedName!!, Short::class.qualifiedName!!,
                    Char::class.qualifiedName!!, Int::class.qualifiedName!!, Long::class.qualifiedName!!,
                    Float::class.qualifiedName!!, Double::class.qualifiedName!!, Number::class.qualifiedName!!,
                    BigInteger::class.qualifiedName!!, BigDecimal::class.qualifiedName!!, Unit::class.qualifiedName!!,
                    Nothing::class.qualifiedName!!)

    fun find(rawType: KClass<*>): ValueDeserializer<*> {
        return if (rawType.isPrimitive) {
            when (rawType) {
                Integer.TYPE.kotlin -> IntDeserializer.PRIMITIVE_INSTANCE
                java.lang.Boolean.TYPE.kotlin -> BooleanDeserializer.PRIMITIVE_INSTANCE
                java.lang.Long.TYPE.kotlin -> LongDeserializer.PRIMITIVE_INSTANCE
                java.lang.Double.TYPE.kotlin -> DoubleDeserializer.PRIMITIVE_INSTANCE
                Character.TYPE.kotlin -> CharDeserializer.PRIMITIVE_INSTANCE
                java.lang.Byte.TYPE.kotlin -> ByteDeserializer.PRIMITIVE_INSTANCE
                java.lang.Short.TYPE.kotlin -> ShortDeserializer.PRIMITIVE_INSTANCE
                java.lang.Float.TYPE.kotlin -> FloatDeserializer.PRIMITIVE_INSTANCE
                Void.TYPE.kotlin -> NullifyingDeserializer.INSTANCE
                else -> throw IllegalArgumentException(
                        "Internal error: can't find deserializer for ${rawType.qualifiedName}")
            }
        } else if (rawType.qualifiedName in ourClassNames) {
            when (rawType) {
                Int::class -> IntDeserializer.WRAPPER_INSTANCE
                Boolean::class -> BooleanDeserializer.WRAPPER_INSTANCE
                Long::class -> LongDeserializer.WRAPPER_INSTANCE
                Double::class -> DoubleDeserializer.WRAPPER_INSTANCE
                Char::class -> CharDeserializer.WRAPPER_INSTANCE
                Byte::class -> ByteDeserializer.WRAPPER_INSTANCE
                Short::class -> ShortDeserializer.WRAPPER_INSTANCE
                Float::class -> FloatDeserializer.WRAPPER_INSTANCE
                Number::class -> NumberDeserializer.INSTANCE
                BigInteger::class -> BigIntegerDeserializer.INSTANCE
                BigDecimal::class -> BigDecimalDeserializer.INSTANCE
                Unit::class -> NullifyingDeserializer.INSTANCE
                Nothing::class -> NullifyingDeserializer.INSTANCE
                else -> throw IllegalArgumentException(
                        "Internal error: can't find deserializer for ${rawType.qualifiedName}")
            }
        } else {
            throw IllegalArgumentException("Internal error: can't find deserializer for ${rawType.qualifiedName}")
        }
    }

    /*
     *******************************************************************************************************************
     * Intermediate base class for things that have both primitive and wrapper types
     *******************************************************************************************************************
     */

    abstract class PrimitiveOrWrapperDeserializer<T : Any>(valueClass: KClass<*>,
            protected val myLogicalType: LogicalType, protected val myNullValue: T?, protected val myEmptyValue: T) :
            StandardScalarDeserializer<T>(valueClass) {

        protected val myIsPrimitive = valueClass.isPrimitive

        override val nullAccessPattern: AccessPattern
            get() = if (myIsPrimitive) {
                AccessPattern.DYNAMIC
            } else if (myNullValue == null) {
                AccessPattern.ALWAYS_NULL
            } else {
                AccessPattern.CONSTANT
            }

        override fun getNullValue(context: DeserializationContext): T? {
            if (myIsPrimitive && context.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
                return context.reportInputMismatch(this,
                        "Cannot map `null` into type ${handledType().className} (set DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to 'false' to allow)")
            }

            return myNullValue
        }

        override fun getEmptyValue(context: DeserializationContext): T {
            return myEmptyValue
        }

        override fun logicalType(): LogicalType {
            return myLogicalType
        }

    }

    /*
     *******************************************************************************************************************
     * Primitive/wrapper types
     *******************************************************************************************************************
     */

    @CirJacksonStandardImplementation
    class BooleanDeserializer(valueClass: KClass<*>, nullValue: Boolean?) :
            PrimitiveOrWrapperDeserializer<Boolean>(valueClass, LogicalType.BOOLEAN, nullValue, false) {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Boolean? {
            val token = parser.currentToken()

            return if (token == CirJsonToken.VALUE_TRUE) {
                true
            } else if (token == CirJsonToken.VALUE_FALSE) {
                false
            } else if (myIsPrimitive) {
                parseBooleanPrimitive(parser, context)
            } else {
                parseBoolean(parser, context, myValueClass)
            }
        }

        @Throws(CirJacksonException::class)
        override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
                typeDeserializer: TypeDeserializer): Boolean? {
            val token = parser.currentToken()

            return if (token == CirJsonToken.VALUE_TRUE) {
                true
            } else if (token == CirJsonToken.VALUE_FALSE) {
                false
            } else if (myIsPrimitive) {
                parseBooleanPrimitive(parser, context)
            } else {
                parseBoolean(parser, context, myValueClass)
            }
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = BooleanDeserializer(java.lang.Boolean.TYPE.kotlin, false)

            internal val WRAPPER_INSTANCE = BooleanDeserializer(Boolean::class, null)

        }

    }

    @CirJacksonStandardImplementation
    open class ByteDeserializer(valueClass: KClass<*>, nullValue: Byte?) :
            PrimitiveOrWrapperDeserializer<Byte>(valueClass, LogicalType.INTEGER, nullValue, 0.toByte()) {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Byte? {
            return if (parser.isExpectedNumberIntToken) {
                parser.byteValue
            } else if (myIsPrimitive) {
                parseBytePrimitive(parser, context)
            } else {
                parseByte(parser, context)
            }
        }

        @Throws(CirJacksonException::class)
        protected open fun parseByte(parser: CirJsonParser, context: DeserializationContext): Byte? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    val action = checkFloatToIntCoercion(parser, context, myValueClass)

                    return if (action == CoercionAction.AS_NULL) {
                        getNullValue(context)
                    } else if (action == CoercionAction.AS_EMPTY) {
                        getEmptyValue(context)
                    } else {
                        parser.byteValue
                    }
                }

                CirJsonTokenId.ID_NULL -> {
                    return getNullValue(context)
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    return parser.byteValue
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as Byte?
                }
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context)
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context)
            }

            text = text.trim()

            if (checkTextualNull(context, text)) {
                return getNullValue(context)
            }

            val value = try {
                NumberInput.parseInt(text)
            } catch (_: IllegalArgumentException) {
                return context.handleWeirdStringValue(myValueClass, text, "not a valid Byte value") as Byte?
            }

            if (byteOverflow(value)) {
                return context.handleWeirdStringValue(myValueClass, text,
                        "overflow, value cannot be represented as 8-bit value") as Byte?
            }

            return value.toByte()
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = ByteDeserializer(java.lang.Byte.TYPE.kotlin, 0.toByte())

            internal val WRAPPER_INSTANCE = ByteDeserializer(Byte::class, null)

        }

    }

    @CirJacksonStandardImplementation
    open class ShortDeserializer(valueClass: KClass<*>, nullValue: Short?) :
            PrimitiveOrWrapperDeserializer<Short>(valueClass, LogicalType.INTEGER, nullValue, 0.toShort()) {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Short? {
            return if (parser.isExpectedNumberIntToken) {
                parser.shortValue
            } else if (myIsPrimitive) {
                parseShortPrimitive(parser, context)
            } else {
                parseShort(parser, context)
            }
        }

        @Throws(CirJacksonException::class)
        protected open fun parseShort(parser: CirJsonParser, context: DeserializationContext): Short? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    val action = checkFloatToIntCoercion(parser, context, myValueClass)

                    return if (action == CoercionAction.AS_NULL) {
                        getNullValue(context)
                    } else if (action == CoercionAction.AS_EMPTY) {
                        getEmptyValue(context)
                    } else {
                        parser.shortValue
                    }
                }

                CirJsonTokenId.ID_NULL -> {
                    return getNullValue(context)
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    return parser.shortValue
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as Short?
                }
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context)
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context)
            }

            text = text.trim()

            if (checkTextualNull(context, text)) {
                return getNullValue(context)
            }

            val value = try {
                NumberInput.parseInt(text)
            } catch (_: IllegalArgumentException) {
                return context.handleWeirdStringValue(myValueClass, text, "not a valid Short value") as Short?
            }

            if (shortOverflow(value)) {
                return context.handleWeirdStringValue(myValueClass, text,
                        "overflow, value cannot be represented as 16-bit value") as Short?
            }

            return value.toShort()
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = ShortDeserializer(java.lang.Short.TYPE.kotlin, 0.toShort())

            internal val WRAPPER_INSTANCE = ShortDeserializer(Short::class, null)

        }

    }

    @CirJacksonStandardImplementation
    open class CharDeserializer(valueClass: KClass<*>, nullValue: Char?) :
            PrimitiveOrWrapperDeserializer<Char>(valueClass, LogicalType.INTEGER, nullValue, '\u0000') {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Char? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    val action = context.findCoercionAction(logicalType(), myValueClass, CoercionInputShape.INTEGER)

                    when (action) {
                        CoercionAction.FAIL -> checkCoercionFail(context, action, myValueClass, parser.numberValue,
                                "Integer value (${parser.text})")

                        CoercionAction.AS_NULL -> return getNullValue(context)

                        CoercionAction.AS_EMPTY -> return getEmptyValue(context)

                        else -> {}
                    }

                    val value = parser.intValue

                    return if (value in 0..0xFFFF) {
                        value.toChar()
                    } else {
                        context.handleWeirdNumberValue(handledType(), value,
                                "value outside valid Char range (0x0000 - 0xFFFF)") as Char?
                    }
                }

                CirJsonTokenId.ID_NULL -> {
                    if (myIsPrimitive) {
                        verifyNullForPrimitive(context)
                    }

                    return getNullValue(context)
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as Char?
                }
            }

            if (text.length == 1) {
                return text[0]
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context)
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context)
            }

            text = text.trim()

            if (checkTextualNull(context, text)) {
                return getNullValue(context)
            }

            return context.handleWeirdStringValue(handledType(), text,
                    "Expected either Int value code or 1-character String") as Char?
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = CharDeserializer(Character.TYPE.kotlin, '\u0000')

            internal val WRAPPER_INSTANCE = CharDeserializer(Char::class, null)

        }

    }

    @CirJacksonStandardImplementation
    class IntDeserializer(valueClass: KClass<*>, nullValue: Int?) :
            PrimitiveOrWrapperDeserializer<Int>(valueClass, LogicalType.INTEGER, nullValue, 0) {

        override val isCacheable: Boolean
            get() = true

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Int? {
            return if (parser.isExpectedNumberIntToken) {
                parser.intValue
            } else if (myIsPrimitive) {
                parseIntPrimitive(parser, context)
            } else {
                parseInt(parser, context, myValueClass)
            }
        }

        @Throws(CirJacksonException::class)
        override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
                typeDeserializer: TypeDeserializer): Int? {
            return if (parser.isExpectedNumberIntToken) {
                parser.intValue
            } else if (myIsPrimitive) {
                parseIntPrimitive(parser, context)
            } else {
                parseInt(parser, context, myValueClass)
            }
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = IntDeserializer(Integer.TYPE.kotlin, 0)

            internal val WRAPPER_INSTANCE = IntDeserializer(Int::class, null)

        }

    }

    @CirJacksonStandardImplementation
    class LongDeserializer(valueClass: KClass<*>, nullValue: Long?) :
            PrimitiveOrWrapperDeserializer<Long>(valueClass, LogicalType.INTEGER, nullValue, 0L) {

        override val isCacheable: Boolean
            get() = true

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Long? {
            return if (parser.isExpectedNumberIntToken) {
                parser.longValue
            } else if (myIsPrimitive) {
                parseLongPrimitive(parser, context)
            } else {
                parseLong(parser, context, myValueClass)
            }
        }

        @Throws(CirJacksonException::class)
        override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
                typeDeserializer: TypeDeserializer): Long? {
            return if (parser.isExpectedNumberIntToken) {
                parser.longValue
            } else if (myIsPrimitive) {
                parseLongPrimitive(parser, context)
            } else {
                parseLong(parser, context, myValueClass)
            }
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = LongDeserializer(java.lang.Long.TYPE.kotlin, 0L)

            internal val WRAPPER_INSTANCE = LongDeserializer(Long::class, null)

        }

    }

    @CirJacksonStandardImplementation
    open class FloatDeserializer(valueClass: KClass<*>, nullValue: Float?) :
            PrimitiveOrWrapperDeserializer<Float>(valueClass, LogicalType.FLOAT, nullValue, 0.0f) {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Float? {
            return if (parser.isExpectedNumberIntToken) {
                parser.floatValue
            } else if (myIsPrimitive) {
                parseFloatPrimitive(parser, context)
            } else {
                parseFloat(parser, context)
            }
        }

        @Throws(CirJacksonException::class)
        protected open fun parseFloat(parser: CirJsonParser, context: DeserializationContext): Float? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    val action = checkIntToFloatCoercion(parser, context, myValueClass)

                    return if (action == CoercionAction.AS_NULL) {
                        getNullValue(context)
                    } else if (action == CoercionAction.AS_EMPTY) {
                        getEmptyValue(context)
                    } else {
                        parser.floatValue
                    }
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    return parser.floatValue
                }

                CirJsonTokenId.ID_NULL -> {
                    return getNullValue(context)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as Float?
                }
            }

            val nan = checkFloatSpecialValue(text)

            if (nan != null) {
                return nan
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context)
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context)
            }

            text = text.trim()

            if (checkTextualNull(context, text)) {
                return getNullValue(context)
            }

            if (NumberInput.looksLikeValidNumber(text)) {
                parser.streamReadConstraints().validateFloatingPointLength(text.length)

                try {
                    return NumberInput.parseFloat(text, parser.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
                } catch (_: IllegalArgumentException) {
                }
            }

            return context.handleWeirdStringValue(myValueClass, text, "not a valid `Float` value") as Float?
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = FloatDeserializer(java.lang.Float.TYPE.kotlin, 0.0f)

            internal val WRAPPER_INSTANCE = FloatDeserializer(Float::class, null)

        }

    }

    @CirJacksonStandardImplementation
    open class DoubleDeserializer(valueClass: KClass<*>, nullValue: Double?) :
            PrimitiveOrWrapperDeserializer<Double>(valueClass, LogicalType.FLOAT, nullValue, 0.0) {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Double? {
            return if (parser.isExpectedNumberIntToken) {
                parser.doubleValue
            } else if (myIsPrimitive) {
                parseDoublePrimitive(parser, context)
            } else {
                parseDouble(parser, context)
            }
        }

        @Throws(CirJacksonException::class)
        protected open fun parseDouble(parser: CirJsonParser, context: DeserializationContext): Double? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    val action = checkIntToFloatCoercion(parser, context, myValueClass)

                    return if (action == CoercionAction.AS_NULL) {
                        getNullValue(context)
                    } else if (action == CoercionAction.AS_EMPTY) {
                        getEmptyValue(context)
                    } else {
                        parser.doubleValue
                    }
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    return parser.doubleValue
                }

                CirJsonTokenId.ID_NULL -> {
                    return getNullValue(context)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as Double?
                }
            }

            val nan = checkDoubleSpecialValue(text)

            if (nan != null) {
                return nan
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context)
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context)
            }

            text = text.trim()

            if (checkTextualNull(context, text)) {
                return getNullValue(context)
            }

            if (NumberInput.looksLikeValidNumber(text)) {
                parser.streamReadConstraints().validateFloatingPointLength(text.length)

                try {
                    return parseDouble(text, parser.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
                } catch (_: IllegalArgumentException) {
                }
            }

            return context.handleWeirdStringValue(myValueClass, text, "not a valid `Double` value") as Double?
        }

        companion object {

            internal val PRIMITIVE_INSTANCE = DoubleDeserializer(java.lang.Double.TYPE.kotlin, 0.0)

            internal val WRAPPER_INSTANCE = DoubleDeserializer(Double::class, null)

        }

    }

    /**
     * For type `Number::class`, we can just rely on type mappings that plain [CirJsonParser.numberValue] returns.
     * 
     * There is one additional complication: some numeric types (specifically, Int/Int? and Double/Double?) are
     * "non-typed"; meaning that they will NEVER be output with type information. But other numeric types may need such
     * type information. This is why [deserializeWithType] must be overridden.
     */
    @CirJacksonStandardImplementation
    open class NumberDeserializer : StandardDeserializer<Any>(Number::class) {

        override fun logicalType(): LogicalType? {
            return LogicalType.INTEGER
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    return if (context.hasSomeOfFeatures(FEATURE_MASK_INT_COERCIONS)) {
                        coerceIntegral(parser, context)
                    } else {
                        parser.numberValue
                    }
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    return if (context.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) && !parser.isNaN) {
                        parser.bigDecimalValue
                    } else {
                        parser.numberValue
                    }
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser)
                }
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context)
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context)
            }

            text = text.trim()

            if (hasTextualNull(text)) {
                return getNullValue(context)
            }

            if (isPositiveInfinity(text)) {
                return Double.POSITIVE_INFINITY
            } else if (isNegativeInfinity(text)) {
                return Double.NEGATIVE_INFINITY
            } else if (isNaN(text)) {
                return Double.NaN
            }

            return try {
                if (isIntNumber(text)) {
                    parser.streamReadConstraints().validateIntegerLength(text.length)

                    if (context.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)) {
                        return NumberInput.parseBigInteger(text,
                                parser.isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
                    }

                    val value = NumberInput.parseLong(text)

                    if (!context.isEnabled(DeserializationFeature.USE_LONG_FOR_INTS) &&
                            value in Int.MIN_VALUE..Int.MAX_VALUE) {
                        value.toInt()
                    } else {
                        value
                    }
                } else if (NumberInput.looksLikeValidNumber(text)) {
                    parser.streamReadConstraints().validateFloatingPointLength(text.length)

                    if (context.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
                        NumberInput.parseBigDecimal(text,
                                parser.isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
                    } else {
                        NumberInput.parseDouble(text, parser.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
                    }
                } else {
                    context.handleWeirdStringValue(myValueClass, text, "not a valid number")
                }
            } catch (_: IllegalArgumentException) {
                context.handleWeirdStringValue(myValueClass, text, "not a valid number")
            }
        }

        /**
         * As mentioned in class KDoc, there is additional complexity in handling potentially mixed type information
         * here. Because of this, we must actually check for "raw" integers and doubles first, before calling type
         * deserializer.
         */
        @Throws(CirJacksonException::class)
        override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
                typeDeserializer: TypeDeserializer): Any? {
            return when (parser.currentTokenId()) {
                CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT, CirJsonTokenId.ID_STRING -> deserialize(
                        parser, context)

                else -> typeDeserializer.deserializeTypedFromScalar(parser, context)
            }
        }

        companion object {

            internal val INSTANCE = NumberDeserializer()

        }

    }

    /*
     *******************************************************************************************************************
     * A bit more complicated (but non-structured) number types
     *******************************************************************************************************************
     */

    @CirJacksonStandardImplementation
    open class BigIntegerDeserializer : StandardScalarDeserializer<BigInteger>(BigInteger::class) {

        override fun getEmptyValue(context: DeserializationContext): Any {
            return BigInteger.ZERO
        }

        override fun logicalType(): LogicalType {
            return LogicalType.INTEGER
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): BigInteger? {
            if (parser.isExpectedNumberIntToken) {
                return parser.bigIntegerValue
            }

            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    val action = checkFloatToIntCoercion(parser, context, myValueClass)

                    return if (action == CoercionAction.AS_NULL) {
                        getNullValue(context) as BigInteger?
                    } else if (action == CoercionAction.AS_EMPTY) {
                        getEmptyValue(context) as BigInteger
                    } else {
                        val bigDecimal = parser.bigDecimalValue
                        parser.streamReadConstraints().validateBigIntegerScale(bigDecimal.scale())
                        bigDecimal.toBigInteger()
                    }
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as BigInteger?
                }
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context) as BigInteger?
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context) as BigInteger
            }

            text = text.trim()

            if (hasTextualNull(text)) {
                return getNullValue(context) as BigInteger?
            }

            return if (isIntNumber(text)) {
                parser.streamReadConstraints().validateIntegerLength(text.length)

                try {
                    NumberInput.parseBigInteger(text, parser.isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
                } catch (_: IllegalArgumentException) {
                    context.handleWeirdStringValue(myValueClass, text, "not a valid representation") as BigInteger?
                }
            } else {
                context.handleWeirdStringValue(myValueClass, text, "not a valid representation") as BigInteger?
            }
        }

        companion object {

            val INSTANCE = BigIntegerDeserializer()

        }

    }

    @CirJacksonStandardImplementation
    open class BigDecimalDeserializer : StandardScalarDeserializer<BigDecimal>(BigDecimal::class) {

        override fun getEmptyValue(context: DeserializationContext): Any {
            return BigDecimal.ZERO
        }

        override fun logicalType(): LogicalType {
            return LogicalType.FLOAT
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): BigDecimal? {
            var text = when (parser.currentTokenId()) {
                CirJsonTokenId.ID_STRING -> {
                    parser.text!!
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    val action = checkFloatToIntCoercion(parser, context, myValueClass)

                    return if (action == CoercionAction.AS_NULL) {
                        getNullValue(context) as BigDecimal?
                    } else if (action == CoercionAction.AS_EMPTY) {
                        getEmptyValue(context) as BigDecimal
                    } else {
                        parser.bigDecimalValue
                    }
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    return parser.bigDecimalValue
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    context.extractScalarFromObject(parser, this, myValueClass)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    return deserializeFromArray(parser, context)
                }

                else -> {
                    return context.handleUnexpectedToken(getValueType(context), parser) as BigDecimal?
                }
            }

            val action = checkFromStringCoercion(context, text)

            if (action == CoercionAction.AS_NULL) {
                return getNullValue(context) as BigDecimal?
            } else if (action == CoercionAction.AS_EMPTY) {
                return getEmptyValue(context) as BigDecimal
            }

            text = text.trim()

            if (hasTextualNull(text)) {
                return getNullValue(context) as BigDecimal?
            }

            return if (NumberInput.looksLikeValidNumber(text)) {
                parser.streamReadConstraints().validateFloatingPointLength(text.length)

                try {
                    NumberInput.parseBigDecimal(text, parser.isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
                } catch (_: IllegalArgumentException) {
                    context.handleWeirdStringValue(myValueClass, text, "not a valid representation") as BigDecimal?
                }
            } else {
                context.handleWeirdStringValue(myValueClass, text, "not a valid representation") as BigDecimal?
            }
        }

        companion object {

            val INSTANCE = BigDecimalDeserializer()

        }

    }

}