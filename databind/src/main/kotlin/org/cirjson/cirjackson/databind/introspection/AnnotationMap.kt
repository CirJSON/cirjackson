package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass

class AnnotationMap(private val myAnnotations: Map<KClass<*>, Annotation>?) : Annotations {

    constructor() : this(null)

    /*
     *******************************************************************************************************************
     * Annotations implementation
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override operator fun <A : Annotation> get(kClass: KClass<A>): A? {
        myAnnotations ?: return null
        return myAnnotations[kClass] as A
    }

    override fun has(annotationClass: KClass<*>): Boolean {
        myAnnotations ?: return false
        return annotationClass in myAnnotations.keys
    }

    /**
     * Helper method that can be used for a "bulk" check to see if at least one of given annotation types is included
     * within this map.
     */
    override fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean {
        myAnnotations ?: return false

        for (annotationClass in annotationClasses) {
            if (annotationClass in myAnnotations.keys) {
                return true
            }
        }

        return false
    }

    /*
     *******************************************************************************************************************
     * Other API
     *******************************************************************************************************************
     */

    fun annotations(): Iterable<Annotation> {
        if (myAnnotations.isNullOrEmpty()) {
            return emptyList()
        }

        return myAnnotations.values
    }

    override val size: Int
        get() = myAnnotations?.size ?: 0

    override fun toString(): String {
        myAnnotations ?: return "[null]"
        return myAnnotations.toString()
    }

    companion object {

        fun of(type: KClass<*>, value: Annotation): AnnotationMap {
            val annotations = HashMap<KClass<*>, Annotation>(4)
            annotations[type] = value
            return AnnotationMap(annotations)
        }

        fun of(rawAnnotations: Collection<Annotation>): AnnotationMap {
            val annotations = HashMap<KClass<*>, Annotation>(rawAnnotations.size)

            for (annotation in rawAnnotations) {
                annotations[annotation.annotationClass] = annotation
            }

            return AnnotationMap(annotations)
        }

        fun merge(primary: AnnotationMap?, secondary: AnnotationMap?): AnnotationMap? {
            if (primary == null || primary.myAnnotations.isNullOrEmpty()) {
                return secondary
            }

            if (secondary == null || secondary.myAnnotations.isNullOrEmpty()) {
                return primary
            }

            val annotations = HashMap<KClass<*>, Annotation>()

            for (annotation in secondary.myAnnotations.values) {
                annotations[annotation.annotationClass] = annotation
            }

            for (annotation in primary.myAnnotations.values) {
                annotations[annotation.annotationClass] = annotation
            }

            return AnnotationMap(annotations)
        }

    }

}