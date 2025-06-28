package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken

/**
 * Exception type for read-side problems that are not direct decoding ("parsing") problems (those would be reported as
 * basic [StreamReadException]s), but rather result from failed attempts to convert specific value out of valid but
 * incompatible input value. One example is numeric coercions where target number type's range does not allow mapping of
 * too large/too small input value.
 *
 * @constructor Constructor that uses current parsing location as location, and sets processor (accessible via
 * [processor]) to specified parser.
 *
 * @param processor Parser in use at the point where failure occurred
 *
 * @param message Exception message to use
 *
 * @param inputType Shape of input that failed to coerce
 *
 * @param targetType Target type of failed coercion
 *
 * @property inputType Accessor for getting information about input type (in form of token, giving "shape" of input) for
 * which coercion failed.
 *
 * @property targetType Accessor for getting information about target type (in form of [Class]) for which coercion
 * failed.
 */
class InputCoercionException(processor: CirJsonParser?, message: String, val inputType: CirJsonToken?,
        val targetType: Class<*>) : StreamReadException(processor, message)