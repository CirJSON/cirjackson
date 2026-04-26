package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern
import kotlin.reflect.KClass

/**
 * Base class for deserializers that handle types that are serialized as CirJSON scalars (non-structured, i.e.
 * non-Object, non-Array, values).
 */
abstract class StandardScalarDeserializer<T : Any> : StandardDeserializer<T> {

    protected constructor(valueClass: KClass<*>) : super(valueClass)

    protected constructor(valueType: KotlinType) : super(valueType)

    protected constructor(source: StandardScalarDeserializer<*>) : super(source)

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun logicalType(): LogicalType {
        return LogicalType.OTHER_SCALAR
    }

    /**
     * By default, assumption is that scalar types cannot be updated: many are immutable values (such as primitives and
     * wrappers)
     */
    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return false
    }

    override val nullAccessPattern: AccessPattern
        get() = AccessPattern.ALWAYS_NULL

    override val emptyAccessPattern: AccessPattern
        get() = AccessPattern.CONSTANT

    /*
     *******************************************************************************************************************
     * Default deserialization method implementations
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromScalar(parser, context)
    }

    /**
     * Overridden to simply call `deserialize` method that does not take value to update, since scalar values are
     * usually non-mergeable.
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: T): T? {
        context.handleBadMerge(this)
        return deserialize(parser, context)
    }

}