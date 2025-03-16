package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Annotation used for indicating view(s) that the property that is defined by method or field annotated is part of.
 *
 * An example annotation would be:
 * ```
 * @CirJsonView(BasicView::class)
 * ```
 * which would specify that property annotated would be included when processing (serializing, deserializing) View
 * identified by `BasicView::class` (or its subclass). If multiple View class identifiers are included, property will be
 * part of all of them.
 *
 * It is also possible to use this annotation on POJO classes to indicate the default view(s) for properties of the
 * type, unless overridden by per-property annotation.
 *
 * @property value View or views that annotated element is part of. Views are identified by classes, and use expected
 * class inheritance relationship: child views contain all elements parent views have, for example.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonView(val value: Array<KClass<*>>)
