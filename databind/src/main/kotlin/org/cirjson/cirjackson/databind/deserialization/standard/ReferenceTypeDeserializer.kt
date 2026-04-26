package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern

/**
 * Base deserializer implementation for properties [ReferenceType][org.cirjson.cirjackson.databind.type.ReferenceType]
 * values. Implements most functionality, only leaving a couple of abstract methods for subclasses to implement.
 *
 * @property myFullType Full type of property (or root value) for which this deserializer has been constructed and
 * contextualized.
 */
abstract class ReferenceTypeDeserializer<T : Any>(protected val myFullType: KotlinType,
        protected val myValueInstantiator: ValueInstantiator?, protected val myValueTypeDeserializer: TypeDeserializer?,
        valueDeserializer: ValueDeserializer<*>?) : StandardDeserializer<T>(myFullType) {

    @Suppress("UNCHECKED_CAST")
    protected val myValueDeserializer = valueDeserializer as ValueDeserializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val deserializer: ValueDeserializer<*> = findConvertingContentDeserializer(context, property,
                myValueDeserializer)?.let {
            context.handleSecondaryContextualization(it, property, myFullType.referencedType!!)
        } ?: context.findContextualValueDeserializer(myFullType.referencedType!!, property)
        val typeDeserializer = myValueTypeDeserializer?.forProperty(property)

        if (deserializer === myValueDeserializer && typeDeserializer == myValueTypeDeserializer) {
            return this
        }

        return withResolved(typeDeserializer, deserializer)
    }

    /*
     *******************************************************************************************************************
     * Partial NullValueProvider implementation
     *******************************************************************************************************************
     */

    /**
     * `null` value varies dynamically (unlike with scalar types), so let's indicate this.
     */
    override val nullAccessPattern: AccessPattern
        get() = AccessPattern.DYNAMIC

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to implement
     *******************************************************************************************************************
     */

    /**
     * Mutant factory method called when changes are needed; should construct newly configured instance with new values
     * as indicated.
     *
     * NOTE: caller has verified that there are changes, so implementations need NOT check if a new instance is needed.
     */
    protected abstract fun withResolved(typeDeserializer: TypeDeserializer?,
            valueDeserializer: ValueDeserializer<*>?): ReferenceTypeDeserializer<T>

    abstract override fun getNullValue(context: DeserializationContext): Any?

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return getNullValue(context)
    }

    abstract fun referenceValue(contents: Any?): T?

    /**
     * Method called in case of "merging update", in which we should try update reference instead of creating a new one.
     * If this does not succeed, should just create a new instance.
     */
    abstract fun updateReference(reference: T, contents: Any?): T

    /**
     * Method that may be called to find contents of specified reference, if any; or `null` if none. Note that method
     * should never fail, so for types that use concept of "absence" vs "presence", `null` is to be returned for both
     * "absent" and "reference to `null`" cases.
     */
    abstract fun getReferenced(reference: T): Any?

    /*
     *******************************************************************************************************************
     * Overridden accessors
     *******************************************************************************************************************
     */

    override val valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator

    override val valueType: KotlinType
        get() = myFullType

    override fun logicalType(): LogicalType? {
        return myValueDeserializer?.logicalType()
    }

    /**
     * By default, we assume that updateability mostly relies on value deserializer; if it supports updates, typically
     * that's what matters. So let's just delegate.
     */
    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return myValueDeserializer?.supportsUpdate(config)
    }

    /*
     *******************************************************************************************************************
     * Deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): T? {
        if (myValueInstantiator != null) {
            val value = myValueInstantiator.createUsingDefault(context) as T
            return deserialize(parser, context, value)
        }

        val contents = if (myValueTypeDeserializer == null) {
            myValueDeserializer!!.deserialize(parser, context)
        } else {
            myValueDeserializer!!.deserializeWithType(parser, context, myValueTypeDeserializer)
        }

        return referenceValue(contents)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: T): T? {
        val supportsUpdate = myValueDeserializer!!.supportsUpdate(context.config)

        val contents = if (supportsUpdate == false || myValueTypeDeserializer != null) {
            if (myValueTypeDeserializer == null) {
                myValueDeserializer.deserialize(parser, context)
            } else {
                myValueDeserializer.deserializeWithType(parser, context, myValueTypeDeserializer)
            }
        } else {
            getReferenced(intoValue).let {
                if (it == null) {
                    val contents = myValueDeserializer.deserialize(parser, context)
                    return referenceValue(contents)
                }

                myValueDeserializer.deserialize(parser, context, it)
            }
        }

        return updateReference(intoValue, contents)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        val token = parser.currentToken()

        if (token == CirJsonToken.VALUE_NULL) {
            return getNullValue(context)
        }

        return if (myValueTypeDeserializer == null) {
            deserialize(parser, context)
        } else {
            referenceValue(myValueTypeDeserializer.deserializeTypedFromAny(parser, context))
        }
    }

}