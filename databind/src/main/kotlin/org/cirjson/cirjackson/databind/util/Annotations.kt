package org.cirjson.cirjackson.databind.util

import kotlin.reflect.KClass

/**
 * Interface that defines interface for accessing the contents of an annotations' collection. This is needed when
 * introspecting annotation-based features from different kinds of things, not just objects that Reflection interface
 * exposes.
 *
 * Standard mutable implementation is [org.cirjson.cirjackson.databind.introspection.AnnotationMap]
 */
interface Annotations {

    /**
     * Main access method used to find value for given annotation.
     */
    operator fun <A : Annotation> get(kClass: KClass<A>): A?

    fun has(annotationClass: KClass<*>): Boolean

    fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean

    /**
     * Returns number of annotation entries in this collection.
     */
    val size: Int

}