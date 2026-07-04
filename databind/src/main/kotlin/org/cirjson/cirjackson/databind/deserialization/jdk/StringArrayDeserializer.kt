package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsConstantProvider
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern

/**
 * Separate implementation for serializing String arrays (instead of using [ObjectArrayDeserializer]). Used if (and only
 * if) no custom value deserializers are used.
 */
@CirJacksonStandardImplementation
class StringArrayDeserializer : StandardDeserializer<Array<String?>> {

    /**
     * Value deserializer to use, if not the standard one (which is inlined)
     */
    private val myElementDeserializer: ValueDeserializer<String>?

    /**
     * Handler we need for dealing with `null` values as elements
     */
    private val myNullProvider: NullValueProvider?

    /**
     * Specific override for this instance (from proper, or global per-type overrides) to indicate whether single value
     * may be taken to mean an unwrapped one-element array or not. If `null`, left to global defaults.
     */
    private val myUnwrapSingle: Boolean?

    /**
     * Marker flag set if the [myNullProvider] indicates that all `null` content values should be skipped (instead of
     * being possibly converted).
     */
    private val mySkipNullValues: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : this(null, null, null)

    @Suppress("UNCHECKED_CAST")
    private constructor(deserializer: ValueDeserializer<*>?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(Array<String?>::class) {
        myElementDeserializer = deserializer as ValueDeserializer<String>?
        myNullProvider = nullValueProvider
        myUnwrapSingle = unwrapSingle
        mySkipNullValues = NullsConstantProvider.isSkipper(nullValueProvider)
    }

    override fun logicalType(): LogicalType {
        return LogicalType.ARRAY
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean {
        return true
    }

    override val emptyAccessPattern: AccessPattern
        get() = AccessPattern.CONSTANT

    override fun getEmptyValue(context: DeserializationContext): Any {
        return NO_STRINGS
    }

    private fun withResolved(deserializer: ValueDeserializer<*>?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?): StringArrayDeserializer {
        if (myElementDeserializer === deserializer && myNullProvider === nullValueProvider &&
                myUnwrapSingle == unwrapSingle) {
            return this
        }

        return StringArrayDeserializer(deserializer, nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    /**
     * Contextualization is needed to see whether we can "inline" deserialization of String values, or if we have to use
     * separate value deserializer.
     */
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val type = context.constructType(String::class)!!
        val deserializer = (findConvertingContentDeserializer(context, property,
                myElementDeserializer)?.let { context.handleSecondaryContextualization(it, property, type) }
                ?: context.findContextualValueDeserializer(type, property))
        val unwrapSingle = findFormatFeature(context, property, Array<String?>::class,
                CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        val nullValueProvider = findContentNullProvider(context, property, deserializer)
        return withResolved(deserializer.takeUnless { isDefaultDeserializer(it) }, nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Array<String?>? {
        if (!parser.isExpectedStartArrayToken) {
            return handleNonArray(parser, context)
        } else if (myElementDeserializer != null) {
            return deserializeCustom(parser, context, null)
        }

        val buffer = context.leaseObjectBuffer()
        var chunk = buffer.resetAndStart()

        var index = 0
        val nullValueProvider = myNullProvider!!

        try {
            while (true) {
                val value = parser.nextTextValue() ?: let {
                    val token = parser.currentToken()

                    if (token == CirJsonToken.END_ARRAY) {
                        break
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (mySkipNullValues) {
                            continue
                        }

                        nullValueProvider.getNullValue(context) as String?
                    } else {
                        parseString(parser, context, nullValueProvider)
                    }
                }

                if (index >= chunk.size) {
                    chunk = buffer.appendCompletedChunk(chunk)
                    index = 0
                }

                chunk[index++] = value
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, chunk, buffer.bufferedSize() + index)
        }

        val result = buffer.completeAndClearBuffer(chunk, index, String::class)
        context.returnObjectBuffer(buffer)
        return result
    }

    /**
     * Offlined version used when we do not use the default deserialization method.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    private fun deserializeCustom(parser: CirJsonParser, context: DeserializationContext,
            old: Array<String?>?): Array<String?> {
        val buffer = context.leaseObjectBuffer()

        var (index, chunk) = old?.let { it.size to buffer.resetAndStart(it as Array<Any?>, it.size) }
                ?: (0 to buffer.resetAndStart())

        val nullValueProvider = myNullProvider!!
        val deserializer = myElementDeserializer!!

        try {
            while (true) {
                val value = if (parser.nextTextValue() == null) {
                    val token = parser.currentToken()

                    if (token == CirJsonToken.END_ARRAY) {
                        break
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (mySkipNullValues) {
                            continue
                        }

                        nullValueProvider.getNullValue(context) as String?
                    } else {
                        deserializer.deserialize(parser, context)
                    }
                } else {
                    deserializer.deserialize(parser, context)
                }

                if (index >= chunk.size) {
                    chunk = buffer.appendCompletedChunk(chunk)
                    index = 0
                }

                chunk[index++] = value
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, String::class, index)
        }

        val result = buffer.completeAndClearBuffer(chunk, index, String::class)
        context.returnObjectBuffer(buffer)
        return result
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromArray(parser, context)
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: Array<String?>): Array<String?> {
        if (!parser.isExpectedStartArrayToken) {
            val array = handleNonArray(parser, context) ?: return intoValue
            val offset = intoValue.size
            val result = array.copyOf(offset + array.size)
            array.copyInto(result, offset)
            return result
        } else if (myElementDeserializer != null) {
            return deserializeCustom(parser, context, intoValue)
        }

        val buffer = context.leaseObjectBuffer()
        var index = intoValue.size
        var chunk = buffer.resetAndStart(intoValue as Array<Any?>, index)

        val nullValueProvider = myNullProvider!!

        try {
            while (true) {
                val value = parser.nextTextValue() ?: let {
                    val token = parser.currentToken()

                    if (token == CirJsonToken.END_ARRAY) {
                        break
                    } else if (token == CirJsonToken.VALUE_NULL) {
                        if (mySkipNullValues) {
                            continue
                        }

                        nullValueProvider.getNullValue(context) as String?
                    } else {
                        parseString(parser, context, nullValueProvider)
                    }
                }

                if (index >= chunk.size) {
                    chunk = buffer.appendCompletedChunk(chunk)
                    index = 0
                }

                chunk[index++] = value
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, chunk, buffer.bufferedSize() + index)
        }

        val result = buffer.completeAndClearBuffer(chunk, index, String::class)
        context.returnObjectBuffer(buffer)
        return result
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    private fun handleNonArray(parser: CirJsonParser, context: DeserializationContext): Array<String?>? {
        val canWrap = myUnwrapSingle ?: context.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

        if (!canWrap) {
            if (!parser.hasToken(CirJsonToken.VALUE_STRING)) {
                return context.handleUnexpectedToken(getValueType(context), parser) as Array<String?>?
            }

            return deserializeFromString(parser, context)
        }

        val nullValueProvider = myNullProvider!!

        val value = if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            nullValueProvider.getNullValue(context) as String?
        } else {
            if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
                val textValue = parser.text!!

                if (textValue.isEmpty()) {
                    val action =
                            context.findCoercionAction(logicalType(), handledType(), CoercionInputShape.EMPTY_STRING)

                    if (action != CoercionAction.FAIL) {
                        return deserializeFromEmptyString(parser, context, action, handledType(),
                                "empty String (\"\")") as Array<String?>?
                    }
                } else if (isBlank(textValue)) {
                    val action = context.findCoercionFromBlankString(logicalType(), handledType(), CoercionAction.FAIL)

                    if (action != CoercionAction.FAIL) {
                        return deserializeFromEmptyString(parser, context, action, handledType(),
                                "blank String (all whitespace)") as Array<String?>?
                    }
                }
            }

            parseString(parser, context, nullValueProvider)
        }

        return arrayOf(value)
    }

    companion object {

        private val NO_STRINGS = emptyArray<String?>()

        val INSTANCE = StringArrayDeserializer()

    }

}