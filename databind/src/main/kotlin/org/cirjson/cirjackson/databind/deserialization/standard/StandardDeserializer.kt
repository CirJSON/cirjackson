package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.util.isCirJacksonStandardImplementation
import kotlin.reflect.KClass

/**
 * Base class for common deserializers. Contains shared base functionality for dealing with primitive values, such as (re)parsing from String.
 */
abstract class StandardDeserializer<T : Any> : ValueDeserializer<T>, ValueInstantiator.Gettable {

    /**
     * Type of values this deserializer handles: sometimes exact types, other times most specific supertype of types
     * deserializer handles (which may be as generic as [Any] in some case)
     */
    protected val myValueClass: KClass<*>

    protected val myValueType: KotlinType?

    protected constructor(valueClass: KClass<*>) {
        myValueClass = valueClass
        myValueType = null
    }

    protected constructor(valueType: KotlinType) {
        myValueType = valueType
        myValueClass = valueType.rawClass
    }

    /**
     * Copy-constructor for subclasses to use, most often when creating new instances via [createContextual].
     */
    protected constructor(source: StandardDeserializer<*>) {
        myValueClass = source.myValueClass
        myValueType = source.myValueType
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun handledType(): KClass<*> {
        return myValueClass
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Exact structured type this deserializer handles, if known.
     */
    open val valueType: KotlinType?
        get() = myValueType

    /**
     * Convenience method for getting handled type as [KotlinType], regardless of whether deserializer has one already
     * resolved (and accessible via [valueType]) or not: equivalent to:
     * ```
     *   myValueType ?: context.constructType(myValueClass)!!
     * ```
     */
    open fun getValueType(context: DeserializationContext): KotlinType {
        return myValueType ?: context.constructType(myValueClass)!!
    }

    override val valueInstantiator: ValueInstantiator?
        get() = null

    /**
     * Method that can be called to determine if given deserializer is the default deserializer CirJackson uses; as
     * opposed to a custom deserializer installed by a module or calling application. Determination is done using
     * [CirJacksonStandardImplementation][org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation]
     * annotation on deserializer class.
     */
    protected open fun isDefaultSerializer(deserializer: ValueDeserializer<*>): Boolean {
        return deserializer.isCirJacksonStandardImplementation
    }

    /**
     * Method that can be called to determine if given deserializer is the default key deserializer CirJackson uses; as
     * opposed to a custom deserializer installed by a module or calling application. Determination is done using
     * [CirJacksonStandardImplementation][org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation]
     * annotation on deserializer class.
     */
    protected open fun isDefaultKeySerializer(keyDeserializer: KeyDeserializer): Boolean {
        return keyDeserializer.isCirJacksonStandardImplementation
    }

    /*
     *******************************************************************************************************************
     * Partial deserialize method implementation
     *******************************************************************************************************************
     */

    /**
     * Base implementation that does not assume specific type inclusion mechanism. Subclasses are expected to override
     * this method if they are to handle type information.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromAny(parser, context)
    }

    companion object {

        /**
         * Bitmask that covers [DeserializationFeature.USE_BIG_INTEGER_FOR_INTS] and
         * [DeserializationFeature.USE_LONG_FOR_INTS], used for more efficient cheks when coercing integral values for
         * untyped deserialization.
         */
        val FEATURE_MASK_INT_COERCIONS =
                DeserializationFeature.USE_BIG_INTEGER_FOR_INTS.mask or DeserializationFeature.USE_LONG_FOR_INTS.mask

    }

}