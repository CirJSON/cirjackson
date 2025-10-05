package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.hasClass
import java.lang.reflect.Member
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass

/**
 * Object that represents method parameters, mostly so that associated annotations can be processed conveniently. Note
 * that many of accessors cannot return meaningful values since parameters do not have standalone objects associated; so
 * access should mostly be limited to checking annotation values which are properly aggregated and included.
 *
 * @property owner Accessor for 'owner' of this parameter; method or constructor that has this parameter as member of
 * its argument list.
 *
 * @property index Accessor for index of this parameter within argument list
 */
class AnnotatedParameter(val owner: AnnotatedWithParams, override val type: KotlinType, context: TypeResolutionContext?,
        annotations: AnnotationMap?, val index: Int) : AnnotatedMember(context, annotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap?): AnnotatedParameter {
        if (fallback === myAnnotations) {
            return this
        }

        return owner.replaceParamAnnotations(index, fallback)
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    /**
     * Since there is no matching element, this accessor will always return `null`
     */
    override val annotated: KAnnotatedElement?
        get() = null

    /**
     * Returns modifiers of the owner, as parameters do not have independent modifiers.
     */
    override val modifiers: Int
        get() = owner.modifiers

    /**
     * Parameters have no names in bytecode (unlike in source code), will always return empty String (`""`).
     */
    override val name: String
        get() = ""

    override val rawType: KClass<*>
        get() = type.rawClass

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val declaringClass: KClass<*>
        get() = owner.declaringClass

    override val member: Member?
        get() = owner.member

    @Throws(UnsupportedOperationException::class)
    override fun setValue(pojo: Any, value: Any) {
        throw UnsupportedOperationException(
                "Cannot call setValue() on constructor parameter of ${declaringClass.qualifiedName}")
    }

    @Suppress("RedundantNullableReturnType")
    @Throws(IllegalArgumentException::class)
    override fun getValue(pojo: Any): Any? {
        throw UnsupportedOperationException(
                "Cannot call getValue() on constructor parameter of ${declaringClass.qualifiedName}")
    }

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    override fun hashCode(): Int {
        return owner.hashCode() + index
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other == null || !other.hasClass(this::class) || other !is AnnotatedParameter) {
            return false
        }

        return owner == other.owner && index == other.index
    }

    override fun toString(): String {
        return "[parameter #$index, annotations: $myAnnotations]"
    }

}