package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Member
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass

open class VirtualAnnotatedMember(typeContext: TypeResolutionContext?, protected val myDeclaringClass: KClass<*>,
        protected val myName: String, protected val myType: KotlinType) : AnnotatedMember(typeContext, null) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap?): Annotated {
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

    override val type: KotlinType
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

    override fun setValue(pojo: Any, value: Any) {
        TODO("Not yet implemented")
    }

    override fun getValue(pojo: Any): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API, generic
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