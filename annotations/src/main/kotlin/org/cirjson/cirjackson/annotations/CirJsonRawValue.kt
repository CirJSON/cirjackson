package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that indicates that the annotated method or field should be serialized by including literal String
 * value of the property as is, without quoting of characters. This can be useful for injecting values already
 * serialized in CirJSON or passing JavaScript function definitions from server to a JavaScript client.
 *
 * Warning: the resulting CirJSON stream may be invalid depending on your input value.
 *
 * @property value Optional argument that defines whether this annotation is active or not. The only use for value
 * `false` if for overriding purposes (which is not needed often); most likely it is needed for use with "mix-in
 * annotations" (aka "annotation overrides"). For most cases, however, default value of `true` is just fine and should
 * be omitted.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonRawValue(val value: Boolean = false)
