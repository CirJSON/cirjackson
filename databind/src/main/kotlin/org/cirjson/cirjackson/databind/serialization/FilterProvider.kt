package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * Interface for objects that provides instances of [PropertyFilter] that match given ids. A provider is configured to
 * be used during serialization, to find filter to used based on id specified by
 * [org.cirjson.cirjackson.annotations.CirJsonFilter] annotation on bean class.
 */
abstract class FilterProvider : Snapshottable<FilterProvider> {

    /**
     * Lookup method used to find [PropertyFilter] that has specified id. Note that id is typically a [String], but is
     * not necessarily limited to that; that is, while standard components use String, custom implementation can choose
     * other kinds of keys.
     *
     * @param filterId ID of the filter to fetch
     * 
     * @param valueToFilter Object being filtered (usually POJO, but may be a [Map] or a container), **if available**;
     * not available when generating schemas.
     *
     * @return Filter to use, if any.
     */
    abstract fun findPropertyFilter(context: SerializerProvider, filterId: Any, valueToFilter: Any?): PropertyFilter?

}