package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.util.checkAndFixAccess
import java.lang.reflect.Member
import kotlin.reflect.KClass

/**
 * Intermediate base class for annotated entities that are members of a class; fields, methods and constructors. This is
 * a superset of things that can represent logical properties as it contains constructors in addition to fields and
 * methods.
 *
 * @property myTypeContext Context object needed for resolving generic type associated with this member (method
 * parameter or return value, or field type).
 */
abstract class AnnotatedMember protected constructor(protected val myTypeContext: TypeResolutionContext?,
        protected val myAnnotations: AnnotationMap?) : Annotated() {

    /**
     * Copy-constructor.
     */
    protected constructor(base: AnnotatedMember) : this(base.myTypeContext, base.myAnnotations)

    /**
     * Fluent factory method that will construct a new instance that uses specified instance annotations instead of
     * currently configured ones.
     */
    abstract fun withAnnotations(fallback: AnnotationMap): Annotated

    abstract val declaringClass: KClass<*>

    abstract val member: Member?

    open val fullName: String
        get() = "${declaringClass.qualifiedName}#$name"

    final override fun <A : Annotation> getAnnotation(kClass: KClass<A>): A? {
        return myAnnotations?.get(kClass)
    }

    final override fun hasAnnotation(kClass: KClass<*>): Boolean {
        return myAnnotations?.has(kClass) ?: false
    }

    override fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean {
        return myAnnotations?.hasOneOf(annotationClasses) ?: false
    }

    open val allAnnotations: AnnotationMap?
        get() = myAnnotations

    /**
     * Method that can be called to modify access rights, by calling [java.lang.reflect.AccessibleObject.setAccessible]
     * on the underlying annotated element.
     *
     * Note that caller should verify that [org.cirjson.cirjackson.databind.MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS]
     * is enabled before calling this method; as well as pass `force` flag appropriately.
     */
    fun fixAccess(force: Boolean) {
        member?.checkAndFixAccess(force)
    }

    /**
     * Optional method that can be used to assign value of this member on given object, if this is a supported operation
     * for member type.
     *
     * This is implemented for fields and single-argument member methods; but not for constructor parameters or other
     * types of methods (like static methods)
     */
    @Throws(UnsupportedOperationException::class, IllegalArgumentException::class)
    abstract fun setValue(pojo: Any, value: Any)

    /**
     * Optional method that can be used to access the value of this member on given object, if this is a supported
     * operation for member type.
     *
     * This is implemented for fields and no-argument member methods; but not for constructor parameters or other types
     * of methods (like static methods)
     */
    @Throws(UnsupportedOperationException::class, IllegalArgumentException::class)
    abstract fun getValue(pojo: Any): Any?

}