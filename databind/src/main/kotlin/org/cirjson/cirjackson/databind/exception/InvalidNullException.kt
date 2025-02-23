package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.quotedOr

/**
 * Exception thrown if a `null` value is being encountered for a property designed as "fail on null" property (see
 * [org.cirjson.cirjackson.annotations.CirJsonSetter]).
 *
 * @property propertyName Name of property, if known, for which `null` was encountered.
 */
open class InvalidNullException protected constructor(context: DeserializationContext?, message: String,
        val propertyName: String?) : MismatchedInputException(context?.parser, message) {

    companion object {

        fun from(context: DeserializationContext?, propertyName: String?, type: KotlinType?): InvalidNullException {
            val message = "Invalid `null` value encountered for property ${propertyName.quotedOr("<UNKNOWN>")}"
            val e = InvalidNullException(context, message, propertyName)
            type?.also { e.withTargetType(type) }
            return e
        }

    }

}