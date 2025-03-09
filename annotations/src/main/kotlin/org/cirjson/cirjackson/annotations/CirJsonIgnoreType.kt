package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that indicates that all properties that have type annotated with this annotation are to be ignored
 * during serialization and deserialization.
 *
 * Note that this does NOT mean that properties included by annotated type are ignored. Given hypothetical types:
 * ```
 * @CirJsonIgnoreType
 * class Credentials(val password: String)
 *
 * class Settings(val userId: int, val name: String, val pwd: Credentials)
 * ```
 * serialization of `Settings` would only include properties "userId" and "name" but NOT "pwd", since it is of type
 * annotated with `@CirJsonIgnoreType`.
 *
 * Note: annotation does have boolean 'value' property (which defaults to `true`), so that it is actually possible to
 * override value using mix-in annotations. Usually value is not specified as it defaults to `true` meaning annotation
 * should take effect.
 *
 * @property value Optional argument that defines whether this annotation is active or not. The only use for value
 * `false` is for overriding purposes (which is not needed often); most likely it is needed for use with "mix-in
 * annotations" ("annotation overrides"). For most cases, however, default value of `true` is just fine and should be
 * omitted.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonIgnoreType(val value: Boolean = true)
