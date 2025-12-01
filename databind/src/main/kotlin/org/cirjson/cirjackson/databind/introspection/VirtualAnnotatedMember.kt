package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.hasClass
import java.lang.reflect.Member
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass

/**
 * Placeholder used by virtual properties as placeholder for underlying [AnnotatedMember].
 */
open class VirtualAnnotatedMember(typeContext: TypeResolutionContext?, protected val myDeclaringClass: KClass<*>,
        protected val myName: String, protected val myType: KotlinType) : AnnotatedMember(typeContext, null) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap?): Annotated {
        return this
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KAnnotatedElement?
        get() = null

    override val modifiers: Int
        get() = 0

    override val name: String
        get() = myName

    override val rawType: KClass<*>
        get() = myType.rawClass

    override val type: KotlinType
        get() = myType

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val declaringClass: KClass<*>
        get() = myDeclaringClass

    override val member: Member?
        get() = null

    @Throws(IllegalArgumentException::class)
    override fun setValue(pojo: Any, value: Any) {
        throw IllegalArgumentException("Cannot set virtual property '$myName'")
    }

    @Throws(IllegalArgumentException::class)
    override fun getValue(pojo: Any): Any? {
        throw IllegalArgumentException("Cannot get virtual property '$myName'")
    }

    /*
     *******************************************************************************************************************
     * Extended API, generic
     *******************************************************************************************************************
     */

    override fun hashCode(): Int {
        return myName.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (!other.hasClass(this::class) || other !is VirtualAnnotatedMember) {
            return false
        }

        return myDeclaringClass == other.myDeclaringClass && myName == other.myName
    }

    override fun toString(): String {
        return "[virtual $fullName]"
    }

}