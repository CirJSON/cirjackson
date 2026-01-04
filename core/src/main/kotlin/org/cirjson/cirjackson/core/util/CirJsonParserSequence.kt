package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher

/**
 * Helper class that can be used to sequence multiple physical [CirJsonParser]s to create a single logical sequence of
 * tokens, as a single [CirJsonParser].
 *
 * Fairly simple use of [CirJsonParserDelegate]: only need to override [nextToken] to handle transition
 *
 * @property myParsers Parsers other than the first one (which is initially assigned as delegate)
 *
 * @property myIsCheckingForExistingToken Configuration that determines whether state of parsers is first verified to
 * see if parser already points to a token (that is, [CirJsonParser.isCurrentTokenNotNull] returns `true`), and if so
 * that token is first return before [CirJsonParser.nextToken] is called. If enabled, this check is made; if disabled,
 * no check is made and [CirJsonParser.nextToken] is always called for all parsers.
 *
 * Default setting is `false`, so that possible existing token is not considered for parsers.
 */
open class CirJsonParserSequence(protected val myIsCheckingForExistingToken: Boolean,
        protected val myParsers: Array<CirJsonParser>) : CirJsonParserDelegate(myParsers[0]) {

    /**
     * Index of the next parser in [myParsers].
     */
    protected var myNextParserIndex = 1

    /**
     * Flag used to indicate that [CirJsonParser.nextToken] should not be called, due to parser already pointing to a
     * token.
     */
    protected var myHasToken = myIsCheckingForExistingToken && delegate.isCurrentTokenNotNull

    protected open fun addFlattenedActiveParsers(listToAddIn: MutableList<CirJsonParser>) {
        val length = myParsers.size

        for (i in myNextParserIndex - 1..<length) {
            val parser = myParsers[i]

            if (parser is CirJsonParserSequence) {
                parser.addFlattenedActiveParsers(listToAddIn)
            } else {
                listToAddIn.add(parser)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Overridden methods, needed: cases where default delegation does not work
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun close() {
        do {
            delegate.close()
        } while (switchToNext())
    }

    @Throws(CirJacksonException::class)
    override fun nextToken(): CirJsonToken? {
        if (myHasToken) {
            myHasToken = false
            return delegate.currentToken()
        }

        return delegate.nextToken() ?: switchAndReturnNext()
    }

    /**
     * Need to override, re-implement similar to how method defined in
     * [org.cirjson.cirjackson.core.base.ParserMinimalBase], to keep state correct here.
     */
    @Throws(CirJacksonException::class)
    override fun skipChildren(): CirJsonParser {
        if (delegate.currentToken() != CirJsonToken.START_OBJECT &&
                delegate.currentToken() != CirJsonToken.START_ARRAY) {
            return this
        }

        var open = 1

        while (true) {
            val token = nextToken() ?: return this

            if (token.isStructStart) {
                ++open
            } else if (token.isStructEnd) {
                if (--open == 0) {
                    return this
                }
            }
        }
    }

    /*
     *******************************************************************************************************************
     * And some more methods where default delegation would cause problems with state handling here
     *******************************************************************************************************************
     */
    override fun nextName(): String? {
        val next = nextToken()

        return if (next == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || next == CirJsonToken.PROPERTY_NAME) {
            currentName()
        } else {
            null
        }
    }

    override fun nextName(string: SerializableString): Boolean {
        val next = nextToken()

        return (next == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || next == CirJsonToken.PROPERTY_NAME) &&
                string.value == currentName()
    }

    override fun nextNameMatch(matcher: PropertyNameMatcher): Int {
        val string = nextName()

        if (string != null) {
            return matcher.matchName(string)
        }

        return if (hasToken(CirJsonToken.END_OBJECT)) {
            PropertyNameMatcher.MATCH_END_OBJECT
        } else {
            PropertyNameMatcher.MATCH_ODD_TOKEN
        }
    }

    /*
     *******************************************************************************************************************
     * Additional extended API
     *******************************************************************************************************************
     */

    /**
     * Method that is most useful for debugging or testing; returns actual number of underlying parsers sequence was
     * constructed with (nor just ones remaining active)
     *
     * @return Number of actual underlying parsers this sequence has
     */
    fun containedParsersCount(): Int {
        return myParsers.size
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Method that will switch active delegate parser from the current one
     * to the next parser in sequence, if there is another parser left:
     * if so, the next parser will become the active delegate parser.
     *
     * @return True if switch succeeded; false otherwise
     */
    protected open fun switchToNext(): Boolean {
        return if (myNextParserIndex < myParsers.size) {
            delegate = myParsers[myNextParserIndex++]
            true
        } else {
            false
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun switchAndReturnNext(): CirJsonToken? {
        while (myNextParserIndex < myParsers.size) {
            delegate = myParsers[myNextParserIndex++]

            if (myIsCheckingForExistingToken && delegate.isCurrentTokenNotNull) {
                return delegate.currentToken()
            }

            val token = delegate.nextToken()

            if (token != null) {
                return token
            }
        }

        return null
    }

    companion object {

        /**
         * Method that will construct a sequence (possibly a sequence) that contains all given sub-parsers. All parsers
         * given are checked to see if they are sequences: and if so, they will be "flattened", that is, contained
         * parsers are directly added in a new sequence instead of adding sequences within sequences. This is done to
         * minimize delegation depth, ideally only having just a single level of delegation.
         *
         * @param checkForExistingToken Flag passed to be assigned as [myIsCheckingForExistingToken] for resulting
         * sequence
         *
         * @param first First parser to traverse
         *
         * @param second Second parser to traverse
         *
         * @return Sequence instance constructed
         */
        fun createFlattened(checkForExistingToken: Boolean, first: CirJsonParser,
                second: CirJsonParser): CirJsonParserSequence {
            if (first !is CirJsonParserSequence && second !is CirJsonParserSequence) {
                return CirJsonParserSequence(checkForExistingToken, arrayOf(first, second))
            }

            val parsers = ArrayList<CirJsonParser>(10)

            if (first is CirJsonParserSequence) {
                first.addFlattenedActiveParsers(parsers)
            } else {
                parsers.add(first)
            }

            if (second is CirJsonParserSequence) {
                second.addFlattenedActiveParsers(parsers)
            } else {
                parsers.add(second)
            }

            return CirJsonParserSequence(checkForExistingToken, parsers.toTypedArray())
        }

    }

}