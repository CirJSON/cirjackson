package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Deserializer implementation where given Java type is first deserialized by a standard CirJackson deserializer into a
 * delegate type; and then this delegate type is converted using a configured [Converter] into desired target type.
 * Common delegate types to use are [Map] and [org.cirjson.cirjackson.databind.CirJsonNode].
 * 
 * Note that although types (delegate, target) may be related, they must not be same; trying to do this will result in
 * an exception.
 *
 * @param T Target type to convert to, from delegate type
 *
 * @see StandardNodeBasedDeserializer
 * 
 * @see Converter
 */
open class StandardConvertingDeserializer<T : Any> : StandardDeserializer<T> {

    /**
     * Converter that was used for creating [myDelegateDeserializer].
     */
    protected val myConverter: Converter<Any, T>

    /**
     * Fully resolved delegate type, with generic information if any available.
     */
    protected val myDelegateType: KotlinType?

    /**
     * Underlying serializer for type `T`.
     */
    protected val myDelegateDeserializer: ValueDeserializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    constructor(converter: Converter<*, T>) : super(Any::class) {
        myConverter = converter as Converter<Any, T>
        myDelegateType = null
        myDelegateDeserializer = null
    }

    @Suppress("UNCHECKED_CAST")
    constructor(converter: Converter<Any, T>, delegateType: KotlinType,
            delegateDeserializer: ValueDeserializer<*>?) : super(delegateType) {
        myConverter = converter
        myDelegateType = delegateType
        myDelegateDeserializer = delegateDeserializer as ValueDeserializer<Any>?
    }

    protected constructor(source: StandardConvertingDeserializer<T>) : super(source) {
        myConverter = source.myConverter
        myDelegateType = source.myDelegateType
        myDelegateDeserializer = source.myDelegateDeserializer
    }

    /**
     * Method used for creating resolved contextual instances. Must be overridden when subclassing.
     */
    protected open fun withDelegate(converter: Converter<Any, T>, delegateType: KotlinType,
            delegateDeserializer: ValueDeserializer<*>?): StandardConvertingDeserializer<T> {
        verifyMustOverride(StandardConvertingDeserializer::class, this, "withDelegate")
        return StandardConvertingDeserializer(converter, delegateType, delegateDeserializer)
    }

    override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<T> {
        verifyMustOverride(StandardConvertingDeserializer::class, this, "unwrappingDeserializer")
        return replaceDelegatee(myDelegateDeserializer!!.unwrappingDeserializer(context, unwrapper))
    }

    override fun replaceDelegatee(deserializer: ValueDeserializer<*>): ValueDeserializer<T> {
        verifyMustOverride(StandardConvertingDeserializer::class, this, "replaceDelegatee")

        if (deserializer === myDelegateDeserializer) {
            return this
        }

        return StandardConvertingDeserializer(myConverter, myDelegateType!!, deserializer)
    }

    /*
     *******************************************************************************************************************
     * Contextualization
     *******************************************************************************************************************
     */

    override fun resolve(context: DeserializationContext) {
        myDelegateDeserializer?.resolve(context)
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        myDelegateDeserializer ?: return myConverter.getInputType(context.typeFactory)
                .let { withDelegate(myConverter, it, context.findContextualValueDeserializer(it, property)) }
        val deserializer = context.handleSecondaryContextualization(myDelegateDeserializer, property, myDelegateType!!)

        if (deserializer === myDelegateDeserializer) {
            return this
        }

        return withDelegate(myConverter, myDelegateType, deserializer)
    }

    /*
     *******************************************************************************************************************
     * Deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): T? {
        val delegateValue = myDelegateDeserializer!!.deserialize(parser, context) ?: return null
        return convertValue(delegateValue)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        val delegateValue = myDelegateDeserializer!!.deserialize(parser, context) ?: return null
        return convertValue(delegateValue)
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: T): T? {
        return if (myDelegateType!!.rawClass.isAssignableFrom(intoValue::class)) {
            myDelegateDeserializer!!.deserialize(parser, context, intoValue) as T?
        } else {
            handleIncompatibleUpdateValue(parser, context, intoValue) as T?
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer, intoValue: T): Any? {
        return if (myDelegateType!!.rawClass.isAssignableFrom(intoValue::class)) {
            myDelegateDeserializer!!.deserialize(parser, context, intoValue)
        } else {
            handleIncompatibleUpdateValue(parser, context, intoValue)
        }
    }

    /**
     * Overridable handler method called when [deserialize] has been called with a value that is not compatible with
     * delegate value. Since no conversion are expected for such "updateValue" case, this is normally not an operation
     * that can be permitted, and the default behavior is to throw exception. Subclasses may choose to try alternative
     * approach if they have more information on exact usage and constraints.
     */
    protected open fun handleIncompatibleUpdateValue(parser: CirJsonParser, context: DeserializationContext,
            intoValue: Any): Any? {
        throw UnsupportedOperationException(
                "Cannot update object of type ${intoValue::class.qualifiedName} (using deserializer for type $myDelegateType)")
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun handledType(): KClass<*> {
        return myDelegateDeserializer!!.handledType()!!
    }

    override fun logicalType(): LogicalType? {
        return myDelegateDeserializer!!.logicalType()
    }

    override val isCacheable: Boolean
        get() = myDelegateDeserializer?.isCacheable ?: false

    override val delegatee: ValueDeserializer<*>?
        get() = myDelegateDeserializer

    override val knownPropertyNames: Collection<Any>?
        get() = myDelegateDeserializer!!.knownPropertyNames

    /*
     *******************************************************************************************************************
     * Null/empty/absent accessors
     *******************************************************************************************************************
     */

    override fun getNullValue(context: DeserializationContext): T? {
        return convertIfNotNull(myDelegateDeserializer!!.getNullValue(context))
    }

    override val nullAccessPattern: AccessPattern
        get() = myDelegateDeserializer!!.nullAccessPattern

    override fun getAbsentValue(context: DeserializationContext): Any? {
        return convertIfNotNull(myDelegateDeserializer!!.getAbsentValue(context))
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return convertIfNotNull(myDelegateDeserializer!!.getEmptyValue(context))
    }

    override val emptyAccessPattern: AccessPattern
        get() = myDelegateDeserializer!!.emptyAccessPattern

    /*
     *******************************************************************************************************************
     * Other accessors
     *******************************************************************************************************************
     */

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return myDelegateDeserializer!!.supportsUpdate(config)
    }

    /*
     *******************************************************************************************************************
     * Overridable methods
     *******************************************************************************************************************
     */

    /**
     * Method called to convert from "delegate value" (which was deserialized from CirJSON using standard CirJackson
     * deserializer for delegate type) into desired target type.
     *
     * The default implementation uses configured [Converter] to do conversion.
     *
     * @return Result of conversion
     */
    protected open fun convertValue(delegateValue: Any): T {
        return myConverter.convert(delegateValue)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun convertIfNotNull(delegateValue: Any?): T? {
        return delegateValue?.let { myConverter.convert(it) }
    }

}