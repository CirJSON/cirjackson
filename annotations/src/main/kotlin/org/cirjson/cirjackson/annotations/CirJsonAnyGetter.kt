package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that can be used to define a non-static, no-argument method to be an "any getter"; accessor for
 * getting a set of key/value pairs, to be serialized as part of containing POJO (similar to unwrapping) along with
 * regular property values it has. This typically serves as a counterpart to "any setter" mutators (see
 * [CirJsonAnySetter]). Note that the return type of annotated methods **must** be [Map]).
 *
 * As with [CirJsonAnySetter], only one property should be annotated with this annotation; if multiple methods are
 * annotated, an exception may be thrown.
 *
 * @property isEnabled Optional argument that defines whether this annotation is active or not. The only use for value
 * 'false' if for overriding purposes. Overriding may be necessary when used with "mix-in annotations" (aka "annotation
 * overrides"). For most cases, however, default value of "true" is just fine and should be omitted.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonAnyGetter(val isEnabled: Boolean = true)
