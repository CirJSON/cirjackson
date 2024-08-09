package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJsonGenerator

/**
 * Strategy class that can be implemented to specify actual inclusion/exclusion criteria for filtering, used by
 * [FilteringGeneratorDelegate].
 */
open class TokenFilter {

    /*
     *******************************************************************************************************************
     * Public API, structured values
     *******************************************************************************************************************
     */

    /**
     * Method called to check whether Object value at current output location should be included in output. Three kinds
     * of return values may be used as follows:
     *
     * * `null` to indicate that the Object should be skipped
     *
     * * [INCLUDE_ALL] to indicate that the Object should be included completely in output
     *
     * * Any other [TokenFilter] implementation (possibly this one) to mean that further inclusion calls on return
     * filter object need to be made on contained properties, as necessary. [filterFinishObject] will also be called on
     * returned filter object
     *
     * Default implementation returns `this`, which means that checks are made recursively for properties of the Object
     * to determine possible inclusion.
     *
     * @return TokenFilter to use for further calls within Object, unless return value is `null` or [INCLUDE_ALL] (which
     * have simpler semantics)
     */
    open fun filterStartObject(): TokenFilter? {
        return this
    }

    /**
     * Method called to check whether Array value at current output location should be included in output. Three kinds
     * of return values may be used as follows:
     *
     * * `null` to indicate that the Array should be skipped
     *
     * * [INCLUDE_ALL] to indicate that the Array should be included completely in output
     *
     * * Any other [TokenFilter] implementation (possibly this one) to mean that further inclusion calls on return
     * filter object need to be made on contained properties, as necessary. [filterFinishArray] will also be called on
     * returned filter object
     *
     * Default implementation returns `this`, which means that checks are made recursively for properties of the Object
     * to determine possible inclusion.
     *
     * @return TokenFilter to use for further calls within Array, unless return value is `null` or [INCLUDE_ALL] (which
     * have simpler semantics)
     */
    open fun filterStartArray(): TokenFilter? {
        return this
    }

    /**
     * Method called to indicate that output of non-filtered Object (one that may have been included either completely,
     * or in part) is completed, in cases where filter other that [INCLUDE_ALL] was returned. This occurs when
     * [CirJsonGenerator.writeEndObject] is called.
     */
    open fun filterEndObject() {
        // no-op
    }

    /**
     * Method called to indicate that output of non-filtered Array (one that may have been included either completely,
     * or in part) is completed, in cases where filter other that [INCLUDE_ALL] was returned. This occurs when
     * [CirJsonGenerator.writeEndArray] is called.
     */
    open fun filterEndArray() {
        // no-op
    }

    /*
     *******************************************************************************************************************
     * Public API, properties/elements
     *******************************************************************************************************************
     */

    /**
     * Method called to check whether property value with specified name, at current output location, should be
     * included in output. Three kinds of return values may be used as follows:
     *
     * * `null` to indicate that the property and its value should be skipped
     *
     * * [INCLUDE_ALL] to indicate that the property and its value should be included completely in output
     *
     * * Any other [TokenFilter] implementation (possibly this one) to mean that further inclusion calls on returned
     * filter object need to be made as necessary, to determine inclusion.
     *
     * The default implementation simply returns `this` to continue calling methods on this filter object, without
     * full inclusion or exclusion.
     *
     * @param name Name of Object property to check
     *
     * @return TokenFilter to use for further calls within property value, unless return value is `null` or
     * [INCLUDE_ALL] (which have simpler semantics)
     */
    open fun includeProperty(name: String): TokenFilter? {
        return this
    }

    /**
     * Method called to check whether array element with specified index (zero-based), at current output location,
     * should be included in output. Three kinds of return values may be used as follows:
     *
     * * `null` to indicate that the Array element should be skipped
     *
     * * [INCLUDE_ALL] to indicate that the Array element should be included completely in output
     *
     * * Any other [TokenFilter] implementation (possibly this one) to mean that further inclusion calls on returned
     * filter object need to be made as necessary, to determine inclusion.
     *
     * The default implementation simply returns `this` to continue calling methods on this filter object, without full
     * inclusion or exclusion.
     *
     * @param index Array element index (0-based) to check
     *
     * @return TokenFilter to use for further calls within element value, unless return value is `null` or [INCLUDE_ALL]
     * (which have simpler semantics)
     */
    open fun includeElement(index: Int): TokenFilter? {
        return this
    }

    /**
     * Method called to check whether root-level value, at current output location, should be included in output. Three
     * kinds of return values may be used as follows:
     *
     * * `null` to indicate that the root value should be skipped
     *
     * * [INCLUDE_ALL] to indicate that the root value should be included completely in output
     *
     * * Any other [TokenFilter] implementation (possibly this one) to mean that further inclusion calls on returned
     * filter object need to be made as necessary, to determine inclusion.
     *
     * The default implementation simply returns `this` to continue calling methods on this filter object, without full
     * inclusion or exclusion.
     *
     * @param index Index (0-based) of the root value to check
     *
     * @return TokenFilter to use for further calls within root value, unless return value is `null` or [INCLUDE_ALL]
     * (which have simpler semantics)
     */
    open fun includeRootValue(index: Int): TokenFilter? {
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, scalar values (being read)
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Other methods
     *******************************************************************************************************************
     */

    /**
     * Overridable default implementation delegated to all scalar value inclusion check methods. The default
     * implementation simply includes all leaf values.
     *
     * @return Whether all leaf scalar values should be included (`true`) or not (`false`)
     */
    protected open fun includeScalar(): Boolean {
        return true
    }

    /*
     *******************************************************************************************************************
     * Overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "TokenFilter.INCLUDE_ALL".takeIf { this === INCLUDE_ALL } ?: super.toString()
    }

    /**
     * Enumeration that controls how TokenFilter return values are interpreted.
     */
    enum class Inclusion {

        /**
         * Tokens will only be included if the filter returns TokenFilter.INCLUDE_ALL
         */
        ONLY_INCLUDE_ALL,

        /**
         * When TokenFilter.INCLUDE_ALL is returned, the corresponding token will be included as well as enclosing
         * tokens up to the root
         */
        INCLUDE_ALL_AND_PATH,

        /**
         * Tokens will be included if any non-null filter is returned. The exception is if a property name returns a
         * non-null filter, but the property value returns a null filter. In this case the property name and value will
         * both be omitted.
         */
        INCLUDE_NON_NULL

    }

    companion object {

        /**
         * Marker value that should be used to indicate inclusion of a structured value (subtree representing Object or
         * Array), or value of a named property (regardless of type). Note that if this instance is returned, it will be
         * used as a marker, and no actual callbacks need to be made. For this reason, it is more efficient to return
         * this instance if the whole subtree is to be included, instead of implementing similar filter functionality
         * explicitly.
         */
        val INCLUDE_ALL = TokenFilter()

    }

}