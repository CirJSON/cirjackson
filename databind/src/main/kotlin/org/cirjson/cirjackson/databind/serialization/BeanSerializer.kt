package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.serialization.bean.BeanAsArraySerializer
import org.cirjson.cirjackson.databind.serialization.bean.BeanSerializerBase
import org.cirjson.cirjackson.databind.serialization.bean.UnwrappingBeanSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.util.NameTransformer

/**
 * Serializer class that can serialize objects that map to CirJSON Object output. Internally handling is mostly dealt
 * with by a sequence of [BeanPropertyWriters][BeanPropertyWriter] that will handle access value to serialize and call
 * appropriate serializers to write out CirJSON.
 * 
 * Implementation note: we will post-process resulting serializer, to figure out actual serializers for final types.
 * This must be done from [resolve] method, and NOT from constructor; otherwise we could end up with an infinite loop.
 */
@CirJacksonStandardImplementation
open class BeanSerializer : BeanSerializerBase {

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    /**
     * @param builder Builder object that contains collected information that may be needed for serializer
     *
     * @param properties Property writers used for actual serialization
     */
    constructor(type: KotlinType, builder: BeanSerializerBuilder?, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?) : super(type, builder, properties, filteredProperties)

    /**
     * Alternate copy constructor that can be used to construct standard [BeanSerializer] passing an instance of
     * "compatible enough" source serializer.
     */
    protected constructor(source: BeanSerializerBase) : super(source)

    protected constructor(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?) : super(source, objectIdWriter)

    protected constructor(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?, filterId: Any?) : super(source,
            objectIdWriter, filterId)

    protected constructor(source: BeanSerializerBase, toIgnore: Set<String>?, toInclude: Set<String>?) : super(source,
            toIgnore, toInclude)

    protected constructor(source: BeanSerializerBase, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?) : super(source, properties, filteredProperties)

    /*
     *******************************************************************************************************************
     * Lifecycle: factory methods, fluent factories
     *******************************************************************************************************************
     */

    override fun unwrappingSerializer(unwrapper: NameTransformer): BeanSerializerBase {
        return UnwrappingBeanSerializer(this, unwrapper)
    }

    override fun withObjectIdWriter(objectIdWriter: ObjectIdWriter?): BeanSerializerBase {
        return BeanSerializer(this, objectIdWriter)
    }

    override fun withFilterId(filterId: Any?): BeanSerializerBase {
        return BeanSerializer(this, myObjectIdWriter, filterId)
    }

    override fun withByNameInclusion(toIgnore: Set<String>?, toInclude: Set<String>?): BeanSerializerBase {
        return BeanSerializer(this, toIgnore, toInclude)
    }

    override fun withProperties(properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?): BeanSerializerBase {
        return BeanSerializer(this, properties, filteredProperties)
    }

    override fun withIgnoredProperties(ignoredProperties: Set<String>?): ValueSerializer<*>? {
        return BeanSerializer(this, ignoredProperties, null)
    }

    /**
     * Implementation has to check whether as-array serialization is possible reliably; if (and only if) so, will
     * construct a [BeanAsArraySerializer], otherwise will return this serializer as is.
     */
    override fun asArraySerializer(): BeanSerializerBase {
        if (canCreateArraySerializer()) {
            return BeanAsArraySerializer.construct(this)
        }

        return this
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
        if (myObjectIdWriter != null) {
            serializeWithObjectId(value, generator, serializers, true)
            return
        }

        if (myPropertyFilterId != null) {
            generator.writeStartObject(value)
            serializePropertiesFiltered(value, generator, serializers, myPropertyFilterId)
            generator.writeEndObject()
            return
        }

        generator.writeStartObject(value)

        if (myFilteredProperties != null && serializers.activeView != null) {
            serializePropertiesMaybeView(value, generator, serializers, myFilteredProperties)
        } else {
            serializePropertiesNoView(value, generator, serializers, myProperties)
        }

        generator.writeEndObject()
    }

    companion object {

        /**
         * Method for constructing dummy bean serializer; one that never outputs any properties
         */
        fun createDummy(forType: KotlinType, builder: BeanSerializerBuilder?): BeanSerializer {
            return BeanSerializer(forType, builder, NO_PROPS, null)
        }

        internal fun construct(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?,
                filterId: Any?): BeanSerializer {
            return BeanSerializer(source, objectIdWriter, filterId)
        }

        internal fun construct(source: BeanSerializerBase, toIgnore: Set<String>?,
                toInclude: Set<String>?): BeanSerializer {
            return BeanSerializer(source, toIgnore, toInclude)
        }

    }

}