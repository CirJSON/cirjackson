package org.cirjson.cirjackson.databind.introspection

import kotlin.reflect.KClass

abstract class AnnotatedMember protected constructor(protected val myTypeContext: TypeResolutionContext,
        protected val myAnnotations: AnnotationMap?) : Annotated() {

    final override fun <A : Annotation> getAnnotation(kClass: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    final override fun hasAnnotation(kClass: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean {
        TODO("Not yet implemented")
    }

    @Throws(UnsupportedOperationException::class, IllegalArgumentException::class)
    abstract fun getValue(pojo: Any): Any?

}