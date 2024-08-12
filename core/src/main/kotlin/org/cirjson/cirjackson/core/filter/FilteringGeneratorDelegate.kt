package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.util.CirJsonGeneratorDelegate

/**
 * Specialized [CirJsonGeneratorDelegate] that allows use of [TokenFilter] for outputting a subset of content that
 * caller tries to generate.
 *
 * @param delegate Generator to delegate calls to
 *
 * @param filter Filter to use
 *
 * @param myInclusion Definition of inclusion criteria
 *
 * @param myIsAllowMultipleMatches Whether to allow multiple matches
 *
 * @property myInclusion Flag that determines whether path leading up to included content should also be automatically
 * included or not. If `false`, no path inclusion is done and only explicitly included entries are output; if `true`
 * then path from main level down to match is also included as necessary.
 *
 * @property myIsAllowMultipleMatches Flag that determines whether filtering will continue after the first match is
 * indicated or not: if `false`, output is based on just the first full match (returning [TokenFilter.INCLUDE_ALL]) and
 * no more checks are made; if `true` then filtering will be applied as necessary until end of content.
 */
open class FilteringGeneratorDelegate(delegate: CirJsonGenerator, filter: TokenFilter,
        protected var myInclusion: TokenFilter.Inclusion?, protected var myIsAllowMultipleMatches: Boolean) :
        CirJsonGeneratorDelegate(delegate, false) {

    /**
     * Object consulted to determine whether to write parts of content generator is asked to write or not.
     */
    var filter = filter
        protected set

    /**
     * Although delegate has its own output context it is not sufficient since we actually have to keep track of
     * excluded (filtered out) structures as well as ones delegate actually outputs.
     */
    var filterContext = TokenFilterContext.createRootContext(filter)
        protected set

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

}