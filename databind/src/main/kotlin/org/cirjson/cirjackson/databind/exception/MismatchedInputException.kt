package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * General exception type used as the base class for all [DatabindExceptions][DatabindException] that are due to input
 * not mapping to target definition; these are typically considered "client errors" since target type definition itself
 * is not the root cause but mismatching input. This is in contrast to [InvalidDefinitionException] which signals a
 * problem with target type definition and not input.
 *
 * This type is used as-is for some input problems, but in most cases there should be more explicit subtypes to use.
 *
 * NOTE: name chosen to differ from `java.util.InputMismatchException` since while that would have been better name, use
 * of same overlapping name causes nasty issues with IDE auto-completion, so slightly less optimal chosen.
 */
open class MismatchedInputException : DatabindException {

    /**
     * Intended target type, with which input did not match, if known; `null` if not known for some reason.
     */
    var targetType: KClass<*>? = null
        protected set

    protected constructor(parser: CirJsonParser?, message: String) : this(parser, message, null as KotlinType?)

    protected constructor(parser: CirJsonParser?, message: String, location: CirJsonLocation) : super(parser, message,
            location)

    protected constructor(parser: CirJsonParser?, message: String, targetType: KClass<*>) : super(parser, message) {
        this.targetType = targetType
    }

    protected constructor(parser: CirJsonParser?, message: String, targetType: KotlinType?) : super(parser, message) {
        this.targetType = targetType?.rawClass
    }

    fun withTargetType(type: KotlinType): MismatchedInputException {
        targetType = type.rawClass
        return this
    }

    companion object {

        fun from(parser: CirJsonParser?, targetType: KotlinType?, message: String): MismatchedInputException {
            return MismatchedInputException(parser, message, targetType)
        }

        fun from(parser: CirJsonParser?, targetType: KClass<*>, message: String): MismatchedInputException {
            return MismatchedInputException(parser, message, targetType)
        }

    }

}