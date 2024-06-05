package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser

/**
 * Intermediate base class for all read-side streaming processing problems, including parsing and input value coercion
 * problems.
 */
open class StreamReadException : CirJacksonException {

    val processor: CirJsonParser?

    constructor(processor: CirJsonParser?, message: String) : super(message) {
        this.processor = processor
    }

    constructor(processor: CirJsonParser?, message: String, cause: Throwable) : super(message, cause) {
        this.processor = processor
    }

}