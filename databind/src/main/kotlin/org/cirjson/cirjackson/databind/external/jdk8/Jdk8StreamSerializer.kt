package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import java.util.stream.Stream

/**
 * Common typed stream serializer
 */
open class Jdk8StreamSerializer : StandardSerializer<Stream<*>> {

    /**
     * Stream elements type (matching T)
     */
    protected val myElementType: KotlinType

    /**
     * Element specific serializer, if any
     */
    @Transient
    protected val myElementSerializer: ValueSerializer<Any>?

    /**
     * Constructor
     *
     * @param streamType Stream type
     *
     * @param elementType Stream elements type (matching T)
     */
    constructor(streamType: KotlinType, elementType: KotlinType) : this(streamType, elementType, null)

    /**
     * Constructor with custom serializer
     *
     * @param streamType Stream type
     *
     * @param elementType Stream elements type (matching T)
     *
     * @param elementSerializer Custom serializer to use for element type
     */
    constructor(streamType: KotlinType, elementType: KotlinType, elementSerializer: ValueSerializer<Any>?) : super(
            streamType) {
        myElementType = elementType
        myElementSerializer = elementSerializer
    }

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        if (myElementType.hasRawClass(Any::class) || !provider.isEnabled(MapperFeature.USE_STATIC_TYPING) &&
                !myElementType.isFinal) {
            return this
        }

        return Jdk8StreamSerializer(provider.typeFactory.constructParametricType(Stream::class, myElementType),
                myElementType, provider.findContentValueSerializer(myElementType, property))
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Stream<*>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        try {
            value.use {
                generator.writeStartArray(it)

                it.forEach { element ->
                    if (myElementSerializer == null) {
                        serializers.writeValue(generator, element)
                    } else if (element == null) {
                        serializers.defaultSerializeNullValue(generator)
                    } else {
                        myElementSerializer.serialize(element, generator, serializers)
                    }
                }

                generator.writeEndArray()
            }
        } catch (e: Exception) {
            wrapAndThrow(serializers, e, value, generator.streamWriteContext().currentIndex)
        }
    }

}