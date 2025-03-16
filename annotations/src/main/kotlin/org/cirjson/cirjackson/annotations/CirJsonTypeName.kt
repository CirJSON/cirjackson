package org.cirjson.cirjackson.annotations

/**
 * Annotation used for binding logical name that the annotated class has. Used with [CirJsonTypeInfo] (and specifically
 * its [CirJsonTypeInfo.use] property) to establish relationship between type names and types.
 *
 * @property value Logical type name for annotated type. If missing (or defined as empty String), defaults to using
 * nonqualified class name as the type.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonTypeName(val value: String = "")
