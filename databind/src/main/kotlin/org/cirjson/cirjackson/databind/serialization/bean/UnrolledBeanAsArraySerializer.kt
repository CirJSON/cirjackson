package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.util.NameTransformer

/**
 * Specialization of [BeanAsArraySerializer], optimized for handling small number of properties where calls to property
 * handlers can be "unrolled" by eliminated looping. This can help optimize execution significantly for some backends.
 */
open class UnrolledBeanAsArraySerializer : BeanSerializerBase {

    /**
     * Serializer that would produce CirJSON Object version; used in cases where array output cannot be used.
     */
    protected val myDefaultSerializer: BeanSerializerBase

    protected val myPropertyCount: Int

    protected var myProperty1: BeanPropertyWriter? = null

    protected var myProperty2: BeanPropertyWriter? = null

    protected var myProperty3: BeanPropertyWriter? = null

    protected var myProperty4: BeanPropertyWriter? = null

    protected var myProperty5: BeanPropertyWriter? = null

    protected var myProperty6: BeanPropertyWriter? = null

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    constructor(source: BeanSerializerBase) : super(source, null as ObjectIdWriter?) {
        myDefaultSerializer = source
        myPropertyCount = myProperties.size
        calculateUnrolled()
    }

    protected constructor(source: BeanSerializerBase, toIgnore: Set<String>?, toInclude: Set<String>?) : super(source,
            toIgnore, toInclude) {
        myDefaultSerializer = source
        myPropertyCount = myProperties.size
        calculateUnrolled()
    }

    private fun calculateUnrolled() {
        val properties = arrayOfNulls<BeanPropertyWriter>(MAX_COUNT)
        val offset = 6 - myPropertyCount
        myProperties.copyInto(properties, offset)

        myProperty1 = properties[0]
        myProperty2 = properties[1]
        myProperty3 = properties[2]
        myProperty4 = properties[3]
        myProperty5 = properties[4]
        myProperty6 = properties[5]
    }

    /*
     *******************************************************************************************************************
     * Lifecycle: factory methods, fluent factories
     *******************************************************************************************************************
     */

    override fun unwrappingSerializer(unwrapper: NameTransformer): BeanSerializerBase {
        return myDefaultSerializer.unwrappingSerializer(unwrapper)
    }

    override fun withObjectIdWriter(objectIdWriter: ObjectIdWriter?): BeanSerializerBase {
        return myDefaultSerializer.withObjectIdWriter(objectIdWriter)
    }

    override fun withFilterId(filterId: Any?): BeanSerializerBase {
        return BeanAsArraySerializer.construct(myDefaultSerializer, myObjectIdWriter, filterId)
    }

    override fun withByNameInclusion(toIgnore: Set<String>?, toInclude: Set<String>?): UnrolledBeanAsArraySerializer {
        return UnrolledBeanAsArraySerializer(this, toIgnore, toInclude)
    }

    override fun withProperties(properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?): BeanSerializerBase {
        return this
    }

    override fun asArraySerializer(): BeanSerializerBase {
        return this
    }

    override fun resolve(provider: SerializerProvider) {
        super.resolve(provider)
        calculateUnrolled()
    }

    /*
     *******************************************************************************************************************
     * ValueSerializer implementation that differs between implementations
     *******************************************************************************************************************
     */

    /**
     * Main serialization method that will delegate actual output to configured [BeanPropertyWriter] instances.
     */
    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED) &&
                hasSingleElement()) {
            serializeNonFiltered(value, generator, serializers)
            return
        }

        generator.writeStartArray(value, myPropertyCount)
        serializeNonFiltered(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeIdDefinition(typeSerializer, value, CirJsonToken.START_ARRAY)
        typeSerializer.writeTypePrefix(generator, serializers, typeIdDefinition)
        serializeNonFiltered(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    /*
     *******************************************************************************************************************
     * Property serialization methods
     *******************************************************************************************************************
     */

    private fun hasSingleElement(): Boolean {
        return myPropertyCount == 1
    }

    @Throws(CirJacksonException::class)
    protected fun serializeNonFiltered(value: Any, generator: CirJsonGenerator, context: SerializerProvider) {
        var property: BeanPropertyWriter? = null

        try {
            if (myPropertyCount !in 0..5) {
                myProperty1!!.also { property = it }.serializeAsElement(value, generator, context)
            }

            if (myPropertyCount >= 5) {
                myProperty2!!.also { property = it }.serializeAsElement(value, generator, context)
            }

            if (myPropertyCount >= 4) {
                myProperty3!!.also { property = it }.serializeAsElement(value, generator, context)
            }

            if (myPropertyCount >= 3) {
                myProperty4!!.also { property = it }.serializeAsElement(value, generator, context)
            }

            if (myPropertyCount >= 2) {
                myProperty5!!.also { property = it }.serializeAsElement(value, generator, context)
            }

            if (myPropertyCount >= 1) {
                myProperty6!!.also { property = it }.serializeAsElement(value, generator, context)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, property!!.name)
        } catch (e: StackOverflowError) {
            throw DatabindException.from(generator, "Infinite recursion (StackOverflowError)", e)
                    .prependPath(value, property!!.name)
        }
    }

    companion object {

        const val MAX_COUNT = 6

        /**
         * Factory method that will construct optimized instance if all the constraints are obeyed; or, if not, return
         * `null` to indicate that instance cannot be created.
         */
        fun tryConstruct(source: BeanSerializerBase): UnrolledBeanAsArraySerializer? {
            if (source.propertyCount() > MAX_COUNT || source.filterId != null || source.hasViewProperties()) {
                return null
            }

            return UnrolledBeanAsArraySerializer(source)
        }

    }

}