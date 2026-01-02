package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.util.RawValue
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Helper class used for creating [CirJsonNode] values directly as part of serialization.
 */
open class TreeBuildingGenerator protected constructor(protected val myObjectWriteContext: ObjectWriteContext,
        protected val myNodeFactory: CirJsonNodeFactory) : CirJsonGenerator() {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Bit flag composed of bits that indicate which [StreamWriteFeatures][StreamWriteFeature] are enabled.
     * 
     * NOTE: most features have no effect on this class
     */
    protected val myStreamWriteFeatures = DEFAULT_STREAM_WRITE_FEATURES

    /*
     *******************************************************************************************************************
     * Output state
     *******************************************************************************************************************
     */

    protected var myRootWriteContext = RootContext(myNodeFactory)

    protected var myTokenWriteContext: TreeWriteContext = myRootWriteContext

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    open fun treeBuilt(): CirJsonNode? {
        return myRootWriteContext.node
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: context
     *******************************************************************************************************************
     */

    override val streamWriteContext: TokenStreamContext
        get() = myTokenWriteContext

    override fun currentValue(): Any? {
        return myTokenWriteContext.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        myTokenWriteContext.assignCurrentValue(value)
    }

    override val objectWriteContext: ObjectWriteContext?
        get() = myObjectWriteContext

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: configuration, introspection
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    override fun configure(feature: StreamWriteFeature, state: Boolean): CirJsonGenerator {
        return this
    }

    override fun isEnabled(feature: StreamWriteFeature): Boolean {
        return myStreamWriteFeatures and feature.mask != 0
    }

    override val streamWriteFeatures: Int
        get() = myStreamWriteFeatures

    override val streamWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>
        get() = BOGUS_WRITE_CAPABILITIES

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: low-level output handling
     *******************************************************************************************************************
     */

    override fun flush() {
        // No-op
    }

    override fun close() {
        // No-op
    }

    override val isClosed: Boolean
        get() = false

    override val streamWriteOutputTarget: Any?
        get() = null

    override val streamWriteOutputBuffered: Int
        get() = -1

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, structural
     *******************************************************************************************************************
     */

    override fun writeStartArray(): CirJsonGenerator {
        return writeStartArray(null)
    }

    override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        myTokenWriteContext = myTokenWriteContext.createChildArrayContext(currentValue)
        return this
    }

    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        return writeStartArray(currentValue)
    }

    override fun writeEndArray(): CirJsonGenerator {
        if (!myTokenWriteContext.isInArray) {
            return reportError("Current context not Array but ${myTokenWriteContext.typeDescription}")
        }

        myTokenWriteContext = myTokenWriteContext.parent!!
        return this
    }

    override fun writeStartObject(): CirJsonGenerator {
        return writeStartObject(null)
    }

    override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        myTokenWriteContext = myTokenWriteContext.createChildObjectContext(currentValue)
        return this
    }

    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        return writeStartObject(currentValue)
    }

    override fun writeEndObject(): CirJsonGenerator {
        if (!myTokenWriteContext.isInObject) {
            return reportError("Current context not Object but ${myTokenWriteContext.typeDescription}")
        }

        myTokenWriteContext = myTokenWriteContext.parent!!
        return this
    }

    override fun writeName(name: String): CirJsonGenerator {
        myTokenWriteContext.writeName(name)
        return this
    }

    override fun writeName(name: SerializableString): CirJsonGenerator {
        myTokenWriteContext.writeName(name.value)
        return this
    }

    override fun writePropertyId(id: Long): CirJsonGenerator {
        myTokenWriteContext.writeName(id.toString())
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, textual
     *******************************************************************************************************************
     */

    override fun writeString(value: String?): CirJsonGenerator {
        value ?: return writeNull()
        myTokenWriteContext.writeString(value)
        return this
    }

    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        return writeString(String(buffer, offset, length))
    }

    override fun writeString(value: SerializableString): CirJsonGenerator {
        myTokenWriteContext.writeString(value.value)
        return this
    }

    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(text: String): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(raw: SerializableString): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(char: Char): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRawValue(text: String): CirJsonGenerator {
        myTokenWriteContext.writeNode(myNodeFactory.rawValueNode(RawValue(text)))
        return this
    }

    override fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator {
        var realText = text

        if (offset > 0 || length != realText.length) {
            realText = realText.substring(offset, offset + length)
        }

        return writeRawValue(realText)
    }

    override fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator {
        return writeRawValue(String(text, offset, length))
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, textual
     *******************************************************************************************************************
     */

    override fun writeNumber(value: Short): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(value: Int): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(value: Long): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(value: BigInteger?): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(value: Double): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(value: Float): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(value: BigDecimal?): CirJsonGenerator {
        myTokenWriteContext.writeNumber(myNodeFactory.numberNode(value))
        return this
    }

    override fun writeNumber(encodedValue: String?): CirJsonGenerator {
        return writeString(encodedValue)
    }

    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        myTokenWriteContext.writeBoolean(state)
        return this
    }

    override fun writeNull(): CirJsonGenerator {
        myTokenWriteContext.writeNull()
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, POJOs/trees
     *******************************************************************************************************************
     */

    override fun writePOJO(pojo: Any?): CirJsonGenerator {
        pojo ?: return writeNull()

        val raw = pojo::class

        if (raw == ByteArray::class || pojo is RawValue) {
            myTokenWriteContext.writePOJO(pojo)
            return this
        }

        myObjectWriteContext.writeValue(this, pojo)
        return this
    }

    override fun writeTree(rootNode: TreeNode?): CirJsonGenerator {
        rootNode ?: return writeNull()

        if (rootNode is CirJsonNode) {
            myTokenWriteContext.writeNode(rootNode)
        } else {
            myTokenWriteContext.writePOJO(rootNode)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: binary
     *******************************************************************************************************************
     */

    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        myTokenWriteContext.writeBinary(data.copyOfRange(offset, offset + length))
        return this
    }

    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        return reportUnsupportedOperation()
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: native ids
     *******************************************************************************************************************
     */

    override val idName: String
        get() = "__cirJsonId__"

    override fun getID(target: Any, isArray: Boolean): String {
        return reportUnsupportedOperation()
    }

    override fun writeObjectId(referenced: Any): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeArrayId(referenced: Any): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeTypeId(id: Any): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeEmbeddedObject(obj: Any?): CirJsonGenerator {
        myTokenWriteContext.writePOJO(obj)
        return this
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    protected abstract class TreeWriteContext(type: Int, protected val myParent: TreeWriteContext?,
            protected val myNodeFactory: CirJsonNodeFactory, protected var myCurrentValue: Any?) :
            TokenStreamContext() {

        init {
            myType = type
        }

        /*
         ***************************************************************************************************************
         * Accessors
         ***************************************************************************************************************
         */

        override fun currentValue(): Any? {
            return myCurrentValue
        }

        override fun assignCurrentValue(value: Any?) {
            myCurrentValue = value
        }

        final override val parent: TreeWriteContext?
            get() = myParent

        override val currentName: String?
            get() = null

        abstract val node: CirJsonNode?

        /*
         ***************************************************************************************************************
         * Write methods
         ***************************************************************************************************************
         */

        abstract fun createChildArrayContext(currentValue: Any?): TreeWriteContext

        abstract fun createChildObjectContext(currentValue: Any?): TreeWriteContext

        open fun writeName(name: String): Boolean {
            return false
        }

        abstract fun writeBinary(data: ByteArray)

        abstract fun writeBoolean(value: Boolean)

        abstract fun writeNull()

        abstract fun writeNumber(value: ValueNode)

        abstract fun writeString(value: String)

        abstract fun writePOJO(value: Any?)

        abstract fun writeNode(node: CirJsonNode)

    }

    protected class RootContext(nodeFactory: CirJsonNodeFactory) :
            TreeWriteContext(TYPE_ROOT, null, nodeFactory, null) {

        private var myNode: CirJsonNode? = null

        override val node: CirJsonNode?
            get() = myNode

        override fun createChildArrayContext(currentValue: Any?): TreeWriteContext {
            return ArrayContext(this, myNodeFactory, currentValue).also { myNode = it.node }
        }

        override fun createChildObjectContext(currentValue: Any?): TreeWriteContext {
            return ObjectContext(this, myNodeFactory, currentValue).also { myNode = it.node }
        }

        override fun writeBinary(data: ByteArray) {
            myNode = myNodeFactory.binaryNode(data)
        }

        override fun writeBoolean(value: Boolean) {
            myNode = myNodeFactory.booleanNode(value)
        }

        override fun writeNull() {
            myNode = myNodeFactory.nullNode()
        }

        override fun writeNumber(value: ValueNode) {
            myNode = value
        }

        override fun writeString(value: String) {
            myNode = myNodeFactory.textNode(value)
        }

        override fun writePOJO(value: Any?) {
            myNode = myNodeFactory.pojoNode(node)
        }

        override fun writeNode(node: CirJsonNode) {
            myNode = node
        }

    }

    protected class ArrayContext(parent: TreeWriteContext, nodeFactory: CirJsonNodeFactory, currentValue: Any?) :
            TreeWriteContext(TYPE_ARRAY, parent, nodeFactory, currentValue) {

        private val myNode = nodeFactory.arrayNode()

        override val node: CirJsonNode
            get() = myNode

        override fun createChildArrayContext(currentValue: Any?): TreeWriteContext {
            return ArrayContext(this, myNodeFactory, currentValue).also { myNode.add(it.node) }
        }

        override fun createChildObjectContext(currentValue: Any?): TreeWriteContext {
            return ObjectContext(this, myNodeFactory, currentValue).also { myNode.add(it.node) }
        }

        override fun writeBinary(data: ByteArray) {
            myNode.add(data)
        }

        override fun writeBoolean(value: Boolean) {
            myNode.add(value)
        }

        override fun writeNull() {
            myNode.addNull()
        }

        override fun writeNumber(value: ValueNode) {
            myNode.add(value)
        }

        override fun writeString(value: String) {
            myNode.add(value)
        }

        override fun writePOJO(value: Any?) {
            myNode.addPOJO(value)
        }

        override fun writeNode(node: CirJsonNode) {
            myNode.add(node)
        }

    }

    protected class ObjectContext(parent: TreeWriteContext, nodeFactory: CirJsonNodeFactory, currentValue: Any?) :
            TreeWriteContext(TYPE_OBJECT, parent, nodeFactory, currentValue) {

        private val myNode = nodeFactory.objectNode()

        private var myCurrentName: String? = null

        private var myExpectValue = false

        override val node: CirJsonNode
            get() = myNode

        override fun createChildArrayContext(currentValue: Any?): TreeWriteContext {
            verifyValueWrite()
            return ArrayContext(this, myNodeFactory, currentValue).also { myNode[myCurrentName!!] = it.node }
        }

        override fun createChildObjectContext(currentValue: Any?): TreeWriteContext {
            verifyValueWrite()
            return ObjectContext(this, myNodeFactory, currentValue).also { myNode[myCurrentName!!] = it.node }
        }

        override fun writeName(name: String): Boolean {
            myCurrentName = name
            myExpectValue = true
            return true
        }

        override fun writeBinary(data: ByteArray) {
            verifyValueWrite()
            myNode.put(myCurrentName!!, data)
        }

        override fun writeBoolean(value: Boolean) {
            verifyValueWrite()
            myNode.put(myCurrentName!!, value)
        }

        override fun writeNull() {
            verifyValueWrite()
            myNode.putNull(myCurrentName!!)
        }

        override fun writeNumber(value: ValueNode) {
            verifyValueWrite()
            myNode[myCurrentName!!] = value
        }

        override fun writeString(value: String) {
            verifyValueWrite()
            myNode.put(myCurrentName!!, value)
        }

        override fun writePOJO(value: Any?) {
            verifyValueWrite()
            myNode.putPOJO(myCurrentName!!, value)
        }

        override fun writeNode(node: CirJsonNode) {
            verifyValueWrite()
            myNode[myCurrentName!!] = node
        }

        private fun verifyValueWrite() {
            if (!myExpectValue) {
                throw IllegalStateException("Expecting FIELD_NAME, not value")
            }

            myExpectValue = false
        }

    }

    companion object {

        val DEFAULT_STREAM_WRITE_FEATURES = StreamWriteFeature.collectDefaults()

        val BOGUS_WRITE_CAPABILITIES = CirJacksonFeatureSet.fromDefaults(StreamWriteCapability.entries)

        fun forSerialization(context: SerializerProvider, nodeFactory: CirJsonNodeFactory): TreeBuildingGenerator {
            return TreeBuildingGenerator(context, nodeFactory)
        }

    }

}