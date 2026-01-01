package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.ParserMinimalBase
import org.cirjson.cirjackson.databind.CirJsonNode
import java.math.BigDecimal
import java.math.BigInteger

open class TreeTraversingParser(protected val mySource: CirJsonNode, readContext: ObjectReadContext) :
        ParserMinimalBase(readContext) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    override fun streamReadInputSource(): CirJsonNode? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Closing, related
     *******************************************************************************************************************
     */

    override fun closeInput() {
        TODO("Not yet implemented")
    }

    override fun releaseBuffers() {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, traversal
     *******************************************************************************************************************
     */

    override fun nextToken(): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, token accessors
     *******************************************************************************************************************
     */

    override val currentName: String?
        get() = TODO("Not yet implemented")

    override val streamReadContext: TokenStreamContext?
        get() = TODO("Not yet implemented")

    override fun assignCurrentValue(value: Any?) {
        TODO("Not yet implemented")
    }

    override fun currentValue(): Any? {
        TODO("Not yet implemented")
    }

    override fun currentTokenLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun currentLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, access to textual content
     *******************************************************************************************************************
     */

    override val text: String?
        get() = TODO("Not yet implemented")

    override val textCharacters: CharArray?
        get() = TODO("Not yet implemented")

    override val textLength: Int
        get() = TODO("Not yet implemented")

    override val textOffset: Int
        get() = TODO("Not yet implemented")

    override val isTextCharactersAvailable: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Public API, typed non-text access
     *******************************************************************************************************************
     */

    override val numberType: NumberType?
        get() = TODO("Not yet implemented")

    override val bigIntegerValue: BigInteger
        get() = TODO("Not yet implemented")

    override val bigDecimalValue: BigDecimal
        get() = TODO("Not yet implemented")

    override val doubleValue: Double
        get() = TODO("Not yet implemented")

    override val floatValue: Float
        get() = TODO("Not yet implemented")

    override val intValue: Int
        get() = TODO("Not yet implemented")

    override val longValue: Long
        get() = TODO("Not yet implemented")

    override val numberValue: Number
        get() = TODO("Not yet implemented")

    override val isNaN: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Public API, typed binary (base64) access
     *******************************************************************************************************************
     */

    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, id
     *******************************************************************************************************************
     */

    override val idName: String
        get() = TODO("Not yet implemented")

    override val objectId: Any?
        get() = TODO("Not yet implemented")

    override val typeId: Any?
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    override fun handleEOF() {
        TODO("Not yet implemented")
    }

}