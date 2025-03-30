package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import kotlin.reflect.KClass

/**
 * Annotation that can be used to indicate a [ValueInstantiator] to use for creating instances of specified type.
 *
 * @property value [ValueInstantiator] to use for annotated type
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonValueInstantiator(val value: KClass<out ValueInstantiator>)
