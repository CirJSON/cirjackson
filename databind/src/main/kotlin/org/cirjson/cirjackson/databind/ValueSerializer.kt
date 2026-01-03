package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitable
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.PropertyWriter
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.emptyIterator
import kotlin.reflect.KClass

/**
 * Abstract class that defines API used by [ObjectMapper] (and other chained [ValueSerializers][ValueSerializer] too) to
 * serialize Objects of arbitrary types into JSON, using provided [CirJsonGenerator]. Note that although API is defined
 * here, custom serializer implementations should almost always be based on
 * [org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer] since it will implement many of optional
 * methods of this class.
 * 
 * If serializer is an aggregate one -- meaning it delegates handling of some of its contents by using other
 * serializer(s) -- it typically also needs to implement [resolve] which can locate secondary serializers needed. This
 * is important to allow dynamic overrides of serializers; separate call interface is needed to separate resolution of
 * secondary serializers (which may have cyclic link back to serializer itself, directly or indirectly).
 * 
 * Initialization of serializers is handled by two main methods:
 * 
 * 1. [resolve]: called after instance is configured to be used for specific type, but without yet knowing property it
 * will be used for (or, in case of root values, without property). Method needs to be implemented for serializers that
 * may work on cyclic types, and specifically is implemented by standard POJO serializer (`BeanSerializer`). It is
 * usually not needed for container types as their type definitions are not cyclic, unlike some POJO types.
 * 
 * 2. [createContextual]: called on resolved instance (whether newly created, or found via cache), when serializer is to
 * be used for specific property, or as root value serializer (no referring property). It is used to apply annotations
 * from property accessors (getter, field), and may also be used for resolving nested types for container serializers
 * (such as ones for [Collections][Collection]).
 * 
 * Caching of serializers occurs after [resolve] is called: cached instances are not contextual.
 * 
 * NOTE: various `serialize` methods are never (to be) called with `null` values -- caller **must** handle `null`
 * values, usually by calling [SerializerProvider.findNullValueSerializer] to obtain serializer to use. This also means
 * that custom serializers cannot be directly used to change the output to produce when serializing `null` values.
 */
abstract class ValueSerializer<T : Any> : CirJsonFormatVisitable {

    /*
     *******************************************************************************************************************
     * Initialization, with former `ResolvableSerializer`, `ContextualSerializer`
     *******************************************************************************************************************
     */

    /**
     * Method called after [SerializerProvider] has registered the serializer, but before it has returned it to the
     * caller. Called object can then resolve its dependencies to other types, including self-references (direct or
     * indirect).
     * 
     * Note that this method does NOT return serializer, since resolution is not allowed to change actual serializer to
     * use.
     *
     * @param provider Provider that has constructed serializer this method is called on.
     */
    open fun resolve(provider: SerializerProvider) {
        // No-op
    }

    /**
     * Method called to see if a different (or differently configured) serializer is needed to serialize values of
     * specified property (or, for root values, in which case `null` is passed). Note that instance that this method is
     * called on is typically shared one and as a result method should **NOT** modify this instance but rather construct
     * and return a new instance. This instance should only be returned as-is, in case it is already suitable for use.
     * 
     * Note that method is only called once per POJO property, and for the first usage as root value serializer; it is
     * not called for every serialization, as doing that would have significant performance impact; most serializers
     * cache contextual instances for future use.
     *
     * @param provider Serializer provider to use for accessing config, other serializers
     * 
     * @param property Property (defined by one or more accessors - field or method - used for accessing logical
     * property value) for which serializer is used to be used; or, `null` for root value (or in cases where caller does
     * not have this information, which is handled as root value case).
     *
     * @return Serializer to use for serializing values of specified property; may be this instance or a new instance.
     */
    open fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        return this
    }

    /*
     *******************************************************************************************************************
     * Fluent factory methods for constructing decorated versions
     *******************************************************************************************************************
     */

    /**
     * Method that will return serializer instance that produces "unwrapped" serialization, if applicable for type being
     * serialized (which is the case for some serializers that produce CirJSON Objects as output). If no unwrapped
     * serializer can be constructed, will simply return serializer as-is.
     * 
     * Default implementation just returns serializer as-is, indicating that no unwrapped variant exists
     *
     * @param unwrapper Name transformation to use to convert between names of unwrapper properties
     */
    open fun unwrappingSerializer(unwrapper: NameTransformer): ValueSerializer<T> {
        return this
    }

    /**
     * Method that can be called to try to replace serializer this serializer delegates calls to. If not supported
     * (either this serializer does not delegate anything; or it does not want any changes), should either throw
     * [UnsupportedOperationException] (if operation does not make sense or is not allowed); or return this serializer
     * as is.
     */
    open fun replaceDelegatee(delegated: ValueSerializer<*>): ValueSerializer<T> {
        throw UnsupportedOperationException()
    }

    /**
     * Mutant factory method that is called if contextual configuration indicates that a specific filter (as specified
     * by `filterId`) is to be used for serialization.
     * 
     * Default implementation simply returns `this`; subclasses that do support filtering will need to create and return
     * new instance if filter changes.
     */
    open fun withFilterId(filterId: Any?): ValueSerializer<*> {
        return this
    }

    /**
     * Mutant factory method called to create a new instance after excluding specified set of properties by name, if
     * there is any.
     *
     * @param ignoredProperties Set of property names to ignore for serialization;
     * 
     * @return Serializer instance that without specified set of properties to ignore (if any)
     */
    open fun withIgnoredProperties(ignoredProperties: Set<String>?): ValueSerializer<*>? {
        return this
    }

    /**
     * Mutant factory called if there is need to create a serializer with specified format overrides (typically from
     * property on which this serializer would be used, based on type declaration). Method is called before
     * [createContextual] but right after serializer is either constructed or fetched from cache.
     * 
     * Method can do one of three things:
     * 
     * * Return `this` instance as is: this means that none of overrides has any effect
     * 
     * * Return an alternate [ValueSerializer], suitable for use with specified format
     * 
     * * Return `null` to indicate that this serializer instance is not suitable for handling format variation, but does
     * not know how to construct new serializer: caller will typically then call
     * [org.cirjson.cirjackson.databind.serialization.SerializerFactory] with overrides to construct new serializer
     * 
     * One example of second approach is the case where [CirJsonFormat.Shape.STRING] indicates String representation and
     * code can just construct simple "string-like serializer", or variant of itself (similar to how [createContextual]
     * is often implemented). And third case (returning `null`) is applicable for cases like format defines
     * [CirJsonFormat.Shape.POJO], requesting "introspect serializer for POJO regardless of type":
     * [org.cirjson.cirjackson.databind.serialization.SerializerFactory] is needed for full re-introspection, typically.
     *
     * @param formatOverrides Override settings, NOT including original format settings (which serializer needs to
     * explicitly retain if needed)
     */
    open fun withFormatOverrides(config: SerializationConfig,
            formatOverrides: CirJsonFormat.Value): ValueSerializer<*>? {
        if (formatOverrides.shape == CirJsonFormat.Shape.ANY) {
            return this
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Serialization methods
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to ask implementation to serialize values of type this serializer handles.
     *
     * @param value Value to serialize; can **not** be `null`.
     * 
     * @param generator Generator used to output resulting CirJSON content
     * 
     * @param serializers Provider that can be used to get serializers for serializing Objects value contains, if any.
     */
    @Throws(CirJacksonException::class)
    abstract fun serialize(value: T, generator: CirJsonGenerator, serializers: SerializerProvider)

    /**
     * Method that can be called to ask implementation to serialize values of type this serializer handles, using
     * specified type serializer for embedding necessary type information.
     * 
     * Default implementation will throw [UnsupportedOperationException] to indicate that proper type handling needs to
     * be implemented.
     * 
     * For simple datatypes written as a single scalar value (CirJSON String, Number, Boolean), implementation would
     * look like:
     * ```
     *  // note: method to call depends on whether this type is serialized as CirJSON scalar, object or Array!
     *  typeSerializer.writeTypePrefixForScalar(value, generator)
     *  serialize(value, generator, provider);
     *  typeSerializer.writeTypeSuffixForScalar(value, generator)
     * ```
     * and implementations for type serialized as CirJSON Arrays or Objects would differ slightly, as
     * `START-ARRAY`/`END-ARRAY` and `START-OBJECT`/`END-OBJECT` pairs need to be properly handled with respect to
     * serializing of contents.
     *
     * @param value Value to serialize; can **not** be `null`.
     * 
     * @param generator Generator used to output resulting CirJSON content
     * 
     * @param serializers Provider that can be used to get serializers for serializing Objects value contains, if any.
     * 
     * @param typeSerializer Type serializer to use for including type information
     */
    @Throws(CirJacksonException::class)
    open fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val clazz = handledType() ?: value::class
        return serializers.reportBadDefinition(clazz,
                "Type id handling not implemented for type ${clazz.qualifiedName} (by serializer of type ${this::class.qualifiedName})")
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Method for accessing type of Objects this serializer can handle. Note that this information is not guaranteed
     * to be exact -- it may be a more generic (super-type) -- but it should not be incorrect (return a non-related
     * type).
     */
    open fun handledType(): KClass<*>? {
        return Any::class
    }

    /**
     * Method that can be called to see whether this serializer instance will use Object Id to handle cyclic references.
     */
    open fun usesObjectId(): Boolean {
        return false
    }

    /**
     * Accessor for checking whether this serializer is an "unwrapping" serializer; this is necessary to know since it
     * may also require caller to suppress writing of the leading property name.
     */
    open val isUnwrappingSerializer: Boolean
        get() = false

    /**
     * Accessor that can be used to determine if this serializer uses another serializer for actual serialization, by
     * delegating calls. If so, will return immediate delegate (which itself may delegate to further serializers);
     * otherwise will return `null`.
     *
     * @return Serializer this serializer delegates calls to, if `null`; `null` otherwise.
     */
    open val delegates: ValueSerializer<*>?
        get() = null

    open val properties: Iterator<PropertyWriter>
        get() = emptyIterator()

    /*
     *******************************************************************************************************************
     * Accessors for introspecting handling of values
     *******************************************************************************************************************
     */

    /**
     * Method called to check whether given serializable value is considered "empty" value (for purposes of suppressing
     * serialization of empty values).
     * 
     * Default implementation will consider only `null` values to be empty.
     */
    open fun isEmpty(provider: SerializerProvider, value: T?): Boolean {
        return value == null
    }

    /*
     *******************************************************************************************************************
     * Default CirJsonFormatVisitable implementation
     *******************************************************************************************************************
     */

    /**
     * Default implementation simply calls [CirJsonFormatVisitorWrapper.expectAnyFormat].
     */
    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectAnyFormat(typeHint)
    }

    /*
     *******************************************************************************************************************
     * Helper class
     *******************************************************************************************************************
     */

    /**
     * This marker class is only to be used with annotations, to indicate that **no serializer is configured**.
     * 
     * Specifically, this class is to be used as the marker for annotation
     * [org.cirjson.cirjackson.databind.annotation.CirJsonSerialize].
     */
    abstract class None private constructor() : ValueSerializer<Any>()

}