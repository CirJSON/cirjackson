package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitable
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonValueFormat
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.serialization.PropertyFilter
import org.cirjson.cirjackson.databind.util.isCirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.util.throwIfError
import org.cirjson.cirjackson.databind.util.throwIfRuntimeException
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.reflect.KClass

/**
 * Base class used by all standard serializers, and can also be used for custom serializers (in fact, this is the
 * recommended base class to use).
 */
@Suppress("ThrowableNotThrown")
abstract class StandardSerializer<T : Any> : ValueSerializer<T>, CirJsonFormatVisitable {

    /**
     * Nominal type supported, usually declared type of property for which serializer is used.
     */
    protected val myHandledType: KClass<*>

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(type: KClass<*>) : super() {
        myHandledType = type
    }

    protected constructor(type: KotlinType) : super() {
        myHandledType = type.rawClass
    }

    protected constructor(source: StandardSerializer<T>) : super() {
        myHandledType = source.myHandledType
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun handledType(): KClass<*>? {
        return myHandledType
    }

    /*
     *******************************************************************************************************************
     * Type introspection API, partial/default implementation
     *******************************************************************************************************************
     */

    /**
     * Default implementation specifies no format. This behavior is usually overridden by custom serializers.
     */
    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectAnyFormat(typeHint)
    }

    /*
     *******************************************************************************************************************
     * Helper methods for CirJSON Schema generation
     *******************************************************************************************************************
     */

    protected open fun createSchemaNode(type: String): ObjectNode {
        val schema = CirJsonNodeFactory.instance.objectNode()
        schema.put("type", type)
        return schema
    }

    protected open fun createSchemaNode(type: String, isOptional: Boolean): ObjectNode {
        val schema = createSchemaNode(type)

        if (!isOptional) {
            schema.put("required", true)
        }

        return schema
    }

    /**
     * Helper method that calls necessary visit method(s) to indicate that the underlying CirJSON type is CirJSON
     * String.
     */
    protected open fun visitStringFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectStringFormat(typeHint)
    }

    /**
     * Helper method that calls necessary visit method(s) to indicate that the underlying CirJSON type is CirJSON
     * String, but that there is a more refined logical type
     */
    protected open fun visitStringFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            format: CirJsonValueFormat) {
        visitor.expectStringFormat(typeHint)?.format(format)
    }

    /**
     * Helper method that calls necessary visit method(s) to indicate that the underlying CirJSON type is CirJSON
     * Integer number.
     */
    protected open fun visitIntFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            numberType: CirJsonParser.NumberType?) {
        val v = visitor.expectIntFormat(typeHint) ?: return

        if (numberType != null) {
            v.numberType(numberType)
        }
    }

    /**
     * Helper method that calls necessary visit method(s) to indicate that the underlying CirJSON type is CirJSON
     * Integer number, but that there is also a further format restriction involved.
     */
    protected open fun visitIntFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            numberType: CirJsonParser.NumberType?, format: CirJsonValueFormat?) {
        val v = visitor.expectIntFormat(typeHint) ?: return

        if (numberType != null) {
            v.numberType(numberType)
        }

        if (format != null) {
            v.format(format)
        }
    }

    /**
     * Helper method that calls necessary visit method(s) to indicate that the underlying CirJSON type is a
     * floating-point CirJSON number.
     */
    protected open fun visitFloatFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            numberType: CirJsonParser.NumberType) {
        visitor.expectIntFormat(typeHint)?.numberType(numberType)
    }

    protected open fun visitArrayFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            itemSerializer: ValueSerializer<*>?, itemType: KotlinType) {
        val v = visitor.expectArrayFormat(typeHint) ?: return

        if (itemSerializer != null) {
            v.itemsFormat(itemSerializer, itemType)
        }
    }

    protected open fun visitArrayFormat(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            itemFormat: CirJsonFormatTypes) {
        visitor.expectArrayFormat(typeHint)?.itemsFormat(itemFormat)
    }

    /*
     *******************************************************************************************************************
     * Helper methods for exception handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun wrapAndThrow(provider: SerializerProvider?, throwable: Throwable, bean: Any, fieldName: String) {
        var realThrowable = throwable

        while (realThrowable is InvocationTargetException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        while (realThrowable is CirJacksonIOException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        realThrowable.throwIfError()

        if (realThrowable !is CirJacksonException) {
            val wrap = provider?.isEnabled(SerializationFeature.WRAP_EXCEPTIONS) ?: true

            if (!wrap) {
                realThrowable.throwIfRuntimeException()
            }
        }

        throw CirJacksonException.wrapWithPath(realThrowable, bean, fieldName)
    }

    @Throws(CirJacksonException::class)
    open fun wrapAndThrow(provider: SerializerProvider?, throwable: Throwable, bean: Any, index: Int) {
        var realThrowable = throwable

        while (realThrowable is InvocationTargetException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        while (realThrowable is CirJacksonIOException && realThrowable.cause != null) {
            realThrowable = realThrowable.cause!!
        }

        realThrowable.throwIfError()

        if (realThrowable !is CirJacksonException) {
            val wrap = provider?.isEnabled(SerializationFeature.WRAP_EXCEPTIONS) ?: true

            if (!wrap) {
                realThrowable.throwIfRuntimeException()
            }
        }

        throw CirJacksonException.wrapWithPath(realThrowable, bean, index)
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Helper method that can be used to see if specified property has annotation indicating that a converter is to be
     * used for contained values (contents of structured types; array/List/Map values)
     *
     * @param existingSerializer (optional) configured content serializer if one already exists.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun findContextualConvertingSerializer(provider: SerializerProvider, property: BeanProperty?,
            existingSerializer: ValueSerializer<*>?): ValueSerializer<*>? {
        var conversions = provider.getAttribute(KEY_CONTENT_CONVERTER_LOCK) as MutableMap<Any?, Any>?

        if (conversions != null) {
            val lock = conversions[property]

            if (lock != null) {
                return existingSerializer
            }
        } else {
            conversions = IdentityHashMap()
            provider.setAttribute(KEY_CONTENT_CONVERTER_LOCK, conversions)
        }

        val introspector = provider.annotationIntrospector ?: return existingSerializer

        property ?: return existingSerializer

        conversions[property] = true

        try {
            val serializer = findConvertingContentSerializer(provider, introspector, property, existingSerializer)

            if (serializer != null) {
                return provider.handleSecondaryContextualization(serializer, property)
            }
        } finally {
            conversions.remove(property)
        }

        return existingSerializer
    }

    private fun findConvertingContentSerializer(provider: SerializerProvider, introspector: AnnotationIntrospector,
            property: BeanProperty, existingSerializer: ValueSerializer<*>?): ValueSerializer<*>? {
        val member = property.member ?: return existingSerializer
        val converterDefinition =
                introspector.findSerializationContentConverter(provider.config, member) ?: return existingSerializer
        val converter = provider.converterInstance(member, converterDefinition)!!
        val delegateType = converter.getOutputType(provider.typeFactory)

        val serializer = if (existingSerializer == null && !delegateType.isJavaLangObject) {
            provider.findValueSerializer(delegateType)
        } else {
            existingSerializer
        }

        return StandardDelegatingSerializer(converter, delegateType, serializer, property)
    }

    /**
     * Helper method used to locate filter that is needed, based on filter id this serializer was constructed with.
     */
    protected open fun findPropertyFilter(provider: SerializerProvider, filterId: Any,
            valueToFilter: Any?): PropertyFilter? {
        val filters = provider.filterProvider ?: return provider.reportBadDefinition(handledType()!!,
                "Cannot resolve PropertyFilter with id '$filterId'; no FilterProvider configured")
        return filters.findPropertyFilter(provider, filterId, valueToFilter)
    }

    /**
     * Helper method that may be used to find if this deserializer has specific [CirJsonFormat] settings, either via
     * property, or through type-specific defaulting.
     *
     * @param typeForDefaults Type (erased) used for finding default format settings
     */
    protected open fun findFormatOverrides(provider: SerializerProvider, property: BeanProperty?,
            typeForDefaults: KClass<*>): CirJsonFormat.Value {
        return property?.findPropertyFormat(provider.config, typeForDefaults) ?: provider.getDefaultPropertyFormat(
                typeForDefaults)
    }

    /**
     * Convenience method that uses [findFormatOverrides] to find possible defaults and/of overrides, and then calls
     * `CirJsonFormat.Value.getFeature(...)` to find whether that feature has been specifically marked as enabled or
     * disabled.
     *
     * @param typeForDefaults Type (erased) used for finding default format settings
     */
    protected open fun findFormatFeature(provider: SerializerProvider, property: BeanProperty?,
            typeForDefaults: KClass<*>, feature: CirJsonFormat.Feature): Boolean? {
        val format = findFormatOverrides(provider, property, typeForDefaults)
        return format.getFeature(feature)
    }

    protected open fun findIncludeOverrides(provider: SerializerProvider, property: BeanProperty?,
            typeForDefaults: KClass<*>): CirJsonInclude.Value? {
        return if (property != null) {
            property.findPropertyInclusion(provider.config, typeForDefaults)
        } else {
            provider.getDefaultPropertyInclusion(typeForDefaults)
        }
    }

    /**
     * Convenience method for finding out possibly configured content value serializer.
     */
    protected open fun findAnnotatedContentSerializer(provider: SerializerProvider,
            property: BeanProperty?): ValueSerializer<*>? {
        property ?: return null
        val member = property.member ?: return null
        val introspector = provider.annotationIntrospector!!
        return provider.serializerInstance(member, introspector.findContentSerializer(provider.config, member))
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    protected open fun isDefaultSerializer(serializer: ValueSerializer<*>?): Boolean {
        return serializer.isCirJacksonStandardImplementation
    }

    companion object {

        /**
         * Key used for storing a lock object to prevent infinite recursion when constructing converting serializers.
         */
        private val KEY_CONTENT_CONVERTER_LOCK = Any()

    }

}