package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class AnnotatedMethod(context: TypeResolutionContext, private val myMethod: KFunction<*>, annotations: AnnotationMap?,
        paramAnnotations: Array<AnnotationMap>?) : AnnotatedWithParams(context, annotations, paramAnnotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override val annotated: KFunction<*>
        get() = TODO("Not yet implemented")

    override val modifiers: Int
        get() = TODO("Not yet implemented")

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

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

}