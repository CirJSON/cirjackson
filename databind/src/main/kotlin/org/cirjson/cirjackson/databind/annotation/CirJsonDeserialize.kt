package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.util.Converter
import kotlin.reflect.KClass

/**
 * Annotation use for configuring deserialization aspects, by attaching to "setter" methods or fields, or to value
 * classes. When annotating value classes, configuration is used for instances of the value class but can be overridden
 * by more specific annotations (ones that attach to methods or fields).
 *
 * An example annotation would be:
 * ```
 * @CirJsonDeserialize(
 *   using=MySerializer::class,
 *   as=MyHashMap::class,
 *   keyAs=MyHashKey::class,
 *   contentAs=MyHashValue::class
 * )
 * ```
 *
 * Something to note on usage:
 *
 * * All other annotations regarding behavior during building should be on **Builder** class and NOT on target POJO
 * class: for example `@CirJsonIgnoreProperties` should be on Builder to prevent "unknown property" errors.
 *
 * * Similarly configuration overrides (see
 * [org.cirjson.cirjackson.databind.configuration.MapperBuilder.withConfigOverride]) should be targeted at Builder
 * class, not target POJO class.
 *
 * @property using Deserializer class to use for deserializing associated value. Depending on what is annotated, value
 * is either an instance of annotated class (used globally anywhere where class deserializer is needed); or only used
 * for deserializing the value of the property annotated.
 *
 * @property contentUsing Deserializer class to use for deserializing contents (elements of a Collection/array, values
 * of Maps) of annotated property. Can only be used on accessors (methods, fields, constructors), to apply to values of
 * [Map]-valued properties; not applicable for value types used as Array elements or [Collection] and [Map] values.
 *
 * @property keyUsing Deserializer class to use for deserializing Map keys of annotated property or Map keys of value
 * type so annotated. Can be used both on accessors (methods, fields, constructors), to apply to values of [Map]-valued
 * properties, and on "key" classes, to apply to use of annotated type as [Map] keys.
 *
 * @property builder Annotation for specifying if an external Builder class is to be used for building up deserialized
 * instances of annotated class. If so, an instance of referenced class is first constructed (possibly using a Creator
 * method; or if none defined, using default constructor), and its "with-methods" are used for populating fields; and
 * finally "build-method" is invoked to complete deserialization.
 *
 * @property converter Which helper object (if any) is to be used to convert from CirJackson-bound intermediate type
 * (source type of converter) into actual property type (which must be same as result type of converter). This is often
 * used for two-step deserialization; CirJackson binds data into suitable intermediate type (like Tree representation),
 * and converter then builds actual property type.
 *
 * @property contentConverter Similar to [converter], but used for values of structures types (List, arrays, Maps).
 *
 * @property valueAs Concrete type to deserialize values as, instead of type otherwise declared. Must be a subtype of
 * declared type; otherwise an exception may be thrown by deserializer.
 *
 * Bogus type [Nothing] can be used to indicate that declared type is used as is (i.e. this annotation property has no
 * setting); this since annotation properties are not allowed to have null value.
 *
 * Note: if [using] is also used it has precedence (since it directly specified deserializer, whereas this would only be
 * used to locate the deserializer) and value of this annotation property is ignored.
 *
 * @property keyAs Concrete type to deserialize keys of [Map] as, instead of type otherwise declared. Must be a subtype
 * of declared type; otherwise an exception may be thrown by deserializer.
 *
 * @property contentAs Concrete type to deserialize content (elements of a Collection/array, values of Maps) values as,
 * instead of type otherwise declared. Must be a subtype of declared type; otherwise an exception may be thrown by
 * deserializer.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION,
        AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonDeserialize(val using: KClass<out ValueDeserializer> = ValueDeserializer.None::class,
        val contentUsing: KClass<out ValueDeserializer> = ValueDeserializer.None::class,
        val keyUsing: KClass<out KeyDeserializer> = KeyDeserializer.None::class,
        val builder: KClass<*> = Nothing::class, val converter: KClass<out Converter> = Converter.None::class,
        val contentConverter: KClass<out Converter> = Converter.None::class, val valueAs: KClass<*> = Nothing::class,
        val keyAs: KClass<*> = Nothing::class, val contentAs: KClass<*> = Nothing::class)
