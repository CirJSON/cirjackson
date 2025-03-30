package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverBuilder
import kotlin.reflect.KClass

/**
 * Annotation that can be used to explicitly define custom resolver used for handling serialization and deserialization
 * of type information, needed for handling of polymorphic types (or sometimes just for linking abstract types to
 * concrete types)
 *
 * @property value Defines implementation class of [TypeResolverBuilder] which is used to construct actual
 * [org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer] and
 * [org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer] instances that handle reading and writing addition
 * type information needed to support polymorphic deserialization.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION,
        AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonTypeResolver(val value: KClass<out TypeResolverBuilder<*>>)
