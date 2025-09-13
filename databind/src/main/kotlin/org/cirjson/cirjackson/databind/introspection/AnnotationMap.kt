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

    override operator fun <A : Annotation> get(kClass: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override fun has(annotationClass: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasOneOf(vararg annotationClasses: Array<KClass<out Annotation>>): Boolean {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")

    companion object {

        fun of(type: KClass<*>, value: Annotation): AnnotationMap {
            TODO("Not yet implemented")
        }

        fun of(rawAnnotations: Collection<Annotation>): AnnotationMap {
            TODO("Not yet implemented")
        }

    }

}