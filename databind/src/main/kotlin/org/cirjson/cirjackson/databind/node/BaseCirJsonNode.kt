package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.databind.CirJsonNode

abstract class BaseCirJsonNode protected constructor() : CirJsonNode() {

    /*
     *******************************************************************************************************************
     * Defaulting for introspection
     *******************************************************************************************************************
     */

    override val isEmbeddedValue: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Support for traversal-as-stream
     *******************************************************************************************************************
     */

    override fun traverse(objectReadContext: ObjectReadContext): CirJsonParser {
        TODO("Not yet implemented")
    }

    override val numberType: CirJsonParser.NumberType?
        get() = TODO("Not yet implemented")

}