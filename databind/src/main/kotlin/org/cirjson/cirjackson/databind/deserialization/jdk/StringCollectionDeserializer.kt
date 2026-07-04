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
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.type.LogicalType

/**
 * Specifically optimized version for [MutableCollections][MutableCollection] that contain String values; reason is that
 * this is a very common type, and we can make use of the fact that Strings are final.
 */
@CirJacksonStandardImplementation
class StringCollectionDeserializer : ContainerDeserializerBase<MutableCollection<String?>> {

    /**
     * Value deserializer to use, if NOT the standard one (if it is, will be `null`).
     */
    private val myValueDeserializer: ValueDeserializer<String>?

    /**
     * Instantiator used in case custom handling is needed for creation.
     */
    private val myValueInstantiator: ValueInstantiator?

    /**
     * Deserializer that is used iff delegate-based creator is to be used for deserializing from CirJSON Object.
     */
    private val myDelegateDeserializer: ValueDeserializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(collectionType: KotlinType, valueDeserializer: ValueDeserializer<*>?,
            valueInstantiator: ValueInstantiator?) : this(collectionType, valueInstantiator, null, valueDeserializer,
            valueDeserializer, null)

    @Suppress("UNCHECKED_CAST")
    private constructor(collectionType: KotlinType, valueInstantiator: ValueInstantiator?,
            delegateDeserializer: ValueDeserializer<*>?, valueDeserializer: ValueDeserializer<*>?,
            nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?) : super(collectionType, nullValueProvider,
            unwrapSingle) {
        myValueDeserializer = valueDeserializer as ValueDeserializer<String>?
        myValueInstantiator = valueInstantiator
        myDelegateDeserializer = delegateDeserializer as ValueDeserializer<Any>?
    }

    private fun withResolved(delegateDeserializer: ValueDeserializer<*>?, valueDeserializer: ValueDeserializer<*>?,
            nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?): StringCollectionDeserializer {
        if (myDelegateDeserializer === delegateDeserializer && myValueDeserializer == valueDeserializer &&
                myNullProvider === nullValueProvider && myUnwrapSingle == unwrapSingle) {
            return this
        }

        return StringCollectionDeserializer(myContainerType, myValueInstantiator, delegateDeserializer,
                valueDeserializer, nullValueProvider, unwrapSingle)
    }

    override val isCacheable: Boolean
        get() = myValueDeserializer == null && myDelegateDeserializer == null

    override fun logicalType(): LogicalType {
        return LogicalType.COLLECTION
    }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val delegateDeserializer = myValueInstantiator?.let {
            if (it.arrayDelegateCreator != null) {
                val delegateType = it.getArrayDelegateType(context.config)!!
                findDeserializer(context, delegateType, property)
            } else if (it.delegateCreator != null) {
                val delegateType = it.getDelegateType(context.config)!!
                findDeserializer(context, delegateType, property)
            } else {
                null
            }
        }

        val valueType = myContainerType.contentType!!
        val valueDeserializer =
                myValueDeserializer?.let { context.handleSecondaryContextualization(it, property, valueType) }
                        ?: findConvertingContentDeserializer(context, property, null)
                        ?: context.findContextualValueDeserializer(valueType, property)
        val unwrapSingle = findFormatFeature(context, property, Array<String?>::class,
                CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        val nullValueProvider = findContentNullProvider(context, property, valueDeserializer)
        return withResolved(delegateDeserializer, valueDeserializer.takeUnless { isDefaultDeserializer(it) },
                nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override val contentDeserializer: ValueDeserializer<Any>?
        get() = myValueDeserializer as? ValueDeserializer<Any>?

    override val valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): MutableCollection<String?>? {
        return if (myDelegateDeserializer != null) {
            myValueInstantiator!!.createUsingDelegate(context,
                    myDelegateDeserializer.deserialize(parser, context)) as MutableCollection<String?>?
        } else {
            deserialize(parser, context,
                    myValueInstantiator!!.createUsingDefault(context)!! as MutableCollection<String?>)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: MutableCollection<String?>): MutableCollection<String?>? {
        if (!parser.isExpectedStartArrayToken) {
            return handleNonArray(parser, context, intoValue)
        } else if (myValueDeserializer != null) {
            return deserializeCustom(parser, context, intoValue)
        }

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

                intoValue.add(value)
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, intoValue, intoValue.size)
        }

        return intoValue
    }

    @Throws(CirJacksonException::class)
    private fun deserializeCustom(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<String?>): MutableCollection<String?> {
        val nullValueProvider = myNullProvider!!
        val deserializer = myValueDeserializer!!

        try {
            while (true) {
                val value = if (parser.nextTextValue() != null) {
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

                result.add(value)
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, result, result.size)
        }

        return result
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromArray(parser, context)
    }

    /**
     * Helper method called when current token is not [CirJsonToken.START_ARRAY]. Will either throw an exception, or try
     * to handle value as if member of implicit array, depending on configuration.
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleNonArray(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<String?>): MutableCollection<String?>? {
        val canWrap = myUnwrapSingle ?: context.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

        if (!canWrap) {
            if (!parser.hasToken(CirJsonToken.VALUE_STRING)) {
                return context.handleUnexpectedToken(myContainerType, parser) as MutableCollection<String?>?
            }

            return deserializeFromString(parser, context)
        }

        val valueDeserializer = myValueDeserializer
        val nullValueProvider = myNullProvider!!
        val token = parser.currentToken()

        val value = if (token == CirJsonToken.VALUE_NULL) {
            if (mySkipNullValues) {
                return result
            }

            nullValueProvider.getNullValue(context) as String?
        } else {
            if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
                val textValue = parser.text!!

                if (textValue.isEmpty()) {
                    val action =
                            context.findCoercionAction(logicalType(), handledType(), CoercionInputShape.EMPTY_STRING)

                    if (action != CoercionAction.FAIL) {
                        return deserializeFromEmptyString(parser, context, action, handledType(),
                                "empty String (\"\")") as MutableCollection<String?>?
                    }
                } else if (isBlank(textValue)) {
                    val action = context.findCoercionFromBlankString(logicalType(), handledType(), CoercionAction.FAIL)

                    if (action != CoercionAction.FAIL) {
                        return deserializeFromEmptyString(parser, context, action, handledType(),
                                "blank String (all whitespace)") as MutableCollection<String?>?
                    }
                }
            }

            if (valueDeserializer == null) {
                parseString(parser, context, nullValueProvider)
            } else {
                valueDeserializer.deserialize(parser, context)
            }
        }

        result.add(value)
        return result
    }

}