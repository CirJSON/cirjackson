package org.cirjson.cirjackson.core

import java.io.Closeable
import java.lang.reflect.InvocationTargetException
import java.util.*

/**
 * Base class for all CirJackson-produced checked exceptions.
 */
open class CirJacksonException : RuntimeException {

    protected var myProcessor: Closeable? = null

    /**
     * Accessor for location information related to position within input or output (depending on operation), if
     * available; otherwise, it may return [CirJsonLocation.NA] (but never `null`).
     *
     * Accuracy of location information depends on backend (format) as well as (in some cases) operation being
     * performed.
     *
     * @return Location in input or output that triggered the problem reported, if available; `null` otherwise.
     */
    var location: CirJsonLocation? = null
        private set

    protected var myPath: LinkedList<Reference>? = null

    constructor(message: String?) : super(message)

    constructor(cause: Throwable?) : super(cause)

    constructor(processor: Closeable?, message: String?, location: CirJsonLocation?, cause: Throwable?) : super(message,
            cause) {
        myProcessor = processor
        this.location = location ?: CirJsonLocation.NA
    }

    constructor(message: String?, cause: Throwable?) : this(null, message, null, cause)

    constructor(message: String?, location: CirJsonLocation?, cause: Throwable?) : this(null, message, location, cause)

    constructor(processor: Closeable?, cause: Throwable?) : super(cause) {
        myProcessor = processor
        location = CirJsonLocation.NA
    }

    constructor(processor: Closeable?, message: String?) : super(message) {
        myProcessor = processor
        location = (processor as? CirJsonParser)?.currentTokenLocation() ?: CirJsonLocation.NA
    }

    constructor(processor: Closeable?, message: String?, cause: Throwable?) : super(message, cause) {
        myProcessor = processor
        location = if (cause is CirJacksonException) {
            cause.location
        } else {
            (processor as? CirJsonParser)?.currentTokenLocation() ?: CirJsonLocation.NA
        }
    }

    constructor(processor: Closeable?, message: String?, location: CirJsonLocation?) : super(message) {
        myProcessor = processor
        this.location = location ?: CirJsonLocation.NA
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
     * Method that allows to remove context information from this exception's message. Useful when you are parsing
     * security-sensitive data and don't want original data excerpts to be present in CirJackson parser error messages.
     *
     * @return This exception instance to allow call chaining
     */
    fun clearLocation(): CirJacksonException {
        location = null
        return this
    }

    /*
     *******************************************************************************************************************
     * Life-cycle: information augmentation (cannot use factory style, alas)
     *******************************************************************************************************************
     */

    /**
     * Method called to prepend a reference information in front of current path
     */
    fun prependPath(from: Any, propertyName: String): CirJacksonException {
        return prependPath(Reference(from, propertyName))
    }

    /**
     * Method called to prepend a reference information in front of current path
     */
    fun prependPath(from: Any, index: Int): CirJacksonException {
        return prependPath(Reference(from, index))
    }

    /**
     * Method called to prepend a reference information in front of current path
     */
    fun prependPath(reference: Reference): CirJacksonException {
        if (myPath == null) {
            myPath = LinkedList()
        }

        if (myPath!!.size < MAX_REFS_TO_LIST) {
            myPath!!.addFirst(reference)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    /**
     * Full structural path within type hierarchy down to problematic property.
     */
    val path: List<Reference>
        get() {
            return myPath?.toList() ?: emptyList()
        }

    /**
     * Description of path that lead to the problem that triggered this exception
     */
    val pathReference: String
        get() = getPathReference(StringBuilder()).toString()

    fun getPathReference(stringBuilder: StringBuilder): StringBuilder {
        appendPathDesc(stringBuilder)
        return stringBuilder
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
    open val processor: Any?
        get() = myProcessor

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

    protected fun appendPathDesc(stringBuilder: StringBuilder) {
        if (myPath == null) {
            return
        }

        val iterator = myPath!!.iterator()

        while (iterator.hasNext()) {
            stringBuilder.append(iterator.next())

            if (iterator.hasNext()) {
                stringBuilder.append("->")
            }
        }
    }

    override fun toString(): String {
        return "${javaClass.name}: $message"
    }

    /**
     * Simple bean class used to contain references. References can be added to indicate execution/reference path that
     * lead to the problem that caused this exception to be thrown.
     */
    open class Reference {

        /**
         * Object through which reference was resolved. Can be either actual instance (usually the case for
         * serialization), or Class (usually the case for deserialization).
         */
        var from: Any? = null
            protected set

        /**
         * Name of property (for POJO) or key (for Maps) that is part of the reference. May be `null` for Collection
         * types (which generally have [index] defined), or when resolving Map classes without (yet) having an instance
         * to operate on.
         */
        var propertyName: String? = null
            internal set

        /**
         * Index within a [Collection] instance that contained the reference; used if index is relevant and available.
         * If either not applicable, or not available, `-1` is used to denote "not known" (or not relevant).
         */
        var index = -1
            internal set

        /**
         * Lazily-constructed description of this instance; needed mostly to allow serialization to work in case where
         * [from] is non-serializable (and has to be dropped) but we still want to pass actual description along.
         */
        var description: String? = null
            get() {
                if (field == null) {
                    val stringBuilder = StringBuilder()

                    if (from == null) {
                        stringBuilder.append("UNKNOWN")
                    } else {
                        var clazz = from as? Class<*> ?: from!!.javaClass

                        var arrays = 0

                        while (clazz.isArray) {
                            clazz = clazz.componentType
                            arrays++
                        }

                        stringBuilder.append(clazz.name)

                        while (--arrays >= 0) {
                            stringBuilder.append("[]")
                        }
                    }

                    stringBuilder.append('[')

                    if (propertyName != null) {
                        stringBuilder.append('"')
                        stringBuilder.append(propertyName)
                        stringBuilder.append('"')
                    } else if (index >= 0) {
                        stringBuilder.append(index)
                    } else {
                        stringBuilder.append('?')
                    }

                    stringBuilder.append(']')
                    field = stringBuilder.toString()
                }

                return field
            }
            internal set

        protected constructor()

        constructor(from: Any) {
            this.from = from
        }

        constructor(from: Any, propertyName: String) {
            this.from = from
            this.propertyName = propertyName
        }

        constructor(from: Any, index: Int) {
            this.from = from
            this.index = index
        }

        override fun toString(): String {
            return description!!
        }

    }

    companion object {

        /**
         * Limit length of reference chain, to limit damage in cases of infinite recursion.
         */
        private const val MAX_REFS_TO_LIST = 1000

        /**
         * Method that can be called to either create a new DatabindException (if underlying exception is not a
         * DatabindException), or augment given exception with given path/reference information.
         *
         * This version of method is called when the reference is through a non-indexed object, such as a Map or
         * POJO/bean.
         */
        fun wrapWithPath(src: Throwable, from: Any, propertyName: String): CirJacksonException {
            return wrapWithPath(src, Reference(from, propertyName))
        }

        /**
         * Method that can be called to either create a new DatabindException (if underlying exception is not a
         * DatabindException), or augment given exception with given path/reference information.
         *
         * This version of method is called when the reference is through an index, which happens with arrays and
         * Collections.
         */
        fun wrapWithPath(src: Throwable, from: Any, index: Int): CirJacksonException {
            return wrapWithPath(src, Reference(from, index))
        }

        fun wrapWithPath(src: Throwable, reference: Reference): CirJacksonException {
            val e = if (src is CirJacksonException) {
                src
            } else {
                var message = exceptionMessage(src)

                if (message.isNullOrEmpty()) {
                    message = "(was ${src.javaClass.name})"
                }

                CirJacksonException(message, src)
            }

            e.prependPath(reference)
            return e
        }

        @Suppress("KotlinConstantConditions", "RedundantNullableReturnType")
        private fun exceptionMessage(throwable: Throwable): String? {
            return (throwable as CirJacksonException).originalMessage
                    ?: (throwable as? InvocationTargetException)?.cause?.message ?: throwable.message
        }

    }

}