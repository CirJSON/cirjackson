package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Modifier
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass

/**
 * Shared base class used for anything on which annotations (included within an [AnnotationMap]).
 */
abstract class Annotated protected constructor() {

    abstract fun <A : Annotation> getAnnotation(kClass: KClass<A>): A?

    abstract fun hasAnnotation(kClass: KClass<*>): Boolean

    abstract fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean

    /**
     * Method that can be used to find actual JDK element that this instance represents. It is non-`null`, except for
     * method/constructor parameters which do not have a JDK counterpart.
     */
    abstract val annotated: KAnnotatedElement?

    abstract val modifiers: Int

    open val isPublic: Boolean
        get() = Modifier.isPublic(modifiers)

    open val isStatic: Boolean
        get() = Modifier.isStatic(modifiers)

    abstract val name: String

    /**
     * Full generic type of the annotated element; definition of what exactly this means depends on subclass.
     */
    abstract val type: KotlinType

    /**
     * "Raw" type (type-erased class) of the annotated element; definition of what exactly this means depends on
     * subclass.
     */
    abstract val rawType: KClass<*>

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    abstract override fun toString(): String

}