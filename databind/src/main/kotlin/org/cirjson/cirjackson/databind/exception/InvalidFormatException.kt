package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonParser
import kotlin.reflect.KClass

/**
 * Specialized subclass of [MismatchedInputException] that is used when the underlying problem appears to be that of bad
 * formatting of a value to deserialize.
 *
 * @property value Accessor for checking source value (String, Number usually) that could not be deserialized into
 * target type ([targetType]). Note that value may not be available, depending on who throws the exception and when.
 */
class InvalidFormatException(parser: CirJsonParser?, message: String?, val value: Any?, targetType: KClass<*>) :
        MismatchedInputException(parser, message, targetType) {

    companion object {

        fun from(parser: CirJsonParser?, message: String?, value: Any?, targetType: KClass<*>): InvalidFormatException {
            return InvalidFormatException(parser, message, value, targetType)
        }

    }

}