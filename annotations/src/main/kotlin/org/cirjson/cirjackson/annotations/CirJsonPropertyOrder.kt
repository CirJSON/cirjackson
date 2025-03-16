package org.cirjson.cirjackson.annotations

/**
 * Annotation that can be used to define ordering (possibly partial) to use when serializing object properties.
 * Properties included in annotation declaration will be serialized first (in defined order), followed by any properties
 * not included in the definition. Annotation definition will override any implicit orderings (such as guarantee that
 * Creator-properties are serialized before non-creator properties)
 *
 * Examples:
 * ```
 * // ensure that "id" and "name" are output before other properties
 * @CirJsonPropertyOrder([ "id", "name" ])
 * // order any properties that don't have explicit setting using alphabetic order
 * @CirJsonPropertyOrder(alphabetic=true)
 * ```
 *
 * This annotation may or may not have effect on deserialization: for basic CirJSON handling there is no effect, but for
 * other supported data types (or structural conventions) there may be.
 *
 * NOTE: annotation is allowed for properties mostly to support alphabetic ordering of [Map] entries.
 *
 * @property value Order in which properties of annotated object are to be serialized in.
 *
 * @property alphabetic Property that defines what to do regarding ordering of properties not explicitly included in
 * annotation instance. If `true`, they will be alphabetically ordered; if `false`, order is undefined (default setting)
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonPropertyOrder(val value: Array<String> = [], val alphabetic: Boolean = false)
