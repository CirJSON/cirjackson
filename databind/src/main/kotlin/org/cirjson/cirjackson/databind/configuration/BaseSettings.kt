package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.Base64Variant
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.PropertyNamingStrategy
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverBuilder
import org.cirjson.cirjackson.databind.introspection.AccessorNamingStrategy
import org.cirjson.cirjackson.databind.introspection.AnnotationIntrospectorPair
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.util.StandardDateFormat
import java.text.DateFormat
import java.util.*

/**
 * Immutable container class used to store simple configuration settings for both serialization and deserialization.
 * Since instances are fully immutable, instances can be freely shared and used without synchronization.
 *
 * @property annotationIntrospector Introspector used for accessing annotation value-based configuration.
 *
 * @property propertyNamingStrategy Custom property naming strategy in use, if any.
 *
 * @property accessorNaming Provider for creating [AccessorNamingStrategy] instances to use
 *
 * @property defaultTyper Type information handler used for "default typing".
 *
 * @property polymorphicTypeValidator Validator that is used to limit allowed subtypes during polymorphic
 * deserialization, mostly for security reasons when dealing with untrusted content.
 *
 * @property dateFormat Custom date format to use for deserialization. If specified, will be used instead of
 * [StandardDateFormat].
 *
 * Note that the configured format object will be cloned once per deserialization process (first time it is needed)
 *
 * @property handlerInstantiator Object used for creating instances of handlers (serializers, deserializers, type and
 * type id resolvers), given class to instantiate. This is typically used to do additional configuration (with
 * dependency injection, for example) beyond simply the construction of instances; or to use alternative constructors.
 *
 * @property locale Default [Locale] used with serialization formats. Default value is [Locale.getDefault].
 *
 * @property myTimeZone Default [TimeZone] used with serialization formats, if (and only if!) explicitly set by use;
 * otherwise `null` to indicate "use default", which means "UTC".
 *
 * Note that if a new value is set, timezone is also assigned to [dateFormat] of this object.
 *
 * @property base64Variant Explicitly default [Base64Variant] to use for handling binary data (`ByteArray`), used with
 * data formats that use base64 encoding (like CirJSON, CSV).
 *
 * @property cacheProvider Used to provide custom cache implementation in downstream components.
 *
 * @property nodeFactory Factory used for constructing [org.cirjson.cirjackson.databind.CirJsonNode] instances.
 *
 * @property myConstructorDetector Handler that specifies some aspects of Constructor autodetection.
 */
class BaseSettings(val annotationIntrospector: AnnotationIntrospector?,
        val propertyNamingStrategy: PropertyNamingStrategy?, val accessorNaming: AccessorNamingStrategy.Provider,
        val defaultTyper: TypeResolverBuilder<*>?, val polymorphicTypeValidator: PolymorphicTypeValidator,
        val dateFormat: DateFormat, val handlerInstantiator: HandlerInstantiator?, val locale: Locale,
        private val myTimeZone: TimeZone?, val base64Variant: Base64Variant, val cacheProvider: CacheProvider,
        val nodeFactory: CirJsonNodeFactory, private val myConstructorDetector: ConstructorDetector?) {

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun withAnnotationIntrospector(annotationIntrospector: AnnotationIntrospector?): BaseSettings {
        if (this.annotationIntrospector === annotationIntrospector) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun withInsertedAnnotationIntrospector(annotationIntrospector: AnnotationIntrospector?): BaseSettings {
        return withAnnotationIntrospector(
                AnnotationIntrospectorPair.create(annotationIntrospector, this.annotationIntrospector))
    }

    fun withAppendedAnnotationIntrospector(annotationIntrospector: AnnotationIntrospector?): BaseSettings {
        return withAnnotationIntrospector(
                AnnotationIntrospectorPair.create(this.annotationIntrospector, annotationIntrospector))
    }

    fun with(annotationIntrospector: AnnotationIntrospector?): BaseSettings {
        if (this.annotationIntrospector === annotationIntrospector) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(propertyNamingStrategy: PropertyNamingStrategy?): BaseSettings {
        if (this.propertyNamingStrategy === propertyNamingStrategy) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(accessorNaming: AccessorNamingStrategy.Provider): BaseSettings {
        if (this.accessorNaming === accessorNaming) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(defaultTyper: TypeResolverBuilder<*>?): BaseSettings {
        if (this.defaultTyper === defaultTyper) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(polymorphicTypeValidator: PolymorphicTypeValidator): BaseSettings {
        if (this.polymorphicTypeValidator === polymorphicTypeValidator) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(dateFormat: DateFormat): BaseSettings {
        if (this.dateFormat === dateFormat) {
            return this
        }

        val realDateFormat = dateFormat.takeUnless { hasExplicitTimeZone() } ?: force(dateFormat, myTimeZone)

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, realDateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(handlerInstantiator: HandlerInstantiator?): BaseSettings {
        if (this.handlerInstantiator === handlerInstantiator) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(locale: Locale): BaseSettings {
        if (this.locale === locale) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    /**
     * Fluent factory for constructing a new instance that uses the specified TimeZone. Note that timezone used with
     * also be assigned to configured [DateFormat], changing time formatting defaults.
     */
    fun with(timeZone: TimeZone?): BaseSettings {
        if (myTimeZone === timeZone) {
            return this
        }

        val dateFormat = force(dateFormat, timeZone ?: DEFAULT_TIMEZONE)
        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, timeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(base64Variant: Base64Variant): BaseSettings {
        if (this.base64Variant === base64Variant) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    /**
     * Fluent factory for constructing a new instance with provided [CacheProvider].
     *
     * @return a new instance with provided [CacheProvider].
     */
    fun with(cacheProvider: CacheProvider): BaseSettings {
        if (this.cacheProvider === cacheProvider) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(nodeFactory: CirJsonNodeFactory): BaseSettings {
        if (this.nodeFactory === nodeFactory) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    fun with(constructorDetector: ConstructorDetector?): BaseSettings {
        if (myConstructorDetector === constructorDetector) {
            return this
        }

        return BaseSettings(annotationIntrospector, propertyNamingStrategy, accessorNaming, defaultTyper,
                polymorphicTypeValidator, dateFormat, handlerInstantiator, locale, myTimeZone, base64Variant,
                cacheProvider, nodeFactory, myConstructorDetector)
    }

    /*
     *******************************************************************************************************************
     * API
     *******************************************************************************************************************
     */

    val timeZone: TimeZone
        get() = myTimeZone ?: DEFAULT_TIMEZONE

    /**
     * Method that may be called to determine whether this settings object has been explicitly configured with a
     * TimeZone (`true`) or is still relying on the default settings (`false`).
     */
    fun hasExplicitTimeZone(): Boolean {
        return myTimeZone != null
    }

    val constructorDetector: ConstructorDetector
        get() = myConstructorDetector ?: ConstructorDetector.DEFAULT

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun force(dateFormat: DateFormat, timeZone: TimeZone?): DateFormat {
        if (dateFormat is StandardDateFormat) {
            return dateFormat.withTimeZone(timeZone)
        }

        val realDateFormat = dateFormat.clone() as DateFormat
        realDateFormat.timeZone = timeZone
        return realDateFormat
    }

    companion object {

        /**
         * We will use a default TimeZone as the baseline.
         */
        private val DEFAULT_TIMEZONE: TimeZone = TimeZone.getTimeZone("UTC")

    }

}