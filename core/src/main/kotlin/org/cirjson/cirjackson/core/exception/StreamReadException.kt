package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser

/**
 * Intermediate base class for all read-side streaming processing problems, including parsing and input value coercion
 * problems.
 */
open class StreamReadException : CirJacksonException {

    private val myProcessor: CirJsonParser?

    override val processor: CirJsonParser?
        get() = myProcessor

    constructor(processor: CirJsonParser?, message: String) : super(message) {
        myProcessor = processor
    }

    constructor(processor: CirJsonParser?, message: String, cause: Throwable) : super(message, cause) {
        myProcessor = processor
    }

    constructor(processor: CirJsonParser?, message: String, location: CirJsonLocation) : super(message, location) {
        myProcessor = processor
    }

}