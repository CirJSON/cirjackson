package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.KotlinType
import java.io.Closeable

/**
 * Exception type used for generic failures during processing by
 * [org.cirjson.cirjackson.databind.deserialization.ValueInstantiator]: commonly used to wrap exceptions thrown by
 * constructor or factory method.
 *
 * Note that this type is sibling of [MismatchedInputException] and [InvalidDefinitionException] since it is not clear
 * if problem is with input, or type definition (or possibly neither). It is recommended that if either specific input,
 * or type definition problem is known, a more accurate exception is used instead.
 */
open class ValueInstantiationException : DatabindException {

    /**
     * Fully resolved type that had the problem; this should always be known and available, never `null`.
     */
    val type: KotlinType

    protected constructor(processor: Closeable?, message: String?, type: KotlinType, cause: Throwable?) : super(
            processor,
            message, cause) {
        this.type = type
    }

    protected constructor(processor: Closeable?, message: String?, type: KotlinType) : super(processor, message) {
        this.type = type
    }

    companion object {

        fun from(processor: Closeable?, message: String?, type: KotlinType,
                cause: Throwable?): ValueInstantiationException {
            return ValueInstantiationException(processor, message, type, cause)
        }

        fun from(processor: Closeable?, message: String?, type: KotlinType): ValueInstantiationException {
            return ValueInstantiationException(processor, message, type)
        }

    }

}