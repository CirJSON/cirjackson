package org.cirjson.cirjackson.annotations

/**
 * Annotation that can be used to define one or more alternative names for a property, accepted during deserialization
 * as alternative to the official name. Alias information is also exposed during POJO introspection, but has no effect
 * during serialization where primary name is always used.
 *
 * Examples:
 *
 * ```kt
 * class Info {
 *   @CirJsonAlias(["n", "Name"])
 *   val name: String
 * }
 * ```
 *
 * NOTE: Order of alias declaration has no effect. All properties are assigned in the order they come from incoming
 * CirJSON document. If same property is assigned more than once with different value, later will remain. For example,
 * deserializing
 *
 * ```kt
 * public class Person {
 *   @CirJsonAlias(["name", "fullName"])
 *   val name: String
 * }
 * ```
 *
 * from
 *
 * ```
 * { "__cirJsonId__": "root", "fullName": "Faster CirJackson", "name": "CirJackson" }
 * ```
 *
 * will have value "CirJackson".
 *
 * Also, can be used with enums where incoming CirJSON properties may not match the defined enum values. For instance,
 * if you have an enum called `Size` with values `SMALL`, `MEDIUM`, and `LARGE`, you can use this annotation to define
 * alternate values for each enum value. This way, the deserialization process can map the incoming CirJSON values to
 * the correct enum values.
 *
 * Sample implementation:
 *
 * ```kt
 * enum class Size {
 *   @CirJsonAlias([ "small", "s", "S" ])
 *   SMALL,
 *
 *   @CirJsonAlias([ "medium", "m", "M" ])
 *   MEDIUM,
 *
 *   @CirJsonAlias([ "large", "l", "L" ])
 *   LARGE
 * }
 * ```
 *
 * During deserialization, any of these CirJSON structures will be valid and correctly mapped to the MEDIUM enum value:
 * {"size": "m"}, {"size": "medium"}, or {"size": "M"}.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonAlias(vararg val value: String)