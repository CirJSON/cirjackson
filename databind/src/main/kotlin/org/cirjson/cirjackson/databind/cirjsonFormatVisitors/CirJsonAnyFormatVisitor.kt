package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

interface CirJsonAnyFormatVisitor {

    /**
     * Default "empty" implementation, useful as the base to start on; especially as it is guaranteed to implement all
     * the method of the interface, even if new methods are getting added.
     */
    open class Base : CirJsonAnyFormatVisitor

}