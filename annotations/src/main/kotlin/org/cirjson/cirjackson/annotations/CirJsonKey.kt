package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that indicates that the value of annotated accessor (either field or "getter" method [a method with
 * non-void return type, no args]) is to be used as the single value to serialize for the instance. This value will be
 * used only if the instance is being serialized as a key in a Map type. Usually value will be of a simple scalar type
 * (`String` or `Number`), but it can be any serializable type (`Collection`, `Map` or `Bean`).
 *
 * At most one accessor of a `Class` can be annotated with this annotation; if more than one is found, an exception may
 * be thrown. Also, if method signature of annotated method is not compatible with Getters, an exception may be thrown
 * (whether exception is thrown or not is an implementation detail (due to filtering during introspection, some
 * annotations may be skipped) and applications should not rely on specific behavior).
 *
 * A typical usage is that of annotating `toString()` method so that returned String value is used as the CirJSON
 * serialization; and if deserialization is needed, there is matching constructor or factory method annotated with
 * [CirJsonCreator] annotation.
 *
 * Boolean argument is only used so that subclasses can "disable" annotation if necessary.
 *
 * @see CirJsonValue
 *
 * @property value Optional argument that defines whether this annotation is active or not. The only use for value
 * `false` if for overriding purposes. Overriding may be necessary when used with "mix-in annotations" (aka "annotation
 * overrides"). For most cases, however, default value of `true` is just fine and should be omitted.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonKey(val value: Boolean = true)
