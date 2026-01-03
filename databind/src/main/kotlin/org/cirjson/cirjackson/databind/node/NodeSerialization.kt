package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper
import java.io.*
import kotlin.math.min

/**
 * Helper value class only used during JDK serialization: contains CirJSON as `ByteArray`
 */
internal class NodeSerialization : Serializable, Externalizable {

    private var myCirJson: ByteArray?

    constructor() {
        myCirJson = null
    }

    constructor(data: ByteArray) {
        myCirJson = data
    }

    @Serial
    private fun readResolve(): Any {
        try {
            return bytesToNode(myCirJson!!)
        } catch (e: CirJacksonException) {
            throw IllegalArgumentException("Failed to JDK deserialize `CirJsonNode` value: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    override fun writeExternal(out: ObjectOutput) {
        out.writeInt(myCirJson!!.size)
        out.write(myCirJson!!)
    }

    @Throws(IOException::class)
    override fun readExternal(input: ObjectInput) {
        myCirJson = read(input, input.readInt())
    }

    @Throws(IOException::class)
    private fun read(input: ObjectInput, expectedLength: Int): ByteArray {
        var realExpectedLength = expectedLength

        if (realExpectedLength <= LONGEST_EAGER_ALLOC) {
            val result = ByteArray(realExpectedLength)
            input.readFully(result)
        }

        ByteArrayBuilder(LONGEST_EAGER_ALLOC).use {
            var buffer = it.resetAndGetFirstSegment()
            var outOffset = 0

            while (true) {
                val toRead = min(buffer.size - outOffset, realExpectedLength)
                input.readFully(buffer, 0, toRead)
                realExpectedLength -= toRead
                outOffset += toRead

                if (realExpectedLength == 0) {
                    return it.completeAndCoalesce(outOffset)
                }

                if (outOffset == buffer.size) {
                    buffer = it.finishCurrentSegment()
                    outOffset = 0
                }
            }
        }
    }

    companion object {

        const val LONGEST_EAGER_ALLOC = 100_000

        private val CIRJSON_MAPPER = CirJsonMapper.shared()

        private val NODE_READER = CIRJSON_MAPPER.readerFor(CirJsonNode::class)

        fun from(obj: Any?): NodeSerialization {
            return NodeSerialization(valueToBytes(obj))
        }

        @Throws(CirJacksonException::class)
        private fun valueToBytes(value: Any?): ByteArray {
            return CIRJSON_MAPPER.writeValueAsBytes(value)
        }

        @Throws(CirJacksonException::class)
        fun bytesToNode(cirjson: ByteArray): CirJsonNode {
            return NODE_READER.readValue(cirjson)
        }

    }

}