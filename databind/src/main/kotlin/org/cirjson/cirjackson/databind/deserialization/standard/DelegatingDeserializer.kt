package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern
import org.cirjson.cirjackson.databind.util.NameTransformer

/**
 * Base class that simplifies implementations of [ValueDeserializers][ValueDeserializer] that mostly delegate
 * functionality to another deserializer implementation (possibly forming a chaining of deserializers delegating
 * functionality in some cases)
 */
abstract class DelegatingDeserializer(protected val myDelegatee: ValueDeserializer<*>) :
        StandardDeserializer<Any>(myDelegatee.handledType()!!) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected abstract fun newDelegatingInstance(newDelegatee: ValueDeserializer<*>): ValueDeserializer<*>

    /*
     *******************************************************************************************************************
     * Overridden methods for contextualization, resolving
     *******************************************************************************************************************
     */

    override fun resolve(context: DeserializationContext) {
        myDelegatee.resolve(context)
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val type = context.constructType(myDelegatee.handledType())!!
        val delegatee = context.handleSecondaryContextualization(myDelegatee, property, type)!!

        if (delegatee === myDelegatee) {
            return this
        }

        return newDelegatingInstance(delegatee)
    }

    @Suppress("UNCHECKED_CAST")
    override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<Any> {
        val unwrapping = myDelegatee.unwrappingDeserializer(context, unwrapper)

        if (unwrapping === myDelegatee) {
            return this
        }

        return newDelegatingInstance(unwrapping) as ValueDeserializer<Any>
    }

    override fun replaceDelegatee(deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        if (deserializer === myDelegatee) {
            return this
        }

        return newDelegatingInstance(deserializer)
    }

    /*
     *******************************************************************************************************************
     * Overridden deserialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        return myDelegatee.deserialize(parser, context)
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        return (myDelegatee as ValueDeserializer<Any>).deserialize(parser, context, intoValue)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return myDelegatee.deserializeWithType(parser, context, typeDeserializer)
    }

    /*
     *******************************************************************************************************************
     * Overridden other methods
     *******************************************************************************************************************
     */

    override val delegatee: ValueDeserializer<*>
        get() = myDelegatee

    override val nullAccessPattern: AccessPattern
        get() = myDelegatee.nullAccessPattern

    override fun getNullValue(context: DeserializationContext): Any? {
        return myDelegatee.getNullValue(context)
    }

    override fun getAbsentValue(context: DeserializationContext): Any? {
        return myDelegatee.getAbsentValue(context)
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return myDelegatee.getEmptyValue(context)
    }

    override val emptyAccessPattern: AccessPattern
        get() = myDelegatee.emptyAccessPattern

    override fun logicalType(): LogicalType? {
        return myDelegatee.logicalType()
    }

    override val isCacheable: Boolean
        get() = myDelegatee.isCacheable

    override val knownPropertyNames: Collection<Any>?
        get() = myDelegatee.knownPropertyNames

    override fun getObjectIdReader(context: DeserializationContext): ObjectIdReader? {
        return myDelegatee.getObjectIdReader(context)
    }

    override fun findBackReference(referenceName: String): SettableBeanProperty? {
        return myDelegatee.findBackReference(referenceName)
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return myDelegatee.supportsUpdate(config)
    }

}