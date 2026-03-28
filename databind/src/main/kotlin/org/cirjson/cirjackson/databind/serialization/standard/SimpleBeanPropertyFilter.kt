package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.PropertyFilter
import org.cirjson.cirjackson.databind.serialization.PropertyWriter

/**
 * Simple [PropertyFilter] implementation that only uses property name to determine whether to serialize property as is,
 * or to filter it out.
 * 
 * Use of this class as the base implementation for any custom [PropertyFilter] implementations is strongly encouraged,
 * because it can provide default implementation for any methods that may be added in [PropertyFilter] (as unfortunate
 * as additions may be).
 */
open class SimpleBeanPropertyFilter protected constructor() : PropertyFilter {

    override fun snapshot(): PropertyFilter {
        return this
    }

    /*
     *******************************************************************************************************************
     * Methods for subclasses
     *******************************************************************************************************************
     */

    /**
     * Method called to determine whether property will be included (if `true` returned) or filtered out (if `false`
     * returned).
     */
    protected open fun include(writer: BeanPropertyWriter): Boolean {
        return true
    }

    /**
     * Method called to determine whether property will be included (if `true` returned) or filtered out (if `false`
     * returned).
     */
    protected open fun include(writer: PropertyWriter): Boolean {
        return true
    }

    /**
     * Method that defines what to do with container elements (values contained in an array or [Collection]: default
     * implementation simply writes them out.
     */
    protected open fun includeElement(elementValue: Any?): Boolean {
        return true
    }

    /*
     *******************************************************************************************************************
     * PropertyFilter implementation
     *******************************************************************************************************************
     */

    override fun serializeAsProperty(pojo: Any, generator: CirJsonGenerator, context: SerializerProvider,
            writer: PropertyWriter) {
        if (include(writer)) {
            writer.serializeAsProperty(pojo, generator, context)
        } else if (!generator.isAbleOmitProperties) {
            writer.serializeAsOmittedProperty(pojo, generator, context)
        }
    }

    override fun serializeAsElement(value: Any, generator: CirJsonGenerator, context: SerializerProvider,
            writer: PropertyWriter) {
        if (includeElement(value)) {
            writer.serializeAsElement(value, generator, context)
        }
    }

    override fun depositSchemaProperty(writer: PropertyWriter, visitor: CirJsonObjectFormatVisitor,
            context: SerializerProvider) {
        if (include(writer)) {
            writer.depositSchemaProperty(visitor, context)
        }
    }

    /*
     *******************************************************************************************************************
     * Subclasses
     *******************************************************************************************************************
     */

    /**
     * Filter implementation which defaults to filtering out unknown properties and only serializes ones explicitly
     * listed.
     *
     * @property myPropertiesToInclude Set of property names to serialize.
     */
    open class FilterExceptFilter(protected val myPropertiesToInclude: Set<String>) : SimpleBeanPropertyFilter() {

        override fun include(writer: BeanPropertyWriter): Boolean {
            return writer.name in myPropertiesToInclude
        }

        override fun include(writer: PropertyWriter): Boolean {
            return writer.name in myPropertiesToInclude
        }

        companion object {

            val EXCLUDE_ALL = FilterExceptFilter(emptySet())

        }

    }

    /**
     * Filter implementation which defaults to serializing all properties, except for ones explicitly listed to be
     * filtered out.
     *
     * @property myPropertiesToExclude Set of property names to filter out.
     */
    open class SerializeExceptFilter(protected val myPropertiesToExclude: Set<String>) : SimpleBeanPropertyFilter() {

        override fun include(writer: BeanPropertyWriter): Boolean {
            return writer.name !in myPropertiesToExclude
        }

        override fun include(writer: PropertyWriter): Boolean {
            return writer.name !in myPropertiesToExclude
        }

        companion object {

            val INCLUDE_ALL = SerializeExceptFilter(emptySet())

        }

    }

    companion object {

        /*
         ***************************************************************************************************************
         * Construction
         ***************************************************************************************************************
         */

        /**
         * Convenience factory method that will return a "no-op" filter that will simply just serialize all properties
         * that are given, and filter out nothing.
         */
        fun serializeAll(): SimpleBeanPropertyFilter {
            return SerializeExceptFilter.INCLUDE_ALL
        }

        /**
         * Convenience factory method that will return a filter that will simply filter out everything.
         */
        fun filterOutAll(): SimpleBeanPropertyFilter {
            return FilterExceptFilter.EXCLUDE_ALL
        }

        /**
         * Factory method to construct filter that filters out all properties **except** ones includes in set.
         */
        fun filterOutAllExcept(properties: Set<String>): SimpleBeanPropertyFilter {
            return FilterExceptFilter(properties)
        }

        /**
         * Factory method to construct filter that filters out all properties **except** ones listed.
         */
        fun filterOutAllExcept(vararg properties: String): SimpleBeanPropertyFilter {
            return FilterExceptFilter(properties.toSet())
        }

        /**
         * Factory method to construct filter that serialize all properties **except** ones includes in set.
         */
        fun serializeAllExcept(properties: Set<String>): SimpleBeanPropertyFilter {
            return SerializeExceptFilter(properties)
        }

        /**
         * Factory method to construct filter that serialize all properties **except** ones listed.
         */
        fun serializeAllExcept(vararg properties: String): SimpleBeanPropertyFilter {
            return SerializeExceptFilter(properties.toSet())
        }

    }

}