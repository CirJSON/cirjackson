package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.util.CirJsonParserDelegate

/**
 * Specialized [CirJsonParserDelegate] that allows use of [TokenFilter] for outputting a subset of content that is
 * visible to caller
 *
 * @param parser Parser to delegate calls to
 *
 * @param filter Filter to use
 *
 * @param myInclusion Definition of inclusion criteria
 *
 * @param myIsAllowingMultipleMatches Whether to allow multiple matches
 *
 * @property myInclusion Flag that determines whether path leading up to included content should also be automatically
 * included or not. If `false`, no path inclusion is done and only explicitly included entries are output; if `true`
 * then path from main level down to match is also included as necessary.
 *
 * @property myIsAllowingMultipleMatches Flag that determines whether filtering will continue after the first match is
 * indicated or not: if `false`, output is based on just the first full match (returning [TokenFilter.INCLUDE_ALL]) and
 * no more checks are made; if `true` then filtering will be applied as necessary until end of content.
 */
open class FilteringParserDelegate(parser: CirJsonParser, filter: TokenFilter,
        protected var myInclusion: TokenFilter.Inclusion?, protected var myIsAllowingMultipleMatches: Boolean) :
        CirJsonParserDelegate(parser) {

    /**
     * Object consulted to determine whether to write parts of content generator is asked to write or not.
     */
    var filter = filter
        protected set

    /**
     * Last token retrieved via [nextToken], if any. Null before the first call to `nextToken()`, as well as if token
     * has been explicitly cleared
     */
    protected var myCurrentToken: CirJsonToken? = null

    override var lastClearedToken: CirJsonToken? = null
        protected set

    /**
     * During traversal this is the actual "open" parse tree, which sometimes is the same as [myExposedContext], and at
     * other times is ahead of it. Note that this context is never `null`.
     */
    protected var myHeadContext = TokenFilterContext.createRootContext(filter)

    /**
     * In cases where [myHeadContext] is "ahead" of context exposed to caller, this context points to what is currently
     * exposed to caller. When the two are in sync, this context reference will be `null`.
     */
    protected var myExposedContext: TokenFilterContext? = null

    /**
     * State that applies to the item within container, used where applicable. Specifically used to pass inclusion state
     * between property name and property, and also used for array elements.
     */
    protected var myItemFilter: TokenFilter? = filter

    /**
     * Accessor for finding number of matches for which [TokenFilter.INCLUDE_ALL] has been returned, where specific
     * token and subtree starting (if structured type) are passed.
     */
    var matchCount = 0
        protected set

    override fun currentToken(): CirJsonToken? {
        return myCurrentToken
    }

    override fun currentTokenId(): Int {
        return myCurrentToken?.id ?: CirJsonTokenId.ID_NO_TOKEN
    }

    override val isCurrentTokenNotNull: Boolean
        get() = myCurrentToken != null

    override fun hasTokenId(id: Int): Boolean {
        return id == (myCurrentToken?.id ?: CirJsonTokenId.ID_NO_TOKEN)
    }

    override fun hasToken(token: CirJsonToken): Boolean {
        return myCurrentToken == token
    }

    override val isExpectedStartArrayToken: Boolean
        get() = myCurrentToken == CirJsonToken.START_ARRAY

    override val isExpectedStartObjectToken: Boolean
        get() = myCurrentToken == CirJsonToken.START_OBJECT

    override val streamReadContext: TokenStreamContext?
        get() = filterContext()

    override val currentName: String?
        get() {
            val context = filterContext()
            return context.parent?.currentName ?: context.currentName
        }

    /*
     *******************************************************************************************************************
     * Public API, token state overrides
     *******************************************************************************************************************
     */

    override fun clearCurrentToken() {
        if (myCurrentToken != null) {
            lastClearedToken = myCurrentToken
            myCurrentToken = null
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, iteration over token stream
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextToken(): CirJsonToken? {
        if (!myIsAllowingMultipleMatches && myCurrentToken != null && myExposedContext != null) {
            if (myCurrentToken!!.isScalarValue && !myHeadContext.isStartHandled &&
                    myInclusion == TokenFilter.Inclusion.ONLY_INCLUDE_ALL && myItemFilter == TokenFilter.INCLUDE_ALL) {
                myCurrentToken = null
                return null
            }
        }

        var context = myExposedContext

        if (context != null) {
            while (true) {
                var token = context!!.nextTokenToRead()

                if (token != null) {
                    return token.also { myCurrentToken = it }
                }

                if (context === myHeadContext) {
                    myExposedContext = null

                    if (context.isInArray) {
                        token = delegate.currentToken().also { myCurrentToken = it }

                        if (token == CirJsonToken.END_ARRAY) {
                            myHeadContext = myHeadContext.parent!!
                            myItemFilter = myHeadContext.filter
                        }

                        return token
                    }

                    token = delegate.currentToken()

                    if (token == CirJsonToken.END_OBJECT) {
                        myHeadContext = myHeadContext.parent!!
                        myItemFilter = myHeadContext.filter
                    }

                    if (token != CirJsonToken.CIRJSON_ID_PROPERTY_NAME && token != CirJsonToken.PROPERTY_NAME) {
                        return token.also { myCurrentToken = it }
                    }

                    break
                }

                context = myHeadContext.findChildOf(context)
                myExposedContext = context

                if (context == null) {
                    throw constructReadException("Unexpected problem: chain of filtered context broken, token: ${null}")
                }
            }
        }

        var token = delegate.nextToken()

        if (token == null) {
            myCurrentToken = null
            return null
        }

        var filter: TokenFilter?

        when (token.id) {
            CirJsonTokenId.ID_START_ARRAY -> {
                filter = myItemFilter

                if (filter === TokenFilter.INCLUDE_ALL) {
                    myHeadContext = myHeadContext.createChildArrayContext(filter, null, true)
                    return token.also { myCurrentToken = it }
                }

                if (filter == null) {
                    delegate.skipChildren()
                    return nextToken2()
                }

                filter = myHeadContext.checkValue(filter)

                if (filter == null) {
                    delegate.skipChildren()
                    return nextToken2()
                }

                if (filter !== TokenFilter.INCLUDE_ALL) {
                    filter = filter.filterStartArray()
                }

                myItemFilter = filter

                if (filter === TokenFilter.INCLUDE_ALL ||
                        filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
                    myHeadContext = myHeadContext.createChildArrayContext(filter, null, true)
                    return token.also { myCurrentToken = it }
                }

                myHeadContext = myHeadContext.createChildArrayContext(filter, null, false)

                if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
                    token = nextTokenWithBuffering(myHeadContext)

                    if (token != null) {
                        return token.also { myCurrentToken = it }
                    }
                }
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                filter = myItemFilter

                if (filter === TokenFilter.INCLUDE_ALL) {
                    myHeadContext = myHeadContext.createChildObjectContext(filter, null, true)
                    return token.also { myCurrentToken = it }
                }

                if (filter == null) {
                    delegate.skipChildren()
                    return nextToken2()
                }

                filter = myHeadContext.checkValue(filter)

                if (filter == null) {
                    delegate.skipChildren()
                    return nextToken2()
                }

                if (filter !== TokenFilter.INCLUDE_ALL) {
                    filter = filter.filterStartObject()
                }

                myItemFilter = filter

                if (filter === TokenFilter.INCLUDE_ALL ||
                        filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
                    myHeadContext = myHeadContext.createChildObjectContext(filter, null, true)
                    return token.also { myCurrentToken = it }
                }

                myHeadContext = myHeadContext.createChildObjectContext(filter, null, false)

                if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
                    token = nextTokenWithBuffering(myHeadContext)

                    if (token != null) {
                        return token.also { myCurrentToken = it }
                    }
                }
            }

            CirJsonTokenId.ID_END_ARRAY, CirJsonTokenId.ID_END_OBJECT -> {
                val returnEnd = myHeadContext.isStartHandled
                filter = myHeadContext.filter

                if (filter != null && filter !== TokenFilter.INCLUDE_ALL) {
                    if (token.id == CirJsonTokenId.ID_END_ARRAY) {
                        filter.filterFinishArray()
                    } else {
                        filter.filterFinishObject()
                    }
                }

                myHeadContext = myHeadContext.parent!!
                myItemFilter = myHeadContext.filter

                if (returnEnd) {
                    return token.also { myCurrentToken = it }
                }
            }

            CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME, CirJsonTokenId.ID_PROPERTY_NAME -> {
                val name = delegate.currentName!!
                filter = myHeadContext.setPropertyName(name)

                if (filter === TokenFilter.INCLUDE_ALL) {
                    myItemFilter = filter
                    return token.also { myCurrentToken = it }
                }

                if (filter == null) {
                    delegate.nextToken()
                    delegate.skipChildren()
                    return nextToken2()
                }

                filter = filter.includeProperty(name)

                if (filter == null) {
                    delegate.nextToken()
                    delegate.skipChildren()
                    return nextToken2()
                }

                myItemFilter = filter

                if (filter === TokenFilter.INCLUDE_ALL) {
                    if (verifyAllowedMatches()) {
                        if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
                            return token.also { myCurrentToken = it }
                        }
                    } else {
                        delegate.nextToken()
                        delegate.skipChildren()
                    }
                }

                if (myInclusion != TokenFilter.Inclusion.ONLY_INCLUDE_ALL) {
                    token = nextTokenWithBuffering(myHeadContext)

                    if (token != null) {
                        return token.also { myCurrentToken = it }
                    }
                }
            }

            else -> {
                filter = myItemFilter

                if (filter === TokenFilter.INCLUDE_ALL) {
                    return token.also { myCurrentToken = it }
                }

                if (filter != null) {
                    filter = myHeadContext.checkValue(filter)

                    if (filter === TokenFilter.INCLUDE_ALL || filter != null && filter.includeValue(delegate)) {
                        return token.also { myCurrentToken = it }
                    }
                }
            }
        }

        return nextToken2()
    }

    /**
     * Offlined handling for cases where there was no buffered token to return, and the token read next could not be
     * returned as-is, at least not yet, but where we have not yet established that buffering is needed.
     */
    @Throws(CirJacksonException::class)
    protected fun nextToken2(): CirJsonToken? {
        while (true) {
            var token = delegate.nextToken()

            if (token == null) {
                myCurrentToken = null
                return null
            }

            var filter: TokenFilter?

            when (val id = token.id) {
                CirJsonTokenId.ID_START_ARRAY -> {
                    filter = myItemFilter

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        myHeadContext = myHeadContext.createChildArrayContext(filter, null, true)
                        return token.also { myCurrentToken = it }
                    }

                    if (filter == null) {
                        delegate.skipChildren()
                        continue
                    }

                    filter = myHeadContext.checkValue(filter)

                    if (filter == null) {
                        delegate.skipChildren()
                        continue
                    }

                    if (filter !== TokenFilter.INCLUDE_ALL) {
                        filter = filter.filterStartArray()
                    }

                    myItemFilter = filter

                    if (filter === TokenFilter.INCLUDE_ALL ||
                            filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
                        myHeadContext = myHeadContext.createChildArrayContext(filter, null, true)
                        return token.also { myCurrentToken = it }
                    }

                    myHeadContext = myHeadContext.createChildArrayContext(filter, null, false)

                    if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
                        token = nextTokenWithBuffering(myHeadContext)

                        if (token != null) {
                            return token.also { myCurrentToken = it }
                        }
                    }

                    continue
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    filter = myItemFilter

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        myHeadContext = myHeadContext.createChildObjectContext(filter, null, true)
                        return token.also { myCurrentToken = it }
                    }

                    if (filter == null) {
                        delegate.skipChildren()
                        continue
                    }

                    filter = myHeadContext.checkValue(filter)

                    if (filter == null) {
                        delegate.skipChildren()
                        continue
                    }

                    if (filter !== TokenFilter.INCLUDE_ALL) {
                        filter = filter.filterStartObject()
                    }

                    myItemFilter = filter

                    if (filter === TokenFilter.INCLUDE_ALL ||
                            filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
                        myHeadContext = myHeadContext.createChildObjectContext(filter, null, true)
                        return token.also { myCurrentToken = it }
                    }

                    myHeadContext = myHeadContext.createChildObjectContext(filter, null, false)

                    if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
                        token = nextTokenWithBuffering(myHeadContext)

                        if (token != null) {
                            return token.also { myCurrentToken = it }
                        }
                    }

                    continue
                }

                CirJsonTokenId.ID_END_ARRAY, CirJsonTokenId.ID_END_OBJECT -> {
                    val returnEnd = myHeadContext.isStartHandled
                    filter = myHeadContext.filter

                    if (filter != null && filter != TokenFilter.INCLUDE_ALL) {
                        val includeEmpty: Boolean

                        if (id == CirJsonTokenId.ID_END_ARRAY) {
                            includeEmpty = filter.includeEmptyArray(myHeadContext.isIndexValid)
                            filter.filterFinishArray()
                        } else {
                            includeEmpty = filter.includeEmptyObject(myHeadContext.hasCurrentName)
                            filter.filterFinishObject()
                        }

                        if (includeEmpty) {
                            return nextBuffered(myHeadContext)
                        }

                        myHeadContext = myHeadContext.parent!!
                        myItemFilter = myHeadContext.filter

                        if (returnEnd) {
                            return token.also { myCurrentToken = it }
                        }

                        continue
                    }
                }

                CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME, CirJsonTokenId.ID_PROPERTY_NAME -> {
                    val name = delegate.currentName!!
                    filter = myHeadContext.setPropertyName(name)

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        myItemFilter = filter
                        return token.also { myCurrentToken = it }
                    }

                    if (filter == null) {
                        delegate.nextToken()
                        delegate.skipChildren()
                        continue
                    }

                    filter = filter.includeProperty(name)

                    if (filter == null) {
                        delegate.nextToken()
                        delegate.skipChildren()
                        continue
                    }

                    myItemFilter = filter

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        if (verifyAllowedMatches()) {
                            if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
                                return token.also { myCurrentToken = it }
                            }
                        } else {
                            delegate.nextToken()
                            delegate.skipChildren()
                        }

                        continue
                    }

                    if (myInclusion != TokenFilter.Inclusion.ONLY_INCLUDE_ALL) {
                        token = nextTokenWithBuffering(myHeadContext)

                        if (token != null) {
                            return token.also { myCurrentToken = it }
                        }
                    }

                    continue
                }

                else -> {
                    filter = myItemFilter

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        return token.also { myCurrentToken = it }
                    }

                    if (filter != null) {
                        filter = myHeadContext.checkValue(filter)

                        if (filter === TokenFilter.INCLUDE_ALL || filter != null && filter.includeValue(delegate)) {
                            return token.also { myCurrentToken = it }
                        }
                    }

                    continue
                }
            }
        }
    }

    /**
     * Method called when a new potentially included context is found.
     */
    @Throws(CirJacksonException::class)
    protected fun nextTokenWithBuffering(bufferedRoot: TokenFilterContext): CirJsonToken? {
        while (true) {
            val token = delegate.nextToken() ?: return null
            var filter: TokenFilter?

            when (val id = token.id) {
                CirJsonTokenId.ID_START_ARRAY -> {
                    filter = myHeadContext.checkValue(myItemFilter!!)

                    if (filter == null) {
                        delegate.skipChildren()
                        continue
                    }

                    if (filter !== TokenFilter.INCLUDE_ALL) {
                        filter = filter.filterStartArray()
                    }

                    myItemFilter = filter

                    if (filter === TokenFilter.INCLUDE_ALL ||
                            filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
                        myHeadContext = myHeadContext.createChildArrayContext(filter, null, true)
                        return nextBuffered(bufferedRoot)
                    }

                    myHeadContext = myHeadContext.createChildArrayContext(filter, null, false)
                    continue
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    filter = myHeadContext.checkValue(myItemFilter!!)

                    if (filter == null) {
                        delegate.skipChildren()
                        continue
                    }

                    if (filter !== TokenFilter.INCLUDE_ALL) {
                        filter = filter.filterStartObject()
                    }

                    myItemFilter = filter

                    if (filter === TokenFilter.INCLUDE_ALL ||
                            filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
                        myHeadContext = myHeadContext.createChildObjectContext(filter, null, true)
                        return nextBuffered(bufferedRoot)
                    }

                    myHeadContext = myHeadContext.createChildObjectContext(filter, null, false)
                    continue
                }

                CirJsonTokenId.ID_END_ARRAY, CirJsonTokenId.ID_END_OBJECT -> {
                    filter = myHeadContext.filter

                    if (filter != null && filter !== TokenFilter.INCLUDE_ALL) {
                        val includeEmpty: Boolean

                        if (id == CirJsonTokenId.ID_END_ARRAY) {
                            includeEmpty = filter.includeEmptyArray(myHeadContext.isIndexValid)
                            filter.filterFinishArray()
                        } else {
                            includeEmpty = filter.includeEmptyObject(myHeadContext.hasCurrentName)
                            filter.filterFinishObject()
                        }

                        if (includeEmpty) {
                            return nextBuffered(bufferedRoot)
                        }
                    }

                    val gotEnd = myHeadContext === bufferedRoot
                    val returnEnd = gotEnd && myHeadContext.isStartHandled

                    myHeadContext = myHeadContext.parent!!
                    myItemFilter = myHeadContext.filter

                    if (returnEnd) {
                        return token
                    }

                    if (gotEnd) {
                        return null
                    }

                    continue
                }

                CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME, CirJsonTokenId.ID_PROPERTY_NAME -> {
                    val name = delegate.currentName!!
                    filter = myHeadContext.setPropertyName(name)

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        myItemFilter = filter
                        return nextBuffered(bufferedRoot)
                    }

                    if (filter == null) {
                        delegate.nextToken()
                        delegate.skipChildren()
                        continue
                    }

                    filter = filter.includeProperty(name)

                    if (filter == null) {
                        delegate.nextToken()
                        delegate.skipChildren()
                        continue
                    }

                    myItemFilter = filter

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        if (verifyAllowedMatches()) {
                            return nextBuffered(bufferedRoot)
                        } else {
                            myItemFilter = myHeadContext.setPropertyName(name)
                        }
                    }

                    continue
                }

                else -> {
                    filter = myItemFilter

                    if (filter === TokenFilter.INCLUDE_ALL) {
                        return nextBuffered(bufferedRoot)
                    }

                    if (filter != null) {
                        filter = myHeadContext.checkValue(filter)

                        if (filter === TokenFilter.INCLUDE_ALL || filter != null && filter.includeValue(delegate)) {
                            if (verifyAllowedMatches()) {
                                return nextBuffered(bufferedRoot)
                            }
                        }
                    }
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun nextBuffered(bufferedRoot: TokenFilterContext): CirJsonToken {
        myExposedContext = bufferedRoot
        var context: TokenFilterContext? = bufferedRoot
        var token = context!!.nextTokenToRead()

        if (token != null) {
            return token
        }

        while (true) {
            if (context === myHeadContext) {
                throw constructReadException("Internal error: failed to locate expected buffered tokens")
            }

            context = myExposedContext!!.findChildOf(context!!)
            myExposedContext = context

            if (context == null) {
                throw constructReadException("Unexpected problem: chain of filtered context broken")
            }

            token = context.nextTokenToRead()

            if (token != null) {
                return token
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun verifyAllowedMatches(): Boolean {
        return if (matchCount == 0 || myIsAllowingMultipleMatches) {
            ++matchCount
            true
        } else {
            false
        }
    }

    @Throws(CirJacksonException::class)
    override fun nextValue(): CirJsonToken? {
        val token = nextToken()

        return if (token != CirJsonToken.CIRJSON_ID_PROPERTY_NAME && token != CirJsonToken.PROPERTY_NAME) {
            token
        } else {
            nextToken()
        }
    }

    @Throws(CirJacksonException::class)
    override fun skipChildren(): CirJsonParser {
        if (myCurrentToken != CirJsonToken.START_ARRAY && myCurrentToken != CirJsonToken.START_OBJECT) {
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
     * Public API, access to token information, text; cannot simply delegate due to access via property names
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    override val text: String?
        get() = if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            currentName
        } else {
            delegate.text
        }

    override val isTextCharactersAvailable: Boolean
        get() = if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            false
        } else {
            delegate.isTextCharactersAvailable
        }

    @get:Throws(CirJacksonException::class)
    override val textCharacters: CharArray?
        get() = if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            currentName!!.toCharArray()
        } else {
            delegate.textCharacters
        }

    @get:Throws(CirJacksonException::class)
    override val textLength: Int
        get() = if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            currentName!!.length
        } else {
            delegate.textLength
        }

    @get:Throws(CirJacksonException::class)
    override val textOffset: Int
        get() = if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            0
        } else {
            delegate.textOffset
        }

    @get:Throws(CirJacksonException::class)
    override val valueAsString: String?
        get() = if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            currentName
        } else {
            delegate.valueAsString
        }

    @Throws(CirJacksonException::class)
    override fun getValueAsString(defaultValue: String?): String? {
        return if (myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                myCurrentToken == CirJsonToken.PROPERTY_NAME) {
            currentName
        } else {
            delegate.getValueAsString(defaultValue)
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, iteration over token stream that CAN NOT just delegate
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextName(): String? {
        val token = nextToken()
        return if (token == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || token == CirJsonToken.PROPERTY_NAME) {
            currentName
        } else {
            null
        }
    }

    @Throws(CirJacksonException::class)
    override fun nextName(string: SerializableString): Boolean {
        val token = nextToken()
        return (token == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || token == CirJsonToken.PROPERTY_NAME) &&
                string.value == currentName
    }

    @Throws(CirJacksonException::class)
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
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun filterContext(): TokenFilterContext {
        return myExposedContext ?: myHeadContext
    }

}