package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.implementation.NullsConstantProvider
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import java.util.*

/**
 * Standard deserializer for [EnumSets][EnumSet].
 *
 * Note: casting within this class is all messed up -- just could not figure out a way to properly deal with recursive
 * definition of `EnumSet<K : Enum<K>, V>`;
 */
open class EnumSetDeserializer : StandardDeserializer<EnumSet<*>> {

    protected val myEnymType: KotlinType

    protected val myEnumDeserializer: ValueDeserializer<Enum<*>>?

    /**
     * If element instances have polymorphic type information, this is the type deserializer that can handle it.
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    /**
     * Handler we need for dealing with `null`.
     */
    protected val myNullProvider: NullValueProvider?

    /**
     * Marker flag set if the [myNullProvider] indicates that all null content values should be skipped (instead of
     * being possibly converted).
     */
    protected val mySkipNullValues: Boolean

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

    @Suppress("UNCHECKED_CAST")
    constructor(enymType: KotlinType, deserializer: ValueDeserializer<*>?,
            valueTypeDeserializer: TypeDeserializer?) : super(EnumSet::class) {
        if (!enymType.isEnumType) {
            throw IllegalArgumentException("Type $enymType not Enum type")
        }

        myEnymType = enymType
        myEnumDeserializer = deserializer as ValueDeserializer<Enum<*>>?
        myValueTypeDeserializer = valueTypeDeserializer
        myNullProvider = null
        mySkipNullValues = false
        myUnwrapSingle = null
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: EnumSetDeserializer, deserializer: ValueDeserializer<*>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(source) {
        myEnymType = source.myEnymType
        myEnumDeserializer = deserializer as ValueDeserializer<Enum<*>>?
        myValueTypeDeserializer = valueTypeDeserializer
        myNullProvider = nullValueProvider
        mySkipNullValues = NullsConstantProvider.isSkipper(nullValueProvider)
        myUnwrapSingle = unwrapSingle
    }

    open fun withDeserializer(deserializer: ValueDeserializer<*>?): EnumSetDeserializer {
        if (myEnumDeserializer === deserializer) {
            return this
        }

        return EnumSetDeserializer(this, deserializer, myValueTypeDeserializer, myNullProvider, myUnwrapSingle)
    }

    open fun withResolved(deserializer: ValueDeserializer<*>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?, unwrapSingle: Boolean?): EnumSetDeserializer {
        if (myUnwrapSingle == unwrapSingle && myEnumDeserializer === deserializer &&
                myValueTypeDeserializer === valueTypeDeserializer && myNullProvider === nullValueProvider) {
            return this
        }

        return EnumSetDeserializer(this, deserializer, valueTypeDeserializer, nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * Basic metadata
     *******************************************************************************************************************
     */

    override val isCacheable: Boolean
        get() = myEnymType.valueHandler == null && myValueTypeDeserializer == null

    override fun logicalType(): LogicalType {
        return LogicalType.COLLECTION
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return true
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return constructSet()
    }

    /*
     *******************************************************************************************************************
     * Contextualization
     *******************************************************************************************************************
     */

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val deserializer: ValueDeserializer<*> =
                myEnumDeserializer?.let { context.handleSecondaryContextualization(it, property, myEnymType) }
                        ?: context.findContextualValueDeserializer(myEnymType, property)
        val valueTypeDeserializer = myValueTypeDeserializer?.forProperty(property)
        val nullValueProvider = findContentNullProvider(context, property, deserializer)
        val unwrapSingle =
                findFormatFeature(context, property, EnumSet::class, CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        return withResolved(deserializer, valueTypeDeserializer, nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): EnumSet<*>? {
        val result = constructSet()

        return if (!parser.isExpectedStartArrayToken) {
            handleNonArray(parser, context, result)
        } else {
            actualDeserialization(parser, context, result)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: EnumSet<*>): EnumSet<*>? {
        return if (!parser.isExpectedStartArrayToken) {
            handleNonArray(parser, context, intoValue)
        } else {
            actualDeserialization(parser, context, intoValue)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected fun actualDeserialization(parser: CirJsonParser, context: DeserializationContext,
            result: EnumSet<*>): EnumSet<*> {
        val typeDeserializer = myValueTypeDeserializer
        val nullValueProvider = myNullProvider!!
        val deserializer = myEnumDeserializer!!

        var token = parser.nextToken()

        try {
            while (token != CirJsonToken.END_ARRAY) {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        token = parser.nextToken()
                        continue
                    }

                    nullValueProvider.getNullValue(context) as Enum<*>?
                } else if (typeDeserializer == null) {
                    deserializer.deserialize(parser, context)
                } else {
                    deserializer.deserializeWithType(parser, context, typeDeserializer) as Enum<*>?
                }

                if (value != null) {
                    (result as EnumSet<DeserializationFeature>).add(value as DeserializationFeature)
                }

                token = parser.nextToken()
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

    @Suppress("UNCHECKED_CAST")
    private fun constructSet(): EnumSet<*> {
        return EnumSet.noneOf(myEnymType.rawClass.java as Class<DeserializationFeature>)
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun handleNonArray(parser: CirJsonParser, context: DeserializationContext,
            result: EnumSet<*>): EnumSet<*>? {
        val canWrap = myUnwrapSingle ?: context.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

        if (!canWrap) {
            return context.handleUnexpectedToken(getValueType(context), parser) as EnumSet<*>?
        } else if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            return context.handleUnexpectedToken(getValueType(context), parser) as EnumSet<*>?
        }

        val deserializer = myEnumDeserializer!!

        try {
            val value = deserializer.deserialize(parser, context)

            if (value != null) {
                (result as EnumSet<DeserializationFeature>).add(value as DeserializationFeature)
            }
        } catch (e: Exception) {
            throw CirJacksonException.wrapWithPath(e, result, result.size)
        }

        return result
    }

}