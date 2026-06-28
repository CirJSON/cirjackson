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
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.type.ArrayType
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern
import kotlin.reflect.KClass

/**
 * Deserializer that can serialize non-primitive arrays.
 */
@CirJacksonStandardImplementation
open class ObjectArrayDeserializer : ContainerDeserializerBase<Any> {

    /**
     * Flag that indicates whether the component type is Any or not. Used for minor optimization when constructing
     * result.
     */
    protected val myUntyped: Boolean

    /**
     * Type of contained elements: needed for constructing actual result array
     */
    protected val myElementClass: KClass<*>

    /**
     * Element deserializer
     */
    protected val myElementDeserializer: ValueDeserializer<Any>?

    /**
     * If element instances have polymorphic type information, this
     * is the type deserializer that can handle it
     */
    protected val myElementTypeDeserializer: TypeDeserializer?

    /**
     * Zero-sized value of array type.
     */
    protected val myEmptyValue: Array<Any?>

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(arrayType: KotlinType, elementDeserializer: ValueDeserializer<Any>?,
            elementTypeDeserializer: TypeDeserializer?) : super(arrayType, null, null) {
        myElementClass = (arrayType as ArrayType).contentType.rawClass
        myUntyped = myElementClass == Any::class
        myElementDeserializer = elementDeserializer
        myElementTypeDeserializer = elementTypeDeserializer
        myEmptyValue = arrayType.emptyArray
    }

    protected constructor(source: ObjectArrayDeserializer, elementDeserializer: ValueDeserializer<Any>?,
            elementTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(source, nullValueProvider, unwrapSingle) {
        myElementClass = source.myElementClass
        myUntyped = source.myUntyped
        myElementDeserializer = elementDeserializer
        myElementTypeDeserializer = elementTypeDeserializer
        myEmptyValue = source.myEmptyValue
    }

    open fun withDeserializer(elementTypeDeserializer: TypeDeserializer?,
            elementDeserializer: ValueDeserializer<*>?): ObjectArrayDeserializer {
        return withResolved(elementTypeDeserializer, elementDeserializer, myNullProvider, myUnwrapSingle)
    }

    @Suppress("UNCHECKED_CAST")
    open fun withResolved(elementTypeDeserializer: TypeDeserializer?, elementDeserializer: ValueDeserializer<*>?,
            nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?): ObjectArrayDeserializer {
        if (unwrapSingle == myUnwrapSingle && nullValueProvider === myNullProvider &&
                elementDeserializer === myElementDeserializer &&
                elementTypeDeserializer === myElementTypeDeserializer) {
            return this
        }

        return ObjectArrayDeserializer(this, elementDeserializer as ValueDeserializer<Any>?, elementTypeDeserializer,
                nullValueProvider, unwrapSingle)
    }

    override val isCacheable: Boolean
        get() = myElementDeserializer == null && myElementTypeDeserializer == null

    override fun logicalType(): LogicalType? {
        return LogicalType.ARRAY
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val unwrapSingle = findFormatFeature(context, property, myContainerType.rawClass,
                CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        val valueType = myContainerType.contentType!!

        val elementDeserializer = findConvertingContentDeserializer(context, property,
                myElementDeserializer)?.let { context.handleSecondaryContextualization(it, property, valueType) }
                ?: context.findContextualValueDeserializer(valueType, property)
        val elementTypeDeserializer = myElementTypeDeserializer?.forProperty(property)
        val nullValueProvider = findContentNullProvider(context, property, elementDeserializer)
        return withResolved(elementTypeDeserializer, elementDeserializer, nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * ContainerDeserializerBase implementation
     *******************************************************************************************************************
     */

    override val contentDeserializer: ValueDeserializer<Any>?
        get() = myElementDeserializer

    override val emptyAccessPattern: AccessPattern
        get() = AccessPattern.CONSTANT

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return myEmptyValue
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (!parser.isExpectedStartArrayToken) {
            return handleNonArray(parser, context)
        }

        val buffer = context.leaseObjectBuffer()
        var chunk = buffer.resetAndStart()
        var index = 0
        val typeDeserializer = myElementTypeDeserializer
        val nullProvider = myNullProvider!!
        val elementDeserializer = myElementDeserializer!!
        var token: CirJsonToken? = null

        try {
            while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        continue
                    }

                    nullProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    elementDeserializer.deserialize(parser, context)
                } else {
                    elementDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (index >= chunk.size) {
                    chunk = buffer.appendCompletedChunk(chunk)
                    index = 0
                }

                chunk[index++] = value
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, context, buffer.bufferedSize() + index)
        }

        val result = if (myUntyped) {
            buffer.completeAndClearBuffer(chunk, index)
        } else {
            buffer.completeAndClearBuffer(chunk, index, myElementClass)
        }

        context.returnObjectBuffer(buffer)
        return result
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromArray(parser, context)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        if (!parser.isExpectedStartArrayToken) {
            val array = handleNonArray(parser, context) as? Array<Any?>? ?: return intoValue
            val offset = (intoValue as Array<Any?>).size
            val result = intoValue.copyOf(array.size + offset)
            array.copyInto(result, offset)
            return result
        }

        val buffer = context.leaseObjectBuffer()
        var index = (intoValue as Array<Any?>).size
        var chunk = buffer.resetAndStart(intoValue, index)
        val typeDeserializer = myElementTypeDeserializer
        val nullProvider = myNullProvider!!
        val elementDeserializer = myElementDeserializer!!
        var token: CirJsonToken? = null

        try {
            while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        continue
                    }

                    nullProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    elementDeserializer.deserialize(parser, context)
                } else {
                    elementDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (index >= chunk.size) {
                    chunk = buffer.appendCompletedChunk(chunk)
                    index = 0
                }

                chunk[index++] = value
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, context, buffer.bufferedSize() + index)
        }

        val result = if (myUntyped) {
            buffer.completeAndClearBuffer(chunk, index)
        } else {
            buffer.completeAndClearBuffer(chunk, index, myElementClass)
        }

        context.returnObjectBuffer(buffer)
        return result
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromBase64(parser: CirJsonParser, context: DeserializationContext): ByteArray {
        val bytes = parser.getBinaryValue(context.base64Variant)!!
        return bytes.copyOf()
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(CirJacksonException::class)
    protected open fun handleNonArray(parser: CirJsonParser, context: DeserializationContext): Any? {
        val canWrap = myUnwrapSingle ?: context.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

        if (!canWrap) {
            return if (!parser.hasToken(CirJsonToken.VALUE_STRING)) {
                context.handleUnexpectedToken(myContainerType, parser)
            } else if (myElementClass == Byte::class) {
                deserializeFromBase64(parser, context)
            } else {
                deserializeFromString(parser, context)
            }
        }

        val typeDeserializer = myElementTypeDeserializer
        val nullProvider = myNullProvider!!
        val elementDeserializer = myElementDeserializer!!
        val token = parser.currentToken()

        val value = if (token == CirJsonToken.VALUE_NULL) {
            if (mySkipNullValues) {
                return myEmptyValue
            }

            nullProvider.getNullValue(context)
        } else {
            if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
                val textValue = parser.text!!

                if (textValue.isEmpty()) {
                    val action =
                            context.findCoercionAction(logicalType(), handledType(), CoercionInputShape.EMPTY_STRING)

                    if (action != CoercionAction.FAIL) {
                        return deserializeFromEmptyString(parser, context, action, handledType(), "empty String (\"\")")
                    }
                } else if (isBlank(textValue)) {
                    val action = context.findCoercionFromBlankString(logicalType(), handledType(), CoercionAction.FAIL)

                    if (action != CoercionAction.FAIL) {
                        return deserializeFromEmptyString(parser, context, action, handledType(),
                                "blank String (all whitespace)")
                    }
                }
            }

            if (typeDeserializer == null) {
                elementDeserializer.deserialize(parser, context)
            } else {
                elementDeserializer.deserializeWithType(parser, context, typeDeserializer)
            }
        }

        val result = if (myUntyped) {
            arrayOfNulls(1)
        } else {
            java.lang.reflect.Array.newInstance(myElementClass.java, 1) as Array<Any?>
        }

        result[0] = value
        return result
    }

}