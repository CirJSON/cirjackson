package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.AccessPattern
import org.cirjson.cirjackson.databind.util.NameTransformer
import kotlin.reflect.KClass

/**
 * Abstract class that defines API used by [ObjectMapper] and [ObjectReader] to deserialize Objects of arbitrary types
 * from CirJSON, using provided [CirJsonParser] (within current read context of [DeserializationContext]. Deserializers
 * use delegation so that calls typically end up as a stack of calls through deserializer hierarchy based on POJO
 * properties.
 * 
 * Custom deserializers should usually not directly extend this class, but instead extend
 * [org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer] (or its subtypes like
 * [org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer]).
 * 
 * If deserializer is an aggregate one -- meaning it delegates handling of some of its contents by using other
 * deserializer(s) -- it typically also needs to implement [resolve] which can locate dependent deserializers. This is
 * important to allow dynamic overrides of deserializers; separate call interface is needed to separate resolution of
 * dependent deserializers (which may have cyclic link back to deserializer itself, directly or indirectly).
 * 
 * In addition, to support per-property annotations (to configure aspects of deserialization on per-property basis),
 * deserializers may want to override [createContextual] which allows specialization of deserializers: it is passed
 * information on property, and can create a newly configured deserializer for handling that particular property.
 * 
 * Resolution of deserializers occurs before contextualization.
 */
abstract class ValueDeserializer<T> : NullValueProvider {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Method called after deserializer instance has been constructed (and registered as necessary by provider objects),
     * but before it has returned it to the caller. Called object can then resolve its dependencies to other types,
     * including self-references (direct or indirect).
     *
     * @param context Context to use for accessing configuration, resolving secondary deserializers
     */
    open fun resolve(context: DeserializationContext) {
        // No-op
    }

    /**
     * Method called to see if a different (or differently configured) deserializer
     * is needed to deserialize values of specified property.
     * Note that instance that this method is called on is typically shared one and
     * as a result method should **NOT** modify this instance but rather construct
     * and return a new instance. This instance should only be returned as-is, in case
     * it is already suitable for use.
     *
     * @param context Deserialization context to access configuration, additional
     *    deserializers that may be needed by this deserializer
     * @param property Method, field or constructor parameter that represents the property
     *   (and is used to assign deserialized value).
     *   Should be available; but there may be cases where caller cannot provide it and
     *   `null` is passed instead (in which case impls usually pass 'this' deserializer as is)
     *
     * @return Deserializer to use for deserializing values of specified property;
     *   may be this instance or a new instance.
     */
    open fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        return this
    }

    /*
     *******************************************************************************************************************
     * Main deserialization methods
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to ask implementation to deserialize CirJSON content into the value type this
     * serializer handles. Returned instance is to be constructed by method itself.
     * 
     * Pre-condition for this method is that the parser points to the first event that is part of value to deserializer
     * (and which is never CirJSON 'null' literal, more on this below): for simple types it may be the only value; and
     * for structured types the Object start marker or a FIELD_NAME.
     * 
     * The two possible input conditions for structured types result from polymorphism via fields. In the ordinary case,
     * CirJackson calls this method when it has encountered an OBJECT_START, and the method implementation must advance
     * to the next token to see the first field name. If the application configures polymorphism via a field, then the
     * object looks like the following.
     * ```
     * {
     *     "__cirJsonId__": "id",
     *     "@class": "class name",
     *     ...
     * }
     * ```
     *  CirJackson consumes the two tokens (the `@class` field name  and its value) in order to learn the class and
     *  select the deserializer.  Thus, the stream is pointing to the FIELD_NAME for the first field  after the @class.
     *  Thus, if you want your method to work correctly  both with and without polymorphism, you must begin your method
     *  with:
     * ```
     * if (p.currentToken() == CirJsonToken.START_OBJECT) {
     *     p.nextToken()
     * }
     * ```
     * This results in the stream pointing to the field name, so that the two conditions align.
     * 
     * Post-condition is that the parser will point to the last event that is part of deserialized value (or in case
     * deserialization fails, event that was not recognized or usable, which may be the same event as the one it pointed
     * to upon call).
     * 
     * Note that this method is never called for CirJSON `null` literal, and thus deserializers need (and should) not
     * check for it.
     *
     * @param parser Parsed used for reading CirJSON content
     * 
     * @param context Context that can be used to access information about this deserialization activity.
     *
     * @return Deserialized value
     */
    @Throws(CirJacksonException::class)
    abstract fun deserialize(parser: CirJsonParser, context: DeserializationContext): T?

    /**
     * Alternate deserialization method (compared to the most commonly used, [deserialize]), which takes in initialized
     * value instance, to be configured and/or populated by deserializer. Method is not necessarily used (or supported)
     * by all types (it will not work for immutable types, for obvious reasons): most commonly it is used for
     * Collections and Maps. It may be used both with "updating readers" (for POJOs) and when Collections and Maps use
     * "getter as setter".
     * 
     * Default implementation just throws [UnsupportedOperationException], to indicate that types that do not explicitly
     * add support do not necessarily support update-existing-value operation (esp. immutable types)
     */
    @Throws(CirJacksonException::class)
    open fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: T): T? {
        context.handleBadMerge(this)
        return deserialize(parser, context)
    }

    /**
     * Deserialization called when type being deserialized is defined to contain additional type identifier, to allow
     * for correctly instantiating correct subtype. This can be due to annotation on type (or its supertype), or due to
     * global settings without annotations.
     * 
     * Default implementation may work for some types, but ideally subclasses should not rely on current default
     * implementation. Implementation is mostly provided to avoid compilation errors with older code.
     *
     * @param typeDeserializer Deserializer to use for handling type information
     */
    @Throws(CirJacksonException::class)
    open fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromAny(parser, context)
    }

    /**
     * Method similar to [deserializeWithType] but called when merging value. Considered "bad merge" by default
     * implementation, but if [MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE] is enabled will a simple delegate to
     * [deserializeWithType].
     */
    @Throws(CirJacksonException::class)
    open fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer, intoValue: T): Any? {
        context.handleBadMerge(this)
        return deserializeWithType(parser, context, typeDeserializer)
    }

    /*
     *******************************************************************************************************************
     * Fluent factory methods for constructing decorated versions
     *******************************************************************************************************************
     */

    /**
     * Method that will return deserializer instance that is able to handle "unwrapped" value instances If no unwrapped
     * instance can be constructed, will simply return this object as-is.
     *
     * Default implementation just returns 'this' indicating that no unwrapped variant exists
     */
    open fun unwrappingDeserializer(context: DeserializationContext, unwrapper: NameTransformer): ValueDeserializer<T> {
        return this
    }

    /**
     * Method that can be called to try to replace deserializer this deserializer delegates calls to. If not supported
     * (either this deserializer does not delegate anything; or it does not want any changes), should either throw
     * [UnsupportedOperationException] (if operation does not make sense or is not allowed); or return this deserializer
     * as is.
     */
    open fun replaceDelegate(deserializer: ValueDeserializer<*>): ValueDeserializer<*> {
        throw UnsupportedOperationException()
    }

    /*
     *******************************************************************************************************************
     * Introspection methods for figuring out configuration/setup of this deserializer instance and/or type it handles
     *******************************************************************************************************************
     */

    /**
     * Method for accessing concrete physical type of values this deserializer produces. Note that this information is
     * not guaranteed to be exact -- it may be a more generic (super-type) -- but it should not be incorrect (return a
     * non-related type).
     *
     * Default implementation will return null, which means almost same as returning `Any::class` would; that is, that
     * nothing is known about handled type.
     *
     * @return Physical type of values this deserializer produces, if known; `null` if not
     */
    open fun handledType(): KClass<*>? {
        return null
    }

    /**
     * Method for accessing logical type of values this deserializer produces. Typically used for further configuring
     * handling of values, for example, to find which coercions are legal.
     *
     * @return Logical type of values this deserializer produces, if known; `null` if not
     */
    open fun logicalType(): LogicalType? {
        return null
    }

    /**
     * Method called to see if deserializer instance is cacheable and usable for other properties of same type (type for
     * which instance was created).
     * 
     * Note that cached instances are still contextualized on per-property basis (but note that [resolved][resolve] just
     * once!) This means that in most cases it is safe to cache instances; however, it only makes sense to cache
     * instances if instantiation is expensive, or if instances are heavy-weight.
     * 
     * Default implementation returns `false`, to indicate that no caching is done.
     */
    open val isCacheable: Boolean
        get() = false

    /**
     * Accessor that can be used to determine if this deserializer uses another deserializer for actual deserialization,
     * by delegating calls. If so, will return immediate delegate (which itself may delegate to further deserializers);
     * otherwise will return `null`.
     *
     * @return Deserializer this deserializer delegates calls to, if not `null`; `null` otherwise.
     */
    open val delegate: ValueDeserializer<*>?
        get() = null

    /**
     * Accessor that will either return `null` to indicate that type being deserializers has no concept of properties;
     * or a collection of identifiers for which `toString` will give external property name. This is only to be used for
     * error reporting and diagnostics purposes (most commonly, to accompany "unknown property" exception).
     */
    open val knownPropertyNames: Collection<Any>?
        get() = null

    /*
     *******************************************************************************************************************
     * Default NullValueProvider implementation
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to determine value to be used for representing `null` values (values deserialized when
     * CirJSON token is [org.cirjson.cirjackson.core.CirJsonToken.VALUE_NULL]). Usually this is simply `null`, but for
     * some types (especially primitives) it may be necessary to use non-`null` values.
     * 
     * This method may be called once, or multiple times, depending on what [nullAccessPattern] returns.
     * 
     * Default implementation simply returns `null`.
     */
    override fun getNullValue(context: DeserializationContext): Any? {
        return null
    }

    /**
     *  This method may be called in conjunction with calls to [getNullValue], to check whether it needs to be called
     *  just once (static values), or each time empty value is needed.
     * 
     * Default implementation indicates that the "`null` value" to use for input `null` does not vary across uses so
     * that [getNullValue] need not be called more than once per deserializer instance. This information may be used as
     * optimization.
     */
    override val nullAccessPattern: AccessPattern
        get() = AccessPattern.CONSTANT

    /**
     * Method called to determine placeholder value to be used for cases where no value was obtained from input, but we
     * must pass a value nonetheless: the common case is that of Creator methods requiring passing a value for every
     * parameter. Usually this is same as [getNullValue] (which in turn is usually simply `null`), but it can be
     * overridden for specific types: most notable scalar types must use "default" values.
     * 
     * This method needs to be called every time a determination is made.
     * 
     * Default implementation simply calls [getNullValue] and returns value.
     */
    override fun getAbsentValue(context: DeserializationContext): Any? {
        return getNullValue(context)
    }

    /*
     *******************************************************************************************************************
     * Accessors for other replacement/placeholder values
     *******************************************************************************************************************
     */

    /**
     * Method called to determine value to be used for "empty" values (most commonly when deserializing from empty
     * CirJSON Strings). Usually this is same as [getNullValue] (which in turn is usually simply `null`), but it can be
     * overridden for specific types. Or, if type should never be converted from empty String, method can also throw an
     * exception.
     * 
     * This method may be called once, or multiple times, depending on what [emptyAccessPattern] returns.
     * 
     * Default implementation simply calls [getNullValue] and returns value.
     */
    open fun getEmptyValue(context: DeserializationContext): Any? {
        return getNullValue(context)
    }

    open val emptyAccessPattern: AccessPattern
        get() = AccessPattern.DYNAMIC

    /*
     *******************************************************************************************************************
     * Other accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor that can be used to check whether this deserializer is expecting to possibly get an Object Identifier
     * value instead of full value serialization. If so, should be able to resolve it to actual Object instance to
     * return as deserialized value.
     * 
     * Default implementation returns `null`, as support cannot be implemented generically. Some standard deserializers
     * (most notably [org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer]) do implement this feature,
     * and may return reader instance, depending on exact configuration of instance (which is based on type, and
     * referring property).
     *
     * @return ObjectIdReader used for resolving possible Object Identifier value, instead of full value serialization,
     * if deserializer can do that; `null` if no Object ID is expected.
     */
    open fun getObjectIdReader(context: DeserializationContext): ObjectIdReader? {
        return null
    }

    /**
     * Method needed by [org.cirjson.cirjackson.databind.deserialization.BeanDeserializerFactory] to properly link
     * managed- and back-reference pairs.
     */
    open fun findBackReference(referenceName: String): SettableBeanProperty? {
        throw IllegalArgumentException(
                "Cannot handle managed/back reference '$referenceName': type: value deserializer of type ${this::class.qualifiedName} does not support them")
    }

    /**
     * Introspection method that may be called to see whether deserializer supports update of an existing value (aka
     * "merging") or not. Return value should either be `false` if update is not supported at all (immutable values);
     * `true` if update should usually work (regular POJOs, for example), or `null` if this is either not known, or may
     * sometimes work.
     * 
     * Information gathered is typically used to either prevent merging update for property (either by skipping, if
     * based on global defaults; or by exception during deserializer construction if explicit attempt made) if `false`
     * returned, or inclusion if `true` is specified. If "unknown" case (`null` returned) behavior is to exclude
     * property if global defaults used; or to allow if explicit per-type or property merging is defined.
     * 
     * Default implementation returns `null` to allow explicit per-type or per-property attempts.
     */
    open fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * This marker class is only to be used with annotations, to indicate that **no deserializer is configured**.
     * 
     * Specifically, this class is to be used as the marker for annotation
     * [org.cirjson.cirjackson.databind.annotation.CirJsonDeserialize]
     */
    abstract class None private constructor() : ValueDeserializer<Any>()

}