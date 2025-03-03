package org.cirjson.cirjackson.annotations

/**
 * Annotation used to indicate that associated property is part of two-way linkage between fields; and that its role is
 * "child" (or "back") link. Value type of the property must be a bean: it can not be a Collection, Map, Array or
 * enumeration. Linkage is handled such that the property annotated with this annotation is not serialized; and during
 * deserialization, its value is set to instance that has the "managed" (forward) link.
 *
 * All references have logical name to allow handling multiple linkages; typical case would be that where nodes have
 * both parent/child and sibling linkages. If so, pairs of references should be named differently. It is an error for a
 * class to have multiple back references with same name, even if types pointed are different.
 *
 * Note: only methods and fields can be annotated with this annotation: constructor arguments should NOT be annotated,
 * as they can not be either managed or back references.
 *
 * @property value Logical name for the reference property pair; used to link managed and back references. Default name
 * can be used if there is just single reference pair (for example, node class that just has parent/child linkage,
 * consisting of one managed reference and matching back reference)
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonBackReference(val value: String = "defaultReference")