package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Method
import kotlin.reflect.KClass

class AnnotatedMethod(context: TypeResolutionContext, private val myMethod: Method, annotations: AnnotationMap?,
        paramAnnotations: Array<AnnotationMap>?) : AnnotatedWithParams(context, annotations, paramAnnotations) {

    /*
     *******************************************************************************************************************
     * Life-cycle
     *******************************************************************************************************************
     */

    override val name: String
        get() = TODO("Not yet implemented")

    override val type: KotlinType
        get() = TODO("Not yet implemented")

    override val rawType: KClass<*>
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * AnnotatedWithParams
     *******************************************************************************************************************
     */

    override val parameterCount: Int
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