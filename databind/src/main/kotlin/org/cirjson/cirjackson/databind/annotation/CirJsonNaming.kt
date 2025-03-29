package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.databind.PropertyNamingStrategy
import kotlin.reflect.KClass

/**
 * Annotation that can be used to indicate a [PropertyNamingStrategy] to use for annotated class. Overrides the global
 * (default) strategy. Note that if the [value] property is omitted, its default value means "use default naming" (that
 * is, no alternate naming method is used). This can be used as an override with mix-ins.
 *
 * @property value Type of [PropertyNamingStrategy] to use, if any; default value of `PropertyNamingStrategy::class`
 * means "no strategy specified" (and may also be used for overriding to remove otherwise applicable naming strategy)
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonNaming(val value: KClass<out PropertyNamingStrategy> = PropertyNamingStrategy::class)
