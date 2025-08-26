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
 * Type deserializer used with [CirJsonTypeInfo.As.WRAPPER_ARRAY] inclusion mechanism. Simple since CirJSON structure
 * used is always the same, regardless of structure used for actual value: wrapping is done using a 3-element CirJSON
 * Array where object id is the first element, type id is the second element, and actual object data as third element.
 */
open class AsArrayTypeDeserializer : TypeDeserializerBase {

    constructor(baseType: KotlinType?, idResolver: TypeIdResolver, typePropertyName: String?, typeIdVisible: Boolean,
            defaultImplementation: KotlinType?) : super(baseType, idResolver, typePropertyName, typeIdVisible,
            defaultImplementation)

    constructor(source: AsArrayTypeDeserializer, property: BeanProperty?) : super(source, property)

    override fun forProperty(property: BeanProperty?): TypeDeserializer {
        if (property === myProperty) {
            return this
        }

        return AsArrayTypeDeserializer(this, property)
    }

    override val typeInclusion: CirJsonTypeInfo.As?
        get() = CirJsonTypeInfo.As.WRAPPER_ARRAY

    /**
     * Method called when actual object is serialized as CirJSON Array.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromArray(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserialize(parser, context)
    }

    /**
     * Method called when actual object is serialized as CirJSON Object.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
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

        val hadStartArray = realParser.isExpectedStartArrayToken
        val typeId = locateTypeId(realParser, context)
        val deserializer = findDeserializer(context, typeId)

        if (myTypeIdVisible && !usesExternalId() && realParser.isExpectedStartObjectToken) {
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

        if (hadStartArray && realParser.currentToken() == CirJsonToken.END_ARRAY) {
            return deserializer.getNullValue(context)
        }

        val value = deserializer.deserialize(realParser, context)

        if (hadStartArray && realParser.currentToken() != CirJsonToken.END_ARRAY) {
            return context.reportWrongTokenException(baseType()!!, CirJsonToken.END_ARRAY,
                    "expected closing `CirJsonToken.END_ARRAY` after type information and deserialized value")
        }

        return value
    }

    @Throws(CirJacksonException::class)
    protected open fun locateTypeId(parser: CirJsonParser, context: DeserializationContext): String {
        if (!parser.isExpectedStartArrayToken) {
            if (myDefaultImplementation == null) {
                return context.reportWrongTokenException(baseType()!!, CirJsonToken.START_ARRAY,
                        "need Array value to contain `As.WRAPPER_ARRAY` type information for class ${baseTypeName()}")
            }

            return myIdResolver.idFromBaseType(context)!!
        }

        parser.nextToken()
        val token = parser.nextToken()

        if (token != CirJsonToken.VALUE_STRING && !(token != null && token.isScalarValue)) {
            return context.reportWrongTokenException(baseType()!!, CirJsonToken.VALUE_STRING,
                    "need String, Number of Boolean value that contains type id (for subtype of ${baseTypeName()})")
        }

        val result = parser.text!!
        parser.nextToken()
        return result
    }

    protected open fun usesExternalId(): Boolean {
        return false
    }

}