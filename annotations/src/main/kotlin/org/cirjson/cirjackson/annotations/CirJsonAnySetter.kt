package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that can be used to define a logical "any setter" mutator -- either using non-static two-argument
 * method (first argument name of property, second value to set) or a field (of type [Map] or POJO) - to be used as a
 * "fallback" handler for all otherwise unrecognized properties found from CirJSON content. It is similar to JAXB
 * `javax.xml.bind.annotation.XmlAnyElement` annotation in behavior; and can only be used to denote a single property
 * per type.
 *
 * If used, all otherwise unmapped key-value pairs from CirJSON Object values are added using mutator.
 *
 * @property isEnabled Optional argument that defines whether this annotation is active or not. The only use for value
 * `false` if for overriding purposes. Overriding may be necessary when used with "mix-in annotations" (aka "annotation
 * overrides"). For most cases, however, default value of `true` is just fine and should be omitted.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonAnySetter(val isEnabled: Boolean = true)
