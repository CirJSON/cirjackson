package org.cirjson.cirjackson.annotations

/**
 * Annotation used to indicate that a **POJO-valued** property should be serialized "unwrapped" -- that is, if it would
 * be serialized as Object value, its properties are instead included as properties of its containing Object -- and
 * deserialized reproducing "missing" structure.
 *
 * For example, consider case of POJO like:
 * ```
 * class Parent {
 *   val age: Int
 *   val name: Name
 * }
 *
 * class Name {
 *   val first: String
 *   val last: String
 * }
 * ```
 * which would normally be serialized as follows (assuming `@CirJsonUnwrapped` had no effect):
 * ```
 * {
 *   "__cirJsonId__": "0",
 *   "age" : 18,
 *   "name" : {
 *     "__cirJsonId__": "1",
 *     "first" : "Joey",
 *     "last" : "Sixpack"
 *   }
 * }
 * ```
 * can be changed to this:
 * ```
 * {
 *   "__cirJsonId__": "0",
 *   "age" : 18,
 *   "first" : "Joey",
 *   "last" : "Sixpack"
 * }
 * ```
 * by changing `Parent` class to:
 * ```
 * class Parent {
 *   val age: Int
 *   @CirJsonUnwrapped
 *   val name: Name
 * }
 * ```
 * Annotation can only be added to properties, and not classes, as it is contextual. When values are deserialized
 * "wrapping" is applied so that serialized output can be read back in.
 *
 * Also note that annotation only applies if:
 *
 * * Value is serialized as an Object value (can not unwrap Array values using this mechanism)
 *
 * * Reading/writing is done using Jackson standard `BeanDeserializer`/`BeanSerializer`; or custom
 * deserializer/serializer MUST explicitly support similar  operation.
 *
 * * Will not work with polymorphic type handling ("polymorphic deserialization")
 *
 * Specifically note that this annotation WILL NOT WORK for structured types like [Maps][Map] or `CirJsonNodes`: for
 * these types you will instead need to use [CirJsonAnyGetter] and [CirJsonAnySetter] to achieve similar operation.
 *
 * @property enabled Property that is usually only used when overriding (masking) annotations, using mix-in annotations.
 * Otherwise, default value of `true` is fine, and value need not be explicitly included.
 *
 * @property prefix Optional property that can be used to add prefix String to use in front of names of properties that
 * are unwrapped: this can be done for example to prevent name collisions.
 *
 * @property suffix Optional property that can be used to add suffix String to append at the end of names of properties
 * that are unwrapped: this can be done for example to prevent name collisions.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonUnwrapped(val enabled: Boolean = true, val prefix: String = "", val suffix: String = "")
