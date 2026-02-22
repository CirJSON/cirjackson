package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

/**
 * Intermediate base class for serializers used for various arrays.
 *
 * @param T Type of arrays serializer handles
 */
abstract class ArraySerializerBase<T : Any> : StandardContainerSerializer<T> {

    protected val myUnwrapSingle: Boolean?

    protected constructor(type: KClass<T>) : super(type) {
        myUnwrapSingle = null
    }

    protected constructor(source: ArraySerializerBase<*>) : super(source) {
        myUnwrapSingle = source.myUnwrapSingle
    }

    protected constructor(source: ArraySerializerBase<*>, property: BeanProperty?, unwrapSingle: Boolean?) : super(
            source, property) {
        myUnwrapSingle = unwrapSingle
    }

    abstract fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*>

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        property ?: return this

        val format = findFormatOverrides(provider, property, handledType()!!)
        val unwrapSingle = format.getFeature(CirJsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)

        if (unwrapSingle == myUnwrapSingle) {
            return withResolved(property, unwrapSingle)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_ARRAY))
        generator.assignCurrentValue(value)
        serializeContents(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    protected abstract fun serializeContents(value: T, generator: CirJsonGenerator, context: SerializerProvider)

    protected fun shouldUnwrapSingle(context: SerializerProvider): Boolean {
        return myUnwrapSingle ?: context.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
    }

}