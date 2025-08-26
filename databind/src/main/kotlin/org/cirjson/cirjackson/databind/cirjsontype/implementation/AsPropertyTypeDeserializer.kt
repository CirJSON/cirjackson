package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.util.CirJsonParserSequence
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.util.TokenBuffer

/**
 * Type deserializer used with [CirJsonTypeInfo.As.PROPERTY] inclusion mechanism. Uses regular form (additional
 * key/value entry before actual data) when typed object is expressed as CirJSON Object; otherwise behaves similar to
 * how [CirJsonTypeInfo.As.WRAPPER_ARRAY] works. Latter is used if CirJSON representation is polymorphic.
 */
open class AsPropertyTypeDeserializer : AsArrayTypeDeserializer {

    protected val myInclusion: CirJsonTypeInfo.As?

    /**
     * Indicates that we should be strict about handling missing type information.
     */
    protected val myStrictTypeIdHandling: Boolean

    protected val myMessageForMissingId = if (myProperty == null) {
        "missing type id property '$myTypePropertyName'"
    } else {
        "missing type id property '$myTypePropertyName' (for POJO property '${myProperty.name}')"
    }

    constructor(baseType: KotlinType?, idResolver: TypeIdResolver, typePropertyName: String?, typeIdVisible: Boolean,
            defaultImplementation: KotlinType?, inclusion: CirJsonTypeInfo.As?, strictTypeIdHandling: Boolean) : super(
            baseType, idResolver, typePropertyName, typeIdVisible, defaultImplementation) {
        myInclusion = inclusion
        myStrictTypeIdHandling = strictTypeIdHandling
    }

    constructor(source: AsPropertyTypeDeserializer, property: BeanProperty?) : super(source, property) {
        myInclusion = source.myInclusion
        myStrictTypeIdHandling = source.myStrictTypeIdHandling
    }

    override fun forProperty(property: BeanProperty?): TypeDeserializer {
        if (property === myProperty) {
            return this
        }

        return AsPropertyTypeDeserializer(this, property)
    }

    override val typeInclusion: CirJsonTypeInfo.As?
        get() = myInclusion

    /**
     * This is the trickiest thing to handle, since property we are looking for may be anywhere...
     */
    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (parser.isReadingTypeIdPossible) {
            val typeId = parser.typeId

            if (typeId != null) {
                return deserializeWithNativeTypeId(parser, context, typeId)
            }
        }

        var token = parser.currentToken()

        if (token == CirJsonToken.START_OBJECT || token == CirJsonToken.CIRJSON_ID_PROPERTY_NAME) {
            if (token == CirJsonToken.START_OBJECT) {
                parser.nextToken()
            }

            parser.nextToken()
            token = parser.nextToken()
        } else if (token != CirJsonToken.PROPERTY_NAME) {
            return deserializeTypedUsingDefaultImplementation(parser, context, null, myMessageForMissingId)
        }

        var tokenBuffer: TokenBuffer? = null
        val ignoreCase = context.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)

        while (token == CirJsonToken.PROPERTY_NAME) {
            val name = parser.currentName!!
            parser.nextToken()

            if (name == myTypePropertyName || ignoreCase && name.equals(myTypePropertyName, true)) {
                val typeId = parser.valueAsString

                if (typeId != null) {
                    return deserializeTypedForId(parser, context, tokenBuffer, typeId)
                }
            }

            if (tokenBuffer == null) {
                tokenBuffer = context.bufferForInputBuffering(parser)
            }

            tokenBuffer.writeName(name)
            tokenBuffer.copyCurrentStructure(parser)

            token = parser.nextToken()
        }

        return deserializeTypedUsingDefaultImplementation(parser, context, tokenBuffer, myMessageForMissingId)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeTypedForId(parser: CirJsonParser, context: DeserializationContext,
            tokenBuffer: TokenBuffer?, typeId: String): Any? {
        var realParser = parser
        var realTokenBuffer = tokenBuffer

        val deserializer = findDeserializer(context, typeId)

        if (myTypeIdVisible) {
            if (realTokenBuffer == null) {
                realTokenBuffer = context.bufferForInputBuffering(realParser)
            }

            realTokenBuffer.writeName(realParser.currentName!!)
            realTokenBuffer.writeString(typeId)
        }

        if (realTokenBuffer != null) {
            realParser.clearCurrentToken()
            realParser = CirJsonParserSequence.createFlattened(false, realTokenBuffer.asParser(context, realParser),
                    realParser)
        }

        if (realParser.currentToken() != CirJsonToken.END_OBJECT) {
            realParser.nextToken()
        }

        return deserializer.deserialize(realParser, context)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeTypedUsingDefaultImplementation(parser: CirJsonParser,
            context: DeserializationContext, tokenBuffer: TokenBuffer?, priorFailureMessage: String): Any? {
        var realParser = parser

        if (!hasDefaultImplementation()) {
            val result = deserializeIfNatural(realParser, myBaseType!!)

            if (result != null) {
                return result
            }

            if (realParser.isExpectedStartArrayToken) {
                return super.deserializeTypedFromAny(realParser, context)
            }

            if (realParser.hasToken(CirJsonToken.VALUE_STRING)) {
                if (context.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)) {
                    val string = realParser.text!!.trim()

                    if (string.isEmpty()) {
                        return null
                    }
                }
            }
        }

        var deserializer = findDefaultImplementationDeserializer(context)

        if (deserializer == null) {
            val type = if (myStrictTypeIdHandling) handleMissingTypeId(context, priorFailureMessage) else myBaseType
            type ?: return null
            deserializer = context.findContextualValueDeserializer(type, myProperty)!!
        }

        if (tokenBuffer != null) {
            tokenBuffer.writeEndObject()
            realParser = tokenBuffer.asParser(context, realParser)
            realParser.nextToken()
        }

        return deserializer.deserialize(realParser, context)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeTypedFromAny(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (parser.hasToken(CirJsonToken.START_ARRAY)) {
            return super.deserializeTypedFromArray(parser, context)
        }

        return deserializeTypedFromObject(parser, context)
    }

}