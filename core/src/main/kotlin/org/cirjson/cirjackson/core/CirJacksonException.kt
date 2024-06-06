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

    /**
     * Method that allows to remove context information from this exception's message. Useful when you are parsing
     * security-sensitive data and don't want original data excerpts to be present in CirJackson parser error messages.
     *
     * @return This exception instance to allow call chaining
     */
    fun withCause(cause: Throwable): CirJacksonException {
        initCause(cause)
        return this
    }

    /**
     * Value that allows accessing the original "message" argument, without additional decorations (like location
     * information) that overridden [message] adds.
     *
     * @return Original, unmodified `message` argument used to construct this exception instance
     */
    val originalMessage: String?
        get() = super.message

    /**
     * Method that allows accessing underlying processor that triggered this exception; typically either [CirJsonParser]
     * or [CirJsonGenerator] for exceptions that originate from streaming API, but may be other types when thrown by
     * databinding.
     *
     * Note that it is possible that `null` may be returned if code throwing exception either has no access to the
     * processor; or has not been retrofitted to set it; this means that caller needs to take care to check for nulls.
     * Subtypes override this method with co-variant return type, for more type-safe access.
     */
    abstract val processor: Any?

    /**
     * Accessor that subclasses can override to append additional information right after the main message, but before
     * source location information.
     *
     * It contains the message suffix configured to be used, if any; `null` if none
     */
    protected open val messageSuffix: String? = null

    /**
     * Default method overridden so that we can add location information
     *
     * It contains the message constructed based on possible optional prefix; explicit `message` passed to constructor
     * as well trailing location description (separate from message by linefeed)
     */
    override val message: String
        get() {
            var msg = super.message ?: "N/A"

            val loc = location
            val suffix = messageSuffix

            if (loc != null || suffix != null) {
                msg = StringBuilder(100).apply {
                    append(msg)

                    if (suffix != null) {
                        append(suffix)
                    }

                    if (loc != null) {
                        append("\n at ")
                        loc.toString(this)
                    }
                }.toString()
            }

            return msg
        }

}