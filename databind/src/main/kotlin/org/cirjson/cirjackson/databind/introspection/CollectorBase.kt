package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

open class CollectorBase protected constructor(protected val myConfig: MapperConfig<*>?) {

    protected val myIntrospector = myConfig?.annotationIntrospector

    protected fun collectAnnotations(annotations: Array<Annotation>): AnnotationCollector {
        var collector = AnnotationCollector.EMPTY_COLLECTOR

        for (annotation in annotations) {
            collector = collector.addOrOverride(annotation)

            if (myIntrospector!!.isAnnotationBundle(annotation)) {
                collector = collectFromBundle(collector, annotation)
            }
        }

        return collector
    }

    protected fun collectAnnotations(collector: AnnotationCollector,
            annotations: Array<Annotation>): AnnotationCollector {
        var realCollector = collector

        for (annotation in annotations) {
            realCollector = realCollector.addOrOverride(annotation)

            if (myIntrospector!!.isAnnotationBundle(annotation)) {
                realCollector = collectFromBundle(realCollector, annotation)
            }
        }

        return realCollector
    }

    protected fun collectFromBundle(collector: AnnotationCollector, bundle: Annotation): AnnotationCollector {
        var realCollector = collector
        val annotations = findClassAnnotations(bundle.annotationClass)

        for (annotation in annotations) {
            if (ignorableAnnotation(annotation)) {
                continue
            }

            if (myIntrospector!!.isAnnotationBundle(annotation)) {
                if (!realCollector.isPresent(annotation)) {
                    realCollector = realCollector.addOrOverride(annotation)
                    realCollector = collectFromBundle(realCollector, annotation)
                }
            } else {
                realCollector = realCollector.addOrOverride(annotation)
            }
        }

        return realCollector
    }

    protected fun collectDefaultAnnotations(collector: AnnotationCollector,
            annotations: Array<Annotation>): AnnotationCollector {
        var realCollector = collector

        for (annotation in annotations) {
            if (realCollector.isPresent(annotation)) {
                continue
            }

            realCollector = realCollector.addOrOverride(annotation)

            if (myIntrospector!!.isAnnotationBundle(annotation)) {
                realCollector = collectDefaultFromBundle(realCollector, annotation)
            }
        }

        return realCollector
    }

    protected fun collectDefaultFromBundle(collector: AnnotationCollector, bundle: Annotation): AnnotationCollector {
        var realCollector = collector
        val annotations = findClassAnnotations(bundle.annotationClass)

        for (annotation in annotations) {
            if (ignorableAnnotation(annotation) || realCollector.isPresent(annotation)) {
                continue
            }

            realCollector = realCollector.addOrOverride(annotation)

            if (myIntrospector!!.isAnnotationBundle(annotation)) {
                realCollector = collectFromBundle(realCollector, annotation)
            }
        }

        return realCollector
    }

    companion object {

        val NO_ANNOTATION_MAPS = emptyArray<AnnotationMap>()

        val NO_ANNOTATIONS = emptyArray<Annotation>()

        private val CLASS_OBJECT = Any::class

        fun ignorableAnnotation(annotation: Annotation): Boolean {
            return annotation is Target || annotation is Retention
        }

        internal fun emptyAnnotationMap(): AnnotationMap {
            return AnnotationMap()
        }

        internal fun emptyAnnotationMaps(count: Int): Array<AnnotationMap> {
            if (count == 0) {
                return NO_ANNOTATION_MAPS
            }

            return Array(count) { emptyAnnotationMap() }
        }

        private fun findClassAnnotations(clazz: KClass<*>): Array<Annotation> {
            if (clazz == CLASS_OBJECT) {
                return NO_ANNOTATIONS
            }

            return clazz.annotations.toTypedArray()
        }

    }

}