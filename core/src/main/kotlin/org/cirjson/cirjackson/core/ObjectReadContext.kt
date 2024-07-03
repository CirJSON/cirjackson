package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import java.io.InputStream
import java.io.Reader

/**
 * Defines API for accessing configuration and state exposed by higher level databind functionality during read (token
 * stream to Object deserialization) process. Access is mostly needed during construction of [CirJsonParser] instances
 * by [TokenStreamFactory].
 */
interface ObjectReadContext {

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    val schema: FormatSchema?

    fun getStreamReadFeatures(defaults: Int): Int

    fun getFormatReadFeatures(defaults: Int): Int

    val tokenStreamFactory: TokenStreamFactory

    val streamReadConstraints: StreamReadConstraints

    /*
     *******************************************************************************************************************
     * Parser construction
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun createParser(input: InputStream): CirJsonParser {
        return tokenStreamFactory.createParser(this, input)
    }

    @Throws(CirJacksonException::class)
    fun createParser(reader: Reader): CirJsonParser {
        return tokenStreamFactory.createParser(this, reader)
    }

    @Throws(CirJacksonException::class)
    fun createParser(content: String): CirJsonParser {
        return tokenStreamFactory.createParser(this, content)
    }

    @Throws(CirJacksonException::class)
    fun createParser(content: ByteArray): CirJsonParser {
        return tokenStreamFactory.createParser(this, content)
    }

    @Throws(CirJacksonException::class)
    fun createParser(content: ByteArray, offset: Int, length: Int): CirJsonParser {
        return tokenStreamFactory.createParser(this, content, offset, length)
    }

    /*
     *******************************************************************************************************************
     * Databinding callbacks, tree node creation
     *******************************************************************************************************************
     */

    /**
     * Method for constructing Array nodes for Tree Model instances.
     *
     * @return Array node created
     */
    fun createArrayNode(): ArrayTreeNode

    /**
     * Method for constructing Object nodes for Tree Model instances.
     *
     * @return Object node created
     */
    fun createObjectNode(): ObjectTreeNode

    /*
     *******************************************************************************************************************
     * Databinding callbacks, tree deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun <T : TreeNode> readTree(parser: CirJsonParser): T

    fun treeAsTokens(node: TreeNode): CirJsonParser {
        return node.traverse(this)
    }

    /*
     *******************************************************************************************************************
     * Databinding callbacks, non-tree value deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun <T> readValue(parser: CirJsonParser, clazz: Class<T>): T

    @Throws(CirJacksonException::class)
    fun <T> readValue(parser: CirJsonParser, typeReference: TypeReference<T>): T

    @Throws(CirJacksonException::class)
    fun <T> readValue(parser: CirJsonParser, resolvedType: ResolvedType): T

    open class Base : ObjectReadContext {

        override val schema: FormatSchema? = null

        override fun getStreamReadFeatures(defaults: Int): Int {
            return defaults
        }

        override fun getFormatReadFeatures(defaults: Int): Int {
            return defaults
        }

        override val tokenStreamFactory: TokenStreamFactory
            get() = reportUnsupportedOperation()

        override val streamReadConstraints: StreamReadConstraints
            get() = StreamReadConstraints.defaults()

        override fun createArrayNode(): ArrayTreeNode {
            return reportUnsupportedOperation()
        }

        override fun createObjectNode(): ObjectTreeNode {
            return reportUnsupportedOperation()
        }

        override fun <T : TreeNode> readTree(parser: CirJsonParser): T {
            return reportUnsupportedOperation()
        }

        override fun <T> readValue(parser: CirJsonParser, clazz: Class<T>): T {
            return reportUnsupportedOperation()
        }

        override fun <T> readValue(parser: CirJsonParser, typeReference: TypeReference<T>): T {
            return reportUnsupportedOperation()
        }

        override fun <T> readValue(parser: CirJsonParser, resolvedType: ResolvedType): T {
            return reportUnsupportedOperation()
        }

        private fun <T> reportUnsupportedOperation(): T {
            throw UnsupportedOperationException(
                    "Operation not supported by `ObjectReadContext` of type ${javaClass.name}")
        }

    }

    companion object {

        private val EMPTY_CONTEXT = Base()

        fun empty(): ObjectReadContext {
            return EMPTY_CONTEXT
        }

    }

}