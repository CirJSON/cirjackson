package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.EnumNamingStrategy
import kotlin.reflect.KClass

/**
 * Annotation that can be used to indicate a [EnumNamingStrategy] to use for annotated class.
 *
 * @property value Type of [EnumNamingStrategy] to use, if any. Default value of `EnumNamingStrategy::class` means "no
 * strategy specified" (and may also be used for overriding to remove otherwise applicable naming strategy)
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class EnumNaming(val value: KClass<out EnumNamingStrategy>)
