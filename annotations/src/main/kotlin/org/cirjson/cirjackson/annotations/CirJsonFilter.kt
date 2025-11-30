package org.cirjson.cirjackson.annotations

/**
 * Annotation used to indicate which logical filter is to be used for filtering out properties of type (class)
 * annotated; association made by this annotation declaring ids of filters, and
 * `org.cirjson.cirjackson.databind.ObjectMapper` (or objects it delegates to) providing matching filters by id.
 *
 * Filters to use are usually of type `org.cirjson.cirjackson.databind.serialization.PropertyFilter` and are registered
 * through `org.cirjson.cirjackson.databind.ObjectMapper`
 *
 * @property value ID of filter to use; if empty String (`""`), no filter is to be used.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonFilter(val value: String)
