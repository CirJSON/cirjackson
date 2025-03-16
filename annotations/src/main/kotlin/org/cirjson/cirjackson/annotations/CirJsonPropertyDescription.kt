package org.cirjson.cirjackson.annotations

/**
 * Annotation used to define a human-readable description for a logical property. Currently used to populate the
 * description field in generated CirJSON Schemas.
 *
 * @property value Defines a human-readable description of the logical property. Currently used to populate the
 * description field in generated CirJSON Schemas.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@CirJacksonAnnotation
annotation class CirJsonPropertyDescription(val value: String = "")
