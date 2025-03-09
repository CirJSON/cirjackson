package org.cirjson.cirjackson.annotations

/**
 * Optional annotation that can be used for customizing details of a reference to Objects for which "Object Identity" is
 * enabled (see [CirJsonIdentityInfo]). The main use case is that of enforcing use of Object ID even for the first time
 * an Object is referenced, instead of first instance being serialized as full POJO.
 *
 * @property alwaysAsID Marker to indicate whether all referenced values are to be serialized as IDs (`true`); or by
 * serializing the first encountered reference as POJO and only then as ID (`false`).
 *
 * Note that if value of `true` is used, deserialization may require additional contextual information, and possibly
 * using a custom ID resolver -- the default handling may not be sufficient.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonIdentityReference(val alwaysAsID: Boolean = false)
