package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializationFeature
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.util.NameTransformer

open class UnwrappingBeanSerializer : BeanSerializerBase {

    /**
     * Transformer used to add prefix and/or suffix for properties of unwrapped POJO.
     */
    protected val myNameTransformer: NameTransformer

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    /**
     * Constructor used for creating unwrapping instance of a standard `BeanSerializer`
     */
    constructor(source: BeanSerializerBase, transformer: NameTransformer) : super(source) {
        myNameTransformer = transformer
    }

    constructor(source: UnwrappingBeanSerializer, objectIdWriter: ObjectIdWriter?) : super(source, objectIdWriter) {
        myNameTransformer = source.myNameTransformer
    }

    constructor(source: UnwrappingBeanSerializer, objectIdWriter: ObjectIdWriter?, filterId: Any?) : super(source,
            objectIdWriter, filterId) {
        myNameTransformer = source.myNameTransformer
    }

    protected constructor(source: UnwrappingBeanSerializer, toIgnore: Set<String>?) : this(source, toIgnore, null)

    protected constructor(source: UnwrappingBeanSerializer, toIgnore: Set<String>?, toInclude: Set<String>?) : super(
            source, toIgnore, toInclude) {
        myNameTransformer = source.myNameTransformer
    }

    protected constructor(source: UnwrappingBeanSerializer, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?) : super(source, properties, filteredProperties) {
        myNameTransformer = source.myNameTransformer
    }

    /*
     *******************************************************************************************************************
     * Lifecycle: factory methods, fluent factories
     *******************************************************************************************************************
     */

    override fun unwrappingSerializer(unwrapper: NameTransformer): BeanSerializerBase {
        return UnwrappingBeanSerializer(this, unwrapper)
    }

    override val isUnwrappingSerializer: Boolean
        get() = true

    override fun withObjectIdWriter(objectIdWriter: ObjectIdWriter?): BeanSerializerBase {
        return UnwrappingBeanSerializer(this, objectIdWriter)
    }

    override fun withFilterId(filterId: Any?): BeanSerializerBase {
        return UnwrappingBeanSerializer(this, myObjectIdWriter, filterId)
    }

    override fun withByNameInclusion(toIgnore: Set<String>?, toInclude: Set<String>?): BeanSerializerBase {
        return UnwrappingBeanSerializer(this, toIgnore, toInclude)
    }

    override fun withProperties(properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?): BeanSerializerBase {
        return UnwrappingBeanSerializer(this, properties, filteredProperties)
    }

    /**
     * CirJSON Array output cannot be done if unwrapping operation is requested; so implementation will simply return
     * `this`.
     */
    override fun asArraySerializer(): BeanSerializerBase {
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
            serializeWithObjectId(value, generator, serializers, false)
            return
        }

        generator.assignCurrentValue(value)

        if (myPropertyFilterId != null) {
            serializePropertiesFiltered(value, generator, serializers, myPropertyFilterId)
            return
        }

        if (myFilteredProperties != null && serializers.activeView != null) {
            serializePropertiesMaybeView(serializers, generator, serializers, myFilteredProperties)
            return
        }

        serializePropertiesNoView(serializers, generator, serializers, myProperties)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        if (serializers.isEnabled(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)) {
            return serializers.reportBadDefinition(handledType()!!,
                    "Unwrapped property requires use of type information: cannot serialize without disabling `SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS`")
        }

        if (myObjectIdWriter != null) {
            serializeWithObjectId(value, generator, serializers, typeSerializer)
            return
        }

        generator.assignCurrentValue(value)

        if (myPropertyFilterId != null) {
            serializePropertiesFiltered(value, generator, serializers, myPropertyFilterId)
            return
        }

        if (myFilteredProperties != null && serializers.activeView != null) {
            serializePropertiesMaybeView(serializers, generator, serializers, myFilteredProperties)
            return
        }

        serializePropertiesNoView(serializers, generator, serializers, myProperties)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "UnwrappingBeanSerializer for ${handledType()!!.qualifiedName}"
    }

    /*
     *******************************************************************************************************************
     * Internal access
     *******************************************************************************************************************
     */

    internal val nameTransformerUsed: NameTransformer
        get() = myNameTransformer

}