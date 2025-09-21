package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KParameter

/**
 * Intermediate base class that encapsulates features that constructors and methods share.
 */
abstract class AnnotatedWithParams : AnnotatedMember {

    protected val myParamAnnotations: Array<AnnotationMap?>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(context: TypeResolutionContext?, annotations: AnnotationMap?,
            paramAnnotations: Array<AnnotationMap?>?) : super(context, annotations) {
        myParamAnnotations = paramAnnotations
    }

    protected constructor(base: AnnotatedWithParams, paramAnnotations: Array<AnnotationMap?>?) : super(base) {
        myParamAnnotations = paramAnnotations
    }

    /**
     * Method called by parameter object when an augmented instance is created; needs to replace parameter with new
     * instance
     */
    protected open fun replaceParameterAnnotations(index: Int, annotationMap: AnnotationMap?): AnnotatedParameter {
        myParamAnnotations!![index] = annotationMap
        return getParameter(index)
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    fun getParameterAnnotations(index: Int): AnnotationMap? {
        return myParamAnnotations?.getOrNull(index)
    }

    fun getParameter(index: Int): AnnotatedParameter {
        return AnnotatedParameter(this, getParameterType(index)!!, myTypeContext, getParameterAnnotations(index), index)
    }

    abstract val parameterCount: Int

    abstract fun getRawParameterType(index: Int): KClass<*>?

    abstract fun getParameterType(index: Int): KotlinType?

    abstract val nativeKotlinParameters: Array<KParameter>

    abstract val nativeParameters: Array<Parameter>

    val annotationCount: Int
        get() = myAnnotations!!.size

    /**
     * Method that can be used to (try to) call this object without arguments. This may succeed or fail, depending on
     * expected number of arguments: caller needs to take care to pass correct number. Exceptions are thrown directly
     * from actual low-level call.
     *
     * Note: only works for constructors and static methods.
     */
    @Throws(Exception::class)
    abstract fun call(): Any?

    /**
     * Method that can be used to (try to) call this object with specified arguments. This may succeed or fail,
     * depending on expected number of arguments: caller needs to take care to pass correct number. Exceptions are
     * thrown directly from actual low-level call.
     *
     * Note: only works for constructors and static methods.
     */
    @Throws(Exception::class)
    abstract fun call(args: Array<Any?>): Any?

    /**
     * Method that can be used to (try to) call this object with single arguments. This may succeed or fail, depending
     * on expected number of arguments: caller needs to take care to pass correct number. Exceptions are thrown directly
     * from actual low-level call.
     *
     * Note: only works for constructors and static methods.
     */
    @Throws(Exception::class)
    abstract fun call(arg: Any?): Any?

}