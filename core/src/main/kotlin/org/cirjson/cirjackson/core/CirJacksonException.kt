package org.cirjson.cirjackson.core

/**
 * Base class for all CirJackson-produced checked exceptions.
 */
abstract class CirJacksonException : RuntimeException {

    /**
     * Accessor for location information related to position within input or output (depending on operation), if
     * available; otherwise, it may return [CirJsonLocation.NA] (but never `null`).
     *
     * Accuracy of location information depends on backend (format) as well as (in some cases) operation being
     * performed.
     *
     * @return Location in input or output that triggered the problem reported, if available; `null` otherwise.
     */
    var location: CirJsonLocation?
        private set

    constructor(message: String?, location: CirJsonLocation?, cause: Throwable) : super(message, cause) {
        this.location = location
    }

    constructor(message: String?) : super(message) {
        this.location = null
    }

    constructor(message: String?, cause: Throwable) : super(message, cause) {
        this.location = null
    }

}