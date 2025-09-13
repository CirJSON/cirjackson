package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass

/**
 * Helper class used to collect annotations to be stored as [Annotations] (like [AnnotationMap]).
 *
 * @property myData Optional data to carry along
 */
abstract class AnnotationCollector protected constructor(protected val myData: Any?) {

    abstract fun asAnnotations(): Annotations

    abstract fun asAnnotationMap(): AnnotationMap

    open val data: Any?
        get() = myData

    /*
     *******************************************************************************************************************
     * API
     *******************************************************************************************************************
     */

    abstract fun isPresent(annotation: Annotation): Boolean

    abstract fun addOrOverride(annotation: Annotation): AnnotationCollector

    /*
     *******************************************************************************************************************
     * Collector implementations
     *******************************************************************************************************************
     */

    private class EmptyCollector(data: Any?) : AnnotationCollector(data) {

        override fun asAnnotations(): Annotations {
            return EMPTY_ANNOTATIONS
        }

        override fun asAnnotationMap(): AnnotationMap {
            return AnnotationMap()
        }

        override fun isPresent(annotation: Annotation): Boolean {
            return false
        }

        override fun addOrOverride(annotation: Annotation): AnnotationCollector {
            return OneCollector(myData, annotation.annotationClass, annotation)
        }

        companion object {

            val INSTANCE = EmptyCollector(null)

        }

    }

    private class OneCollector(data: Any?, private val myType: KClass<*>, private var myValue: Annotation) :
            AnnotationCollector(data) {

        override fun asAnnotations(): Annotations {
            return OneAnnotation(myType, myValue)
        }

        override fun asAnnotationMap(): AnnotationMap {
            return AnnotationMap.of(myType, myValue)
        }

        override fun isPresent(annotation: Annotation): Boolean {
            return annotation.annotationClass == myType
        }

        override fun addOrOverride(annotation: Annotation): AnnotationCollector {
            val type = annotation.annotationClass

            if (myType == type) {
                myValue = annotation
                return this
            }

            return NCollector(myData, myType, myValue, type, annotation)
        }

    }

    private class NCollector(data: Any?, type1: KClass<*>, value1: Annotation, type2: KClass<*>, value2: Annotation) :
            AnnotationCollector(data) {

        private val myAnnotations = hashMapOf(type1 to value1, type2 to value2)

        override fun asAnnotations(): Annotations {
            if (myAnnotations.size == 2) {
                val iterator = myAnnotations.entries.iterator()
                val entry1 = iterator.next()
                val entry2 = iterator.next()
                return TwoAnnotations(entry1.key, entry1.value, entry2.key, entry2.value)
            }

            return AnnotationMap(myAnnotations)
        }

        override fun asAnnotationMap(): AnnotationMap {
            return AnnotationMap.of(myAnnotations.values)
        }

        override fun isPresent(annotation: Annotation): Boolean {
            return annotation.annotationClass in myAnnotations
        }

        override fun addOrOverride(annotation: Annotation): AnnotationCollector {
            myAnnotations[annotation.annotationClass] = annotation
            return this
        }

    }

    /*
     *******************************************************************************************************************
     * Annotations implementations
     *******************************************************************************************************************
     */

    /**
     * Immutable implementation for case where no annotations are associated with an annotatable entity.
     */
    class NoAnnotations : Annotations {

        override operator fun <A : Annotation> get(kClass: KClass<A>): A? {
            return null
        }

        override fun has(annotationClass: KClass<*>): Boolean {
            return false
        }

        override fun hasOneOf(vararg annotationClasses: Array<KClass<out Annotation>>): Boolean {
            return false
        }

        override val size: Int
            get() = 0

    }

    class OneAnnotation(private val myType: KClass<*>, private val myValue: Annotation) : Annotations {

        @Suppress("UNCHECKED_CAST")
        override operator fun <A : Annotation> get(kClass: KClass<A>): A? {
            if (myType == kClass) {
                return myValue as A
            }

            return null
        }

        override fun has(annotationClass: KClass<*>): Boolean {
            return myType == annotationClass
        }

        override fun hasOneOf(vararg annotationClasses: Array<KClass<out Annotation>>): Boolean {
            for (annotationClass in annotationClasses) {
                if (myType == annotationClass) {
                    return true
                }
            }

            return false
        }

        override val size: Int
            get() = 1

    }

    class TwoAnnotations(private val myType1: KClass<*>, private val myValue1: Annotation,
            private val myType2: KClass<*>, private val myValue2: Annotation) : Annotations {

        @Suppress("UNCHECKED_CAST")
        override operator fun <A : Annotation> get(kClass: KClass<A>): A? {
            if (myType1 == kClass) {
                return myValue1 as A
            }

            if (myType2 == kClass) {
                return myValue2 as A
            }

            return null
        }

        override fun has(annotationClass: KClass<*>): Boolean {
            return myType1 == annotationClass || myType2 == annotationClass
        }

        override fun hasOneOf(vararg annotationClasses: Array<KClass<out Annotation>>): Boolean {
            for (annotationClass in annotationClasses) {
                if (myType1 == annotationClass || myType2 == annotationClass) {
                    return true
                }
            }

            return false
        }

        override val size: Int
            get() = 2

    }

    /*
     *******************************************************************************************************************
     * Companion
     *******************************************************************************************************************
     */

    companion object {

        val EMPTY_ANNOTATIONS = NoAnnotations()

        val EMPTY_COLLECTOR: AnnotationCollector = EmptyCollector.INSTANCE

        fun emptyCollector(data: Any?): AnnotationCollector {
            return EmptyCollector(data)
        }

    }

}