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
import org.cirjson.cirjackson.databind.deserialization.ReadableObjectId
import org.cirjson.cirjackson.databind.deserialization.UnresolvedForwardReferenceException
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.throwIfRuntimeException
import org.cirjson.cirjackson.databind.util.typeDescription
import kotlin.reflect.KClass

/**
 * Basic serializer that can take JSON "Array" structure and construct a [Collection] instance, with typed contents.
 * 
 * Note: for untyped content (one indicated by passing `Any::class` as the type), [UntypedObjectDeserializer] is used
 * instead. It can also construct [Lists][List], but not with specific POJO types, only other containers and
 * primitives/wrappers.
 */
@CirJacksonStandardImplementation
open class CollectionDeserializer : ContainerDeserializerBase<MutableCollection<Any?>> {

    /**
     * Value deserializer.
     */
    protected val myValueDeserializer: ValueDeserializer<Any>?

    /**
     * If element instances have polymorphic type information, this is the type deserializer that can handle it
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    protected val myValueInstantiator: ValueInstantiator?

    /**
     * Deserializer that is used iff delegate-based creator is to be used for deserializing from CirJSON Object.
     */
    protected val myDelegateDeserializer: ValueDeserializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructor for context-free instances, where we do not yet know which property is using this deserializer.
     */
    constructor(collectionType: KotlinType, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, valueInstantiator: ValueInstantiator?) : this(collectionType,
            valueDeserializer, valueTypeDeserializer, valueInstantiator, null, null, null)

    /**
     * Constructor used when creating contextualized instances.
     */
    protected constructor(collectionType: KotlinType, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, valueInstantiator: ValueInstantiator?,
            delegateDeserializer: ValueDeserializer<Any>?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(collectionType, nullValueProvider, unwrapSingle) {
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = valueInstantiator
        myDelegateDeserializer = delegateDeserializer
    }

    /**
     * Copy-constructor that can be used by subclasses to allow copy-on-write styling copying of settings of an existing
     * instance.
     */
    protected constructor(source: CollectionDeserializer) : super(source) {
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = source.myValueTypeDeserializer
        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
    }

    /**
     * Fluent-factory method call to construct contextual instance.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun withResolved(delegateDeserializer: ValueDeserializer<*>?,
            valueDeserializer: ValueDeserializer<*>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?): CollectionDeserializer {
        return CollectionDeserializer(myContainerType, valueDeserializer as ValueDeserializer<Any>?,
                valueTypeDeserializer, myValueInstantiator, delegateDeserializer as ValueDeserializer<Any>?,
                nullValueProvider, unwrapSingle)
    }

    override val isCacheable: Boolean
        get() = myValueDeserializer == null && myValueTypeDeserializer == null && myDelegateDeserializer == null

    override fun logicalType(): LogicalType? {
        return LogicalType.COLLECTION
    }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    /**
     * Method called to finalize setup of this deserializer, when it is known for which property deserializer is needed
     * for.
     */
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): CollectionDeserializer {
        val delegateDeserializer = if (myValueInstantiator != null) {
            if (myValueInstantiator.canCreateUsingDelegate()) {
                val delegateType =
                        myValueInstantiator.getDelegateType(context.config) ?: return context.reportBadDefinition(
                                myContainerType,
                                "Invalid delegate-creator definition for $myContainerType: value instantiator (${myValueInstantiator::class.qualifiedName}) returned true for 'canCreateUsingDelegate()', but null for 'getDelegateType()'")
                findDeserializer(context, delegateType, property)
            } else if (myValueInstantiator.canCreateUsingArrayDelegate()) {
                val delegateType =
                        myValueInstantiator.getArrayDelegateType(context.config) ?: return context.reportBadDefinition(
                                myContainerType,
                                "Invalid delegate-creator definition for $myContainerType: value instantiator (${myValueInstantiator::class.qualifiedName}) returned true for 'canCreateUsingArrayDelegate()', but null for 'getArrayDelegateType()'")
                findDeserializer(context, delegateType, property)
            } else {
                null
            }
        } else {
            null
        }

        val unwrapSingle = findFormatFeature(context, property, Collection::class,
                CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        var valueDeserializer = findConvertingContentDeserializer(context, property, myValueDeserializer)
        val valueType = myContainerType.contentType!!

        valueDeserializer = valueDeserializer?.let { context.handleSecondaryContextualization(it, property, valueType) }
                ?: context.findContextualValueDeserializer(valueType, property)
        val valueTypeDeserializer = myValueTypeDeserializer?.forProperty(property)
        val nullValueProvider = findContentNullProvider(context, property, valueDeserializer)

        if (unwrapSingle == myUnwrapSingle && nullValueProvider === myNullProvider && delegateDeserializer === myDelegateDeserializer && valueDeserializer === myValueDeserializer && valueTypeDeserializer === myValueTypeDeserializer) {
            return this
        }

        return withResolved(delegateDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * ContainerDeserializerBase implementation
     *******************************************************************************************************************
     */

    override val contentDeserializer: ValueDeserializer<Any>?
        get() = myValueDeserializer

    override val valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): MutableCollection<Any?>? {
        return if (myDelegateDeserializer != null) {
            myValueInstantiator!!.createUsingDelegate(context,
                    myDelegateDeserializer.deserialize(parser, context)) as MutableCollection<Any?>?
        } else if (parser.isExpectedStartArrayToken) {
            deserializeFromArray(parser, context, createDefaultInstance(context)!!)
        } else if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
            deserializeFromString(parser, context, parser.text!!)
        } else {
            handleNonArray(parser, context, createDefaultInstance(context)!!)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun createDefaultInstance(context: DeserializationContext): MutableCollection<Any?>? {
        return myValueInstantiator!!.createUsingDefault(context)!! as MutableCollection<Any?>
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: MutableCollection<Any?>): MutableCollection<Any?>? {
        return if (parser.isExpectedStartArrayToken) {
            deserializeFromArray(parser, context, intoValue)
        } else {
            handleNonArray(parser, context, intoValue)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromAny(parser, context)
    }

    /**
     * Logic extracted to deal with incoming String value.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun deserializeFromString(parser: CirJsonParser, context: DeserializationContext,
            value: String): MutableCollection<Any?>? {
        val rawTargetType = handledType()

        if (value.isEmpty()) {
            val action = context.findCoercionAction(logicalType(), rawTargetType, CoercionInputShape.EMPTY_STRING)

            if (action != CoercionAction.FAIL) {
                return deserializeFromEmptyString(parser, context, action, rawTargetType,
                        "empty String (\"\")") as MutableCollection<Any?>?
            }
        } else if (isBlank(value)) {
            val action = context.findCoercionFromBlankString(logicalType(), rawTargetType, CoercionAction.FAIL)

            if (action != CoercionAction.FAIL) {
                return deserializeFromEmptyString(parser, context, action, rawTargetType,
                        "blank String (all whitespace)") as MutableCollection<Any?>?
            }
        }

        return handleNonArray(parser, context, createDefaultInstance(context)!!)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromArray(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<Any?>?): MutableCollection<Any?>? {
        result!!
        parser.assignCurrentValue(result)

        val valueDeserializer = myValueDeserializer!!

        if (valueDeserializer.getObjectIdReader(context) != null) {
            return deserializeWithObjectId(parser, context, result)
        }

        val typeDeserializer = myValueTypeDeserializer
        var token: CirJsonToken? = null

        while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
            try {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        continue
                    }

                    myNullProvider!!.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (value == null) {
                    tryToAddNull(parser, context, result)
                    continue
                }

                result.add(value)
            } catch (e: Exception) {
                val wrap = context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)
                val throwable = e.takeIf { wrap } ?: e.throwIfRuntimeException()
                throw CirJacksonException.wrapWithPath(throwable, result, result.size)
            }
        }

        return result
    }

    /**
     * Helper method called when current token is no [CirJsonToken.START_ARRAY]. Will either throw an exception, or try
     * to handle value as if member of implicit array, depending on configuration.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun handleNonArray(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<Any?>): MutableCollection<Any?>? {
        val canWrap = myUnwrapSingle ?: context.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

        if (!canWrap) {
            return context.handleUnexpectedToken(myContainerType, parser) as MutableCollection<Any?>?
        }

        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer
        val token = parser.currentToken()

        val value = try {
            when {
                token == CirJsonToken.VALUE_NULL -> {
                    if (mySkipNullValues) {
                        return result
                    }

                    myNullProvider!!.getNullValue(context)
                }

                typeDeserializer == null -> {
                    valueDeserializer.deserialize(parser, context)
                }

                else -> {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }
            }.also {
                if (it == null) {
                    tryToAddNull(parser, context, result)
                    return result
                }
            }
        } catch (e: Exception) {
            val wrap = context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)
            val throwable = e.takeIf { wrap } ?: e.throwIfRuntimeException()
            throw CirJacksonException.wrapWithPath(throwable, Any::class, result.size)
        }

        result.add(value)
        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithObjectId(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<Any?>): MutableCollection<Any?>? {
        if (!parser.isExpectedStartArrayToken) {
            return handleNonArray(parser, context, result)
        }

        parser.assignCurrentValue(result)

        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer
        val referringAccumulator = CollectionReferringAccumulator(myContainerType.contentType!!.rawClass, result)
        var token: CirJsonToken? = null

        while (parser.nextToken().also { token = it } != CirJsonToken.END_ARRAY) {
            try {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        continue
                    }

                    myNullProvider!!.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (value == null && mySkipNullValues) {
                    continue
                }

                referringAccumulator.add(value)
            } catch (reference: UnresolvedForwardReferenceException) {
                val referring = referringAccumulator.handleUnresolvedReference(reference)
                reference.readableObjectId!!.appendReferring(referring)
            } catch (e: Exception) {
                val wrap = context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)
                val throwable = e.takeIf { wrap } ?: e.throwIfRuntimeException()
                throw CirJacksonException.wrapWithPath(throwable, result, result.size)
            }
        }

        return result
    }

    /**
     * [java.util.TreeSet] (and possibly other [Collection] types) does not allow addition of `null` values, so isolate
     * handling here.
     *
     */
    protected open fun tryToAddNull(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<Any?>) {
        if (mySkipNullValues) {
            return
        }

        try {
            result.add(null)
        } catch (_: NullPointerException) {
            context.handleUnexpectedToken(myValueType!!, CirJsonToken.VALUE_NULL, parser,
                    "`MutableCollection` of type ${
                        getValueType(context).typeDescription
                    } does not accept `null` values")
        }
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Helper class for dealing with Object ID references for values contained in collections being deserialized.
     */
    class CollectionReferringAccumulator(private val myElementType: KClass<*>,
            private val myResult: MutableCollection<Any?>) {

        /**
         * A list of [CollectionReferring] to maintain ordering.
         */
        private val myAccumulator = ArrayList<CollectionReferring>()

        fun add(value: Any?) {
            if (myAccumulator.isEmpty()) {
                myResult.add(value)
            } else {
                val referring = myAccumulator[myAccumulator.lastIndex]
                referring.next.add(value)
            }
        }

        fun handleUnresolvedReference(reference: UnresolvedForwardReferenceException): ReadableObjectId.Referring {
            return CollectionReferring(this, reference, myElementType).also { myAccumulator.add(it) }
        }

        fun resolveForwardReference(id: Any, value: Any?) {
            val iterator = myAccumulator.iterator()
            var previous = myResult

            while (iterator.hasNext()) {
                val referring = iterator.next()

                if (referring.hasId(id)) {
                    iterator.remove()
                    previous.add(value)
                    previous.addAll(referring.next)
                }

                previous = referring.next
            }

            throw IllegalArgumentException(
                    "Trying to resolve a forward reference with id [$id] that wasn't previously seen as unresolved.")
        }
    }

    /**
     * Helper class to maintain processing order of value. The resolved object associated with `id` parameter from
     * [handleResolvedForwardReference] comes before the values in [next].
     */
    private class CollectionReferring(private val myParent: CollectionReferringAccumulator,
            reference: UnresolvedForwardReferenceException, contentType: KClass<*>) :
            ReadableObjectId.Referring(reference, contentType) {

        val next = ArrayList<Any?>()

        @Throws(CirJacksonException::class)
        override fun handleResolvedForwardReference(id: Any, value: Any?) {
            myParent.resolveForwardReference(id, value)
        }

    }

}