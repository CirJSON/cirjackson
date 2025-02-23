package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.DatabindException

open class CirJsonNodeException protected constructor(val node: CirJsonNode, message: String) :
        DatabindException(message) {

    companion object {

        fun from(node: CirJsonNode, message: String): CirJsonNodeException {
            return CirJsonNodeException(node, message)
        }

    }

}