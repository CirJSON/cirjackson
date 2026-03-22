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
 * Efficient implement for serializing [Collections][Collection] that contain Strings. The only complexity is due to
 * possibility that serializer for [String] may be overridden; because of this, logic is needed to ensure that the
 * default serializer is in use to use the fastest mode, or if not, to defer to custom String serializer.
 */
@CirJacksonStandardImplementation
open class StringCollectionSerializer : StaticListSerializerBase<Collection<String?>> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor() : super(Collection::class)

    protected constructor(source: StringCollectionSerializer, unwrapSingle: Boolean?) : super(source, unwrapSingle)

    override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
        return StringCollectionSerializer(this, unwrapSingle)
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
    override fun serialize(value: Collection<String?>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val size = value.size

        if (size == 1) {
            if (myUnwrapSingle ?: serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
                serializeContents(value, generator, serializers)
                return
            }
        }

        generator.writeStartArray(value, size)
        serializeContents(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Collection<String?>, generator: CirJsonGenerator,
            serializers: SerializerProvider, typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_ARRAY))
        generator.assignCurrentValue(value)
        serializeContents(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    private fun serializeContents(value: Collection<String?>, generator: CirJsonGenerator,
            context: SerializerProvider) {
        var i = 0

        try {
            for (string in value) {
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

        val INSTANCE = StringCollectionSerializer()

    }

}