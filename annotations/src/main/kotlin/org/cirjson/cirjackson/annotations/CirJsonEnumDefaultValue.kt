package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that can be used to define a default value used when trying to deserialize unknown Enum values.
 *
 * This annotation is only applicable when the `DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` is
 * enabled.
 *
 * If the more than one enum value is marked with this annotation, the first one to be detected will be used. Which one
 * exactly is undetermined.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonEnumDefaultValue
