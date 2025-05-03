package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize.Typing.DYNAMIC
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize.Typing.STATIC
import org.cirjson.cirjackson.databind.util.Converter
import kotlin.reflect.KClass

/**
 * Annotation used for configuring serialization aspects, by attaching to "getter" methods or fields, or to value
 * classes. When annotating value classes, configuration is used for instances of the value class but can be overridden
 * by more specific annotations (ones that attach to methods or fields).
 *
 * An example annotation would be:
 * ```
 * @CirJsonSerialize(using=MySerializer::class,
 *   valueAs=MySubClass::class,
 *   typing=CirJsonSerialize.Typing.STATIC
 * )
 * ```
 * (which would be redundant, since some properties block others: specifically, `using` has precedence over `valueAs`,
 * which has precedence over `typing` setting)
 *
 * @property using Serializer class to use for serializing associated value. Depending on what is annotated, value is
 * either an instance of annotated class (used globally anywhere where class serializer is needed); or only used for
 * serializing the value of the property annotated.
 *
 * @property contentUsing Serializer class to use for serializing contents (elements of a Collection/array, values of
 * Maps) of annotated property. Can only be used on accessors (methods, fields, constructors), to apply to values of
 * [Map]-valued properties; not applicable for value types used as Array elements or [Collection] and [Map] values.
 *
 * @property keyUsing Serializer class to use for deserializing Map keys of annotated property or Map keys of value type
 * so annotated. Can be used both on accessors (methods, fields, constructors), to apply to values of [Map]-valued
 * properties, and on "key" classes, to apply to use of annotated type as [Map] keys.
 *
 * @property nullsUsing Serializer class to use for serializing nulls for properties that are annotated, instead of the
 * default `null` serializer. Note that using this property when annotation types (classes) has no effect currently (it
 * is possible this could be improved in future).
 *
 * @property valueAs Supertype (of declared type, which itself is supertype of runtime type) to use as type when
 * locating serializer to use.
 *
 * Bogus type [Nothing] can be used to indicate that declared type is used as is (i.e. this annotation property has no
 * setting); this since annotation properties are not allowed to have null value.
 *
 * Note: if [using] is also used it has precedence (since it directly specifies serializer, whereas this would only be
 * used to locate the serializer) and value of this annotation property is ignored.
 *
 * @property keyAs Concrete type to serialize keys of [Map] as, instead of type otherwise declared. Must be a supertype
 * of declared type; otherwise an exception may be thrown by serializer.
 *
 * @property contentAs Concrete type to serialize content value (elements of a Collection/array, values of Maps) as,
 * instead of type otherwise declared. Must be a supertype of declared type; otherwise an exception may be thrown by
 * serializer.
 *
 * @property typing Whether type detection used is dynamic or static: that is, whether actual runtime type is used
 * (dynamic), or just the declared type (static).
 *
 * Note that the default is `DEFAULT_TYPING`, which is roughly same as saying "whatever". This is important as it allows
 * avoiding accidental overrides at property level.
 *
 * @property converter Which helper object is to be used to convert type into something that CirJackson knows how to
 * serialize; either because base type cannot be serialized easily, or just to alter serialization.
 *
 * @property contentConverter Similar to [converter], but used for values of structures types (List, arrays, Maps). Note
 * that this property does NOT have effect when used as Class annotation; it can only be used as property annotation:
 * this because association between container and value types is loose and as such converters seldom make sense for such
 * usage.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION,
        AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonSerialize(val using: KClass<out ValueSerializer> = ValueSerializer.None::class,
        val contentUsing: KClass<out ValueSerializer> = ValueSerializer.None::class,
        val keyUsing: KClass<out ValueSerializer> = ValueSerializer.None::class,
        val nullsUsing: KClass<out ValueSerializer> = ValueSerializer.None::class,
        val valueAs: KClass<*> = Nothing::class, val keyAs: KClass<*> = Nothing::class,
        val contentAs: KClass<*> = Nothing::class, val typing: Typing = Typing.DEFAULT_TYPING,
        val converter: KClass<out Converter<*, *>> = Converter.None::class,
        val contentConverter: KClass<out Converter<*, *>> = Converter.None::class) {

    /**
     * Enumeration used with [CirJsonSerialize.typing] property to define whether type detection is based on dynamic
     * runtime type ([DYNAMIC]) or declared type ([STATIC]).
     */
    enum class Typing {

        /**
         * Value that indicates that the actual dynamic runtime type is to be used.
         */
        DYNAMIC,

        /**
         * Value that indicates that the static declared type is to be used.
         */
        STATIC,

        /**
         * Pseudo-value that is used to indicate "use whatever is default used at higher level".
         */
        DEFAULT_TYPING

    }

}
