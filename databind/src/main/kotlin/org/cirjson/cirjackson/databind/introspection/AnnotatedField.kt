package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.hasClass
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

/**
 * Object that represents non-static (and usually non-transient/volatile) fields of a class.
 *
 * @property myField Actual [KProperty] used for access.
 */
class AnnotatedField(context: TypeResolutionContext, private val myField: KProperty<*>, annotations: AnnotationMap?) :
        AnnotatedMember(context, annotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap?): AnnotatedField {
        return AnnotatedField(myTypeContext!!, myField, fallback)
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KProperty<*>
        get() = myField

    override val modifiers: Int
        get() = myField.javaField!!.modifiers

    override val name: String
        get() = myField.name

    override val rawType: KClass<*>
        get() = myField.javaField!!.type.kotlin

    override val type: KotlinType
        get() = myTypeContext!!.resolveType(myField.returnType)

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val declaringClass: KClass<*>
        get() = myField.javaField!!.declaringClass.kotlin

    override val member: Member
        get() = myField.javaField!!

    @Throws(IllegalArgumentException::class)
    override fun setValue(pojo: Any, value: Any) {
        try {
            myField.javaField!!.set(pojo, value)
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Failed to setValue() for field $fullName: ${e.message}", e)
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getValue(pojo: Any): Any? {
        try {
            return myField.javaField!!.get(pojo)
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Failed to getValue() for field $fullName: ${e.message}", e)
        }
    }

    /*
     *******************************************************************************************************************
     * Extended API, generic
     *******************************************************************************************************************
     */

    val annotationCount: Int
        get() = myAnnotations!!.size

    val isTransient: Boolean
        get() = Modifier.isTransient(modifiers)

    override fun hashCode(): Int {
        return myField.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other == null || !other.hasClass(this::class) || other !is AnnotatedField) {
            return false
        }

        return myField == other.myField
    }

    override fun toString(): String {
        return "[field $fullName]"
    }

}