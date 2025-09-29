package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.hasClass
import java.lang.reflect.Member
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.javaConstructor

class AnnotatedConstructor(context: TypeResolutionContext?, private val myConstructor: KFunction<*>,
        classAnnotations: AnnotationMap?, paramAnnotations: Array<AnnotationMap?>?) :
        AnnotatedWithParams(context, classAnnotations, paramAnnotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap?): AnnotatedConstructor {
        return AnnotatedConstructor(myTypeContext, myConstructor, fallback, myParamAnnotations)
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KFunction<*>
        get() = myConstructor

    override val modifiers: Int
        get() = myConstructor.javaConstructor!!.modifiers

    override val name: String
        get() = myConstructor.name

    override val type: KotlinType
        get() = myTypeContext!!.resolveType(rawType.starProjectedType)

    override val rawType: KClass<*>
        get() = (myConstructor.javaConstructor!!.declaringClass as Class<*>).kotlin

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    override val parameterCount: Int
        get() = myConstructor.parameters.size

    override fun getRawParameterType(index: Int): KClass<*>? {
        return myConstructor.parameters.getOrNull(index)?.type?.classifier as? KClass<*>
    }

    override fun getParameterType(index: Int): KotlinType? {
        return myConstructor.parameters.getOrNull(index)?.let { myTypeContext!!.resolveType(it.type) }
    }

    override val nativeKotlinParameters: Array<KParameter>
        get() = myConstructor.parameters.toTypedArray()

    override val nativeParameters: Array<Parameter>
        get() = myConstructor.javaConstructor!!.parameters

    @Throws(Exception::class)
    override fun call(): Any? {
        return myConstructor.call()
    }

    @Throws(Exception::class)
    override fun call(args: Array<Any?>): Any? {
        return myConstructor.call(*args)
    }

    @Throws(Exception::class)
    override fun call(arg: Any?): Any? {
        return myConstructor.call(arg)
    }

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val declaringClass: KClass<*>
        get() = (myConstructor.javaConstructor!!.declaringClass as Class<*>).kotlin

    override val member: Member
        get() = myConstructor.javaConstructor!!

    @Throws(UnsupportedOperationException::class)
    override fun setValue(pojo: Any, value: Any) {
        throw UnsupportedOperationException("Cannot call setValue() on constructor of ${declaringClass.qualifiedName}")
    }

    @Throws(UnsupportedOperationException::class)
    override fun getValue(pojo: Any): Any? {
        throw UnsupportedOperationException("Cannot call getValue() on constructor of ${declaringClass.qualifiedName}")
    }

    /*
     *******************************************************************************************************************
     * Extended API, specific annotations
     *******************************************************************************************************************
     */

    override fun toString(): String {
        val argCount = myConstructor.parameters.size
        return "[constructor for ${declaringClass.qualifiedName} ($argCount arg${"s".takeUnless { argCount == 1 } ?: ""}), annotations: $myAnnotations"
    }

    override fun hashCode(): Int {
        return myConstructor.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other == null || !other.hasClass(this::class) || other !is AnnotatedConstructor) {
            return false
        }

        return myConstructor == other.myConstructor
    }

}