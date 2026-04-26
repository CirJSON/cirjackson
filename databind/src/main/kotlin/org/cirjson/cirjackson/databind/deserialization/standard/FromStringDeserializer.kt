package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.util.className
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import java.net.MalformedURLException
import java.net.UnknownHostException
import kotlin.reflect.KClass

/**
 * Base class for building simple scalar value deserializers that accept
 * String values.
 */
abstract class FromStringDeserializer<T : Any> : StandardScalarDeserializer<T> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(valueClass: KClass<*>) : super(valueClass)

    protected constructor(valueType: KotlinType) : super(valueType)

    /*
     *******************************************************************************************************************
     * Deserializer implementations
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): T? {
        var text = parser.valueAsString

        if (text == null) {
            val token = parser.currentToken()

            if (token != CirJsonToken.START_OBJECT) {
                return deserializeFromOther(parser, context, token) as T?
            }

            text = context.extractScalarFromObject(parser, this, myValueClass)
        }

        if (text.isEmpty()) {
            return deserializeFromEmptyString(context) as T?
        }

        if (shouldTrim()) {
            val old = text
            text = text.trim()

            if (text != old) {
                if (text.isEmpty()) {
                    return deserializeFromEmptyString(context) as T?
                }
            }
        }

        val cause = try {
            return deserialize(text, context)
        } catch (e: IllegalArgumentException) {
            e
        } catch (e: MalformedURLException) {
            e
        } catch (e: UnknownHostException) {
            e
        }

        val problem = cause.message?.let { ", problem: $it" } ?: ""
        val message = "not a valid textual representation$problem"
        throw context.weirdStringException(text, myValueClass, message).withCause(cause)
    }

    /**
     * Main method from trying to deserialize actual value from non-empty String.
     */
    @Throws(CirJacksonException::class, MalformedURLException::class, UnknownHostException::class)
    protected abstract fun deserialize(value: String, context: DeserializationContext): T?

    protected open fun shouldTrim(): Boolean {
        return true
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromOther(parser: CirJsonParser, context: DeserializationContext,
            token: CirJsonToken?): Any? {
        if (token == CirJsonToken.START_ARRAY) {
            return deserializeFromArray(parser, context)
        }

        if (token != CirJsonToken.VALUE_EMBEDDED_OBJECT) {
            return context.handleUnexpectedToken(getValueType(context), parser)
        }

        val obj = parser.embeddedObject ?: return null

        return if (myValueClass.isAssignableFrom(obj::class)) {
            obj
        } else {
            deserializeEmbedded(obj, context)
        }
    }

    /**
     * Overridable method to allow coercion from embedded value that is neither `null` nor directly assignable to target
     * type. Used, for example, by
     * [UUIDDeserializer][org.cirjson.cirjackson.databind.deserialization.jdk.UUIDDeserializer] to coerce from
     * `ByteArray`.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeEmbedded(obj: Any, context: DeserializationContext): T? {
        return context.reportInputMismatch(this,
                "Don't know how to convert embedded Object of type ${obj.className} into ${myValueClass.qualifiedName}")
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromEmptyString(context: DeserializationContext): Any? {
        val action = context.findCoercionAction(logicalType(), myValueClass, CoercionInputShape.EMPTY_STRING)

        return when (action) {
            CoercionAction.FAIL -> context.reportInputMismatch(this,
                    "Cannot coerce empty String (\"\") to ${coercedTypeDescription()} (but could if enabling coercion using `CoercionConfig`)")

            CoercionAction.AS_NULL -> getNullValue(context)
            CoercionAction.AS_EMPTY -> getEmptyValue(context)
            else -> deserializeFromEmptyStringDefault(context)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromEmptyStringDefault(context: DeserializationContext): Any? {
        return getNullValue(context)
    }

}