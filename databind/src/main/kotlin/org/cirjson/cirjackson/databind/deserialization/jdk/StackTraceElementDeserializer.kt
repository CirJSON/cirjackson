package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer

open class StackTraceElementDeserializer protected constructor(
        protected val myAdapterDeserializer: ValueDeserializer<*>?) :
        StandardScalarDeserializer<StackTraceElement>(StackTraceElement::class) {

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): StackTraceElement? {
        val token = parser.currentToken()

        return if (token == CirJsonToken.START_OBJECT || token == CirJsonToken.PROPERTY_NAME) {
            val adapter = if (myAdapterDeserializer == null) {
                context.readValue(parser, Adapter::class)
            } else {
                myAdapterDeserializer.deserialize(parser, context) as Adapter?
            }!!
            constructValue(context, adapter)
        } else if (token == CirJsonToken.START_ARRAY &&
                context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
            parser.nextToken()
            val value = deserialize(parser, context)

            if (parser.nextToken() != CirJsonToken.END_ARRAY) {
                handleMissingEndArrayForSingle(parser, context)
            }

            value
        } else {
            context.handleUnexpectedToken(getValueType(context), parser) as StackTraceElement?
        }
    }

    protected open fun constructValue(context: DeserializationContext, adapter: Adapter): StackTraceElement {
        return constructValue(context, adapter.className, adapter.methodName, adapter.fileName, adapter.lineNumber,
                adapter.moduleName, adapter.moduleVersion, adapter.classLoaderName)
    }

    /**
     * Overridable factory method used for constructing [StackTraceElements][StackTraceElement].
     */
    protected open fun constructValue(context: DeserializationContext, className: String, methodName: String,
            fileName: String?, lineNumber: Int, moduleName: String?, moduleVersion: String?,
            classLoaderName: String?): StackTraceElement {
        return StackTraceElement(className, methodName, fileName, lineNumber)
    }

    /**
     * Intermediate class used both for convenience of binding and to support `PropertyNamingStrategy`.
     */
    class Adapter {

        var className = ""

        var classLoaderName: String? = null

        var declaringClass: String? = null

        var format: String? = null

        var fileName = ""

        var methodName = ""

        var lineNumber = -1

        var moduleName: String? = null

        var moduleVersion: String? = null

        var nativeMethod = false

    }

    companion object {

        fun construct(context: DeserializationContext): ValueDeserializer<*> {
            val adapterDeserializer = context.findRootValueDeserializer(context.constructType(Adapter::class)!!)
            return StackTraceElementDeserializer(adapterDeserializer)
        }

    }

}