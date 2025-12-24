package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.deserialization.DeserializationProblemHandler.Companion.NOT_HANDLED
import kotlin.reflect.KClass

/**
 * This is the class that can be registered (via [org.cirjson.cirjackson.databind.DeserializationConfig] object owner by
 * [org.cirjson.cirjackson.databind.ObjectMapper]) to get called when a potentially recoverable problem is encountered
 * during deserialization process. Handlers can try to resolve the problem, throw an exception or just skip the content.
 * 
 * Default implementations for all methods implemented minimal "do nothing" functionality, which is roughly equivalent
 * to not having a registered listener at all. This allows for only implemented handler methods one is interested in,
 * without handling other cases.
 * 
 * NOTE: it is typically **NOT** acceptable to simply do nothing, because this will result in unprocessed tokens being
 * left in token stream (read via [CirJsonParser], in case a structured (CirJSON Object or CirJSON Array) value is being
 * pointed to by parser.
 */
abstract class DeserializationProblemHandler {

    /**
     * Method called when a CirJSON Object property with an unrecognized name is encountered. Content (supposedly)
     * matching the property are accessible via parser that can be obtained from passed deserialization context. Handler
     * can also choose to skip the content; if so, it MUST return true to indicate it did handle property successfully.
     * Skipping is usually done like so:
     * ```
     *  parser.skipChildren()
     * ```
     * 
     * Note: [org.cirjson.cirjackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES]) takes effect only
     * **after** handler is called, and only if handler did **not** handle the problem.
     *
     * @param beanOrClass Either bean instance being deserialized (if one has been instantiated so far); or Class that
     * indicates type that will be instantiated (if no instantiation done yet: for example when bean uses non-default
     * constructors)
     *
     * @param parser Parser to use for handling problematic content
     *
     * @return `true` if the problem is resolved (and content available used or skipped); `false` if the handler did not
     * anything and the problem is unresolved. Note that in latter case caller will either throw an exception or
     * explicitly skip the content, depending on configuration.
     */
    @Throws(CirJacksonException::class)
    open fun handleUnknownProperty(context: DeserializationContext, parser: CirJsonParser,
            deserializer: ValueDeserializer<*>?, beanOrClass: Any, propertyName: String?): Boolean {
        return false
    }

    /**
     * Method called when a property name from input cannot be converted to a non-String key type (passed as
     * `rawKeyType`) due to format problem. Handler may choose to do one of 3 things:
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual key value to use as replacement, and continue processing.
     * 
     * @param failureMessage Message that will be used by caller (by calling [DeserializationContext.weirdKeyException])
     * to indicate type of failure unless handler produces key to use
     *
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown), or
     * value to use as key (possibly `null`
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdKey(context: DeserializationContext, rawKeyType: KClass<*>, keyValue: String,
            failureMessage: String?): Any? {
        return NOT_HANDLED
    }

    /**
     * Method called when a String value cannot be converted to a non-String value type due to specific problem (as
     * opposed to String values never being usable). Handler may choose to do one of 3 things:
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual converted value (of type `targetType`) to use as replacement, and continue processing.
     *    
     * @param failureMessage Message that will be used by caller (by calling
     * [DeserializationContext.weirdNumberException]) to indicate type of failure unless handler produces key to use
     *
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown),
     * or value to use as (possibly `null`)
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdStringValue(context: DeserializationContext, targetType: KClass<*>, valueToConvert: String,
            failureMessage: String?): Any? {
        return NOT_HANDLED
    }

    /**
     * Method called when a numeric value (integral or floating-point from input cannot be converted to a non-numeric
     * value type due to specific problem (as opposed to numeric values never being usable). Handler may choose to do
     * one of 3 things:
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual converted value (of type `targetType`) to use as
     * replacement, and continue processing.
     *    
     * @param failureMessage Message that will be used by caller (by calling
     * [DeserializationContext.weirdNumberException]) to indicate type of failure unless handler produces key to use
     *
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown), or
     * value to use as (possibly `null`)
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdNumberValue(context: DeserializationContext, targetType: KClass<*>, valueToConvert: Number,
            failureMessage: String?): Any? {
        return NOT_HANDLED
    }

    /**
     * Method called when an embedded (native) value ([CirJsonToken.VALUE_EMBEDDED_OBJECT]) cannot be converted directly
     * into expected value type (usually POJO). Handler may choose to do one of 3 things:
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual converted value (of type `targetType`) to use as replacement, and continue processing.
     *    
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown), or
     * value to use (possibly `null`)
     */
    @Throws(CirJacksonException::class)
    open fun handleWeirdNativeValue(context: DeserializationContext, targetType: KotlinType, valueToConvert: Any,
            parser: CirJsonParser?): Any? {
        return NOT_HANDLED
    }

    /**
     * Method that deserializers should call if the first token of the value to deserialize is of unexpected type (that
     * is, type of token that deserializer cannot handle). This could occur, for example, if a Number deserializer
     * encounter [CirJsonToken.START_ARRAY] instead of [CirJsonToken.VALUE_NUMBER_INT] or
     * [CirJsonToken.VALUE_NUMBER_FLOAT].
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Handle content to match (by consuming or skipping it), and return actual instantiated value (of type
     * `targetType`) to use as replacement; value may be `null` as well as expected target type.
     *    
     * @param failureMessage Message that will be used by caller to indicate type of failure unless handler produces
     * value to use
     *
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown), or
     * value to use (possibly `null`
     */
    @Throws(CirJacksonException::class)
    open fun handleUnexpectedToken(context: DeserializationContext, targetType: KotlinType, token: CirJsonToken?,
            parser: CirJsonParser, failureMessage: String?): Any? {
        return NOT_HANDLED
    }

    /**
     * Method called when instance creation for a type fails due to an exception. Handler may choose to do one of
     * following things:
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual instantiated value (of type `targetType`) to use as replacement, and continue processing.
     *    
     * * Return `null` to use null as value but not to try further processing (in cases where properties would otherwise
     * be bound)
     *   
     * @param instantiatedClass Type that was to be instantiated
     *
     * @param argument (optional) Additional argument that was passed to creator, if any
     *
     * @param throwable Exception that caused instantiation failure
     *
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown), or
     * value to use (possibly `null`
     */
    @Throws(CirJacksonException::class)
    open fun handleInstantiationProblem(context: DeserializationContext, instantiatedClass: KClass<*>, argument: Any?,
            throwable: Throwable): Any? {
        return NOT_HANDLED
    }

    /**
     * Method called when instance creation for a type fails due to lack of an instantiator. Method is called before
     * actual deserialization from input is attempted, so handler may do one of following things:
     * 
     * * Indicate it does not know what to do by returning [NOT_HANDLED]
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Handle content to match (by consuming or skipping it), and return actual instantiated value (of type
     * `targetType`) to use as replacement; value may be `null` as well as expected target type.
     *    
     * @param instantiatedClass Type that was to be instantiated
     *
     * @param parser Parser to use for accessing content that needs handling, to either use it or skip it (the latter
     * with [CirJsonParser.skipChildren]).
     *
     * @return Either [NOT_HANDLED] to indicate that handler does not know what to do (and exception may be thrown),
     * or value to use (possibly `null`
     */
    @Throws(CirJacksonException::class)
    open fun handleMissingInstantiator(context: DeserializationContext, instantiatedClass: KClass<*>,
            valueInstantiator: ValueInstantiator?, parser: CirJsonParser?, failureMessage: String?): Any? {
        return NOT_HANDLED
    }

    /**
     * Handler method called if resolution of type id from given String failed to produce a subtype; usually because
     * logical id is not mapped to actual implementation class. Handler may choose to do one of following things:
     * 
     * * Indicate it does not know what to do by returning `null`
     * 
     * * Indicate that nothing should be deserialized, by return `Unit::class`
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual resolved type to use for type id.
     * 
     * @param context Deserialization context to use for accessing information or constructing exception to throw
     * 
     * @param baseType Base type to use for resolving subtype id
     * 
     * @param subtypeId Subtype id that failed to resolve
     * 
     * @param failureMessage Informational message that would be thrown as part of exception, if resolution still fails
     *
     * @return Actual type to use, if resolved; `null` if handler does not know what to do; or `Void.class` to indicate
     * that nothing should be deserialized for type with the id (which caller may choose to do... or not)
     */
    @Throws(CirJacksonException::class)
    open fun handleUnknownTypeId(context: DeserializationContext, baseType: KotlinType, subtypeId: String,
            idResolver: TypeIdResolver, failureMessage: String?): KotlinType? {
        return null
    }

    /**
     * Handler method called if an expected type id for a polymorphic value is not found and no "default type" is
     * specified or allowed. Handler may choose to do one of following things:
     * 
     * * Indicate it does not know what to do by returning `null`
     * 
     * * Indicate that nothing should be deserialized, by return `Unit::class`
     * 
     * * Throw a [java.io.IOException] to indicate specific fail message (instead of standard exception caller would
     * throw
     *    
     * * Return actual resolved type to use for this particular case.
     *
     * @param context Deserialization context to use for accessing information or constructing exception to throw
     * 
     * @param baseType Base type to use for resolving subtype id
     * 
     * @param failureMessage Informational message that would be thrown as part of exception, if resolution still fails
     *
     * @return Actual type to use, if resolved; `null` if handler does not know what to do; or `Void.class` to indicate
     * that nothing should be deserialized for type with the id (which caller may choose to do... or not)
     */
    @Throws(CirJacksonException::class)
    open fun handleMissingTypeId(context: DeserializationContext, baseType: KotlinType, idResolver: TypeIdResolver,
            failureMessage: String?): KotlinType? {
        return null
    }

    companion object {

        /**
         * Marker value returned by some handler methods to indicate that they could not handle problem and produce
         * replacement value.
         */
        val NOT_HANDLED = Any()

    }

}