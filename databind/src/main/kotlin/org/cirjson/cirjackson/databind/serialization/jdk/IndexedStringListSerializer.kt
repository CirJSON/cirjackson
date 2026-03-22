package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonArrayFormatVisitor
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * Efficient implementation for serializing [Lists][List] that contains Strings and are random-accessible (like
 * [ArrayList]). The only complexity is due to possibility that serializer for [String] may be overridden; because of
 * this, logic is needed to ensure that the default serializer is in use to use the fastest mode, or if not, to defer to
 * custom String serializer.
 */
@CirJacksonStandardImplementation
class IndexedStringListSerializer : StaticListSerializerBase<List<String?>> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    private constructor() : super(List::class)

    constructor(source: IndexedStringListSerializer, unwrapSingle: Boolean?) : super(source, unwrapSingle)

    override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
        return IndexedStringListSerializer(this, unwrapSingle)
    }

    override fun contentSchema(): CirJsonNode {
        return createSchemaNode("string", true)
    }

    override fun acceptContentVisitor(visitor: CirJsonArrayFormatVisitor) {
        visitor.itemsFormat(CirJsonFormatTypes.STRING)
    }

    /*
     *******************************************************************************************************************
     * Actual serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: List<String?>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val size = value.size

        if (size == 1) {
            if (myUnwrapSingle ?: serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
                serializeContents(value, generator, serializers, size)
                return
            }
        }

        generator.writeStartArray(value, size)
        serializeContents(value, generator, serializers, size)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: List<String?>, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_ARRAY))
        generator.assignCurrentValue(value)
        serializeContents(value, generator, serializers, value.size)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    private fun serializeContents(value: List<String?>, generator: CirJsonGenerator, context: SerializerProvider,
            size: Int) {
        var i = 0

        try {
            while (i < size) {
                val string = value[i]

                if (string == null) {
                    context.defaultSerializeNullValue(generator)
                } else {
                    generator.writeString(string)
                }

                i++
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, i)
        }
    }

    companion object {

        val INSTANCE = IndexedStringListSerializer()

    }

}