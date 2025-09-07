package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Member
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class AnnotatedField(context: TypeResolutionContext, private val myField: KProperty<*>, annotations: AnnotationMap?) :
        AnnotatedMember(context, annotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap): AnnotatedField {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KProperty<*>
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