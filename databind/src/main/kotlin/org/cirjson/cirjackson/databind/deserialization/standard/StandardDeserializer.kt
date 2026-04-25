package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializerBase
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsAsEmptyProvider
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsConstantProvider
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsFailProvider
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.*
import java.io.IOException
import java.util.*
import kotlin.reflect.KClass

/**
 * Base class for common deserializers. Contains shared base functionality for dealing with primitive values, such as (re)parsing from String.
 */
abstract class StandardDeserializer<T : Any> : ValueDeserializer<T>, ValueInstantiator.Gettable {

    /**
     * Type of values this deserializer handles: sometimes exact types, other times most specific supertype of types
     * deserializer handles (which may be as generic as [Any] in some case)
     */
    protected val myValueClass: KClass<*>

    protected val myValueType: KotlinType?

    protected constructor(valueClass: KClass<*>) {
        myValueClass = valueClass
        myValueType = null
    }

    protected constructor(valueType: KotlinType) {
        myValueType = valueType
        myValueClass = valueType.rawClass
    }

    /**
     * Copy-constructor for subclasses to use, most often when creating new instances via [createContextual].
     */
    protected constructor(source: StandardDeserializer<*>) {
        myValueClass = source.myValueClass
        myValueType = source.myValueType
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun handledType(): KClass<*> {
        return myValueClass
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Exact structured type this deserializer handles, if known.
     */
    open val valueType: KotlinType?
        get() = myValueType

    /**
     * Convenience method for getting handled type as [KotlinType], regardless of whether deserializer has one already
     * resolved (and accessible via [valueType]) or not: equivalent to:
     * ```
     *   myValueType ?: context.constructType(myValueClass)!!
     * ```
     */
    open fun getValueType(context: DeserializationContext): KotlinType {
        return myValueType ?: context.constructType(myValueClass)!!
    }

    override val valueInstantiator: ValueInstantiator?
        get() = null

    /**
     * Method that can be called to determine if given deserializer is the default deserializer CirJackson uses; as
     * opposed to a custom deserializer installed by a module or calling application. Determination is done using
     * [CirJacksonStandardImplementation][org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation]
     * annotation on deserializer class.
     */
    protected open fun isDefaultSerializer(deserializer: ValueDeserializer<*>): Boolean {
        return deserializer.isCirJacksonStandardImplementation
    }

    /**
     * Method that can be called to determine if given deserializer is the default key deserializer CirJackson uses; as
     * opposed to a custom deserializer installed by a module or calling application. Determination is done using
     * [CirJacksonStandardImplementation][org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation]
     * annotation on deserializer class.
     */
    protected open fun isDefaultKeySerializer(keyDeserializer: KeyDeserializer): Boolean {
        return keyDeserializer.isCirJacksonStandardImplementation
    }

    /*
     *******************************************************************************************************************
     * Partial deserialize method implementation
     *******************************************************************************************************************
     */

    /**
     * Base implementation that does not assume specific type inclusion mechanism. Subclasses are expected to override
     * this method if they are to handle type information.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromAny(parser, context)
    }

    /*
     *******************************************************************************************************************
     * High-level handling of secondary input shapes (with possible coercion)
     *******************************************************************************************************************
     */

    /**
     * Helper method that allows easy support for array-related coercion features: checks for either empty array, or
     * single-value array-wrapped value (if coercion enabled by `CoercionConfigs`), and either reports an exception (if
     * no coercion allowed), or returns appropriate result value using coercion mechanism indicated.
     * 
     * This method should NOT be called if Array representation is explicitly supported for type: it should only be
     * called in case it is otherwise unrecognized.
     * 
     * NOTE: in case of unwrapped single element, will handle actual decoding by calling [deserializeWrappedValue],
     * which by default calls [deserialize].
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun deserializeFromArray(parser: CirJsonParser, context: DeserializationContext): T? {
        val action = findCoercionFromEmptyArray(context)
        val unwrap = context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)

        if (!unwrap && action == CoercionAction.FAIL) {
            return context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser, null) as T?
        }

        val token = parser.nextToken()

        return if (token == CirJsonToken.END_ARRAY) {
            when (action) {
                CoercionAction.AS_EMPTY -> getEmptyValue(context) as T?
                CoercionAction.AS_NULL, CoercionAction.TRY_CONVERT -> getNullValue(context) as T?
                else -> context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser,
                        null) as T?
            }
        } else if (unwrap) {
            val parsed = deserializeWrappedValue(parser, context)

            if (parser.nextToken() != CirJsonToken.END_ARRAY) {
                handleMissingEndArrayForSingle(parser, context)
            }

            parsed
        } else {
            context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser, null) as T?
        }
    }

    /**
     * Helper method to call in case deserializer does not support native automatic use of incoming String values, but
     * there may be standard coercions to consider.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun deserializeFromString(parser: CirJsonParser, context: DeserializationContext): T? {
        val instantiator = valueInstantiator
        val rawTargetType = handledType()
        var value = parser.valueAsString!!

        if (instantiator?.canCreateFromString() ?: false) {
            return instantiator.createFromString(context, value) as T?
        }

        if (value.isEmpty()) {
            val action = context.findCoercionAction(logicalType(), rawTargetType, CoercionInputShape.EMPTY_STRING)
            return deserializeFromEmptyString(parser, context, action, rawTargetType, "empty String (\"\")") as T?
        }

        if (isBlank(value)) {
            val action = context.findCoercionFromBlankString(logicalType(), rawTargetType, CoercionAction.FAIL)
            return deserializeFromEmptyString(parser, context, action, rawTargetType,
                    "blank String (all whitespace)") as T?
        }

        instantiator ?: return context.handleMissingInstantiator(rawTargetType, null, context.parser,
                "no String-argument constructor/factory method to deserialize from String value ('$value')") as T?

        value = value.trim()

        if (instantiator.canCreateFromInt()) {
            if (context.findCoercionAction(LogicalType.INTEGER, Int::class,
                            CoercionInputShape.STRING) == CoercionAction.TRY_CONVERT) {
                return instantiator.createFromInt(context, parseIntPrimitive(parser, context, value)) as T?
            }
        }

        if (instantiator.canCreateFromLong()) {
            if (context.findCoercionAction(LogicalType.INTEGER, Long::class,
                            CoercionInputShape.STRING) == CoercionAction.TRY_CONVERT) {
                return instantiator.createFromLong(context, parseLongPrimitive(parser, context, value)) as T?
            }
        }

        if (instantiator.canCreateFromBoolean()) {
            if (context.findCoercionAction(LogicalType.BOOLEAN, Boolean::class,
                            CoercionInputShape.STRING) == CoercionAction.TRY_CONVERT) {
                val string = value.trim()

                if ("true" == string) {
                    return instantiator.createFromBoolean(context, true) as T?
                }

                if ("false" == string) {
                    return instantiator.createFromBoolean(context, false) as T?
                }
            }
        }

        return context.handleMissingInstantiator(rawTargetType, null, context.parser,
                "no String-argument constructor/factory method to deserialize from String value ('$value')") as T?
    }

    protected open fun deserializeFromEmptyString(parser: CirJsonParser, context: DeserializationContext,
            action: CoercionAction, rawTargetType: KClass<*>, description: String): Any? {
        return when (action) {
            CoercionAction.AS_EMPTY -> getEmptyValue(context)
            CoercionAction.FAIL -> checkCoercionFail(context, action, rawTargetType, "", "empty String (\"\")")
            else -> null
        }
    }

    /**
     * Helper called to support [DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS]: default implementation simply calls
     * [deserialize], but handling may be overridden.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun deserializeWrappedValue(parser: CirJsonParser, context: DeserializationContext): T? {
        return if (parser.hasToken(CirJsonToken.START_ARRAY)) {
            handleNestedArrayForSingle(parser, context) as T?
        } else {
            deserialize(parser, context)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, parsing: while mostly useful for numeric types, can be also useful for dealing
     * with things serialized as numbers (such as Dates).
     *******************************************************************************************************************
     */

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseBooleanPrimitive(parser: CirJsonParser, context: DeserializationContext): Boolean {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return true == coerceBooleanFromInt(parser, context, Boolean::class)
            }

            CirJsonTokenId.ID_TRUE -> {
                return true
            }

            CirJsonTokenId.ID_FALSE -> {
                return false
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return false
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Boolean::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Boolean
                    }

                    val parsed = parseBooleanPrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Boolean::class)!!, parser)!! as Boolean
                }
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.BOOLEAN, Boolean::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return false
        }

        if (action == CoercionAction.AS_EMPTY) {
            return false
        }

        text = text.trim()
        val length = text.length

        if (length == 4) {
            if (isTrue(text)) {
                return true
            }
        } else if (length == 5) {
            if (isFalse(text)) {
                return false
            }
        }

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return false
        }

        val value = context.handleWeirdStringValue(Boolean::class, text,
                "only \"true\"/\"True\"/\"TRUE\" or \"false\"/\"False\"/\"FALSE\" recognized") as Boolean?
        return true == value
    }

    protected open fun isTrue(text: String): Boolean {
        return when (text[0]) {
            't' -> "true" == text
            'T' -> "TRUE" == text || "True" == text
            else -> false
        }
    }

    protected open fun isFalse(text: String): Boolean {
        return when (text[0]) {
            'f' -> "false" == text
            'F' -> "FALSE" == text || "False" == text
            else -> false
        }
    }

    /**
     * Helper method called for cases where non-primitive, boolean-based value is to be deserialized: result of this
     * method will be [Boolean], although actual target type may be something different.
     * 
     * Note: does NOT dynamically access "empty value" or "`null` value" of deserializer since those values could be of
     * type other than [Boolean]. Caller may need to translate from 3 possible result types into appropriately matching
     * output types.
     *
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     * 
     * @param targetType Actual type that is being deserialized, may be same as [handledType] but could be
     * `AtomicBoolean` for example. Used for coercion config access.
     */
    @Throws(CirJacksonException::class)
    protected fun parseBoolean(parser: CirJsonParser, context: DeserializationContext,
            targetType: KClass<*>): Boolean? {
        var text = when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> parser.text!!
            CirJsonTokenId.ID_NUMBER_INT -> return coerceBooleanFromInt(parser, context, targetType)
            CirJsonTokenId.ID_TRUE -> return true
            CirJsonTokenId.ID_FALSE -> return false
            CirJsonTokenId.ID_NULL -> return null
            CirJsonTokenId.ID_START_OBJECT -> context.extractScalarFromObject(parser, this, targetType)
            CirJsonTokenId.ID_START_ARRAY -> return deserializeFromArray(parser, context) as Boolean?
            else -> return context.handleUnexpectedToken(context.constructType(targetType)!!, parser) as Boolean?
        }

        val action = checkFromStringCoercion(context, text, LogicalType.BOOLEAN, targetType)

        if (action == CoercionAction.AS_NULL) {
            return null
        }

        if (action == CoercionAction.AS_EMPTY) {
            return false
        }

        text = text.trim()
        val length = text.length

        if (length == 4) {
            if (isTrue(text)) {
                return true
            }
        } else if (length == 5) {
            if (isFalse(text)) {
                return false
            }
        }

        if (checkTextualNull(context, text)) {
            return null
        }

        return context.handleWeirdStringValue(targetType, text, "only \"true\" or \"false\" recognized") as Boolean?
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseBytePrimitive(parser: CirJsonParser, context: DeserializationContext): Byte {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return when (checkFloatToIntCoercion(parser, context, Byte::class)) {
                    CoercionAction.AS_NULL -> 0.toByte()
                    CoercionAction.AS_EMPTY -> 0.toByte()
                    else -> parser.byteValue
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return parser.byteValue
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return 0.toByte()
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Byte::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Byte
                    }

                    val parsed = parseBytePrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Byte::class)!!, parser)!! as Byte
                }
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Byte::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return 0.toByte()
        }

        if (action == CoercionAction.AS_EMPTY) {
            return 0.toByte()
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return 0.toByte()
        }

        parser.streamReadConstraints().validateIntegerLength(text.length)

        val value = try {
            NumberInput.parseInt(text)
        } catch (_: IllegalArgumentException) {
            return context.handleWeirdStringValue(myValueClass, text, "not a valid `Byte` value") as Byte
        }

        if (byteOverflow(value)) {
            return context.handleWeirdStringValue(myValueClass, text,
                    "overflow, value cannot be represented as 8-bit value") as Byte
        }

        return value.toByte()
    }

    /**
     * @param parser Underlying parser
     *
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseShortPrimitive(parser: CirJsonParser, context: DeserializationContext): Short {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return when (checkFloatToIntCoercion(parser, context, Short::class)) {
                    CoercionAction.AS_NULL -> 0.toShort()
                    CoercionAction.AS_EMPTY -> 0.toShort()
                    else -> parser.shortValue
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return parser.shortValue
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return 0.toShort()
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Short::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Short
                    }

                    val parsed = parseShortPrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Short::class)!!, parser)!! as Short
                }
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Short::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return 0.toShort()
        }

        if (action == CoercionAction.AS_EMPTY) {
            return 0.toShort()
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return 0.toShort()
        }

        parser.streamReadConstraints().validateIntegerLength(text.length)

        val value = try {
            NumberInput.parseInt(text)
        } catch (_: IllegalArgumentException) {
            return context.handleWeirdStringValue(myValueClass, text, "not a valid `Short` value") as Short
        }

        if (byteOverflow(value)) {
            return context.handleWeirdStringValue(myValueClass, text,
                    "overflow, value cannot be represented as 16-bit value") as Short
        }

        return value.toShort()
    }

    /**
     * @param parser Underlying parser
     *
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseIntPrimitive(parser: CirJsonParser, context: DeserializationContext): Int {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return when (checkFloatToIntCoercion(parser, context, Int::class)) {
                    CoercionAction.AS_NULL -> 0
                    CoercionAction.AS_EMPTY -> 0
                    else -> parser.valueAsInt
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return parser.intValue
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return 0
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Int::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Int
                    }

                    val parsed = parseIntPrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Int::class)!!, parser)!! as Int
                }
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Int::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return 0
        }

        if (action == CoercionAction.AS_EMPTY) {
            return 0
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return 0
        }

        return parseIntPrimitive(parser, context, text)
    }

    /**
     * @param parser Underlying parser
     *
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseIntPrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Int {
        return try {
            if (text.length <= 9) {
                return NumberInput.parseInt(text)
            }

            parser.streamReadConstraints().validateIntegerLength(text.length)
            val long = NumberInput.parseLong(text)

            if (intOverflow(long)) {
                val value = context.handleWeirdStringValue(Int::class, text,
                        "Overflow: numeric value ($text) out of range of int (${Int.MIN_VALUE} -${Int.MAX_VALUE})") as Number?
                nonNullNumber(value).toInt()
            } else {
                long.toInt()
            }
        } catch (_: IllegalArgumentException) {
            val value = context.handleWeirdStringValue(Int::class, text, "not a valid `Int` value") as Number?
            nonNullNumber(value).toInt()
        }
    }

    /**
     * Helper method called for cases where non-primitive, int-based value is to be deserialized: result of this method
     * will be [Int], although actual target type may be something different.
     *
     * Note: does NOT dynamically access "empty value" or "`null` value" of deserializer since those values could be of
     * type other than [Int].
     *
     * @param parser Underlying parser
     *
     * @param context Deserialization context for accessing configuration
     *
     * @param targetType Actual type that is being deserialized, may be same as [handledType] but could be
     * `AtomicInteger` for example. Used for coercion config access.
     */
    @Throws(CirJacksonException::class)
    protected fun parseInt(parser: CirJsonParser, context: DeserializationContext, targetType: KClass<*>): Int? {
        var text = when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return when (checkFloatToIntCoercion(parser, context, Int::class)) {
                    CoercionAction.AS_NULL -> getNullValue(context) as Int?
                    CoercionAction.AS_EMPTY -> getEmptyValue(context) as Int?
                    else -> parser.valueAsInt
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return parser.intValue
            }

            CirJsonTokenId.ID_NULL -> {
                return getNullValue(context) as Int?
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Int::class)
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                return deserializeFromArray(parser, context) as Int?
            }

            else -> {
                return context.handleUnexpectedToken(context.constructType(Int::class)!!, parser) as Int?
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Int::class)

        if (action == CoercionAction.AS_NULL) {
            return getNullValue(context) as Int?
        }

        if (action == CoercionAction.AS_EMPTY) {
            return getEmptyValue(context) as Int?
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            return getNullValue(context) as Int?
        }

        return parseInt(parser, context, text)
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseInt(parser: CirJsonParser, context: DeserializationContext, text: String): Int? {
        return try {
            if (text.length <= 9) {
                return NumberInput.parseInt(text)
            }

            parser.streamReadConstraints().validateIntegerLength(text.length)
            val long = NumberInput.parseLong(text)

            if (intOverflow(long)) {
                context.handleWeirdStringValue(Int::class, text,
                        "Overflow: numeric value ($text) out of range of int (${Int.MIN_VALUE} -${Int.MAX_VALUE})") as Int?
            } else {
                long.toInt()
            }
        } catch (_: IllegalArgumentException) {
            context.handleWeirdStringValue(Int::class, text, "not a valid `Int?` value") as Int?
        }
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseLongPrimitive(parser: CirJsonParser, context: DeserializationContext): Long {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return when (checkFloatToIntCoercion(parser, context, Long::class)) {
                    CoercionAction.AS_NULL -> 0L
                    CoercionAction.AS_EMPTY -> 0L
                    else -> parser.valueAsLong
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return parser.longValue
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return 0L
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Long::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Long
                    }

                    val parsed = parseLongPrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Int::class)!!, parser)!! as Long
                }
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Long::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return 0L
        }

        if (action == CoercionAction.AS_EMPTY) {
            return 0L
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return 0L
        }

        return parseLongPrimitive(parser, context, text)
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseLongPrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Long {
        parser.streamReadConstraints().validateIntegerLength(text.length)

        return try {
            NumberInput.parseLong(text)
        } catch (_: IllegalArgumentException) {
            val value = context.handleWeirdStringValue(Int::class, text, "not a valid `Long` value") as Number?
            nonNullNumber(value).toLong()
        }
    }

    /**
     * Helper method called for cases where non-primitive, long-based value is to be deserialized: result of this method
     * will be [Long], although actual target type may be something different.
     * 
     * Note: does NOT dynamically access "empty value" or "`null` value" of deserializer since those values could be of
     * type other than [Long].
     *
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     * 
     * @param targetType Actual type that is being deserialized, may be same as [handledType] but could be
     * `AtomicLong` for example. Used for coercion config access.
     */
    @Throws(CirJacksonException::class)
    protected fun parseLong(parser: CirJsonParser, context: DeserializationContext, targetType: KClass<*>): Long? {
        var text = when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return when (checkFloatToIntCoercion(parser, context, Long::class)) {
                    CoercionAction.AS_NULL -> getNullValue(context) as Long?
                    CoercionAction.AS_EMPTY -> getEmptyValue(context) as Long?
                    else -> parser.valueAsLong
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return parser.longValue
            }

            CirJsonTokenId.ID_NULL -> {
                return getNullValue(context) as Long?
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Long::class)
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                return deserializeFromArray(parser, context) as Long?
            }

            else -> {
                return context.handleUnexpectedToken(context.constructType(Long::class)!!, parser) as Long?
            }
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Long::class)

        if (action == CoercionAction.AS_NULL) {
            return getNullValue(context) as Long?
        }

        if (action == CoercionAction.AS_EMPTY) {
            return getEmptyValue(context) as Long?
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            return getNullValue(context) as Long?
        }

        return parseLong(context, text)
    }

    /**
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseLong(context: DeserializationContext, text: String): Long? {
        context.parser!!.streamReadConstraints().validateIntegerLength(text.length)

        return try {
            NumberInput.parseLong(text)
        } catch (_: IllegalArgumentException) {
            context.handleWeirdStringValue(Long::class, text, "not a valid `Long?` value") as Long?
        }
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseFloatPrimitive(parser: CirJsonParser, context: DeserializationContext): Float {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return when (checkIntToFloatCoercion(parser, context, Float::class)) {
                    CoercionAction.AS_NULL -> 0.0f
                    CoercionAction.AS_EMPTY -> 0.0f
                    else -> parser.floatValue
                }
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return parser.floatValue
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return 0.0f
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Float::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Float
                    }

                    val parsed = parseFloatPrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Float::class)!!, parser)!! as Float
                }
            }
        }

        val nan = checkFloatSpecialValue(text)

        if (nan != null) {
            return nan
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Float::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return 0.0f
        }

        if (action == CoercionAction.AS_EMPTY) {
            return 0.0f
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return 0.0f
        }

        return parseFloatPrimitive(parser, context, text)
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseFloatPrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Float {
        if (!NumberInput.looksLikeValidNumber(text)) {
            val value = context.handleWeirdStringValue(Float::class, text, "not a valid `Float` value") as Number?
            return nonNullNumber(value).toFloat()
        }

        parser.streamReadConstraints().validateFloatingPointLength(text.length)

        return try {
            NumberInput.parseFloat(text, parser.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
        } catch (_: IllegalArgumentException) {
            val value = context.handleWeirdStringValue(Float::class, text, "not a valid `Float` value") as Number?
            return nonNullNumber(value).toFloat()
        }
    }

    /**
     * Helper method called to check whether given String value contains one of "special" values (currently, NaN
     * ("not-a-number") and plus/minus Infinity) and if so, returns that value; otherwise returns `null`.
     *
     * @param text String value to check
     *
     * @return One of [Float] constants referring to special value decoded, if value matched; `null` otherwise.
     */
    protected open fun checkFloatSpecialValue(text: String): Float? {
        if (text.isEmpty()) {
            return null
        }

        return when (text[0]) {
            'I' -> Float.POSITIVE_INFINITY.takeIf { isPositiveInfinity(text) }
            'N' -> Float.NaN.takeIf { isNaN(text) }
            '-' -> Float.NEGATIVE_INFINITY.takeIf { isNegativeInfinity(text) }
            else -> null
        }
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseDoublePrimitive(parser: CirJsonParser, context: DeserializationContext): Double {
        var text = when (val tokenId = parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                return when (checkIntToFloatCoercion(parser, context, Double::class)) {
                    CoercionAction.AS_NULL -> 0.0
                    CoercionAction.AS_EMPTY -> 0.0
                    else -> parser.doubleValue
                }
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                return parser.doubleValue
            }

            CirJsonTokenId.ID_NULL -> {
                verifyNullForPrimitive(context)
                return 0.0
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, Double::class)
            }

            else -> {
                return if (tokenId == CirJsonTokenId.ID_START_ARRAY &&
                        context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    if (parser.nextToken() == CirJsonToken.START_ARRAY) {
                        return handleNestedArrayForSingle(parser, context) as Double
                    }

                    val parsed = parseDoublePrimitive(parser, context)
                    verifyEndArrayForSingle(parser, context)
                    parsed
                } else {
                    context.handleUnexpectedToken(context.constructType(Double::class)!!, parser)!! as Double
                }
            }
        }

        val nan = checkDoubleSpecialValue(text)

        if (nan != null) {
            return nan
        }

        val action = checkFromStringCoercion(context, text, LogicalType.INTEGER, Double::class)

        if (action == CoercionAction.AS_NULL) {
            verifyNullForPrimitive(context)
            return 0.0
        }

        if (action == CoercionAction.AS_EMPTY) {
            return 0.0
        }

        text = text.trim()

        if (hasTextualNull(text)) {
            verifyNullForPrimitiveCoercion(context, text)
            return 0.0
        }

        return parseDoublePrimitive(parser, context, text)
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseDoublePrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Double {
        return try {
            parseDouble(text, parser.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
        } catch (_: IllegalArgumentException) {
            val value = context.handleWeirdStringValue(Double::class, text, "not a valid `Double` value") as Number?
            return nonNullNumber(value).toDouble()
        }
    }

    /**
     * Helper method called to check whether given String value contains one of "special" values (currently, NaN
     * ("not-a-number") and plus/minus Infinity) and if so, returns that value; otherwise returns `null`.
     *
     * @param text String value to check
     *
     * @return One of [Double] constants referring to special value decoded, if value matched; `null` otherwise.
     */
    protected open fun checkDoubleSpecialValue(text: String): Double? {
        if (text.isEmpty()) {
            return null
        }

        return when (text[0]) {
            'I' -> Double.POSITIVE_INFINITY.takeIf { isPositiveInfinity(text) }
            'N' -> Double.NaN.takeIf { isNaN(text) }
            '-' -> Double.NEGATIVE_INFINITY.takeIf { isNegativeInfinity(text) }
            else -> null
        }
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected open fun parseDate(parser: CirJsonParser, context: DeserializationContext): Date? {
        val text = when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                parser.text!!
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                val timestamp = try {
                    parser.longValue
                } catch (_: InputCoercionException) {
                    val value = context.handleWeirdNumberValue(myValueClass, parser.numberValue,
                            "not a valid 64-bit `Long` for creating `java.util.Date`") as Number
                    value.toLong()
                }

                return Date(timestamp)
            }

            CirJsonTokenId.ID_NULL -> {
                return getNullValue(context) as Date?
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                context.extractScalarFromObject(parser, this, myValueClass)
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                return parseDateFromArray(parser, context)
            }

            else -> {
                return context.handleUnexpectedToken(getValueType(context), parser) as Date?
            }
        }

        return parseDate(text.trim(), context)
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected open fun parseDateFromArray(parser: CirJsonParser, context: DeserializationContext): Date? {
        val action = findCoercionFromEmptyArray(context)
        val unwrap = context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)

        if (!unwrap && action == CoercionAction.FAIL) {
            return context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser, null) as Date?
        }

        val token = parser.nextToken()

        return if (token == CirJsonToken.END_ARRAY) {
            when (action) {
                CoercionAction.AS_EMPTY -> getEmptyValue(context) as Date?
                CoercionAction.AS_NULL, CoercionAction.TRY_CONVERT -> getNullValue(context) as Date?
                else -> context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser,
                        null) as Date?
            }
        } else if (unwrap) {
            if (token == CirJsonToken.START_ARRAY) {
                handleNestedArrayForSingle(parser, context) as Date?
            } else {
                val parsed = parseDate(parser, context)
                verifyEndArrayForSingle(parser, context)
                parsed
            }
        } else {
            context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser, null) as Date?
        }
    }

    /**
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected open fun parseDate(value: String, context: DeserializationContext): Date? {
        return try {
            if (value.isEmpty()) {
                val action = checkFromStringCoercion(context, value)

                if (action == CoercionAction.AS_EMPTY) {
                    Date(0L)
                } else {
                    null
                }
            } else if (hasTextualNull(value)) {
                null
            } else {
                context.parseDate(value)
            }
        } catch (e: IllegalArgumentException) {
            context.handleWeirdStringValue(myValueClass, value,
                    "not a valid representation (error: ${e.exceptionMessage()})") as Date?
        }
    }

    /**
     * Helper method used for deserializing String value, if possible, doing necessary conversion or throwing exception
     * as necessary.
     * 
     * Will check `CoercionConfig` configuration to check whether coercion needed is allowed.
     *
     * @param parser Currently active parser being iterated over
     * 
     * @param context Deserialization context
     * 
     * @param nullValueProvider Entity we (only) need for case of secondary value type being coerced into `null`: if so,
     * provider is asked for possible "`null` replacement" value.
     */
    @Throws(CirJacksonException::class)
    protected fun parseString(parser: CirJsonParser, context: DeserializationContext,
            nullValueProvider: NullValueProvider): String? {
        val rawTargetType = String::class

        val action = when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                return parser.text!!
            }

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> {
                val obj = parser.embeddedObject

                return if (obj is ByteArray) {
                    context.base64Variant.encode(obj, false)
                } else {
                    obj?.toString()
                }
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                return context.extractScalarFromObject(parser, this, rawTargetType)
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                checkIntToStringCoercion(parser, context, rawTargetType)
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                checkFloatToStringCoercion(parser, context, rawTargetType)
            }

            CirJsonTokenId.ID_TRUE, CirJsonTokenId.ID_FALSE -> {
                checkBooleanToStringCoercion(parser, context, rawTargetType)
            }

            else -> {
                CoercionAction.TRY_CONVERT
            }
        }

        if (action == CoercionAction.AS_NULL) {
            return nullValueProvider.getNullValue(context) as String?
        }

        if (action == CoercionAction.AS_EMPTY) {
            return ""
        }

        if (parser.currentToken()!!.isScalarValue) {
            val text = parser.valueAsString

            if (text != null) {
                return text
            }
        }

        return context.handleUnexpectedToken(rawTargetType, parser) as String?
    }

    /**
     * Helper method called to determine if we are seeing String value of `"null"`, and, further, that it should be
     * coerced to `null` just like `null` token.
     */
    protected open fun hasTextualNull(value: String): Boolean {
        return "null" == value
    }

    protected fun isNegativeInfinity(text: String): Boolean {
        return "-Infinity" == text || "-INF" == text
    }

    protected fun isPositiveInfinity(text: String): Boolean {
        return "Infinity" == text || "INF" == text
    }

    protected fun isNaN(text: String): Boolean {
        return "NaN" == text
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, coercion checks
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun checkFromStringCoercion(context: DeserializationContext, value: String): CoercionAction {
        return checkFromStringCoercion(context, value, logicalType(), handledType())
    }

    @Throws(CirJacksonException::class)
    protected open fun checkFromStringCoercion(context: DeserializationContext, value: String,
            logicalType: LogicalType?, rawTargetType: KClass<*>): CoercionAction {
        return if (value.isEmpty()) {
            val action = context.findCoercionAction(logicalType, rawTargetType, CoercionInputShape.EMPTY_STRING)
            checkCoercionFail(context, action, rawTargetType, value, "empty String (\"\")")
        } else if (isBlank(value)) {
            val action = context.findCoercionFromBlankString(logicalType, rawTargetType, CoercionAction.FAIL)
            checkCoercionFail(context, action, rawTargetType, value, "blank String (all whitespace)")
        } else if (context.isEnabled(StreamReadCapability.UNTYPED_SCALARS)) {
            CoercionAction.TRY_CONVERT
        } else {
            val action = context.findCoercionAction(logicalType, rawTargetType, CoercionInputShape.STRING)

            if (action == CoercionAction.FAIL) {
                return context.reportInputMismatch(this,
                        "Cannot coerce String value (\"$value\") to ${coercedTypeDescription()} (but might if coercion using `CoercionConfig` was enabled)")
            }

            action
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun checkFloatToIntCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        val action = context.findCoercionAction(LogicalType.INTEGER, rawTargetType, CoercionInputShape.FLOAT)
        return action.takeUnless { it == CoercionAction.FAIL } ?: checkCoercionFail(context, action, rawTargetType,
                parser.numberValue, "Floating-point value (${parser.text})")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkIntToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        return checkToStringCoercion(parser, context, rawTargetType, parser.numberValue, CoercionInputShape.INTEGER)
    }

    @Throws(CirJacksonException::class)
    protected open fun checkFloatToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        return checkToStringCoercion(parser, context, rawTargetType, parser.numberValue, CoercionInputShape.FLOAT)
    }

    @Throws(CirJacksonException::class)
    protected open fun checkBooleanToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        return checkToStringCoercion(parser, context, rawTargetType, parser.booleanValue, CoercionInputShape.BOOLEAN)
    }

    @Throws(CirJacksonException::class)
    protected open fun checkToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>, inputValue: Any, inputShape: CoercionInputShape): CoercionAction {
        val action = context.findCoercionAction(LogicalType.TEXTUAL, rawTargetType, inputShape)
        return action.takeUnless { it == CoercionAction.FAIL } ?: checkCoercionFail(context, action, rawTargetType,
                parser.numberValue, "${inputShape.name} value (${parser.text})")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkIntToFloatCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        val action = context.findCoercionAction(LogicalType.FLOAT, rawTargetType, CoercionInputShape.INTEGER)
        return action.takeUnless { it == CoercionAction.FAIL } ?: checkCoercionFail(context, action, rawTargetType,
                parser.numberValue, "Integer value (${parser.text})")
    }

    @Throws(CirJacksonException::class)
    protected open fun coerceBooleanFromInt(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): Boolean? {
        val action = context.findCoercionAction(LogicalType.BOOLEAN, rawTargetType, CoercionInputShape.INTEGER)

        return when (action) {
            CoercionAction.FAIL -> {
                checkCoercionFail(context, action, rawTargetType, parser.numberValue, "Integer value (${parser.text})")
                false
            }

            CoercionAction.AS_NULL -> null

            CoercionAction.AS_EMPTY -> false

            else -> {
                if (parser.numberType == CirJsonParser.NumberType.INT) {
                    parser.intValue != 0
                } else {
                    "0" != parser.text
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun checkCoercionFail(context: DeserializationContext, action: CoercionAction, targetType: KClass<*>,
            inputValue: Any, inputDescription: String): CoercionAction {
        return action.takeUnless { it == CoercionAction.FAIL } ?: context.reportBadCoercion(this, targetType,
                inputValue, "Cannot coerce $inputDescription to ${
            coercedTypeDescription(targetType)
        } (but could if coercion was enabled using `CoercionConfig`)")
    }

    /**
     * Method called when otherwise unrecognized String value is encountered for a non-primitive type: should see if it
     * is String value `"null"`, and if so, whether it is acceptable according to configuration or not.
     */
    protected open fun checkTextualNull(context: DeserializationContext, text: String): Boolean {
        return if (hasTextualNull(text)) {
            if (!context.isEnabled(MapperFeature.ALLOW_COERCION_OF_SCALARS)) {
                reportFailedNullCoerce(context, true, MapperFeature.ALLOW_COERCION_OF_SCALARS, "String \"null\"")
            }

            true
        } else {
            false
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, other coercions
     *******************************************************************************************************************
     */

    /**
     * Helper method called in case where an integral number is encountered, but config settings suggest that a coercion
     * may be needed to "upgrade" [Number] into "bigger" type like [Long] or [java.math.BigInteger]
     *
     * @see DeserializationFeature.USE_BIG_INTEGER_FOR_INTS
     * 
     * @see DeserializationFeature.USE_LONG_FOR_INTS
     */
    @Throws(CirJacksonException::class)
    protected open fun coerceIntegral(parser: CirJsonParser, context: DeserializationContext): Any? {
        return if (context.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)) {
            parser.bigIntegerValue
        } else if (context.isEnabled(DeserializationFeature.USE_LONG_FOR_INTS)) {
            parser.longValue
        } else {
            parser.numberValue
        }
    }

    /**
     * Method called to verify that `null` token from input is acceptable for primitive (unboxed) target type. It should
     * NOT be called if `null` was received by other means (coerced due to configuration, or even from optionally
     * acceptable String `"null"` token).
     */
    @Throws(DatabindException::class)
    protected fun verifyNullForPrimitive(context: DeserializationContext) {
        if (context.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
            return context.reportInputMismatch(this,
                    "Cannot coerce `null` to ${coercedTypeDescription()} (disable `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` to allow)")
        }
    }

    /**
     * Method called to verify that text value `"null"` from input is acceptable for primitive (unboxed) target type. It
     * should not be called if actual `null` token was received, or if null is a result of coercion from Some other
     * input type.
     */
    @Throws(DatabindException::class)
    protected fun verifyNullForPrimitiveCoercion(context: DeserializationContext, string: String) {
        val (feature, enable) = if (!context.isEnabled(MapperFeature.ALLOW_COERCION_OF_SCALARS)) {
            MapperFeature.ALLOW_COERCION_OF_SCALARS to true
        } else if (context.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)) {
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES to false
        } else {
            return
        }

        val stringDescription = string.takeUnless { it.isEmpty() }?.let { "String \"$it\"" } ?: "empty String (\"\")"
        reportFailedNullCoerce(context, enable, feature, stringDescription)
    }

    @Throws(DatabindException::class)
    protected open fun reportFailedNullCoerce(context: DeserializationContext, state: Boolean, feature: Enum<*>,
            inputDescription: String): Nothing {
        val enableDescription = if (state) "enable" else "disable"
        context.reportInputMismatch<Nothing>(this,
                "Cannot coerce $inputDescription to Null value as ${coercedTypeDescription()} ($enableDescription `${feature.declaringJavaClass.simpleName}.${feature.name}` to allow)")
    }

    /**
     * Helper method called to get a description of type into which a scalar value coercion is (most likely) being
     * applied, to be used for constructing exception messages on coerce failure.
     *
     * @return Message with backtick-enclosed name of type this deserializer supports
     */
    protected open fun coercedTypeDescription(): String {
        val type = valueType

        val (structured, typeDescription) = if (!(type?.isPrimitive ?: true)) {
            (type.isContainerType || type.isReferenceType) to type.typeDescription
        } else {
            val clazz = handledType()
            clazz.isCollectionMapOrArray to clazz.classDescription
        }

        return if (structured) {
            "element of $typeDescription"
        } else {
            "$typeDescription value"
        }
    }

    /**
     * Helper method called to get a description of type into which a scalar value coercion is being applied, to be used
     * for constructing exception messages on coerce failure.
     *
     * @return Message with backtick-enclosed name of target type
     */
    protected open fun coercedTypeDescription(rawTargetType: KClass<*>): String {
        val typeDescription = rawTargetType.classDescription

        return if (rawTargetType.isCollectionMapOrArray) {
            "element of $typeDescription"
        } else {
            "$typeDescription value"
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, resolving dependencies
     *******************************************************************************************************************
     */

    /**
     * Helper method used to locate deserializers for properties the type this deserializer handles contains (usually
     * for properties of bean types)
     *
     * @param type Type of property to deserialize
     * 
     * @param property Actual property object (field, method, constructor parameter) used for passing deserialized
     * values; provided so deserializer can be contextualized if necessary
     */
    protected open fun findDeserializer(context: DeserializationContext, type: KotlinType,
            property: BeanProperty?): ValueDeserializer<Any> {
        return context.findContextualValueDeserializer(type, property)
    }

    /**
     * Helper method to check whether given text refers to what looks like a clean simple integer number, consisting of
     * optional sign followed by a sequence of digits.
     * 
     * Note that definition is quite loose as leading zeroes are allowed, in addition to plus sign (not just minus).
     */
    protected fun isIntNumber(text: String): Boolean {
        val length = text.length

        if (length <= 0) {
            return false
        }

        val startChar = text[0]

        val start = if (startChar == '-' || startChar == '+') {
            if (length == 1) {
                return false
            }

            1
        } else {
            0
        }

        for (i in start..<length) {
            val char = text[i]

            if (char !in '0'..'9') {
                return false
            }
        }

        return true
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, deserializer construction
     *******************************************************************************************************************
     */

    /**
     * Helper method that can be used to see if specified property has annotation indicating that a converter is to be
     * used for contained values (contents of structured types; array/List/Map values)
     *
     * @param existingDeserializer (optional) configured content serializer if one already exists.
     */
    protected open fun findConvertingContentDeserializer(context: DeserializationContext, property: BeanProperty?,
            existingDeserializer: ValueDeserializer<*>?): ValueDeserializer<*>? {
        val introspector = context.annotationIntrospector ?: return existingDeserializer
        property ?: return existingDeserializer
        val member = property.member ?: return existingDeserializer
        val converterDefinition =
                introspector.findDeserializationContentConverter(context.config, member) ?: return existingDeserializer
        val converter = context.converterInstance(member, converterDefinition)!!
        val delegateType = converter.getInputType(context.typeFactory)
        return StandardConvertingDeserializer(converter, delegateType,
                existingDeserializer ?: context.findContextualValueDeserializer(delegateType, property))
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, accessing contextual config settings
     *******************************************************************************************************************
     */

    /**
     * Helper method that may be used to find if this deserializer has specific [CirJsonFormat] settings, either via
     * property, or through type-specific defaulting.
     *
     * @param typeForDefaults Type (erased) used for finding default format settings, if any
     */
    protected open fun findFormatOverrides(context: DeserializationContext, property: BeanProperty?,
            typeForDefaults: KClass<*>): CirJsonFormat.Value {
        return property?.findPropertyFormat(context.config, typeForDefaults) ?: context.getDefaultPropertyFormat(
                typeForDefaults)
    }

    /**
     * Convenience method that uses [findFormatOverrides] to find possible defaults and/of overrides, and then calls
     * `CirJsonFormat.Value.getFeature` to find whether that feature has been specifically marked as enabled or
     * disabled.
     *
     * @param typeForDefaults Type (erased) used for finding default format settings, if any
     */
    protected open fun findFormatFeature(context: DeserializationContext, property: BeanProperty?,
            typeForDefaults: KClass<*>, feature: CirJsonFormat.Feature): Boolean? {
        val format = findFormatOverrides(context, property, typeForDefaults)
        return format.getFeature(feature)
    }

    /**
     * Method called to find [NullValueProvider] for a primary property, using "value `nulls`" setting. If no provider
     * found (not defined, or is "skip"), will return `null`.
     */
    protected open fun findValueNullProvider(context: DeserializationContext, property: SettableBeanProperty?,
            propertyMetadata: PropertyMetadata): NullValueProvider? {
        return property?.let { findNullProvider(context, it, propertyMetadata.valueNulls, it.valueDeserializer) }
    }

    /**
     * Method called to find [NullValueProvider] for a contents of a structured primary property (Collection, Map,
     * array), using "content `nulls`" setting. If no provider found (not defined), will return given value deserializer
     * (which is a `null` value provider itself).
     */
    protected open fun findValueNullProvider(context: DeserializationContext, property: BeanProperty?,
            valueDeserializer: ValueDeserializer<*>): NullValueProvider {
        return when (val nulls = findContentNullStyle(context, property)!!) {
            Nulls.SKIP -> {
                NullsConstantProvider.skipper()
            }

            Nulls.FAIL -> {
                property?.let { NullsFailProvider.constructForProperty(it, it.type.contentType) } ?: let {
                    val type = context.constructType(valueDeserializer.handledType())!!
                            .let { type -> type.takeIf { it.isContainerType }?.contentType ?: type }
                    NullsFailProvider.constructForRootValue(type)
                }
            }

            else -> {
                findNullProvider(context, property, nulls, valueDeserializer) ?: valueDeserializer
            }
        }
    }

    protected open fun findContentNullStyle(context: DeserializationContext, property: BeanProperty?): Nulls? {
        return if (property == null) {
            context.config.defaultNullHandling.contentNulls
        } else {
            property.metadata.contentNulls
        }
    }

    protected fun findNullProvider(context: DeserializationContext, property: BeanProperty?, nulls: Nulls?,
            valueDeserializer: ValueDeserializer<*>?): NullValueProvider? {
        if (nulls == Nulls.FAIL) {
            return property?.let { NullsFailProvider.constructForProperty(it) }
                    ?: NullsFailProvider.constructForRootValue(
                            context.constructType(valueDeserializer?.handledType() ?: Any::class))
        }

        if (nulls != Nulls.AS_EMPTY) {
            return if (nulls == Nulls.SKIP) {
                NullsConstantProvider.skipper()
            } else {
                null
            }
        }

        valueDeserializer ?: return null

        if (valueDeserializer is BeanDeserializerBase) {
            val valueInstantiator = valueDeserializer.valueInstantiator!!

            if (!valueInstantiator.canCreateUsingDefault()) {
                val type = property?.type ?: valueDeserializer.valueType!!
                return context.reportBadDefinition(type, "Cannot create empty instance of $type, no default Creator")
            }
        }

        val accessPattern = valueDeserializer.emptyAccessPattern

        return when (accessPattern) {
            AccessPattern.ALWAYS_NULL -> NullsConstantProvider.nuller()
            AccessPattern.CONSTANT -> NullsConstantProvider.forValue(valueDeserializer.getEmptyValue(context))
            else -> NullsAsEmptyProvider(valueDeserializer)
        }
    }

    protected open fun findCoercionFromEmptyString(context: DeserializationContext): CoercionAction {
        return context.findCoercionAction(logicalType(), handledType(), CoercionInputShape.EMPTY_STRING)
    }

    protected open fun findCoercionFromEmptyArray(context: DeserializationContext): CoercionAction {
        return context.findCoercionAction(logicalType(), handledType(), CoercionInputShape.EMPTY_ARRAY)
    }

    protected open fun findCoercionFromBlankString(context: DeserializationContext): CoercionAction {
        return context.findCoercionFromBlankString(logicalType(), handledType(), CoercionAction.FAIL)
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, problem reporting
     *******************************************************************************************************************
     */

    /**
     * Method called to deal with a property that did not map to a known Bean property. Method can deal with the problem
     * as it sees fit (ignore, throw exception); but if it does return, it has to skip the matching CirJSON content
     * parser has.
     *
     * @param parser Parser that points to value of the unknown property
     * 
     * @param context Context for deserialization; allows access to the parser, error reporting functionality
     * 
     * @param instanceOrClass Instance that is being populated by this deserializer or, if not known, KClass that would
     * be instantiated. If `null`, will assume type is what [handledType] returns.
     * 
     * @param propertyName Name of the property that cannot be mapped
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnknownProperty(parser: CirJsonParser, context: DeserializationContext,
            instanceOrClass: Any?, propertyName: String) {
        if (context.handleUnknownProperty(parser, this, instanceOrClass ?: handledType(), propertyName)) {
            return
        }

        parser.skipChildren()
    }

    @Throws(CirJacksonException::class)
    protected open fun handleMissingEndArrayForSingle(parser: CirJsonParser, context: DeserializationContext) {
        return context.reportWrongTokenException(this, CirJsonToken.END_ARRAY,
                "Attempted to unwrap '${handledType().qualifiedName}' value from an array (with `DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS`) but it contains more than one value")
    }

    /**
     * Helper method called when detecting a deep(er) nesting of Arrays when trying to unwrap value for
     * [DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS].
     */
    @Throws(CirJacksonException::class)
    protected open fun handleNestedArrayForSingle(parser: CirJsonParser, context: DeserializationContext): Any {
        return context.handleUnexpectedToken(getValueType(context), parser.currentToken(), parser,
                "Cannot deserialize value of type ${myValueClass.name} out of ${CirJsonToken.START_ARRAY} token: nested Arrays not allowed with `DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS`")!!
    }

    @Throws(CirJacksonException::class)
    protected open fun verifyEndArrayForSingle(parser: CirJsonParser, context: DeserializationContext) {
        if (parser.nextToken() != CirJsonToken.END_ARRAY) {
            handleMissingEndArrayForSingle(parser, context)
        }
    }

    protected open fun wrapIOFailure(context: DeserializationContext, e: IOException): CirJacksonIOException {
        return CirJacksonIOException.construct(e, context.parser)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    protected fun byteOverflow(value: Int): Boolean {
        return value !in Byte.MIN_VALUE..255
    }

    protected fun shortOverflow(value: Int): Boolean {
        return value !in Short.MIN_VALUE..Short.MAX_VALUE
    }

    protected fun intOverflow(value: Long): Boolean {
        return value !in Int.MIN_VALUE..Int.MAX_VALUE
    }

    protected open fun nonNullNumber(number: Number?): Number {
        return number ?: 0
    }

    companion object {

        /**
         * Bitmask that covers [DeserializationFeature.USE_BIG_INTEGER_FOR_INTS] and
         * [DeserializationFeature.USE_LONG_FOR_INTS], used for more efficient cheks when coercing integral values for
         * untyped deserialization.
         */
        val FEATURE_MASK_INT_COERCIONS =
                DeserializationFeature.USE_BIG_INTEGER_FOR_INTS.mask or DeserializationFeature.USE_LONG_FOR_INTS.mask

        /**
         * Helper method for encapsulating calls to low-level double value parsing; single place just because we need a
         * work-around that must be applied to all calls.
         */
        @Throws(NumberFormatException::class)
        fun parseDouble(numberString: String, useFastParser: Boolean): Double {
            return NumberInput.parseDouble(numberString, useFastParser)
        }

        fun isBlank(text: String): Boolean {
            return text.none { it.code > 0x0020 }
        }

    }

}