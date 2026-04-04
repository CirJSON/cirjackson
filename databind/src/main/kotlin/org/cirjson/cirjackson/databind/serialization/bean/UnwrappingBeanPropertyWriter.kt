package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import org.cirjson.cirjackson.databind.util.NameTransformer
import kotlin.reflect.KClass

/**
 * Variant of [BeanPropertyWriter] which will handle unwrapping of CirJSON Object (including of properties of Object
 * within surrounding CirJSON object, and not as subobject).
 */
open class UnwrappingBeanPropertyWriter : BeanPropertyWriter {

    /**
     * Transformer used to add prefix and/or suffix for properties of unwrapped POJO.
     */
    protected val myNameTransformer: NameTransformer

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(base: BeanPropertyWriter, unwrapper: NameTransformer) : super(base) {
        myNameTransformer = unwrapper
    }

    protected constructor(base: BeanPropertyWriter, transformer: NameTransformer, name: SerializedString) : super(base,
            name) {
        myNameTransformer = transformer
    }

    override fun rename(transformer: NameTransformer): BeanPropertyWriter {
        val oldName = myName!!.value
        val newName = transformer.transform(oldName)
        return new(NameTransformer.chainedTransformer(transformer, myNameTransformer), SerializedString(newName))
    }

    /**
     * Overridable factory method used by subclasses
     */
    protected open fun new(transformer: NameTransformer, newName: SerializedString): UnwrappingBeanPropertyWriter {
        return UnwrappingBeanPropertyWriter(this, transformer, newName)
    }

    /*
     *******************************************************************************************************************
     * Overrides, public methods
     *******************************************************************************************************************
     */

    override val isUnwrapping: Boolean
        get() = true

    @Throws(Exception::class)
    override fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        val realValue = get(value) ?: return
        val serializer = mySerializer ?: realValue::class.let {
            val map = myDynamicSerializers!!
            map.serializerFor(it) ?: findAndAddDynamic(map, it, provider)
        }

        if (mySuppressableValue != null) {
            if (MARKER_FOR_EMPTY === mySuppressableValue) {
                if (serializer.isEmpty(provider, realValue)) {
                    return
                }
            } else if (mySuppressableValue == realValue) {
                return
            }
        }

        if (realValue === value) {
            if (handleSelfReference(value, generator, provider, serializer)) {
                return
            }
        }

        if (!serializer.isUnwrappingSerializer) {
            generator.writeName(myName!!)
        }

        if (myTypeSerializer == null) {
            serializer.serialize(realValue, generator, provider)
        } else {
            serializer.serializeWithType(realValue, generator, provider, myTypeSerializer!!)
        }
    }

    override fun assignSerializer(serializer: ValueSerializer<Any>?) {
        if (serializer == null) {
            super.assignSerializer(serializer)
            return
        }

        var transformer = myNameTransformer

        if (serializer.isUnwrappingSerializer && serializer is UnwrappingBeanSerializer) {
            transformer = NameTransformer.chainedTransformer(transformer, serializer.nameTransformerUsed)
        }

        super.assignSerializer(serializer.unwrappingSerializer(transformer))
    }

    /*
     *******************************************************************************************************************
     * Overrides: schema generation
     *******************************************************************************************************************
     */

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        val serializer = provider.findPrimaryPropertySerializer(type, this).unwrappingSerializer(myNameTransformer)

        if (serializer.isUnwrappingSerializer) {
            serializer.acceptCirJsonFormatVisitor(object : CirJsonFormatVisitorWrapper.Base(provider) {

                override fun expectObjectFormat(type: KotlinType): CirJsonObjectFormatVisitor {
                    return objectVisitor
                }

            }, type)
        } else {
            super.depositSchemaProperty(objectVisitor, provider)
        }
    }

    override fun depositSchemaProperty(propertiesNode: ObjectNode, schemaNode: CirJsonNode?) {
        val properties = schemaNode!!["properties"] ?: return
        val iterator = properties.fields()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val name = myNameTransformer.transform(entry.key)
            propertiesNode[name] = entry.value
        }
    }

    /*
     *******************************************************************************************************************
     * Overrides: internal, other
     *******************************************************************************************************************
     */

    override fun findAndAddDynamic(map: PropertySerializerMap, rawType: KClass<*>,
            provider: SerializerProvider): ValueSerializer<Any> {
        var serializer = myNonTrivialBaseType?.let {
            val subtype = provider.constructSpecializedType(it, rawType)
            provider.findPrimaryPropertySerializer(subtype, this)
        } ?: provider.findPrimaryPropertySerializer(rawType, this)

        var transformer = myNameTransformer

        if (serializer.isUnwrappingSerializer && serializer is UnwrappingBeanSerializer) {
            transformer = NameTransformer.chainedTransformer(transformer, serializer.nameTransformerUsed)
        }

        serializer = serializer.unwrappingSerializer(transformer)

        myDynamicSerializers = myDynamicSerializers!!.newWith(rawType, serializer)
        return serializer
    }

}