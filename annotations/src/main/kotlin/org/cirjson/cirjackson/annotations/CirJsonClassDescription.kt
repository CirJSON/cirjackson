package org.cirjson.cirjackson.annotations

/**
 * Annotation used to define a human-readable description for annotated type (class). Currently used to populate the
 * description field in generated CirJSON Schemas.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@CirJacksonAnnotation
annotation class CirJsonClassDescription(val value: String = "")
