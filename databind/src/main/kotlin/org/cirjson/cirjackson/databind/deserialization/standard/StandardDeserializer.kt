package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.isCirJacksonStandardImplementation
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
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseBytePrimitive(parser: CirJsonParser, context: DeserializationContext): Byte {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseShortPrimitive(parser: CirJsonParser, context: DeserializationContext): Short {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseIntPrimitive(parser: CirJsonParser, context: DeserializationContext): Int {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseIntPrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Int {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseInt(parser: CirJsonParser, context: DeserializationContext, text: String): Int? {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseLongPrimitive(parser: CirJsonParser, context: DeserializationContext): Long {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseLongPrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Long {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseLong(context: DeserializationContext, text: String): Long? {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseFloatPrimitive(parser: CirJsonParser, context: DeserializationContext): Float {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseFloatPrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Float {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseDoublePrimitive(parser: CirJsonParser, context: DeserializationContext): Double {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected fun parseDoublePrimitive(parser: CirJsonParser, context: DeserializationContext, text: String): Double {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected open fun parseDate(parser: CirJsonParser, context: DeserializationContext): Date? {
        TODO("Not yet implemented")
    }

    /**
     * @param parser Underlying parser
     * 
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected open fun parseDateFromArray(parser: CirJsonParser, context: DeserializationContext): Date? {
        TODO("Not yet implemented")
    }

    /**
     * @param context Deserialization context for accessing configuration
     */
    @Throws(CirJacksonException::class)
    protected open fun parseDate(value: String, context: DeserializationContext): Date? {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to determine if we are seeing String value of `"null"`, and, further, that it should be
     * coerced to `null` just like `null` token.
     */
    protected open fun hasTextualNull(value: String): Boolean {
        TODO("Not yet implemented")
    }

    protected fun isNegativeInfinity(text: String): Boolean {
        TODO("Not yet implemented")
    }

    protected fun isPositiveInfinity(text: String): Boolean {
        TODO("Not yet implemented")
    }

    protected fun isNaN(text: String): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses, coercion checks
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun checkFromStringCoercion(context: DeserializationContext, value: String): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkFromStringCoercion(context: DeserializationContext, value: String,
            logicalType: LogicalType?, rawTargetType: KClass<*>): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkFloatToIntCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkIntToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkFloatToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkBooleanToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkToStringCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>, inputValue: Any, inputShape: CoercionInputShape): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkIntToFloatCoercion(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): CoercionAction {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun coerceBooleanFromInt(parser: CirJsonParser, context: DeserializationContext,
            rawTargetType: KClass<*>): Boolean? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun checkCoercionFail(context: DeserializationContext, action: CoercionAction, targetType: KClass<*>,
            inputValue: Any, inputDescription: String): CoercionAction {
        TODO("Not yet implemented")
    }

    /**
     * Method called when otherwise unrecognized String value is encountered for a non-primitive type: should see if it
     * is String value `"null"`, and if so, whether it is acceptable according to configuration or not.
     */
    protected open fun checkTextualNull(context: DeserializationContext, text: String): Boolean {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Method called to verify that `null` token from input is acceptable for primitive (unboxed) target type. It should
     * NOT be called if `null` was received by other means (coerced due to configuration, or even from optionally
     * acceptable String `"null"` token).
     */
    @Throws(DatabindException::class)
    protected fun verifyNullForPrimitive(context: DeserializationContext) {
        TODO("Not yet implemented")
    }

    /**
     * Method called to verify that text value `"null"` from input is acceptable for primitive (unboxed) target type. It
     * should not be called if actual `null` token was received, or if null is a result of coercion from Some other
     * input type.
     */
    @Throws(DatabindException::class)
    protected fun verifyNullForPrimitiveCoercion(context: DeserializationContext, string: String) {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    protected open fun reportFailedNullCoerce(context: DeserializationContext, state: Boolean, feature: Enum<*>,
            inputDescription: String): Nothing {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to get a description of type into which a scalar value coercion is (most likely) being
     * applied, to be used for constructing exception messages on coerce failure.
     *
     * @return Message with backtick-enclosed name of type this deserializer supports
     */
    protected open fun coercedTypeDescription(): String {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to get a description of type into which a scalar value coercion is being applied, to be used
     * for constructing exception messages on coerce failure.
     *
     * @return Message with backtick-enclosed name of target type
     */
    protected open fun coercedTypeDescription(rawTargetType: KClass<*>): String {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Helper method to check whether given text refers to what looks like a clean simple integer number, consisting of
     * optional sign followed by a sequence of digits.
     * 
     * Note that definition is quite loose as leading zeroes are allowed, in addition to plus sign (not just minus).
     */
    protected fun isIntNumber(text: String): Boolean {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Method called to find [NullValueProvider] for a primary property, using "value `nulls`" setting. If no provider
     * found (not defined, or is "skip"), will return `null`.
     */
    protected open fun findValueNullProvider(context: DeserializationContext, property: BeanProperty?,
            propertyMetadata: PropertyMetadata): NullValueProvider? {
        TODO("Not yet implemented")
    }

    /**
     * Method called to find [NullValueProvider] for a contents of a structured primary property (Collection, Map,
     * array), using "content `nulls`" setting. If no provider found (not defined), will return given value deserializer
     * (which is a `null` value provider itself).
     */
    protected open fun findValueNullProvider(context: DeserializationContext, property: BeanProperty?,
            valueDeserializer: ValueDeserializer<*>): NullValueProvider {
        TODO("Not yet implemented")
    }

    protected open fun findContentNullStyle(context: DeserializationContext, property: BeanProperty?): Nulls? {
        TODO("Not yet implemented")
    }

    protected fun findNullProvider(context: DeserializationContext, property: BeanProperty?, nulls: Nulls?,
            valueDeserializer: ValueDeserializer<*>?): NullValueProvider? {
        TODO("Not yet implemented")
    }

    protected open fun findCoercionFromEmptyString(context: DeserializationContext): CoercionAction {
        TODO("Not yet implemented")
    }

    protected open fun findCoercionFromEmptyArray(context: DeserializationContext): CoercionAction {
        TODO("Not yet implemented")
    }

    protected open fun findCoercionFromBlankString(context: DeserializationContext): CoercionAction {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun handleMissingEndArrayForSingle(parser: CirJsonParser, context: DeserializationContext) {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called when detecting a deep(er) nesting of Arrays when trying to unwrap value for
     * [DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS].
     */
    @Throws(CirJacksonException::class)
    protected open fun handleNestedArrayForSingle(parser: CirJsonParser, context: DeserializationContext): Any {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun verifyEndArrayForSingle(parser: CirJsonParser, context: DeserializationContext) {
        TODO("Not yet implemented")
    }

    protected open fun wrapIOFailure(context: DeserializationContext, e: IOException): CirJacksonIOException {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    protected fun byteOverflow(value: Int): Boolean {
        TODO("Not yet implemented")
    }

    protected fun shortOverflow(value: Int): Boolean {
        TODO("Not yet implemented")
    }

    protected fun intOverflow(value: Long): Boolean {
        TODO("Not yet implemented")
    }

    protected open fun nonNullNumber(number: Number?): Number {
        TODO("Not yet implemented")
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