package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.MethodHelper
import org.cirjson.cirjackson.databind.util.hasClass
import org.cirjson.cirjackson.databind.util.name
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod

class AnnotatedMethod(context: TypeResolutionContext, private val myMethod: KFunction<*>, annotations: AnnotationMap?,
        paramAnnotations: Array<AnnotationMap?>?) : AnnotatedWithParams(context, annotations, paramAnnotations) {

    private var myParameterClasses: Array<KClass<*>>? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap?): AnnotatedMethod {
        return AnnotatedMethod(myTypeContext!!, myMethod, fallback, myParamAnnotations)
    }

    override val annotated: KFunction<*>
        get() = myMethod

    override val modifiers: Int
        get() = myMethod.javaMethod!!.modifiers

    override val name: String
        get() = myMethod.name

    /**
     * For methods, this returns declared return type, which is only useful with getters (setters do not return
     * anything; hence `Void` would be returned here)
     */
    override val type: KotlinType
        get() = myTypeContext!!.resolveType(myMethod.returnType)

    /**
     * For methods, this returns declared return type, which is only useful with getters (setters do not usually return
     * anything; hence "void" type is returned here)
     */
    override val rawType: KClass<*>
        get() = myMethod.javaMethod!!.returnType.kotlin

    /*
     *******************************************************************************************************************
     * AnnotatedWithParams
     *******************************************************************************************************************
     */

    @Throws(Exception::class)
    override fun call(): Any? {
        return myMethod.javaMethod!!.invoke(null)
    }

    @Throws(Exception::class)
    override fun call(args: Array<Any?>): Any? {
        return myMethod.javaMethod!!.invoke(null, *args)
    }

    @Throws(Exception::class)
    override fun call(arg: Any?): Any? {
        return myMethod.javaMethod!!.invoke(null, arg)
    }

    @Throws(Exception::class)
    fun callOn(pojo: Any?): Any? {
        return MethodHelper.callOn(myMethod.javaMethod!!, pojo)
    }

    @Throws(Exception::class)
    fun callOnWith(pojo: Any?, vararg args: Any?): Any? {
        return myMethod.javaMethod!!.invoke(pojo, *args)
    }

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val parameterCount: Int
        get() = myMethod.parameters.size

    override fun getRawParameterType(index: Int): KClass<*>? {
        return rawParameterTypes.getOrNull(index)
    }

    override fun getParameterType(index: Int): KotlinType? {
        val type = myMethod.parameters.getOrNull(index)?.type ?: return null
        return myTypeContext!!.resolveType(type)
    }

    override val nativeKotlinParameters: Array<KParameter>
        get() = myMethod.parameters.toTypedArray()

    override val nativeParameters: Array<Parameter>
        get() = myMethod.javaMethod!!.parameters

    override val declaringClass: KClass<*>
        get() = myMethod.javaMethod!!.declaringClass.kotlin

    override val member: Method
        get() = myMethod.javaMethod!!

    @Throws(IllegalArgumentException::class)
    override fun setValue(pojo: Any, value: Any) {
        try {
            myMethod.javaMethod!!.invoke(pojo, value)
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Failed to setValue() for field $fullName: ${e.message}", e)
        } catch (e: InvocationTargetException) {
            throw IllegalArgumentException("Failed to setValue() for field $fullName: ${e.message}", e)
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getValue(pojo: Any): Any? {
        try {
            return MethodHelper.callOn(myMethod.javaMethod!!, pojo)
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Failed to getValue() for field $fullName: ${e.message}", e)
        } catch (e: InvocationTargetException) {
            throw IllegalArgumentException("Failed to getValue() for field $fullName: ${e.message}", e)
        }
    }

    /*
     *******************************************************************************************************************
     * Extended API, generic
     *******************************************************************************************************************
     */

    override val fullName: String
        get() {
            val methodName = super.fullName

            return when (val count = parameterCount) {
                0 -> "$methodName()"
                1 -> "$methodName(${getRawParameterType(0)!!.name})"
                else -> "$methodName($count params)"
            }
        }

    val rawParameterTypes: Array<KClass<*>>
        get() = myParameterClasses ?: myMethod.javaMethod!!.parameterTypes.map { it.kotlin }.toTypedArray()
                .also { myParameterClasses = it }

    val rawReturnType: KClass<*>
        get() = myMethod.javaMethod!!.returnType.kotlin

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[method $fullName]"
    }

    override fun hashCode(): Int {
        return myMethod.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other == null || !other.hasClass(this::class) || other !is AnnotatedMethod) {
            return false
        }

        return myMethod == other.myMethod
    }

}