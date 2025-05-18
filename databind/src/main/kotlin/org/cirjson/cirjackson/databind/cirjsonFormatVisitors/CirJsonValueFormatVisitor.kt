package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

interface CirJsonValueFormatVisitor {

    /**
     * Method called to indicate the configured format for the value type being visited.
     */
    fun format(format: CirJsonValueFormat)

    /**
     * Method called to indicate enumerated ([String]) values type being visited can take as values.
     */
    fun enumTypes(enum: Set<String>)

    /**
     * Default "empty" implementation, useful as the base to start on; especially as it is guaranteed to implement all
     * the method of the interface, even if new methods are getting added.
     */
    open class Base : CirJsonValueFormatVisitor {

        override fun format(format: CirJsonValueFormat) {}

        override fun enumTypes(enum: Set<String>) {}

    }

}