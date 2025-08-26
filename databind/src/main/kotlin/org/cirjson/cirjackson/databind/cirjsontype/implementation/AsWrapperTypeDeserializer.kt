package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.util.CirJsonParserSequence
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Type deserializer used with [CirJsonTypeInfo.As.WRAPPER_OBJECT] inclusion mechanism. Simple since CirJSON structure
 * used is always the same, regardless of structure used for actual value: wrapping is done using a two-elements
 * CirJSON Object where, after a dummy object id, type id is the key, and actual object data as the value.
 */
open class AsWrapperTypeDeserializer : TypeDeserializerBase {

    constructor(baseType: KotlinType?, idResolver: TypeIdResolver, typePropertyName: String?, typeIdVisible: Boolean,
            defaultImplementation: KotlinType?) : super(baseType, idResolver, typePropertyName, typeIdVisible,
            defaultImplementation)

    constructor(source: AsWrapperTypeDeserializer, property: BeanProperty?) : super(source, property)

    override fun forProperty(property: BeanProperty?): TypeDeserializer {
        if (property === myProperty) {
            return this
        }

        return AsWrapperTypeDeserializer(this, property)
    }

    override val typeInclusion: CirJsonTypeInfo.As?
        get() = CirJsonTypeInfo.As.WRAPPER_OBJECT

    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserialize(parser, context)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromArray(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserialize(parser, context)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromScalar(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserialize(parser, context)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromAny(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserialize(parser, context)
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    /**
     * Method that handles type information wrapper, locates actual subtype deserializer to use, and calls it to do
     * actual deserialization.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        var realParser = parser

        if (realParser.isReadingTypeIdPossible) {
            val typeId = realParser.typeId

            if (typeId != null) {
                return deserializeWithNativeTypeId(realParser, context, typeId)
            }
        }

        var token = realParser.currentToken()

        if (token == CirJsonToken.START_OBJECT || token == CirJsonToken.CIRJSON_ID_PROPERTY_NAME) {
            if (token == CirJsonToken.START_OBJECT) {
                realParser.nextToken()
            }

            realParser.nextToken()

            if (realParser.nextToken() != CirJsonToken.PROPERTY_NAME) {
                return context.reportWrongTokenException(baseType()!!, CirJsonToken.PROPERTY_NAME,
                        "need CirJSON String that contains type id (for subtype of ${baseTypeName()})")
            }
        } else if (token != CirJsonToken.PROPERTY_NAME) {
            return context.reportWrongTokenException(baseType()!!, CirJsonToken.PROPERTY_NAME,
                    "need CirJSON Object to contain As.WRAPPER_OBJECT type information for class ${baseTypeName()}")
        }

        val typeId = realParser.text!!
        val deserializer = findDeserializer(context, typeId)
        realParser.nextToken()

        if (myTypeIdVisible && realParser.isExpectedStartObjectToken) {
            val tokenBuffer = context.bufferForInputBuffering(realParser)
            tokenBuffer.writeStartObject()
            tokenBuffer.writeName(ID_NAME)
            tokenBuffer.writeString(System.identityHashCode(Any()).toString())
            tokenBuffer.writeName(myTypePropertyName)
            tokenBuffer.writeString(typeId)
            realParser.clearCurrentToken()
            realParser =
                    CirJsonParserSequence.createFlattened(false, tokenBuffer.asParser(context, realParser), realParser)
            realParser.nextToken()
            realParser.nextToken()
            realParser.nextToken()
        }

        val value = deserializer.deserialize(realParser, context)

        if (realParser.nextToken() != CirJsonToken.END_OBJECT) {
            return context.reportWrongTokenException(baseType()!!, CirJsonToken.END_OBJECT,
                    "expected closing `CirJsonToken.END_OBJECT` after type information and deserialized value")
        }

        return value
    }

}