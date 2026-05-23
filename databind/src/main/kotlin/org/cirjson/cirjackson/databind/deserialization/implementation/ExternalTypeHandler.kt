package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.bean.BeanPropertyMap
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyBasedCreator
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyValueBuffer
import org.cirjson.cirjackson.databind.util.TokenBuffer
import java.util.*

/**
 * Helper class that is used to flatten CirJSON structure when using "external type id" (see
 * [org.cirjson.cirjackson.annotations.CirJsonTypeInfo.As.EXTERNAL_PROPERTY]). This is needed to store temporary state
 * and buffer tokens, as the structure is rearranged a bit so that actual type deserializer can resolve type and
 * finalize deserialization.
 */
class ExternalTypeHandler {

    private val myBeanType: KotlinType

    private val myProperties: Array<ExternalTypedProperty>

    /**
     * Mapping from external property ids to one or more indexes; in most cases single index as `Int`, but occasionally
     * same name maps to multiple ones: if so, `List<Int>`.
     */
    private val myNameToPropertyIndex: Map<String, Any>

    private val myTypeIds: Array<String?>

    private val myTokens: Array<TokenBuffer?>

    private constructor(beanType: KotlinType, properties: Array<ExternalTypedProperty>,
            nameToPropertyIndex: Map<String, Any>, typeIds: Array<String?>, tokens: Array<TokenBuffer?>) {
        myBeanType = beanType
        myProperties = properties
        myNameToPropertyIndex = nameToPropertyIndex
        myTypeIds = typeIds
        myTokens = tokens
    }

    private constructor(source: ExternalTypeHandler) {
        myBeanType = source.myBeanType
        myProperties = source.myProperties
        myNameToPropertyIndex = source.myNameToPropertyIndex
        val size = myProperties.size
        myTypeIds = arrayOfNulls(size)
        myTokens = arrayOfNulls(size)
    }

    /**
     * Method called to start collection process by creating non-blueprint instances.
     */
    fun start(): ExternalTypeHandler {
        return ExternalTypeHandler(this)
    }

    /**
     * Method called to see if given property/value pair is an external type id; and if so handle it. This is **only**
     * to be called in case containing POJO has similarly named property as the external type id AND value is of scalar
     * type: otherwise [handlePropertyValue] should be called instead.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    fun handleTypePropertyValue(parser: CirJsonParser, context: DeserializationContext, propertyName: String,
            bean: Any?): Boolean {
        val obj = myNameToPropertyIndex[propertyName] ?: return false
        val typeId = parser.text

        return if (obj is List<*>) {
            var result = false

            for (index in obj as List<Int>) {
                if (handleTypePropertyValue(parser, context, propertyName, bean, typeId, index)) {
                    result = true
                }
            }

            result
        } else {
            handleTypePropertyValue(parser, context, propertyName, bean, typeId, obj as Int)
        }
    }

    @Throws(CirJacksonException::class)
    private fun handleTypePropertyValue(parser: CirJsonParser, context: DeserializationContext, propertyName: String,
            bean: Any?, typeId: String?, index: Int): Boolean {
        val property = myProperties[index]

        if (!property.hasTypePropertyName(propertyName)) {
            return false
        }

        val canDeserialize = bean != null && myTokens[index] != null

        if (canDeserialize) {
            deserializeAndSet(parser, context, bean, index, typeId)
            myTokens[index] = null
        } else {
            myTypeIds[index] = typeId
        }

        return true
    }

    /**
     * Method called to ask handler to handle value of given property, at point where parser points to the first token
     * of the value. Handling can mean either resolving type id it contains (if it matches type property name), or by
     * buffering the value for further use.
     *
     * @return `true`, if the given property was properly handled
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    fun handlePropertyValue(parser: CirJsonParser, context: DeserializationContext, propertyName: String,
            bean: Any?): Boolean {
        val obj = myNameToPropertyIndex[propertyName] ?: return false

        if (obj is List<*>) {
            val iterator = (obj as List<Int>).iterator()
            val index = iterator.next()

            val property = myProperties[index]

            if (property.hasTypePropertyName(propertyName)) {
                val typeId = parser.text
                parser.skipChildren()
                myTypeIds[index] = typeId

                while (iterator.hasNext()) {
                    myTypeIds[iterator.next()] = typeId
                }
            } else {
                val tokens = context.bufferAsCopyOfValue(parser)
                myTokens[index] = tokens

                while (iterator.hasNext()) {
                    myTokens[iterator.next()] = tokens
                }
            }

            return true
        }

        val index = obj as Int
        val property = myProperties[index]

        val canDeserialize = if (property.hasTypePropertyName(propertyName)) {
            myTypeIds[index] = parser.valueAsString
            parser.skipChildren()
            bean != null && myTokens[index] != null
        } else {
            val tokens = context.bufferAsCopyOfValue(parser)
            myTokens[index] = tokens
            bean != null && myTypeIds[index] != null
        }

        if (canDeserialize) {
            val typeId = myTypeIds[index]
            myTypeIds[index] = typeId
            deserializeAndSet(parser, context, bean!!, index, typeId)
            myTokens[index] = null
        }

        return true
    }

    /**
     * Method called after CirJSON Object closes, and has to ensure that all external type ids have been handled.
     */
    @Throws(CirJacksonException::class)
    fun complete(parser: CirJsonParser, context: DeserializationContext, bean: Any): Any {
        for ((i, externalProperty) in myProperties.withIndex()) {
            val typeId = myTypeIds[i]?.also {
                if (myTokens[i] == null) {
                    val property = externalProperty.property

                    return if (property.isRequired ||
                            context.isEnabled(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)) {
                        context.reportPropertyInputMismatch(bean::class, property.name,
                                "Missing property '${property.name}' for external type id '${externalProperty.typePropertyName}'")
                    } else {
                        bean
                    }
                }
            } ?: let {
                val tokens = myTokens[i] ?: continue
                val token = tokens.firstToken()!!

                if (token.isScalarValue) {
                    val buffered = tokens.asParser(context, parser)
                    buffered.nextToken()
                    val property = externalProperty.property
                    val result = TypeDeserializer.deserializeIfNatural(buffered, property.type)

                    if (result != null) {
                        property.set(bean, result)
                        continue
                    }
                }

                if (!externalProperty.hasDefaultType()) {
                    context.reportPropertyInputMismatch(myBeanType, externalProperty.property.name,
                            "Missing external type id property '${externalProperty.typePropertyName}' (and no 'defaultImplementation' specified)")
                } else {
                    externalProperty.getDefaultTypeId(context) ?: context.reportPropertyInputMismatch(myBeanType,
                            externalProperty.property.name,
                            "Invalid default type id for property '${externalProperty.typePropertyName}': `null` returned by TypeIdResolver")
                }
            }

            deserializeAndSet(parser, context, bean, i, typeId)
        }

        return bean
    }

    /**
     * Variant called when creation of the POJO involves buffering of creator properties as well as property-based
     * creator.
     */
    fun complete(parser: CirJsonParser, context: DeserializationContext, buffer: PropertyValueBuffer,
            creator: PropertyBasedCreator): Any {
        val values = Array(myProperties.size) { i ->
            val externalProperty = myProperties[i]
            val typeId = myTypeIds[i] ?: let {
                val tokenBuffer = myTokens[i] ?: return@Array null

                if (tokenBuffer.firstToken() == CirJsonToken.VALUE_NULL) {
                    return@Array null
                }

                if (!externalProperty.hasDefaultType()) {
                    context.reportPropertyInputMismatch(myBeanType, externalProperty.property.name,
                            "Missing external type id property '${externalProperty.typePropertyName}'")
                } else {
                    externalProperty.getDefaultTypeId(context)
                }
            }

            val result = if (myTokens[i] != null) {
                deserialize(parser, context, i, typeId)
            } else if (context.isEnabled(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)) {
                val propertyName = externalProperty.property.name
                return context.reportPropertyInputMismatch(myBeanType, propertyName,
                        "Missing property '$propertyName' for external type id '${externalProperty.typePropertyName}'")
            } else {
                deserializeMissingToken(parser, context, i, typeId)
            }

            val property = externalProperty.property

            if (property.creatorIndex < 0) {
                return@Array result
            }

            buffer.assignParameter(property, result)

            val typeProperty = externalProperty.typeProperty ?: return@Array result

            if (typeProperty.creatorIndex < 0) {
                return@Array result
            }

            val value = if (typeProperty.type.hasRawClass(String::class)) {
                typeId
            } else {
                val tokenBuffer = context.bufferForInputBuffering(parser)
                tokenBuffer.writeString(typeId)
                typeProperty.valueDeserializer!!.deserialize(tokenBuffer.asParserOnFirstToken(context), context)
                        .also { tokenBuffer.close() }
            }

            buffer.assignParameter(typeProperty, value)

            result
        }

        val bean = creator.build(context, buffer)!!

        for (i in myProperties.indices) {
            val property = myProperties[i].property

            if (property.creatorIndex < 0) {
                property.set(bean, values[i])
            }
        }

        return bean
    }

    @Throws(CirJacksonException::class)
    private fun deserialize(parser: CirJsonParser, context: DeserializationContext, index: Int, typeId: String?): Any? {
        val otherParser = myTokens[index]!!.asParser(context, parser)
        val token = otherParser.nextToken()

        if (token == CirJsonToken.VALUE_NULL) {
            return null
        }

        val merged = context.bufferForInputBuffering(parser)
        merged.writeStartArray()
        merged.writeString(typeId)
        merged.copyCurrentStructure(otherParser)
        merged.writeEndArray()

        val mergedParser = merged.asParser(context, parser)
        mergedParser.nextToken()
        return myProperties[index].property.deserialize(mergedParser, context)
    }

    @Throws(CirJacksonException::class)
    private fun deserializeMissingToken(parser: CirJsonParser, context: DeserializationContext, index: Int,
            typeId: String?): Any? {
        val merged = context.bufferForInputBuffering(parser)
        merged.writeStartArray()
        merged.writeString(typeId)
        merged.writeEndArray()

        val mergedParser = merged.asParser(context, parser)
        mergedParser.nextToken()
        return myProperties[index].property.deserialize(mergedParser, context)
    }

    @Throws(CirJacksonException::class)
    private fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, bean: Any, index: Int,
            typeId: String?) {
        typeId ?: return context.reportInputMismatch(myBeanType,
                "Internal error in external Type Id handling: `null` type id passed")

        val otherParser = myTokens[index]!!.asParser(context, parser)
        val token = otherParser.nextToken()

        if (token == CirJsonToken.VALUE_NULL) {
            myProperties[index].property.set(bean, null)
            return
        }

        val merged = context.bufferForInputBuffering(parser)
        merged.writeStartArray()
        merged.writeString(typeId)

        merged.copyCurrentStructure(otherParser)
        merged.writeEndArray()

        val mergedParser = merged.asParser(context, parser)
        mergedParser.nextToken()
        myProperties[index].property.deserializeAndSet(mergedParser, context, bean)
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    class Builder(private val myBeanType: KotlinType) {

        private val myProperties = mutableListOf<ExternalTypedProperty>()

        private val myNameToPropertyIndex = mutableMapOf<String, Any>()

        fun addExternal(property: SettableBeanProperty, typeDeserializer: TypeDeserializer) {
            val index = myProperties.size
            myProperties.add(ExternalTypedProperty(property, typeDeserializer))
            addPropertyIndex(property.name, index)
            addPropertyIndex(typeDeserializer.propertyName, index)
        }

        @Suppress("UNCHECKED_CAST")
        fun addPropertyIndex(name: String, index: Int) {
            when (val property = myNameToPropertyIndex[name]) {
                null -> myNameToPropertyIndex[name] = index
                is MutableList<*> -> (property as MutableList<Any>).add(index)
                else -> LinkedList(listOf(property, index)).let { myNameToPropertyIndex[name] = it }
            }
        }

        /**
         * Method called after all external properties have been assigned, to further link property with polymorphic
         * value with possible property for type id itself. This is needed to support type ids as Creator properties.
         */
        fun build(otherProperties: BeanPropertyMap): ExternalTypeHandler {
            val properties = Array(myProperties.size) { i ->
                myProperties[i].apply {
                    val typePropertyId = typePropertyName
                    val typedProperty = otherProperties.findDefinition(typePropertyId) ?: return@apply
                    linkTypeProperty(typedProperty)
                }
            }

            return ExternalTypeHandler(myBeanType, properties, myNameToPropertyIndex, emptyArray(), emptyArray())
        }

    }

    private class ExternalTypedProperty(val property: SettableBeanProperty,
            private val myTypeDeserializer: TypeDeserializer) {

        val typePropertyName = myTypeDeserializer.propertyName

        var typeProperty: SettableBeanProperty? = null
            private set

        fun linkTypeProperty(property: SettableBeanProperty?) {
            typeProperty = property
        }

        fun hasTypePropertyName(name: String): Boolean {
            return name == typePropertyName
        }

        fun hasDefaultType(): Boolean {
            return myTypeDeserializer.hasDefaultImplementation()
        }

        /**
         * Specialized called when we need to expose type id of `defaultImplementation` when deserializing: we may need
         * to expose it for assignment to a property, or it may be requested as visible for some other reason.
         */
        fun getDefaultTypeId(context: DeserializationContext): String? {
            val defaultType = myTypeDeserializer.defaultImplementation ?: return null
            return myTypeDeserializer.typeIdResolver.idFromValueAndType(context, null, defaultType)
        }

        companion object {

            fun builder(beanType: KotlinType): Builder {
                return Builder(beanType)
            }

        }

    }

}