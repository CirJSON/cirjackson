package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import java.io.OutputStream
import java.io.Writer

/**
 * Defines API for accessing configuration and state exposed by higher level databind functionality during write (Object
 * to token stream serialization) process. Access is mostly needed during construction of [CirJsonGenerator] instances
 * by [TokenStreamFactory].
 */
interface ObjectWriteContext {

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    val schema: FormatSchema?

    val characterEscapes: CharacterEscapes?

    /**
     * Accessor for getting [PrettyPrinter] instance to use for a new generator. Note that this MUST BE a thread-safe
     * instance: that is, if the pretty printer implementation is stateful, a new unshared instance needs to be returned
     * -- caller will NOT try to make a copy of [org.cirjson.cirjackson.core.util.Instantiatable] printers, context must
     * do that.
     */
    val prettyPrinter: PrettyPrinter?

    /**
     * Accessor similar to [prettyPrinter] but which only indicates whether a non-`null` instance would be constructed
     * if requested, or not. This is useful for backends that have custom pretty-printing instead of relying on
     * CirJackson standard mechanism.
     */
    val isPrettyPrinterNotNull: Boolean

    fun getRootValueSeparator(defaultSeparator: SerializableString): SerializableString

    fun getStreamWriteFeatures(defaults: Int): Int

    fun getFormatWriteFeatures(defaults: Int): Int

    val tokenStreamFactory: TokenStreamFactory

    /*
     *******************************************************************************************************************
     * Generator construction
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun createGenerator(output: OutputStream): CirJsonGenerator {
        return tokenStreamFactory.createGenerator(this, output)
    }

    @Throws(CirJacksonException::class)
    fun createGenerator(output: OutputStream, encoding: CirJsonEncoding): CirJsonGenerator {
        return tokenStreamFactory.createGenerator(this, output, encoding)
    }

    @Throws(CirJacksonException::class)
    fun createGenerator(writer: Writer): CirJsonGenerator {
        return tokenStreamFactory.createGenerator(this, writer)
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
     * Databinding callbacks, value serialization
     *******************************************************************************************************************
     */

    /**
     * Method that may be called to serialize given value, using specified token stream generator.
     *
     * @param generator Generator to use for serialization
     *
     * @param value Java value to be serialized
     *
     * @throws CirJacksonIOException for low-level write problems,
     *
     * @throws StreamWriteException for encoding problems
     *
     * @throws CirJacksonException (various subtypes) for databinding problems
     */
    @Throws(CirJacksonException::class)
    fun writeValue(generator: CirJsonGenerator, value: Any?)

    @Throws(CirJacksonException::class)
    fun writeTree(generator: CirJsonGenerator, value: TreeNode)

    open class Base : ObjectWriteContext {

        override val schema: FormatSchema?
            get() = null

        override val characterEscapes: CharacterEscapes?
            get() = null

        override val prettyPrinter: PrettyPrinter?
            get() = null

        override val isPrettyPrinterNotNull: Boolean
            get() = prettyPrinter != null

        override fun getRootValueSeparator(defaultSeparator: SerializableString): SerializableString {
            return defaultSeparator
        }

        override fun getStreamWriteFeatures(defaults: Int): Int {
            return defaults
        }

        override fun getFormatWriteFeatures(defaults: Int): Int {
            return defaults
        }

        override val tokenStreamFactory: TokenStreamFactory
            get() = reportUnsupportedOperation()

        override fun createArrayNode(): ArrayTreeNode {
            return reportUnsupportedOperation()
        }

        override fun createObjectNode(): ObjectTreeNode {
            return reportUnsupportedOperation()
        }

        override fun writeValue(generator: CirJsonGenerator, value: Any?) {
            return reportUnsupportedOperation()
        }

        override fun writeTree(generator: CirJsonGenerator, value: TreeNode) {
            return reportUnsupportedOperation()
        }

        private fun <T> reportUnsupportedOperation(): T {
            throw UnsupportedOperationException(
                    "Operation not supported by `ObjectWriteContext` of type ${javaClass.name}")
        }

    }

    companion object {

        private val EMPTY_CONTEXT = Base()

        fun empty(): ObjectWriteContext {
            return EMPTY_CONTEXT
        }

    }

}