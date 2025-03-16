package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that can be used on a property accessor (field, getter or setter, constructor parameter) to
 * indicate that the property is to contain type id to use when including polymorphic type information. Annotation
 * should **only be used** if the intent is to override generation of standard type id: if so, value of the property
 * will be accessed during serialization and used as the type id.
 *
 * On deserialization annotation has no effect, as visibility of type id is governed by value of
 * [CirJsonTypeInfo.visible]; properties with this annotation get no special handling.
 *
 * On serialization, this annotation will exclude property from being serialized along other properties; instead, its
 * value is serialized as the type identifier. Since type identifier may be included in various places, it may still
 * appear like 'normal' property (when using [CirJsonTypeInfo.As.PROPERTY]), but is more commonly embedded in a
 * different place, as per inclusion rules (see [CirJsonTypeInfo] for details).
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonTypeId
