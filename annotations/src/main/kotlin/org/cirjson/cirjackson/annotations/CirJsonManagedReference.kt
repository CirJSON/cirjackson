package org.cirjson.cirjackson.annotations

/**
 * Annotation used to indicate that annotated property is part of two-way linkage between fields; and that its role is
 * "parent" (or "forward") link. Value type (class) of property must have a single compatible property annotated with
 * [CirJsonBackReference]. Linkage is handled such that the property annotated with this annotation is handled normally
 * (serialized normally, no special handling for deserialization); it is the matching back reference that requires
 * special handling
 *
 * All references have logical name to allow handling multiple linkages; typical case would be that where nodes have
 * both parent/child and sibling linkages. If so, pairs of references should be named differently. It is an error for a
 * class to have multiple managed references with same name, even if types pointed are different.
 *
 * Note: only methods and fields can be annotated with this annotation: constructor arguments should NOT be annotated,
 * as they can not be either managed or back references.
 *
 * @property value Logical have for the reference property pair; used to link managed and back references. Default name
 * can be used if there is just single reference pair (for example, node class that just has parent/child linkage,
 * consisting of one managed reference and matching back reference)
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonManagedReference(val value: String = "defaultReference")
