package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.ParserMinimalBase
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Facade over [CirJsonNode] that implements [CirJsonParser] to allow accessing contents of CirJSON tree in alternate
 * form (stream of tokens). Useful when a streaming source is expected by code, such as data binding functionality.
 */
open class TreeTraversingParser(protected val mySource: CirJsonNode, readContext: ObjectReadContext) :
        ParserMinimalBase(readContext) {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Traversal context within tree
     */
    protected var myNodeCursor: NodeCursor? = NodeCursor.createRoot(mySource)

    /*
     *******************************************************************************************************************
     * State
     *******************************************************************************************************************
     */

    /**
     * Flag that indicates whether parser is closed or not. Gets set when parser is either closed by explicit call
     * ([close]) or when end-of-input is reached.
     */
    protected var myClosed = false

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(node: CirJsonNode) : this(node, ObjectReadContext.empty())

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    override val streamReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>
        get() = DEFAULT_READ_CAPABILITIES

    override fun streamReadInputSource(): CirJsonNode {
        return mySource
    }

    /*
     *******************************************************************************************************************
     * Closing, related
     *******************************************************************************************************************
     */

    override fun close() {
        if (myClosed) {
            return
        }

        myClosed = true
        myNodeCursor = null
        myCurrentToken = null
    }

    override fun closeInput() {
        // No-op
    }

    override fun releaseBuffers() {
        // No-op
    }

    /*
     *******************************************************************************************************************
     * Public API, traversal
     *******************************************************************************************************************
     */

    override fun nextToken(): CirJsonToken? {
        myCurrentToken = myNodeCursor!!.nextToken()

        when (myCurrentToken) {
            null -> {
                myClosed = true
                return null
            }

            CirJsonToken.START_OBJECT -> {
                myNodeCursor = myNodeCursor!!.startObject()
            }

            CirJsonToken.START_ARRAY -> {
                myNodeCursor = myNodeCursor!!.startArray()
            }

            CirJsonToken.END_OBJECT, CirJsonToken.END_ARRAY -> {
                myNodeCursor = myNodeCursor!!.parent
            }

            else -> {}
        }

        return myCurrentToken
    }

    override fun skipChildren(): CirJsonParser {
        if (myCurrentToken == CirJsonToken.START_OBJECT) {
            myNodeCursor = myNodeCursor!!.parent
            myCurrentToken = CirJsonToken.END_OBJECT
        } else if (myCurrentToken == CirJsonToken.START_ARRAY) {
            myNodeCursor = myNodeCursor!!.parent
            myCurrentToken = CirJsonToken.END_ARRAY
        }

        return this
    }

    override var isClosed: Boolean
        get() = myClosed
        set(value) {
            super.isClosed = value
        }

    /*
     *******************************************************************************************************************
     * Public API, token accessors
     *******************************************************************************************************************
     */

    override val currentName: String?
        get() {
            var cursor = myNodeCursor

            if (myCurrentToken == CirJsonToken.START_OBJECT || myCurrentToken == CirJsonToken.START_ARRAY) {
                cursor = cursor!!.parent
            }

            return cursor?.currentName
        }

    override val streamReadContext: TokenStreamContext?
        get() = myNodeCursor

    override fun assignCurrentValue(value: Any?) {
        myNodeCursor!!.assignCurrentValue(value)
    }

    override fun currentValue(): Any? {
        return myNodeCursor!!.currentValue()
    }

    override fun currentTokenLocation(): CirJsonLocation {
        return CirJsonLocation.NA
    }

    override fun currentLocation(): CirJsonLocation {
        return CirJsonLocation.NA
    }

    /*
     *******************************************************************************************************************
     * Public API, access to textual content
     *******************************************************************************************************************
     */

    override val text: String?
        get() = when (val current = myCurrentToken) {
            null -> null
            CirJsonToken.PROPERTY_NAME, CirJsonToken.CIRJSON_ID_PROPERTY_NAME -> myNodeCursor!!.currentName
            CirJsonToken.VALUE_STRING -> currentNode()!!.textValue()
            CirJsonToken.VALUE_NUMBER_INT, CirJsonToken.VALUE_NUMBER_FLOAT -> currentNode()!!.numberValue()!!.toString()
            CirJsonToken.VALUE_EMBEDDED_OBJECT -> currentNode()?.takeIf { it.isBinary }?.asText() ?: current.token
            else -> current.token
        }

    override val textCharacters: CharArray
        get() = text!!.toCharArray()

    override val textLength: Int
        get() = text!!.length

    override val textOffset: Int
        get() = 0

    override val isTextCharactersAvailable: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * Public API, typed non-text access
     *******************************************************************************************************************
     */

    override val numberType: NumberType?
        get() = (currentNode() as? NumericNode)?.numberType

    override val numberTypeFP: NumberTypeFP?
        get() = when (numberType) {
            NumberType.BIG_DECIMAL -> NumberTypeFP.BIG_DECIMAL
            NumberType.DOUBLE -> NumberTypeFP.DOUBLE64
            NumberType.FLOAT -> NumberTypeFP.FLOAT32
            else -> NumberTypeFP.UNKNOWN
        }

    @get:Throws(InputCoercionException::class)
    override val bigIntegerValue: BigInteger
        get() = currentNumericNode(NUMBER_BIG_INTEGER).bigIntegerValue()

    @get:Throws(InputCoercionException::class)
    override val bigDecimalValue: BigDecimal
        get() = currentNumericNode(NUMBER_BIG_DECIMAL).bigDecimalValue()

    @get:Throws(InputCoercionException::class)
    override val doubleValue: Double
        get() = currentNumericNode(NUMBER_DOUBLE).doubleValue()

    @get:Throws(InputCoercionException::class)
    override val floatValue: Float
        get() = currentNumericNode(NUMBER_FLOAT).floatValue()

    @get:Throws(InputCoercionException::class)
    override val intValue: Int
        get() = currentNumericNode(NUMBER_INT).takeIf { it.canConvertToInt() }?.intValue() ?: reportOverflowInt()

    @get:Throws(InputCoercionException::class)
    override val longValue: Long
        get() = currentNumericNode(NUMBER_INT).takeIf { it.canConvertToLong() }?.longValue() ?: reportOverflowLong()

    @get:Throws(InputCoercionException::class)
    override val numberValue: Number
        get() = currentNumericNode(-1).numberValue()!!

    override val embeddedObject: Any?
        get() {
            if (myClosed) {
                return null
            }

            val node = currentNode() ?: return null

            if (node.isPojo) {
                return (node as POJONode).pojo
            }

            if (node.isBinary) {
                return (node as BinaryNode).binaryValue()
            }

            return null
        }

    override val isNaN: Boolean
        get() {
            if (myClosed) {
                return false
            }

            return (currentNode() as? NumericNode)?.isNaN() ?: false
        }

    /*
     *******************************************************************************************************************
     * Public API, typed binary (base64) access
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray? {
        val node = currentNode() ?: return null

        return if (node is TextNode) {
            node.getBinaryValue(base64Variant)
        } else {
            node.binaryValue()
        }
    }

    @Throws(CirJacksonException::class)
    override fun readBinaryValue(base64Variant: Base64Variant, output: OutputStream): Int {
        val data = getBinaryValue(base64Variant) ?: return 0

        try {
            output.write(data, 0, data.size)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return data.size
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

    protected open fun currentNode(): CirJsonNode? {
        if (myClosed || myNodeCursor == null) {
            return null
        }

        return myNodeCursor!!.currentNode()
    }

    protected open fun currentNumericNode(targetNumberType: Int): CirJsonNode {
        val node = currentNode()

        if (node?.isNumber ?: true) {
            val token = node?.asToken()
            throw constructNotNumericType(token, targetNumberType)
        }

        return node
    }

    override fun handleEOF() {
        return throwInternal()
    }

}