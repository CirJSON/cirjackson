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
 * Specialized POJO serializer that differs from [org.cirjson.cirjackson.databind.serialization.BeanSerializer] in that,
 * instead of producing a CirJSON Object, it will output a CirJSON Array, omitting field names, and serializing values
 * in specified serialization order. This behavior is usually triggered by using annotation
 * [org.cirjson.cirjackson.annotations.CirJsonFormat] or its equivalents.
 * 
 * This serializer can be used for "simple" instances; and will NOT be used if one of following is true:
 * 
 * * Unwrapping is used (no way to expand out array in CirJSON Object)
 * 
 * * Type information ("type id") is to be used: while this could work for some embedding methods, it would likely cause
 * conflicts.
 * 
 * * Object Identity ("object id") is used: while references would work, the problem is inclusion of id itself.
 * 
 * Note that it is theoretically possible that last 2 issues could be addressed (by reserving room in array, for
 * example); and if so, support improved.
 * 
 * In cases where array-based output is not feasible, this serializer can instead delegate to the original Object-based
 * serializer; this is why a reference is retained to the original serializer.
 */
open class BeanAsArraySerializer : BeanSerializerBase {

    protected val myDefaultSerializer: BeanSerializerBase

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    constructor(source: BeanSerializerBase) : super(source, null as ObjectIdWriter?) {
        myDefaultSerializer = source
    }

    protected constructor(source: BeanSerializerBase, toIgnore: Set<String>?, toInclude: Set<String>?) : super(source,
            toIgnore, toInclude) {
        myDefaultSerializer = source
    }

    protected constructor(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?, filterId: Any?) : super(source,
            objectIdWriter, filterId) {
        myDefaultSerializer = source
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
        return BeanAsArraySerializer(this, myObjectIdWriter, filterId)
    }

    override fun withByNameInclusion(toIgnore: Set<String>?, toInclude: Set<String>?): BeanAsArraySerializer {
        return BeanAsArraySerializer(this, toIgnore, toInclude)
    }

    override fun withProperties(properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?): BeanSerializerBase {
        return this
    }

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
        val filtered = myFilteredProperties != null && serializers.activeView != null

        if (serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED) &&
                hasSingleElement()) {
            if (filtered) {
                serializeFiltered(value, generator, serializers)
            } else {
                serializeNonFiltered(value, generator, serializers)
            }

            return
        }

        generator.writeStartArray(value, myProperties.size)

        if (filtered) {
            serializeFiltered(value, generator, serializers)
        } else {
            serializeNonFiltered(value, generator, serializers)
        }

        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        if (myObjectIdWriter != null) {
            serializeWithObjectId(value, generator, serializers, typeSerializer)
            return
        }

        val typeIdDefinition = typeIdDefinition(typeSerializer, value, CirJsonToken.START_ARRAY)
        typeSerializer.writeTypePrefix(generator, serializers, typeIdDefinition)

        if (myFilteredProperties != null && serializers.activeView != null) {
            serializeFiltered(value, generator, serializers)
        } else {
            serializeNonFiltered(value, generator, serializers)
        }

        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    /*
     *******************************************************************************************************************
     * Property serialization methods
     *******************************************************************************************************************
     */

    private fun hasSingleElement(): Boolean {
        return myProperties.size == 1
    }

    @Throws(CirJacksonException::class)
    protected fun serializeNonFiltered(value: Any, generator: CirJsonGenerator, context: SerializerProvider) {
        val properties = myProperties
        var i = 0
        var left = properties.size
        var property: BeanPropertyWriter? = null

        try {
            if (left > 3) {
                do {
                    properties[i].also { property = it }.serializeAsElement(value, generator, context)
                    properties[i + 1].also { property = it }.serializeAsElement(value, generator, context)
                    properties[i + 2].also { property = it }.serializeAsElement(value, generator, context)
                    properties[i + 3].also { property = it }.serializeAsElement(value, generator, context)
                    left -= 4
                    i += 4
                } while (left > 3)
            }

            if (left == 3) {
                properties[i++].also { property = it }.serializeAsElement(value, generator, context)
            }

            if (left >= 2) {
                properties[i++].also { property = it }.serializeAsElement(value, generator, context)
            }

            if (left >= 1) {
                properties[i].also { property = it }.serializeAsElement(value, generator, context)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, property!!.name)
        } catch (e: StackOverflowError) {
            throw DatabindException.from(generator, "Infinite recursion (StackOverflowError)", e)
                    .prependPath(value, property!!.name)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun serializeFiltered(value: Any, generator: CirJsonGenerator, context: SerializerProvider) {
        val properties = myFilteredProperties!!
        var i = 0
        var left = properties.size
        var property: BeanPropertyWriter? = null

        try {
            if (left > 3) {
                do {
                    property = properties[i]

                    if (property == null) {
                        generator.writeNull()
                    } else {
                        property.serializeAsElement(value, generator, context)
                    }

                    property = properties[i + 1]

                    if (property == null) {
                        generator.writeNull()
                    } else {
                        property.serializeAsElement(value, generator, context)
                    }

                    property = properties[i + 2]

                    if (property == null) {
                        generator.writeNull()
                    } else {
                        property.serializeAsElement(value, generator, context)
                    }

                    property = properties[i + 3]

                    if (property == null) {
                        generator.writeNull()
                    } else {
                        property.serializeAsElement(value, generator, context)
                    }

                    left -= 4
                    i += 4
                } while (left > 3)
            }

            if (left == 3) {
                property = properties[i++]

                if (property == null) {
                    generator.writeNull()
                } else {
                    property.serializeAsElement(value, generator, context)
                }
            }

            if (left >= 2) {
                property = properties[i++]

                if (property == null) {
                    generator.writeNull()
                } else {
                    property.serializeAsElement(value, generator, context)
                }
            }

            if (left >= 1) {
                property = properties[i]

                if (property == null) {
                    generator.writeNull()
                } else {
                    property.serializeAsElement(value, generator, context)
                }
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, property!!.name)
        } catch (e: StackOverflowError) {
            throw DatabindException.from(generator, "Infinite recursion (StackOverflowError)", e)
                    .prependPath(value, property!!.name)
        }
    }

    companion object {

        fun construct(source: BeanSerializerBase): BeanSerializerBase {
            return UnrolledBeanAsArraySerializer.tryConstruct(source) ?: BeanAsArraySerializer(source)
        }

        internal fun construct(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?,
                filterId: Any?): BeanSerializerBase {
            return BeanAsArraySerializer(source, objectIdWriter, filterId)
        }

    }

}