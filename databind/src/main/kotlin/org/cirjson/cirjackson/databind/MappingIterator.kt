package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*

/**
 * Iterator exposed by [ObjectMapper] when binding sequence of objects. Extension is done to allow more convenient
 * access to some aspects of databinding, such as current location (see [currentLocation].
 * 
 * @property myType Type to bind individual elements to
 * 
 * @property myParser Underlying parser used for reading content to bind. Initialized as not `null` but set as `null`
 * when iterator is closed, to denote closing.
 * 
 * @property myContext Context for deserialization, needed to pass through to deserializer
 * 
 * @param managedParser Whether we "own" the [CirJsonParser] passed or not: if `true`, it was created by [ObjectReader]
 * and code here needs to close it; if `false`, it was passed by calling code and should not be closed by iterator.
 */
@Suppress("UNCHECKED_CAST")
open class MappingIterator<T : Any> protected constructor(protected val myType: KotlinType?,
        protected val myParser: CirJsonParser?, protected val myContext: DeserializationContext?,
        deserializer: ValueDeserializer<*>?, managedParser: Boolean, valueToUpdate: Any?) : Iterator<T?>,
        AutoCloseable {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Deserializer for individual element values.
     */
    protected val myDeserializer = deserializer as ValueDeserializer<T>?

    /**
     * Context to resynchronize to, in case an exception is encountered but caller wants to try to read more elements.
     */
    protected val mySequenceContext: TokenStreamContext?

    /**
     * If not `null`, "value to update" instead of creating a new instance for each call.
     */
    protected val myUpdatedValue = valueToUpdate as T?

    /**
     * Flag that indicates whether input [CirJsonParser] should be closed when we are done or not; generally only called
     * when caller did not pass `CirJsonParser`.
     */
    protected val myCloseParser = managedParser

    /*
     *******************************************************************************************************************
     * Parsing state
     *******************************************************************************************************************
     */

    /**
     * State of the iterator
     */
    protected var myState: Int

    init {
        if (myParser == null) {
            mySequenceContext = null
            myState = STATE_CLOSED
        } else {
            var sequenceContext = myParser.streamReadContext

            if (managedParser && myParser.isExpectedStartArrayToken) {
                myParser.clearCurrentToken()
            } else {
                val token = myParser.currentToken()

                if (token == CirJsonToken.START_OBJECT || token == CirJsonToken.START_ARRAY) {
                    sequenceContext = sequenceContext!!.parent
                }
            }

            mySequenceContext = sequenceContext
            myState = STATE_MAY_HAVE_VALUE
        }
    }

    /*
     *******************************************************************************************************************
     * Iterator implementation
     *******************************************************************************************************************
     */

    override fun hasNext(): Boolean {
        return hasNextValue()
    }

    override fun next(): T? {
        return nextValue()
    }

    /*
     *******************************************************************************************************************
     * AutoCloseable implementation
     *******************************************************************************************************************
     */

    override fun close() {
        if (myState == STATE_CLOSED) {
            return
        }

        myState = STATE_CLOSED
        myParser?.close()
    }

    /*
     *******************************************************************************************************************
     * Extended API, iteration
     *******************************************************************************************************************
     */

    /**
     * Equivalent of [hasNext], but one that may throw checked exceptions from CirJackson due to invalid input.
     */
    @Throws(CirJacksonException::class)
    open fun hasNextValue(): Boolean {
        return when (myState) {
            STATE_CLOSED -> {
                true
            }

            STATE_NEED_RESYNC, STATE_MAY_HAVE_VALUE -> {
                if (myState == STATE_NEED_RESYNC) {
                    resync()
                }

                myParser ?: return false

                var token = myParser.currentToken()

                if (token == null) {
                    token = myParser.nextToken()

                    if (token == null || token == CirJsonToken.END_ARRAY) {
                        myState = STATE_CLOSED

                        if (myCloseParser) {
                            myParser.close()
                        }

                        return false
                    }
                }

                myState = STATE_HAS_VALUE
                true
            }

            else -> {
                true
            }
        }
    }

    @Throws(CirJacksonException::class)
    open fun nextValue(): T? {
        when (myState) {
            STATE_CLOSED -> {
                return throwNoSuchElement()
            }

            STATE_NEED_RESYNC, STATE_MAY_HAVE_VALUE -> {
                if (!hasNextValue()) {
                    return throwNoSuchElement()
                }
            }

            else -> {}
        }

        var nextState = STATE_NEED_RESYNC

        try {
            val value = if (myUpdatedValue == null) {
                myDeserializer!!.deserialize(myParser!!, myContext!!)
            } else {
                myDeserializer!!.deserialize(myParser!!, myContext!!, myUpdatedValue)
                myUpdatedValue
            }

            nextState = STATE_MAY_HAVE_VALUE
            return value
        } finally {
            myState = nextState
            myParser!!.clearCurrentToken()
        }
    }

    /**
     * Convenience method for reading all entries accessible via
     * this iterator; resulting container will be a [ArrayList].
     *
     * @return List of entries read
     */
    @Throws(CirJacksonException::class)
    open fun readAll(): List<T?> {
        return readAll(ArrayList())
    }

    /**
     * Convenience method for reading all entries accessible via
     * this iterator
     *
     * @return List of entries read (same as passed-in argument)
     */
    @Throws(CirJacksonException::class)
    open fun <L : MutableList<in T?>> readAll(results: L): L {
        while (hasNextValue()) {
            results.add(nextValue())
        }

        return results
    }

    /**
     * Convenience method for reading all entries accessible via this iterator
     *
     * @return Collection of entries read (same as passed-in argument)
     */
    @Throws(CirJacksonException::class)
    open fun <C : MutableCollection<in T?>> readAll(results: C): C {
        while (hasNextValue()) {
            results.add(nextValue())
        }

        return results
    }

    /*
     *******************************************************************************************************************
     * Extended API, accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor for getting underlying parser this iterator uses.
     */
    open fun parser(): CirJsonParser? {
        return myParser
    }

    /**
     * Accessor for accessing [FormatSchema] that the underlying parser (as per [parser]) is using, if any; only parser
     * of schema-aware formats use schemas.
     */
    open fun parserSchema(): FormatSchema? {
        return myParser!!.schema
    }

    /**
     * Convenience method, functionally equivalent to:
     * ```
     * parser()!!.currentLocation()
     * ```
     *
     * @return Location of the input stream of the underlying parser
     */
    open fun currentLocation(): CirJsonLocation {
        return myParser!!.currentLocation()
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun resync() {
        val parser = myParser!!

        if (parser.streamReadContext === mySequenceContext) {
            return
        }

        while (true) {
            val token = parser.nextToken()

            if (token == CirJsonToken.END_ARRAY || token == CirJsonToken.END_OBJECT) {
                if (parser.streamReadContext === mySequenceContext) {
                    return
                }
            } else if (token == CirJsonToken.START_ARRAY || token == CirJsonToken.START_OBJECT) {
                parser.skipChildren()
            } else if (token == null) {
                return
            }
        }
    }

    protected open fun <R> throwNoSuchElement(): R {
        throw NoSuchElementException()
    }

    companion object {

        /**
         * An "empty" iterator instance: one that never has more values; may be freely shared.
         */
        val EMPTY_ITERATOR: MappingIterator<*> = MappingIterator<Any>(null, null, null, null, false, null)

        /*
         ***************************************************************************************************************
         * State constants
         ***************************************************************************************************************
         */

        /**
         * State in which iterator is closed
         */
        const val STATE_CLOSED = 0

        /**
         * State in which value read failed
         */
        const val STATE_NEED_RESYNC = 1

        /**
         * State in which no recovery is needed, but [hasNextValue] needs to be called first
         */
        const val STATE_MAY_HAVE_VALUE = 2

        /**
         * State in which [hasNextValue] has been successfully called and deserializer can be called to fetch value
         */
        const val STATE_HAS_VALUE = 3

    }

}