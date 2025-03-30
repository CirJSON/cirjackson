package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import kotlin.reflect.KClass

/**
 * Annotation that can be used to plug a custom type identifier handler ([TypeIdResolver]) to be used by
 * [TypeSerializers][org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer] and
 * [TypeDeserializers][org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer] for converting between types and
 * type id included in CirJSON content. In simplest cases this can be a simple class with static mapping between type
 * names and matching classes.
 *
 * @property value Defines implementation class of [TypeIdResolver] to use for converting between external type id (type
 * name) and actual type of object.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION,
        AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonTypeIdResolver(val value: KClass<out TypeIdResolver>)
