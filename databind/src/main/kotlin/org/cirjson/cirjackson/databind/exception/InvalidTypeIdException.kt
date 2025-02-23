package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.KotlinType

/**
 * Exception thrown when resolution of a type id fails.
 *
 * @property baseType Base type for which subtype was to be resolved.
 *
 * @property typeId Type id that failed to be resolved to a subtype; `null` in cases where no type id was located.
 */
class InvalidTypeIdException(parser: CirJsonParser?, message: String, val baseType: KotlinType, val typeId: String?) :
        MismatchedInputException(parser, message) {

    companion object {

        fun from(parser: CirJsonParser?, message: String, baseType: KotlinType,
                typeId: String?): InvalidTypeIdException {
            return InvalidTypeIdException(parser, message, baseType, typeId)
        }

    }

}