package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType

/**
 * Simple deserializer that will call configured type deserializer, passing in configured data deserializer, and
 * exposing it all as a simple deserializer. This is necessary when there is no "parent" deserializer which could handle
 * details of calling a [TypeDeserializer], most commonly used with root values.
 */
class TypeWrappedDeserializer(private val myTypeDeserializer: TypeDeserializer, deserializer: ValueDeserializer<*>) :
        ValueDeserializer<Any>() {

    @Suppress("UNCHECKED_CAST")
    private val myDeserializer = deserializer as ValueDeserializer<Any>

    override fun logicalType(): LogicalType? {
        return myDeserializer.logicalType()
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return myDeserializer.supportsUpdate(config)
    }

    override val delegatee: ValueDeserializer<*>?
        get() = myDeserializer.delegatee

    override val knownPropertyNames: Collection<Any>?
        get() = myDeserializer.knownPropertyNames

    override fun getNullValue(context: DeserializationContext): Any? {
        return myDeserializer.getNullValue(context)
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return myDeserializer.getEmptyValue(context)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        return myDeserializer.deserializeWithType(parser, context, myTypeDeserializer)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        throw IllegalStateException("Type-wrapped deserializer's deserializeWithType should never get called")
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        return myDeserializer.deserialize(parser, context, intoValue)
    }

}