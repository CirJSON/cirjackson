package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser

/**
 * Intermediate base class for all read-side streaming processing problems, including parsing and input value coercion
 * problems.
 */
open class StreamReadException : CirJacksonException {

    override val processor: CirJsonParser?
        get() = myProcessor as CirJsonParser?

    constructor(processor: CirJsonParser?, message: String) : this(processor, message, processor?.currentLocation())

    constructor(processor: CirJsonParser?, message: String, cause: Throwable) : this(processor, message,
            processor?.currentLocation(), cause)

    constructor(processor: CirJsonParser?, message: String, location: CirJsonLocation?) : super(message, location) {
        myProcessor = processor
    }

    constructor(processor: CirJsonParser?, message: String, location: CirJsonLocation?, cause: Throwable) : super(
            message, location, cause) {
        myProcessor = processor
    }

}