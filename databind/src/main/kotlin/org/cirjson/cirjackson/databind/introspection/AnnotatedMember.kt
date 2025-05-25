package org.cirjson.cirjackson.databind.introspection

import kotlin.reflect.KClass

abstract class AnnotatedMember protected constructor(protected val myTypeContext: TypeResolutionContext,
        protected val myAnnotations: AnnotationMap?) : Annotated() {

    override fun <A : Annotation> getAnnotation(kClass: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    abstract val rawType: KClass<*>

    @Throws(UnsupportedOperationException::class, IllegalArgumentException::class)
    abstract fun getValue(pojo: Any): Any?

}