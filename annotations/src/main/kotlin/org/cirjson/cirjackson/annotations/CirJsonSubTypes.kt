package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Annotation used with [CirJsonTypeInfo] to indicate subtypes of serializable polymorphic types, and to associate
 * logical names used within CirJSON content (which is more portable than using physical class names).
 *
 * Note that just annotating a property or base type with this annotation does NOT enable polymorphic type handling: in
 * addition, [CirJsonTypeInfo] or equivalent (such as enabling of so-called "default typing") annotation is needed, and
 * only in such case is subtype information used.
 *
 * @property value Subtypes of the annotated type (annotated class, or property value type associated with the annotated
 * method). These will be checked recursively so that types can be defined by only including direct subtypes.
 *
 * @property failOnRepeatedNames Subtypes of the annotated type may have logical type name and names properties. When
 * set to `true`, logical type name and names are going to be checked for repeated values. Repeated values are
 * considered a definition violation during that check.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonSubTypes(val value: Array<Type>, val failOnRepeatedNames: Boolean = false) {

    /**
     * Definition of a subtype, along with optional name(s). If no name is defined (empty Strings are ignored), class of
     * the type will be checked for [CirJsonTypeName] annotation; and if that is also missing or empty, a default name
     * will be constructed by type id mechanism. Default name is usually based on class name.
     *
     * @property value Class of the subtype.
     *
     * @property name Logical type name used as the type identifier for the class, if defined; empty String means "not
     * defined". Used unless [names] is defined as non-empty.
     *
     * @property names (optional) Logical type names used as the type identifier for the class: used if more than one
     * type name should be associated with the same type.
     */
    annotation class Type(val value: KClass<*>, val name: String = "", val names: Array<String> = [])

}
