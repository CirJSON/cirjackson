package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import kotlin.reflect.KClass

/**
 * Convenience deserializer that may be used to deserialize values given an intermediate tree representation
 * ([CirJsonNode]). Note that this is a slightly simplified alternative to [StandardConvertingDeserializer]).
 *
 * @param T Target type of this deserializer; that is, type of values that input data is deserialized into.
 */
abstract class StandardNodeBasedDeserializer<T : Any> : StandardDeserializer<T> {

    protected var myTreeDeserializer: ValueDeserializer<Any>? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(targetType: KotlinType) : super(targetType)

    protected constructor(targetType: KClass<T>) : super(targetType)

    /**
     * "Copy-constructor" used when creating a modified copies, most often if subclass overrides [createContextual].
     */
    protected constructor(source: StandardNodeBasedDeserializer<*>) : super(source) {
        myTreeDeserializer = source.myTreeDeserializer
    }

    override fun resolve(context: DeserializationContext) {
        myTreeDeserializer = context.findRootValueDeserializer(context.constructType(CirJsonNode::class)!!)
    }

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    abstract fun convert(root: CirJsonNode?, context: DeserializationContext): T?

    /**
     * Facilitates usage with
     * [ObjectMapper.readerForUpdating][org.cirjson.cirjackson.databind.ObjectMapper.readerForUpdating] and
     * [deserialize] by eliminating the need to manually convert the value to a [CirJsonNode].
     *
     * If this method is not overridden, it falls back to the behavior of [convert].
     */
    @Throws(CirJacksonException::class)
    open fun convert(root: CirJsonNode?, context: DeserializationContext, newValue: T): T? {
        context.handleBadMerge(this)
        return convert(root, context)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): T? {
        val node = myTreeDeserializer!!.deserialize(parser, context) as CirJsonNode?
        return convert(node, context)
    }

    /**
     * Added to support [convert] the uses `newValue`
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: T): T? {
        val node = myTreeDeserializer!!.deserialize(parser, context) as CirJsonNode?
        return convert(node, context, intoValue)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        val node = myTreeDeserializer!!.deserializeWithType(parser, context, typeDeserializer) as CirJsonNode?
        return convert(node, context)
    }

}