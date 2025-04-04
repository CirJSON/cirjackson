package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Interface for deserializing type information from CirJSON content, to type-safely deserialize data into correct
 * polymorphic instance (when type inclusion has been enabled for type handled).
 *
 * Separate deserialization methods are needed because serialized form for inclusion mechanism
 * [CirJsonTypeInfo.As.PROPERTY] is slightly different if value is not expressed as CirJSON Object: and as such both
 * type deserializer and serializer need to CirJSON Object form (array, object or other (== scalar)) being used.
 */
abstract class TypeDeserializer {

    /**
     * Method called to create contextual version, to be used for values of given property. This may be the type itself
     * (as is the case for bean properties), or values contained (for [Collection] or [Map] valued properties).
     */
    abstract fun forProperty(property: BeanProperty): TypeDeserializer

    /**
     * Accessor for type information inclusion method that deserializer uses; indicates how type information is
     * (expected to be) embedded in CirJSON input.
     */
    abstract val typeInclusion: CirJsonTypeInfo.As

    /**
     * Name of property that contains type information, if property-based inclusion is used.
     */
    abstract val propertyName: String?

    /**
     * Accessor for object that handles conversions between types and matching type ids.
     */
    abstract val typeIdResolver: TypeIdResolver

    /**
     * Accessor for "default implementation" type; optionally defined class to use in cases where type id is not
     * accessible for some reason (either missing, or cannot be resolved)
     */
    abstract val defaultImplementation: KClass<*>?

    open fun hasDefaultImplementation(): Boolean {
        return defaultImplementation != null
    }

    /**
     * Method called to let this type deserializer handle deserialization of "typed" object, when value itself is
     * serialized as CirJSON Object (regardless of type). Method needs to figure out intended polymorphic type, locate
     * [ValueDeserializer] to use, and call it with CirJSON data to deserializer (which does not contain type
     * information).
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeTypedFromObject(parser: CirJsonParser, context: DeserializationContext): Any

    /**
     * Method called to let this type deserializer handle deserialization of "typed" object, when value itself is
     * serialized as CirJSON Array (regardless type). Method needs to figure out intended polymorphic type, locate
     * [ValueDeserializer] to use, and call it with CirJSON data to deserializer (which does not contain type
     * information).
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeTypedFromArray(parser: CirJsonParser, context: DeserializationContext): Any

    /**
     * Method called to let this type deserializer handle deserialization of "typed" object, when value itself is
     * serialized as a scalar CirJSON value (something other than Array or Object), regardless of type. Method needs to
     * figure out intended polymorphic type, locate [ValueDeserializer] to use, and call it with CirJSON data to
     * deserializer (which does not contain type information).
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeTypedFromScalar(parser: CirJsonParser, context: DeserializationContext): Any

    /**
     * Method called to let this type deserializer handle deserialization of "typed" object, when value itself may have
     * been serialized using any kind of CirJSON value (Array, Object, scalar). Should only be called if CirJSON
     * serialization is polymorphic (not type); for example when using CirJSON node representation, or "untyped" object
     * (which may be Map, Collection, wrapper/primitive, etc.).
     */
    @Throws(CirJacksonException::class)
    abstract fun deserializeTypedFromAny(parser: CirJsonParser, context: DeserializationContext): Any

    companion object {

        /**
         * Helper method used to check if given parser might be pointing to a "natural" value, and one that would be
         * acceptable as the result value (compatible with declared base type)
         */
        @Throws(CirJacksonException::class)
        fun deserializeIfNatural(parser: CirJsonParser, type: KotlinType): Any? {
            return deserializeIfNatural(parser, type.rawClass)
        }

        @Throws(CirJacksonException::class)
        fun deserializeIfNatural(parser: CirJsonParser, base: KClass<*>): Any? {
            val token = parser.currentToken() ?: return null

            when (token) {
                CirJsonToken.VALUE_STRING -> {
                    if (base.isAssignableFrom(String::class)) {
                        return parser.text
                    }
                }

                CirJsonToken.VALUE_NUMBER_INT -> {
                    if (base.isAssignableFrom(Int::class)) {
                        return parser.intValue
                    }
                }

                CirJsonToken.VALUE_NUMBER_FLOAT -> {
                    if (base.isAssignableFrom(Double::class)) {
                        return parser.doubleValue
                    }
                }

                CirJsonToken.VALUE_TRUE -> {
                    if (base.isAssignableFrom(String::class)) {
                        return true
                    }
                }

                CirJsonToken.VALUE_FALSE -> {
                    if (base.isAssignableFrom(String::class)) {
                        return false
                    }
                }

                else -> return null
            }

            return null
        }

    }

}