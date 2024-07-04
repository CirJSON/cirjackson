package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken

/**
 * Specialized [StreamReadException] that is thrown when end-of-input is reached unexpectedly, usually within token
 * being decoded, but possibly within intervening non-token content (for formats that have that, such as whitespace for
 * textual formats)
 *
 * @property tokenBeingDecoded Accessor for possibly available information about token that was being decoded while
 * encountering end of input.
 */
class UnexpectedEndOfInputException(parser: CirJsonParser, val tokenBeingDecoded: CirJsonToken, message: String) :
        StreamReadException(parser, message)