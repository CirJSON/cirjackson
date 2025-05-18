package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.core.CirJsonParser

interface CirJsonNumberFormatVisitor : CirJsonValueFormatVisitor {

    /**
     * Method called to provide a more exact type of number being serialized (regardless of logical type, which may be
     * [java.util.Date] or [Enum], in addition to actual numeric types like [Int]).
     */
    fun numberType(type: CirJsonParser.NumberType)

    /**
     * Default "empty" implementation, useful as the base to start on; especially as it is guaranteed to implement all
     * the method of the interface, even if new methods are getting added.
     */
    open class Base : CirJsonValueFormatVisitor.Base(), CirJsonNumberFormatVisitor {

        override fun numberType(type: CirJsonParser.NumberType) {}

    }

}