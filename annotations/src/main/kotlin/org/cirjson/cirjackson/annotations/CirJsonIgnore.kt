package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that indicates that the logical property that the accessor (field, getter/setter method or Creator
 * parameter [of [CirJsonCreator]-annotated constructor or factory method]) is to be ignored by introspection-based
 * serialization and deserialization functionality.
 *
 * Annotation only needs to be added to one of the accessors (often getter method, but may be setter, field or creator
 * parameter), if the complete removal of the property is desired. However: if only particular accessor is to be ignored
 * (for example, when ignoring one of potentially conflicting setter methods), this can be done by annotating other
 * not-to-be-ignored accessors with [CirJsonProperty] (or its equivalents). This is considered so-called "split
 * property" case and allows definitions of "read-only" (read from input into POJO) and "write-only" (write in output
 * but ignore on output)
 *
 * NOTE! There is a new and improved way to define `read-only` and `write-only` properties, using
 * [CirJsonProperty.access] annotation: this is recommended over use of separate `CirJsonIgnore` and [CirJsonProperty]
 * annotations.
 *
 * For example, a "getter" method that would otherwise denote a property (like, say, "getValue" to suggest property
 * "value") to serialize, would be ignored and no such property would be output unless another annotation defines
 * alternative method to use.
 *
 * When ignoring the whole property, the default behavior if encountering such property in input is to ignore it without
 * exception; but if there is a [CirJsonAnySetter] it will be called instead. Either way, no exception will be thrown.
 *
 * Annotation is usually used just a like a marker annotation, that is, without explicitly defining `value` argument
 * (which defaults to `true`): but argument can be explicitly defined. This can be done to override an existing
 * `CirJsonIgnore` by explicitly defining one with `false` argument: either in a subclass, or by using "mix-in
 * annotations".
 *
 * @property value Optional argument that defines whether this annotation is active or not. The only use for value
 * `false` if for overriding purposes (which is not needed often); most likely it is needed for use with "mix-in
 * annotations" (aka "annotation overrides"). For most cases, however, default value of `true` is just fine and should
 * be omitted.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonIgnore(val value: Boolean = true)
