package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Member
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass

class AnnotatedParameter(val owner: AnnotatedWithParams, override val type: KotlinType, val index: Int,
        context: TypeResolutionContext, annotations: AnnotationMap?) : AnnotatedMember(context, annotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap): AnnotatedParameter {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KAnnotatedElement?
        get() = TODO("Not yet implemented")

    override val modifiers: Int
        get() = TODO("Not yet implemented")

    override val name: String
        get() = TODO("Not yet implemented")

    override val rawType: KClass<*>
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val declaringClass: KClass<*>
        get() = TODO("Not yet implemented")

    override val member: Member?
        get() = TODO("Not yet implemented")

    @Throws(IllegalArgumentException::class)
    override fun setValue(pojo: Any, value: Any) {
        TODO("Not yet implemented")
    }

    @Throws(IllegalArgumentException::class)
    override fun getValue(pojo: Any): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        TODO("Not yet implemented")
    }

}