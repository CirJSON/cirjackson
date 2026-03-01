package org.cirjson.cirjackson.databind.external

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import org.w3c.dom.Node
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

open class DOMSerializer : StandardSerializer<Node>(Node::class) {

    protected val myTransformerFactory = try {
        TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setTransformerFactoryAttribute(this, XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setTransformerFactoryAttribute(this, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }
    } catch (e: Exception) {
        throw IllegalStateException("Could not instantiate `TransformerFactory`: ${e.message}", e)
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Node, generator: CirJsonGenerator, serializers: SerializerProvider) {
        try {
            val transformer = myTransformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            transformer.setOutputProperty(OutputKeys.INDENT, "no")
            val result = StreamResult(StringWriter())
            transformer.transform(DOMSource(value), result)
            generator.writeString(result.writer.toString())
        } catch (e: TransformerConfigurationException) {
            throw IllegalStateException("Could not create XML Transformer for writing DOM `Node` value: ${e.message}",
                    e)
        } catch (e: TransformerException) {
            return serializers.reportMappingProblem(e, "DOM `Node` value serialization failed: ${e.message}")
        }
    }

    companion object {

        fun setTransformerFactoryAttribute(transformerFactory: TransformerFactory, name: String, value: Any) {
            try {
                transformerFactory.setAttribute(name, value)
            } catch (e: Exception) {
                System.err.println("[DOMSerializer] Failed to set TransformerFactory attribute: $name")
            }
        }

    }
}