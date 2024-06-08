package org.cirjson.cirjackson.core.exception

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonLocation

class StreamConstraintsException : CirJacksonException {

    constructor(message: String?, location: CirJsonLocation?) : super(message, location, null)

    constructor(message: String?) : super(message)

    override val processor: Any?
        get() = null

}