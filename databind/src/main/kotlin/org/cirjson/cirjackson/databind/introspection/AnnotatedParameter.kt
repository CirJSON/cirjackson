package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

class AnnotatedParameter(val owner: AnnotatedWithParams, override val type: KotlinType, val index: Int,
        context: TypeResolutionContext, annotations: AnnotationMap?) : AnnotatedMember(context, annotations) {

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val rawType: KClass<*>
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    @Throws(IllegalArgumentException::class)
    override fun getValue(pojo: Any): Any? {
        TODO("Not yet implemented")
    }

}