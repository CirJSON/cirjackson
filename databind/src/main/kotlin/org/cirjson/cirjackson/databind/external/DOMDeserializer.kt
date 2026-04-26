package org.cirjson.cirjackson.databind.external

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.standard.FromStringDeserializer
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.reflect.KClass

/**
 * Base for deserializers that allows parsing DOM Documents from CirJSON Strings. Nominal type can be either [Node] or
 * [Document].
 */
abstract class DOMDeserializer<T : Any> protected constructor(clazz: KClass<T>) : FromStringDeserializer<T>(clazz) {

    protected fun parse(value: String): Document {
        return try {
            documentBuilder().parse(InputSource(StringReader(value)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse CirJSON String as XML: ${e.message}", e)
        }
    }

    /**
     * Overridable factory method used to create [DocumentBuilder] for parsing XML as DOM.
     */
    @Throws(ParserConfigurationException::class)
    protected open fun documentBuilder(): DocumentBuilder {
        return DEFAULT_PARSER_FACTORY.newDocumentBuilder()
    }

    /*
     *******************************************************************************************************************
     * Concrete deserializers
     *******************************************************************************************************************
     */

    open class NodeDeserializer : DOMDeserializer<Node>(Node::class) {

        @Throws(IllegalArgumentException::class)
        override fun deserialize(value: String, context: DeserializationContext): Node? {
            return parse(value)
        }

    }

    open class DocumentDeserializer : DOMDeserializer<Document>(Document::class) {

        @Throws(IllegalArgumentException::class)
        override fun deserialize(value: String, context: DeserializationContext): Document? {
            return parse(value)
        }

    }

    companion object {

        @Suppress("HttpUrlsUsage")
        private val DEFAULT_PARSER_FACTORY = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false

            try {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            } catch (_: ParserConfigurationException) {
            }

            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }

            try {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {
            }
        }

    }

}