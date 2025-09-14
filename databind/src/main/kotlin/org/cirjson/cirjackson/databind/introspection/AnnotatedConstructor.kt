package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Member
import java.lang.reflect.Parameter
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

class AnnotatedConstructor(context: TypeResolutionContext, private val myConstructor: KFunction<*>,
        classAnnotations: AnnotationMap?, paramAnnotations: Array<AnnotationMap?>?) :
        AnnotatedWithParams(context, classAnnotations, paramAnnotations) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAnnotations(fallback: AnnotationMap): AnnotatedConstructor {
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

    override val type: KotlinType
        get() = TODO("Not yet implemented")

    override val rawType: KClass<*>
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    override val parameterCount: Int
        get() = TODO("Not yet implemented")

    override fun getRawParameterType(index: Int): KClass<*> {
        TODO("Not yet implemented")
    }

    override fun getParameterType(index: Int): KotlinType {
        TODO("Not yet implemented")
    }

    override val nativeKotlinParameters: Array<KParameter>
        get() = TODO("Not yet implemented")

    override val nativeParameters: Array<Parameter>
        get() = TODO("Not yet implemented")

    @Throws(Exception::class)
    override fun call(): Any? {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    override fun call(args: Array<Any?>): Any? {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    override fun call(arg: Any?): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * AnnotatedMember implementation
     *******************************************************************************************************************
     */

    override val declaringClass: KClass<*>
        get() = TODO("Not yet implemented")

    override val member: Member?
        get() = TODO("Not yet implemented")

    @Throws(UnsupportedOperationException::class)
    override fun setValue(pojo: Any, value: Any) {
        TODO("Not yet implemented")
    }

    @Throws(UnsupportedOperationException::class)
    override fun getValue(pojo: Any): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API, specific annotations
     *******************************************************************************************************************
     */

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

}