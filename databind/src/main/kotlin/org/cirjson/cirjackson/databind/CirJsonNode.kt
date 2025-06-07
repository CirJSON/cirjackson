package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJsonPointer
import org.cirjson.cirjackson.core.TreeNode

abstract class CirJsonNode protected constructor() : CirJacksonSerializable.Base(), TreeNode, Iterable<CirJsonNode> {

    /*
     *******************************************************************************************************************
     * TreeNode implementation
     *******************************************************************************************************************
     */

    override val size: Int
        get() = TODO("Not yet implemented")

    final override val isValueNode: Boolean
        get() = TODO("Not yet implemented")

    final override val isContainerNode: Boolean
        get() = TODO("Not yet implemented")

    override val isMissingNode: Boolean
        get() = TODO("Not yet implemented")

    override val isArray: Boolean
        get() = TODO("Not yet implemented")

    override val isObject: Boolean
        get() = TODO("Not yet implemented")

    override val propertyNames: Iterator<String>
        get() = TODO("Not yet implemented")

    final override fun at(pointer: CirJsonPointer): TreeNode {
        TODO("Not yet implemented")
    }

    final override fun at(pointerExpression: String): TreeNode {
        TODO("Not yet implemented")
    }

    final override val isNull: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Public API, container access
     *******************************************************************************************************************
     */

    final override fun iterator(): Iterator<CirJsonNode> {
        TODO("Not yet implemented")
    }

}