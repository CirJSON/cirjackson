package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass

class AnnotationMap(private val myAnnotations: Map<KClass<*>, Annotation>) : Annotations {

    /*
     *******************************************************************************************************************
     * Annotations implementation
     *******************************************************************************************************************
     */

    override operator fun <A : Annotation> get(kClass: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override fun has(kClass: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasOneOf(vararg kClass: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")

}